package com.example.aichat.proxy.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "proxy_ip", indexes = {
    @Index(name = "idx_proxy_ip_status", columnList = "status"),
    @Index(name = "idx_proxy_ip_source", columnList = "source")
})
public class ProxyIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String ip;

    @Column(nullable = false)
    private Integer port;

    @Column(length = 10)
    private String protocol = "http";

    @Column(length = 100)
    private String source;

    @Column(length = 50)
    private String region;

    @Column(length = 500)
    private String tags;

    @Column(length = 20, nullable = false)
    private String status = "pending";

    @Column(name = "last_scan_at")
    private LocalDateTime lastScanAt;

    @Column(length = 500)
    private String remark;

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
