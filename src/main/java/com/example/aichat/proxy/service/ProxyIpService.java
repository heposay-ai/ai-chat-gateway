package com.example.aichat.proxy.service;

import com.example.aichat.proxy.model.entity.ProxyIp;
import com.example.aichat.proxy.repository.ProxyIpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProxyIpService {

    private static final Logger log = LoggerFactory.getLogger(ProxyIpService.class);

    private final ProxyIpRepository proxyIpRepository;

    public ProxyIpService(ProxyIpRepository proxyIpRepository) {
        this.proxyIpRepository = proxyIpRepository;
    }

    public Page<ProxyIp> list(int page, int size, String status, String keyword) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return proxyIpRepository.findByFilters(
                status != null && !status.isBlank() ? status : null,
                keyword != null && !keyword.isBlank() ? keyword : null,
                pageRequest
        );
    }

    public ProxyIp create(ProxyIp ip) {
        // 格式校验
        if (ip.getIp() == null || ip.getIp().isBlank()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }
        if (ip.getPort() == null || ip.getPort() < 1 || ip.getPort() > 65535) {
            throw new IllegalArgumentException("端口必须在 1-65535 之间");
        }
        if (ip.getProtocol() == null || ip.getProtocol().isBlank()) {
            ip.setProtocol("http");
        }
        if (ip.getStatus() == null) {
            ip.setStatus("pending");
        }
        log.info("[新增IP] {}:{}", ip.getIp(), ip.getPort());
        return proxyIpRepository.save(ip);
    }

    public ProxyIp update(Long id, ProxyIp update) {
        ProxyIp existing = proxyIpRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("IP 不存在: " + id));
        if (update.getIp() != null) existing.setIp(update.getIp());
        if (update.getPort() != null) existing.setPort(update.getPort());
        if (update.getProtocol() != null) existing.setProtocol(update.getProtocol());
        if (update.getSource() != null) existing.setSource(update.getSource());
        if (update.getRegion() != null) existing.setRegion(update.getRegion());
        if (update.getTags() != null) existing.setTags(update.getTags());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        return proxyIpRepository.save(existing);
    }

    public void delete(Long id) {
        log.warn("[删除IP] id={}", id);
        proxyIpRepository.deleteById(id);
    }

    public void batchDelete(List<Long> ids) {
        log.warn("[批量删除IP] ids={}", ids);
        proxyIpRepository.deleteAllById(ids);
    }

    public Map<String, Object> batchImport(List<ProxyIp> items) {
        int imported = 0;
        int skipped = 0;
        List<ProxyIp> toSave = new ArrayList<>();

        for (ProxyIp item : items) {
            if (item.getIp() == null || item.getIp().isBlank()) {
                skipped++;
                continue;
            }
            // 去重: 相同 IP+端口 跳过
            if (proxyIpRepository.findByIpAndPort(item.getIp(), item.getPort()).isPresent()) {
                skipped++;
                continue;
            }
            if (item.getProtocol() == null) item.setProtocol("http");
            if (item.getStatus() == null) item.setStatus("pending");
            toSave.add(item);
            imported++;
        }

        if (!toSave.isEmpty()) {
            proxyIpRepository.saveAll(toSave);
        }

        log.info("[批量导入IP] 导入={}, 跳过={}", imported, skipped);
        return Map.of("imported", imported, "skipped", skipped);
    }

    public List<ProxyIp> findByIds(List<Long> ids) {
        return proxyIpRepository.findByIdIn(ids);
    }

    /**
     * 返回轻量 IP 列表（供扫描选择器使用）
     */
    public List<Map<String, Object>> listAllSimple() {
        return proxyIpRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(ip -> Map.<String, Object>of(
                        "id", ip.getId(),
                        "ip", ip.getIp(),
                        "port", ip.getPort(),
                        "protocol", ip.getProtocol() != null ? ip.getProtocol() : "http",
                        "status", ip.getStatus() != null ? ip.getStatus() : "pending",
                        "source", ip.getSource() != null ? ip.getSource() : "",
                        "region", ip.getRegion() != null ? ip.getRegion() : ""
                ))
                .toList();
    }
}
