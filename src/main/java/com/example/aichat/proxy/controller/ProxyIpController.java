package com.example.aichat.proxy.controller;

import com.example.aichat.proxy.model.entity.ProxyIp;
import com.example.aichat.proxy.service.ProxyIpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proxy/ips")
public class ProxyIpController {

    private static final Logger log = LoggerFactory.getLogger(ProxyIpController.class);

    private final ProxyIpService proxyIpService;

    public ProxyIpController(ProxyIpService proxyIpService) {
        this.proxyIpService = proxyIpService;
    }

    @GetMapping
    public Page<ProxyIp> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return proxyIpService.list(page, size, status, keyword);
    }

    @PostMapping
    public ProxyIp create(@RequestBody ProxyIp ip) {
        return proxyIpService.create(ip);
    }

    /**
     * 上传 TXT 文件批量导入 IP
     * 文件每行一个，格式: IP:端口 或 IP（默认端口4000）
     */
    @PostMapping("/batch")
    public Map<String, Object> batchImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", required = false, defaultValue = "file_import") String source) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<ProxyIp> items = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                ProxyIp ip = parseLine(line);
                if (ip != null) {
                    ip.setSource(source);
                    items.add(ip);
                }
            }
            if (items.isEmpty()) {
                throw new IllegalArgumentException("文件中没有有效的 IP 记录");
            }
            log.info("[文件导入IP] file={}, 解析行数={}, source={}", file.getOriginalFilename(), items.size(), source);
            return proxyIpService.batchImport(items);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[文件导入IP失败]", e);
            throw new IllegalArgumentException("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析一行文本为 ProxyIp，支持以下格式:
     *   https://18.138.70.63          → ip=18.138.70.63, port=443, protocol=https
     *   http://4.194.232.251:4000     → ip=4.194.232.251, port=4000, protocol=http
     *   http://47.84.129.214          → ip=47.84.129.214, port=80, protocol=http
     *   192.168.1.1:4000              → ip=192.168.1.1, port=4000, protocol=http
     *   10.0.0.1                      → ip=10.0.0.1, port=4000, protocol=http
     */
    private ProxyIp parseLine(String line) {
        try {
            ProxyIp ip = new ProxyIp();

            if (line.startsWith("http://") || line.startsWith("https://")) {
                // 完整 URL 格式
                URI uri = URI.create(line);
                String protocol = uri.getScheme();      // http 或 https
                String host = uri.getHost();
                int port = uri.getPort();               // -1 表示未指定

                if (host == null || host.isEmpty()) return null;

                ip.setIp(host);
                ip.setProtocol(protocol);
                if (port > 0) {
                    ip.setPort(port);
                } else {
                    ip.setPort("https".equals(protocol) ? 443 : 4000);
                }
            } else {
                // 纯 IP 或 IP:port 格式
                // 注意要从最后一个冒号分割（兼容 IPv6）
                int lastColon = line.lastIndexOf(':');
                if (lastColon > 0) {
                    String hostPart = line.substring(0, lastColon);
                    String portPart = line.substring(lastColon + 1);
                    ip.setIp(hostPart);
                    ip.setPort(safeParsePort(portPart, 4000));
                } else {
                    ip.setIp(line);
                    ip.setPort(4000);
                }
                ip.setProtocol("http");
            }
            return ip;
        } catch (Exception e) {
            log.warn("[行解析跳过] line={}, error={}", line, e.getMessage());
            return null;
        }
    }

    private int safeParsePort(String s, int defaultPort) {
        try {
            int port = Integer.parseInt(s.trim());
            return (port >= 1 && port <= 65535) ? port : defaultPort;
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    @DeleteMapping("/batch")
    public void batchDelete(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null && !ids.isEmpty()) {
            proxyIpService.batchDelete(ids);
        }
    }

    /**
     * 轻量 IP 列表（供扫描选择器使用，不分页）
     */
    @GetMapping("/all-simple")
    public List<Map<String, Object>> allSimple() {
        return proxyIpService.listAllSimple();
    }

    @PutMapping("/{id}")
    public ProxyIp update(@PathVariable Long id, @RequestBody ProxyIp ip) {
        return proxyIpService.update(id, ip);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        proxyIpService.delete(id);
    }
}
