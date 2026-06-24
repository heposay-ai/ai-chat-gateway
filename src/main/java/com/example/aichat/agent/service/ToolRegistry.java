package com.example.aichat.agent.service;

import com.example.aichat.agent.model.Agent;
import com.example.aichat.agent.tool.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Object> toolInstances = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> toolMeta = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolRegistry(WebSearchTool webSearch,
                        KnowledgeBaseTool kbTool,
                        UrlReaderTool urlReader,
                        CurrentTimeTool currentTime,
                        CodeInterpreterTool codeInterpreter,
                        LlmSummarizeTool llmSummarize,
                        FileWriterTool fileWriter,
                        KbWriterTool kbWriter) {
        register("web_search", webSearch, "联网搜索", "搜索互联网获取最新信息",  "🔍");
        register("knowledge_base", kbTool, "知识库查询", "查询企业知识库文档", "🌐");
        register("url_reader", urlReader, "网页读取", "读取指定URL网页内容", "📚");
        register("current_time", currentTime, "当前时间", "获取当前日期和时间", "🕐");
        register("code_interpreter", codeInterpreter, "代码执行", "执行简单数学表达式或JS代码", "🔢");
        register("llm_summarize", llmSummarize, "AI总结", "使用AI对内容进行总结分析改写", "🤖");
        register("file_writer", fileWriter, "文件写入", "将内容保存为本地文件", "💾");
        register("kb_writer", kbWriter, "知识库写入", "将内容写入指定知识库", "📥");
    }

    private void register(String name, Object instance, String label, String description, String icon) {
        toolInstances.put(name, instance);
        toolMeta.put(name, Map.of("name", name, "label", label, "description", description, "icon", icon));
    }

    /**
     * 根据 Agent 的 toolsConfig 返回对应的工具实例列表
     */
    public List<Object> getToolsForAgent(Agent agent) {
        if (agent.getToolsConfig() == null || agent.getToolsConfig().isBlank()) {
            return List.of();
        }

        try {
            List<String> toolNames = objectMapper.readValue(
                    agent.getToolsConfig(), new TypeReference<List<String>>() {});
            List<Object> tools = new ArrayList<>();
            for (String name : toolNames) {
                // 支持 "knowledge_base:3" 格式（工具名:参数），取工具名部分
                String toolKey = name.contains(":") ? name.substring(0, name.indexOf(':')) : name;
                Object tool = toolInstances.get(toolKey);
                if (tool != null) {
                    tools.add(tool);
                } else {
                    log.warn("[ToolRegistry] 未知工具: {}", name);
                }
            }
            return tools;
        } catch (Exception e) {
            log.warn("[ToolRegistry] 解析 toolsConfig 失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 Agent toolsConfig 中提取知识库ID（如有）
     */
    public Long extractKbId(Agent agent) {
        if (agent.getToolsConfig() == null) return null;
        try {
            List<String> toolNames = objectMapper.readValue(
                    agent.getToolsConfig(), new TypeReference<List<String>>() {});
            for (String name : toolNames) {
                if (name.startsWith("knowledge_base:")) {
                    return Long.parseLong(name.substring("knowledge_base:".length()));
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * W6: 工作流引擎直接调用工具（通过反射调用 @Tool 方法）
     */
    public String executeTool(String toolName, String input) {
        Object tool = toolInstances.get(toolName);
        if (tool == null) {
            throw new RuntimeException("工具不存在: " + toolName);
        }

        // 查找带 @Tool 注解的方法并调用
        for (var method : tool.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                try {
                    method.setAccessible(true);
                    Object result;
                    if (method.getParameterCount() == 1) {
                        result = method.invoke(tool, input);
                    } else if (method.getParameterCount() == 0) {
                        result = method.invoke(tool);
                    } else {
                        // 多参数时仅传第一个参数
                        Object[] args = new Object[method.getParameterCount()];
                        args[0] = input;
                        for (int i = 1; i < args.length; i++) args[i] = null;
                        result = method.invoke(tool, args);
                    }
                    return result != null ? result.toString() : "";
                } catch (Exception e) {
                    log.error("[ToolRegistry] 执行工具 {} 失败: {}", toolName, e.getMessage(), e);
                    throw new RuntimeException("工具执行失败: " + e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("工具 " + toolName + " 没有 @Tool 方法");
    }

    /**
     * 获取工具实例（供工作流引擎使用）
     */
    public Object getToolInstance(String toolName) {
        return toolInstances.get(toolName);
    }

    /**
     * 列出所有可用工具（供前端选择）
     */
    public List<Map<String, Object>> listAvailableTools() {
        return new ArrayList<>(toolMeta.values());
    }
}
