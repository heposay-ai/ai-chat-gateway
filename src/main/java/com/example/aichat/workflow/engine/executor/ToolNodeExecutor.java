package com.example.aichat.workflow.engine.executor;

import com.example.aichat.agent.service.ToolRegistry;
import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TOOL 节点执行器
 * 调用 W5 的 ToolRegistry 中已注册的工具
 * config: {"toolName": "knowledge_base", "inputTemplate": "{{userInput}}", "knowledgeBaseId": 1}
 */
@Component
public class ToolNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolNodeExecutor.class);
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolNodeExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            String toolName = config.get("toolName").asText();
            String inputTemplate = config.has("inputTemplate")
                ? config.get("inputTemplate").asText() : "{{userInput}}";
            String input = ctx.resolveTemplate(inputTemplate);

            Long knowledgeBaseId = config.hasNonNull("knowledgeBaseId")
                ? config.get("knowledgeBaseId").asLong()
                : null;
            String result = toolRegistry.executeTool(toolName, input, knowledgeBaseId);
            log.info("Tool 节点完成: tool={}, outputLen={}", toolName,
                result != null ? result.length() : 0);
            return NodeResult.of(result);

        } catch (Exception e) {
            log.error("Tool 节点执行失败: {}", e.getMessage());
            return NodeResult.of("工具调用失败: " + e.getMessage());
        }
    }
}
