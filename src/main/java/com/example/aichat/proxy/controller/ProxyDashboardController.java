package com.example.aichat.proxy.controller;

import com.example.aichat.proxy.service.ProxyDashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proxy/dashboard")
public class ProxyDashboardController {

    private final ProxyDashboardService dashboardService;

    public ProxyDashboardController(ProxyDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return dashboardService.getOverview();
    }

    @GetMapping("/token-trend")
    public List<Map<String, Object>> tokenTrend(@RequestParam(defaultValue = "7") int days) {
        return dashboardService.getTokenTrend(days);
    }

    @GetMapping("/cost-trend")
    public List<Map<String, Object>> costTrend(@RequestParam(defaultValue = "7") int days) {
        return dashboardService.getCostTrend(days);
    }

    @GetMapping("/model-distribution")
    public List<Map<String, Object>> modelDistribution(@RequestParam(defaultValue = "7") int days) {
        return dashboardService.getModelDistribution(days);
    }

    @GetMapping("/account-ranking")
    public List<Map<String, Object>> accountRanking(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return dashboardService.getAccountRanking(days, limit);
    }
}
