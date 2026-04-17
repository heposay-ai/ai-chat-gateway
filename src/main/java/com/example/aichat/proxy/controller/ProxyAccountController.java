package com.example.aichat.proxy.controller;

import com.example.aichat.proxy.model.entity.ProxyAccount;
import com.example.aichat.proxy.service.HealthCheckService;
import com.example.aichat.proxy.service.ProxyAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/proxy/accounts")
public class ProxyAccountController {

    private static final Logger log = LoggerFactory.getLogger(ProxyAccountController.class);

    private final ProxyAccountService accountService;
    private final HealthCheckService healthCheckService;

    public ProxyAccountController(ProxyAccountService accountService,
                                   HealthCheckService healthCheckService) {
        this.accountService = accountService;
        this.healthCheckService = healthCheckService;
    }

    @GetMapping
    public Page<ProxyAccount> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String healthStatus,
            @RequestParam(required = false) String keyword) {
        return accountService.list(page, size, healthStatus, keyword);
    }

    @PostMapping
    public ProxyAccount create(@RequestBody ProxyAccount account) {
        return accountService.create(account);
    }

    @PutMapping("/{id}")
    public ProxyAccount update(@PathVariable Long id, @RequestBody ProxyAccount account) {
        return accountService.update(id, account);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        accountService.delete(id);
    }

    @PostMapping("/{id}/health-check")
    public Map<String, String> healthCheck(@PathVariable Long id) {
        return healthCheckService.checkAccount(id);
    }

    @PostMapping("/health-check-all")
    public Map<String, String> healthCheckAll() {
        healthCheckService.checkAll();
        return Map.of("status", "completed");
    }

    @PutMapping("/{id}/enable")
    public void enable(@PathVariable Long id) {
        accountService.enable(id);
    }

    @PutMapping("/{id}/disable")
    public void disable(@PathVariable Long id) {
        accountService.disable(id);
    }
}
