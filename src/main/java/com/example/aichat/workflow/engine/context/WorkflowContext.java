package com.example.aichat.workflow.engine.context;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行上下文
 * 线程安全，存储变量和节点输出，支持模板替换
 */
public class WorkflowContext {

    private final Long executionId;
    private final Long tenantId;
    private final Map<String, String> variables = new ConcurrentHashMap<>();
    private String lastNodeKey;
    private volatile boolean paused = false;
    private String pausedAtNodeKey;

    public WorkflowContext(Long executionId, Long tenantId, String userInput) {
        this.executionId = executionId;
        this.tenantId = tenantId;
        variables.put("userInput", userInput != null ? userInput : "");
        variables.put("executionId", String.valueOf(executionId));
        variables.put("timestamp", String.valueOf(System.currentTimeMillis()));
    }

    public Long getExecutionId() { return executionId; }
    public Long getTenantId() { return tenantId; }

    public void setNodeOutput(String nodeKey, String output) {
        variables.put(nodeKey + "_output", output != null ? output : "");
        variables.put("output", output != null ? output : "");
        this.lastNodeKey = nodeKey;
    }

    public String getNodeOutput(String nodeKey) {
        return variables.get(nodeKey + "_output");
    }

    public void setVariable(String key, String value) {
        variables.put(key, value);
    }

    public String getVariable(String key) {
        return variables.get(key);
    }

    public Map<String, String> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * 模板变量替换: {{userInput}}, {{nodeKey_output}} 等
     * 按 key 长度降序替换，避免短 key 部分匹配长 key
     */
    public String resolveTemplate(String template) {
        if (template == null) return "";
        String result = template;

        List<Map.Entry<String, String>> sorted = new ArrayList<>(variables.entrySet());
        sorted.sort((a, b) -> b.getKey().length() - a.getKey().length());

        for (Map.Entry<String, String> entry : sorted) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder,
                    entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return result;
    }

    public void pauseAtNode(String nodeKey) {
        this.paused = true;
        this.pausedAtNodeKey = nodeKey;
    }

    public boolean isPaused() { return paused; }
    public String getPausedAtNodeKey() { return pausedAtNodeKey; }
    public String getLastNodeKey() { return lastNodeKey; }
}
