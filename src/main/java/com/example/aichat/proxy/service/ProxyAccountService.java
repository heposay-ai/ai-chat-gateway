package com.example.aichat.proxy.service;

import com.example.aichat.proxy.model.entity.ProxyAccount;
import com.example.aichat.proxy.repository.ProxyAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProxyAccountService {

    private static final Logger log = LoggerFactory.getLogger(ProxyAccountService.class);

    private final ProxyAccountRepository accountRepository;

    public ProxyAccountService(ProxyAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Page<ProxyAccount> list(int page, int size, String healthStatus, String keyword) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return accountRepository.findByFilters(
                healthStatus != null && !healthStatus.isBlank() ? healthStatus : null,
                keyword != null && !keyword.isBlank() ? keyword : null,
                pageRequest
        );
    }

    public ProxyAccount create(ProxyAccount account) {
        if (account.getEndpointUrl() == null || account.getEndpointUrl().isBlank()) {
            throw new IllegalArgumentException("端点 URL 不能为空");
        }
        if (account.getHealthStatus() == null) {
            account.setHealthStatus("unknown");
        }
        log.info("[新增账号] endpoint={}", account.getEndpointUrl());
        return accountRepository.save(account);
    }

    public ProxyAccount update(Long id, ProxyAccount update) {
        ProxyAccount existing = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + id));
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getEndpointUrl() != null) existing.setEndpointUrl(update.getEndpointUrl());
        if (update.getApiKey() != null) existing.setApiKey(update.getApiKey());
        if (update.getProvider() != null) existing.setProvider(update.getProvider());
        if (update.getSupportedModels() != null) existing.setSupportedModels(update.getSupportedModels());
        if (update.getWeight() != null) existing.setWeight(update.getWeight());
        if (update.getMaxRpm() != null) existing.setMaxRpm(update.getMaxRpm());
        if (update.getExtraInfo() != null) existing.setExtraInfo(update.getExtraInfo());
        return accountRepository.save(existing);
    }

    public void delete(Long id) {
        log.warn("[删除账号] id={}", id);
        accountRepository.deleteById(id);
    }

    public void enable(Long id) {
        ProxyAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + id));
        account.setStatus(1);
        accountRepository.save(account);
    }

    public void disable(Long id) {
        ProxyAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + id));
        account.setStatus(0);
        accountRepository.save(account);
    }

    public List<ProxyAccount> getHealthyAccounts() {
        return accountRepository.findByStatusAndHealthStatus(1, "healthy");
    }

    public List<ProxyAccount> getEnabledAccounts() {
        return accountRepository.findByStatus(1);
    }

    public long countByHealth(String healthStatus) {
        return accountRepository.countByHealthStatus(healthStatus);
    }

    public long countTotal() {
        return accountRepository.count();
    }
}
