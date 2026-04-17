package com.example.aichat.proxy.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "scan_task", indexes = {
    @Index(name = "idx_scan_task_script", columnList = "script_id"),
    @Index(name = "idx_scan_task_status", columnList = "status")
})
public class ScanTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id")
    private Long scriptId;

    @Column(name = "target_ips", columnDefinition = "LONGTEXT")
    private String targetIps;

    @Column(columnDefinition = "JSON")
    private String params;

    @Column(length = 20, nullable = false)
    private String status = "pending";

    @Column(name = "result_summary", columnDefinition = "JSON")
    private String resultSummary;

    @Column(name = "log_output", columnDefinition = "LONGTEXT")
    private String logOutput;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
