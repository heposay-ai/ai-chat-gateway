package com.example.aichat.proxy.service;

import com.example.aichat.proxy.gateway.ProxyRoutingStrategy;
import com.example.aichat.proxy.model.entity.ProxyAccount;
import com.example.aichat.proxy.model.entity.ProxyRequestLog;
import com.example.aichat.proxy.repository.ProxyAccountRepository;
import com.example.aichat.proxy.repository.ProxyRequestLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProxyGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ProxyGatewayService.class);

    @Value("${proxy.gateway.unsupported-params:context_management,prompt_caching,allowed_openai_params,top_p,top_k,betas}")
    private String unsupportedParams;

    private final ProxyAccountRepository accountRepository;
    private final ProxyRequestLogRepository logRepository;
    private final ProxyRoutingStrategy routingStrategy;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public ProxyGatewayService(ProxyAccountRepository accountRepository,
                                ProxyRequestLogRepository logRepository,
                                ProxyRoutingStrategy routingStrategy,
                                ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.logRepository = logRepository;
        this.routingStrategy = routingStrategy;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 获取所有可用账号的聚合模型列表
     */
    public List<Map<String, Object>> listModels() {
        List<ProxyAccount> accounts = accountRepository.findByStatusAndHealthStatus(1, "healthy");
        Set<String> modelSet = new LinkedHashSet<>();

        for (ProxyAccount account : accounts) {
            if (account.getSupportedModels() != null) {
                try {
                    List<String> models = objectMapper.readValue(account.getSupportedModels(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    modelSet.addAll(models);
                } catch (Exception ignored) {}
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String model : modelSet) {
            result.add(Map.of(
                    "id", model,
                    "object", "model",
                    "created", System.currentTimeMillis() / 1000,
                    "owned_by", "proxy-pool"
            ));
        }
        return result;
    }

    /**
     * 转发 chat completions 请求
     */
    public ResponseEntity<Flux<String>> forwardChatCompletions(String requestBody, String clientIp) {
        long startTime = System.currentTimeMillis();

        // 1. 解析请求
        ObjectNode body;
        try {
            body = (ObjectNode) objectMapper.readTree(requestBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON request body");
        }

        String model = body.has("model") ? body.get("model").asText() : null;
        boolean stream = body.has("stream") && body.get("stream").asBoolean();

        // 2. 清洗参数
        sanitizeBody(body);

        // 3. 选择账号
        List<ProxyAccount> candidates = accountRepository.findByStatusAndHealthStatus(1, "healthy");
        ProxyAccount account = routingStrategy.select(candidates, model);
        if (account == null) {
            throw new IllegalStateException("没有可用的代理账号");
        }

        log.info("[网关转发] model={}, account={}, stream={}", model, account.getName(), stream);

        // 4. 构建请求
        String targetUrl = account.getEndpointUrl().replaceAll("/$", "") + "/chat/completions";
        String serializedBody;
        try {
            serializedBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body");
        }

        if (stream) {
            // 流式响应
            Flux<String> responseFlux = webClient.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + account.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(serializedBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnComplete(() -> logRequest(account, model, startTime, "success", null, clientIp))
                    .doOnError(e -> logRequest(account, model, startTime, "error", e.getMessage(), clientIp));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/event-stream")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(responseFlux);
        } else {
            // 非流式: 同步转发
            Flux<String> responseFlux = webClient.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + account.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(serializedBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnComplete(() -> logRequest(account, model, startTime, "success", null, clientIp))
                    .doOnError(e -> logRequest(account, model, startTime, "error", e.getMessage(), clientIp));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(responseFlux);
        }
    }

    /**
     * 清洗请求体, 移除不支持的参数
     */
    private void sanitizeBody(ObjectNode body) {
        Set<String> params = Set.of(unsupportedParams.split(","));
        for (String param : params) {
            String trimmed = param.trim();
            if (body.has(trimmed)) {
                body.remove(trimmed);
                log.debug("[参数清洗] 移除: {}", trimmed);
            }
        }

        // 移除 anthropic-beta 相关字段
        body.remove("anthropic-beta");
    }

    /**
     * 异步记录请求日志
     */
    @Async
    public void logRequest(ProxyAccount account, String model, long startTime, String status, String error, String clientIp) {
        try {
            ProxyRequestLog requestLog = new ProxyRequestLog();
            requestLog.setAccountId(account.getId());
            requestLog.setAccountName(account.getName());
            requestLog.setModel(model);
            requestLog.setDurationMs((int) (System.currentTimeMillis() - startTime));
            requestLog.setStatus(status);
            requestLog.setErrorMessage(error);
            requestLog.setClientIp(clientIp);
            logRepository.save(requestLog);

            // 更新账号统计
            account.setTotalRequests((account.getTotalRequests() != null ? account.getTotalRequests() : 0) + 1);
            account.setLastUsedAt(LocalDateTime.now());
            accountRepository.save(account);
        } catch (Exception e) {
            log.error("[请求日志] 记录失败", e);
        }
    }

    /**
     * 网关健康信息
     */
    public Map<String, Object> getHealthInfo() {
        long totalAccounts = accountRepository.count();
        long healthyAccounts = accountRepository.countByHealthStatus("healthy");
        return Map.of(
                "status", healthyAccounts > 0 ? "ok" : "no_healthy_accounts",
                "totalAccounts", totalAccounts,
                "healthyAccounts", healthyAccounts
        );
    }
}
