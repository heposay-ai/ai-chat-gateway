package com.example.aichat.proxy.controller;

import com.example.aichat.proxy.model.entity.ScanScript;
import com.example.aichat.proxy.model.entity.ScanTask;
import com.example.aichat.proxy.service.ScanExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proxy")
public class ScanScriptController {

    private static final Logger log = LoggerFactory.getLogger(ScanScriptController.class);

    private final ScanExecutorService scanExecutorService;

    public ScanScriptController(ScanExecutorService scanExecutorService) {
        this.scanExecutorService = scanExecutorService;
    }

    // ============ 脚本管理 ============

    @GetMapping("/scripts")
    public List<ScanScript> listScripts() {
        return scanExecutorService.listScripts();
    }

    @PostMapping("/scripts")
    public ScanScript createScript(@RequestBody ScanScript script) {
        return scanExecutorService.createScript(script);
    }

    @PutMapping("/scripts/{id}")
    public ScanScript updateScript(@PathVariable Long id, @RequestBody ScanScript script) {
        return scanExecutorService.updateScript(id, script);
    }

    @DeleteMapping("/scripts/{id}")
    public void deleteScript(@PathVariable Long id) {
        scanExecutorService.deleteScript(id);
    }

    @PostMapping("/scripts/{id}/execute")
    public ScanTask executeScan(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String targetIps = (String) body.getOrDefault("targetIps", "");
        String params = null;
        if (body.containsKey("params")) {
            try {
                params = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body.get("params"));
            } catch (Exception e) {
                log.warn("[参数序列化失败] scriptId={}, error={}", id, e.getMessage());
            }
        }
        int lineCount = targetIps.isBlank() ? 0 : targetIps.split("\n").length;
        log.info("[执行扫描请求] scriptId={}, 目标端点={}个, params={}", id, lineCount, params);
        return scanExecutorService.createAndExecuteTask(id, targetIps, params);
    }

    // ============ 扫描任务 ============

    @GetMapping("/scan-tasks")
    public Page<ScanTask> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return scanExecutorService.listTasks(page, size);
    }

    @GetMapping("/scan-tasks/{id}")
    public ScanTask getTask(@PathVariable Long id) {
        return scanExecutorService.getTask(id);
    }
}
