package com.example.aichat.proxy.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "proxy_account", indexes = {
    @Index(name = "idx_proxy_account_status", columnList = "status"),
    @Index(name = "idx_proxy_account_health", columnList = "health_status")
})
public class ProxyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String name;

    @Column(name = "endpoint_url", nullable = false, length = 500)
    private String endpointUrl;

    @Column(name = "api_key", length = 256)
    private String apiKey;

    @Column(name = "auth_header", length = 100)
    private String authHeader;

    @Column(name = "supported_models", columnDefinition = "JSON")
    private String supportedModels;

    @Column(length = 50)
    private String provider;

    @Column(name = "health_status", length = 20, nullable = false)
    private String healthStatus = "unknown";

    @Column(name = "health_check_at")
    private LocalDateTime healthCheckAt;

    @Column(name = "health_message", length = 500)
    private String healthMessage;

    @Column(name = "total_requests")
    private Long totalRequests = 0L;

    @Column(name = "total_tokens_used")
    private Long totalTokensUsed = 0L;

    @Column(name = "total_cost", precision = 12, scale = 4)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "source_ip_id")
    private Long sourceIpId;

    @Column(nullable = false)
    private Integer weight = 1;

    @Column(name = "max_rpm")
    private Integer maxRpm;

    @Column(name = "extra_info", columnDefinition = "JSON")
    private String extraInfo;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
