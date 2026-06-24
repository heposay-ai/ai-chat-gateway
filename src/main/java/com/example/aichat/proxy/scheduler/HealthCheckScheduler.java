package com.example.aichat.proxy.scheduler;

import com.example.aichat.proxy.service.HealthCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HealthCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckScheduler.class);

    @Value("${proxy.health-check.enabled:true}")
    private boolean enabled;

    private final HealthCheckService healthCheckService;

    public HealthCheckScheduler(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @Scheduled(fixedDelayString = "${proxy.health-check.interval-seconds:300}000")
    public void scheduledHealthCheck() {
        if (!enabled) {
            return;
        }
        log.info("[定时健康检查] 开始执行");
        try {
            healthCheckService.checkAll();
        } catch (Exception e) {
            log.error("[定时健康检查] 异常", e);
        }
    }
}
