package com.example.aichat.workflow.engine.executor;

import com.example.aichat.agent.model.Agent;
import com.example.aichat.agent.repository.AgentRepository;
import com.example.aichat.agent.service.AgentChatService;
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
 * AGENT 节点执行器
 * 调用 W5 的 AgentChatService 实现 Agent 对话
 * config: {"agentId": 1, "prompt": "{{userInput}}"}
 */
@Component
public class AgentNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentNodeExecutor.class);
    private final AgentRepository agentRepo;
    private final AgentChatService agentChatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentNodeExecutor(AgentRepository agentRepo, AgentChatService agentChatService) {
        this.agentRepo = agentRepo;
        this.agentChatService = agentChatService;
    }

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            Long agentId = config.get("agentId").asLong();
            String promptTemplate = config.has("prompt") ? config.get("prompt").asText() : "{{userInput}}";
            String prompt = ctx.resolveTemplate(promptTemplate);

            Agent agent = agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent 不存在: " + agentId));

            // 使用同步方式调用 Agent（工作流场景不需要流式）
            String response = agentChatService.chatSync(agent, prompt);
            log.info("Agent 节点完成: agentId={}, outputLen={}", agentId,
                response != null ? response.length() : 0);
            return NodeResult.of(response);

        } catch (Exception e) {
            log.error("Agent 节点执行失败: {}", e.getMessage());
            return NodeResult.of("Agent 调用失败: " + e.getMessage());
        }
    }
}
