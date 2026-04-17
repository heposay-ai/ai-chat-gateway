package com.example.aichat.proxy.service;

import com.example.aichat.proxy.model.entity.ProxyAccount;
import com.example.aichat.proxy.model.entity.ScanScript;
import com.example.aichat.proxy.model.entity.ScanTask;
import com.example.aichat.proxy.repository.ProxyAccountRepository;
import com.example.aichat.proxy.repository.ScanScriptRepository;
import com.example.aichat.proxy.repository.ScanTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ScanExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ScanExecutorService.class);

    private final ScanScriptRepository scriptRepository;
    private final ScanTaskRepository taskRepository;
    private final ProxyAccountRepository accountRepository;
    private final LitellmScanner litellmScanner;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    public ScanExecutorService(ScanScriptRepository scriptRepository,
                                ScanTaskRepository taskRepository,
                                ProxyAccountRepository accountRepository,
                               LitellmScanner litellmScanner,
                                ObjectMapper objectMapper,
                                ApplicationContext applicationContext) {
        this.scriptRepository = scriptRepository;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.litellmScanner = litellmScanner;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    // ============ Script CRUD ============

    public List<ScanScript> listScripts() {
        return scriptRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public ScanScript createScript(ScanScript script) {
        log.info("[新增扫描脚本] name={}", script.getName());
        return scriptRepository.save(script);
    }

    public ScanScript updateScript(Long id, ScanScript update) {
        ScanScript existing = scriptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在: " + id));
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getDescription() != null) existing.setDescription(update.getDescription());
        if (update.getScriptType() != null) existing.setScriptType(update.getScriptType());
        if (update.getScriptPath() != null) existing.setScriptPath(update.getScriptPath());
        if (update.getDefaultParams() != null) existing.setDefaultParams(update.getDefaultParams());
        return scriptRepository.save(existing);
    }

    public void deleteScript(Long id) {
        log.warn("[删除扫描脚本] id={}", id);
        scriptRepository.deleteById(id);
    }

    // ============ Task ============

    public Page<ScanTask> listTasks(int page, int size) {
        return taskRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public ScanTask getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
    }

    public ScanTask createAndExecuteTask(Long scriptId, String targetIps, String params) {
        ScanScript script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在: " + scriptId));

        ScanTask task = new ScanTask();
        task.setScriptId(scriptId);
        task.setTargetIps(targetIps);
        task.setParams(params);
        task.setStatus("pending");
        task = taskRepository.save(task);

        // 通过 Spring 代理调用 @Async 方法，避免自调用绕过 AOP
        applicationContext.getBean(ScanExecutorService.class).executeAsync(task, script);
        return task;
    }

    @Async
    public void executeAsync(ScanTask task, ScanScript script) {
        long startMs = System.currentTimeMillis();
        log.info("[扫描任务启动] taskId={}, scriptId={}, scriptName={}",
                task.getId(), script.getId(), script.getName());

        task.setStatus("running");
        task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);

        try {
            // 解析目标 IP 列表
            List<String> endpoints = new ArrayList<>();
            if (task.getTargetIps() != null) {
                task.getTargetIps().lines()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty())
                        .forEach(endpoints::add);
            }

            if (endpoints.isEmpty()) {
                log.warn("[扫描终止] taskId={}, 原因=目标IP列表为空", task.getId());
                task.setStatus("failed");
                task.setLogOutput("[ERROR] 目标 IP 列表为空");
                task.setCompletedAt(LocalDateTime.now());
                taskRepository.save(task);
                return;
            }

            // 解析参数（V3 内部使用 1s 连接 + 2s 请求超时，这里的值仅做日志参考）
            int timeout = 3;
            int workers = 200;
            List<String> passwords = null;
            try {
                if (task.getParams() != null) {
                    Map<String, Object> params = objectMapper.readValue(task.getParams(), new TypeReference<>() {});
                    if (params.containsKey("timeout")) {
                        timeout = ((Number) params.get("timeout")).intValue();
                    }
                    if (params.containsKey("workers")) {
                        workers = ((Number) params.get("workers")).intValue();
                    }
                    if (params.containsKey("passwords") && params.get("passwords") instanceof List<?> pwdList) {
                        passwords = pwdList.stream().map(Object::toString).toList();
                    }
                }
            } catch (Exception e) {
                log.warn("[参数解析失败] taskId={}, rawParams={}, error={}",
                        task.getId(), task.getParams(), e.getMessage());
            }

            log.info("[扫描开始] taskId={}, endpoints={}个, timeout={}s, workers={}, 密码数={}",
                    task.getId(), endpoints.size(), timeout, workers,
                    passwords != null ? passwords.size() : "默认(5)");

            // 打印前5个端点用于排查
            int previewCount = Math.min(5, endpoints.size());
            for (int i = 0; i < previewCount; i++) {
                log.debug("[扫描目标预览] taskId={}, [{}/{}] {}",
                        task.getId(), i + 1, endpoints.size(), endpoints.get(i));
            }
            if (endpoints.size() > 5) {
                log.debug("[扫描目标预览] taskId={}, ... 省略其余{}个", task.getId(), endpoints.size() - 5);
            }

            // 调用全流程扫描（密码 + 模型探测），V3 全异步，semaphore(200) 控制并发
            List<LitellmScanner.ScanFullResult> fullResults = litellmScanner.fullScan(
                    endpoints, passwords, timeout, workers, endpoints.size());

            long scanDurationSec = (System.currentTimeMillis() - startMs) / 1000;

            // 将扫描结果入库到账号池（含可用模型）
            int imported = importFullScanResults(fullResults);

            // 统计
            int totalSuccess = 0, totalFailed = 0;
            StringBuilder logBuilder = new StringBuilder();
            for (LitellmScanner.ScanFullResult r : fullResults) {
                if (r.success()) {
                    totalSuccess++;
                    logBuilder.append(String.format("[+] %s -> %d models, best=%s, password=%s\n",
                            r.baseURL(), r.totalModels(), r.bestModel(), r.bestPassword()));
                } else {
                    totalFailed++;
                    logBuilder.append(String.format("[-] %s -> %s\n", r.baseURL(), r.error()));
                }
            }
            log.info("[扫描完成] taskId={}, 耗时={}s, 总端点={}, 成功={}, 失败={}, 入库={}",
                    task.getId(), scanDurationSec, endpoints.size(), totalSuccess, totalFailed, imported);

            // 更新任务
            task.setStatus("completed");
            task.setLogOutput(logBuilder.toString());

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalEndpoints", endpoints.size());
            summary.put("totalSuccess", totalSuccess);
            summary.put("totalFailed", totalFailed);
            summary.put("imported", imported);
            summary.put("durationSeconds", scanDurationSec);
            task.setResultSummary(objectMapper.writeValueAsString(summary));

        } catch (Exception e) {
            long failDurationSec = (System.currentTimeMillis() - startMs) / 1000;
            log.error("[扫描异常] taskId={}, 耗时={}s, errorClass={}, message={}",
                    task.getId(), failDurationSec, e.getClass().getSimpleName(), e.getMessage(), e);
            task.setStatus("failed");
            task.setLogOutput("[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        task.setCompletedAt(LocalDateTime.now());
        try {
            taskRepository.save(task);
            log.info("[扫描任务保存成功] taskId={}, status={}", task.getId(), task.getStatus());
        } catch (Exception e) {
            log.error("[扫描任务保存失败] taskId={}, status={}, logSize={}, error={}",
                    task.getId(), task.getStatus(),
                    task.getLogOutput() != null ? task.getLogOutput().length() : 0,
                    e.getMessage());
        }
    }

    /** 允许入库的模型关键词（小写匹配） */
    private static final List<String> ALLOWED_MODEL_KEYWORDS = List.of(
            "claude", "gpt", "codex", "gemini", "glm"
    );

    /**
     * 判断模型名是否包含允许的关键词
     */
    private static boolean isAllowedModel(String modelName) {
        String lower = modelName.toLowerCase();
        return ALLOWED_MODEL_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /**
     * 将全流程扫描结果（含可用模型）导入到账号池
     * 仅保存包含 claude/gpt/codex/gemini/glm 模型的端点
     */
    private int importFullScanResults(List<LitellmScanner.ScanFullResult> results) {
        int imported = 0;
        int skipped = 0;
        int filtered = 0;

        for (LitellmScanner.ScanFullResult r : results) {
            if (!r.success() || r.bestPassword() == null) {
                log.debug("[入库跳过] ip={}, 原因={}", r.ip(), r.error());
                continue;
            }

            // 过滤：仅保留包含允许关键词的模型
            List<String> allowedModels = r.availableModels().stream()
                    .filter(ScanExecutorService::isAllowedModel)
                    .toList();

            if (allowedModels.isEmpty()) {
                filtered++;
                log.info("[入库过滤] endpoint={}, 原因=无匹配模型(claude/gpt/codex/gemini/glm), 原始模型={}",
                        r.baseURL(), r.availableModels());
                continue;
            }

            try {
                ProxyAccount account = new ProxyAccount();
                account.setEndpointUrl(r.baseURL());
                account.setApiKey(r.bestPassword());

                // 保存认证头（来自 openapi.json 的 securitySchemes）
                account.setAuthHeader(r.authHeader());

                String nameBase = r.baseURL().replaceAll("https?://", "").replaceAll("[:/]", "-");
                if (nameBase.length() > 30) nameBase = nameBase.substring(0, 30);
                account.setName("scan-" + nameBase);

                account.setProvider("litellm");
                account.setHealthStatus(r.bestModel() != null ? "healthy" : "unknown");
                account.setHealthCheckAt(LocalDateTime.now());

                String healthMsg = String.format("models=%d, best=%s",
                        r.totalModels(), r.bestModel());
                account.setHealthMessage(healthMsg);

                // 仅保存过滤后的允许模型
                account.setSupportedModels(objectMapper.writeValueAsString(allowedModels));

                account.setStatus(1);
                accountRepository.save(account);
                imported++;

                String maskedKey = r.bestPassword().length() > 4
                        ? r.bestPassword().substring(0, 4) + "****"
                        : "****";
                log.info("[扫描入库成功] endpoint={}, key={}, header={}, availableModels={}",
                        r.baseURL(), maskedKey, r.authHeader(), r.availableModels().size());
            } catch (Exception e) {
                skipped++;
                log.warn("[扫描入库失败] ip={}, error={}", r.ip(), e.getMessage());
            }
        }

        log.info("[入库汇总] 成功={}, 模型过滤={}, 失败={}", imported, filtered, skipped);
        return imported;
    }
}
