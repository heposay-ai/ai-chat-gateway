package com.example.aichat.proxy.service;

import com.example.aichat.proxy.model.entity.ProxyAccount;
import com.example.aichat.proxy.repository.ProxyAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    @Value("${proxy.health-check.timeout-seconds:10}")
    private int timeoutSeconds;

    private final ProxyAccountRepository accountRepository;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HealthCheckService(ProxyAccountRepository accountRepository,
                               AlertService alertService,
                               ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.alertService = alertService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Map<String, String> checkAccount(Long id) {
        ProxyAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + id));
        return doHealthCheck(account);
    }

    public void checkAll() {
        List<ProxyAccount> accounts = accountRepository.findByStatus(1);
        log.info("[全量健康检查] 总数={}", accounts.size());

        List<ProxyAccount> unhealthyList = new ArrayList<>();
        for (ProxyAccount account : accounts) {
            Map<String, String> result = doHealthCheck(account);
            if ("unhealthy".equals(result.get("healthStatus"))) {
                unhealthyList.add(account);
            }
        }

        // 告警
        if (!unhealthyList.isEmpty()) {
            alertService.sendAlert("健康检查告警",
                    String.format("发现 %d 个不健康账号: %s",
                            unhealthyList.size(),
                            unhealthyList.stream()
                                    .map(a -> a.getName() != null ? a.getName() : a.getEndpointUrl())
                                    .toList().toString()));
        }
    }

    private Map<String, String> doHealthCheck(ProxyAccount account) {
        String healthStatus;
        String healthMessage;

        try {
            String endpoint = account.getEndpointUrl().replaceAll("/$", "");
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/models"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET();

            if (account.getApiKey() != null && !account.getApiKey().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + account.getApiKey());
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 尝试解析模型列表
                try {
                    JsonNode root = objectMapper.readTree(response.body());
                    if (root.has("data") && root.get("data").isArray()) {
                        int modelCount = root.get("data").size();
                        List<String> modelNames = new ArrayList<>();
                        for (JsonNode m : root.get("data")) {
                            if (m.has("id")) {
                                modelNames.add(m.get("id").asText());
                            }
                        }
                        // 更新支持的模型
                        account.setSupportedModels(objectMapper.writeValueAsString(modelNames));
                        healthMessage = modelCount + " models available";
                    } else {
                        healthMessage = "authenticated (no model list)";
                    }
                } catch (Exception e) {
                    healthMessage = "authenticated";
                }
                healthStatus = "healthy";
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                healthStatus = "unhealthy";
                healthMessage = "auth failed: " + response.statusCode();
            } else {
                healthStatus = "unhealthy";
                healthMessage = "HTTP " + response.statusCode();
            }

        } catch (java.net.http.HttpTimeoutException e) {
            healthStatus = "unhealthy";
            healthMessage = "timeout";
        } catch (Exception e) {
            healthStatus = "unhealthy";
            healthMessage = "error: " + e.getMessage();
        }

        // 更新数据库
        account.setHealthStatus(healthStatus);
        account.setHealthMessage(healthMessage);
        account.setHealthCheckAt(LocalDateTime.now());
        accountRepository.save(account);

        log.debug("[健康检查] endpoint={}, status={}, msg={}", account.getEndpointUrl(), healthStatus, healthMessage);
        return Map.of("healthStatus", healthStatus, "healthMessage", healthMessage);
    }
}
