package com.example.aichat.proxy.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "proxy_request_log", indexes = {
    @Index(name = "idx_proxy_log_account_time", columnList = "account_id,created_at"),
    @Index(name = "idx_proxy_log_model_time", columnList = "model,created_at"),
    @Index(name = "idx_proxy_log_created", columnList = "created_at")
})
public class ProxyRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "account_name", length = 100)
    private String accountName;

    @Column(length = 50)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens")
    private Integer completionTokens = 0;

    @Column(name = "total_tokens")
    private Integer totalTokens = 0;

    @Column(name = "estimated_cost", precision = 10, scale = 6)
    private BigDecimal estimatedCost = BigDecimal.ZERO;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
