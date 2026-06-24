package com.example.aichat.proxy.service;

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
import java.util.Map;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    @Value("${proxy.alert.webhook-url:}")
    private String webhookUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AlertService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void sendAlert(String title, String message) {
        // 1. 日志告警 (始终执行)
        log.warn("[ALERT] {} - {}", title, message);

        // 2. Webhook 告警 (如果配置了)
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            sendWebhook(title, message);
        }
    }

    private void sendWebhook(String title, String message) {
        try {
            Map<String, Object> payload = Map.of(
                    "title", title,
                    "message", message,
                    "timestamp", LocalDateTime.now().toString(),
                    "source", "ai-chat-gateway-proxy"
            );

            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[Webhook告警] 发送成功: {}", title);
            } else {
                log.warn("[Webhook告警] 发送失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[Webhook告警] 发送异常: {}", e.getMessage());
        }
    }
}
