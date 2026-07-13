package com.example.aichat.agent.service;

import com.example.aichat.agent.model.Agent;
import com.example.aichat.agent.repository.AgentChatMessageRepository;
import com.example.aichat.agent.repository.AgentChatSessionRepository;
import com.example.aichat.agent.repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRepository agentRepo;
    private final AgentChatSessionRepository sessionRepo;
    private final AgentChatMessageRepository messageRepo;

    public AgentService(AgentRepository agentRepo,
                        AgentChatSessionRepository sessionRepo,
                        AgentChatMessageRepository messageRepo) {
        this.agentRepo = agentRepo;
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    public List<Agent> listByTenant(Long tenantId) {
        return agentRepo.findByTenantIdAndIsTemplateFalseOrderByCreatedAtDesc(tenantId);
    }

    public List<Agent> listTemplates() {
        return agentRepo.findByIsTemplateTrueOrderByCreatedAtAsc();
    }

    public Agent getById(Long id) {
        return agentRepo.findById(id).orElseThrow(() -> new RuntimeException("Agent不存在: " + id));
    }

    public Agent getByIdAndTenant(Long id, Long tenantId) {
        return agentRepo.findByIdAndTenantId(id, tenantId)
                .or(() -> agentRepo.findById(id).filter(a -> Boolean.TRUE.equals(a.getIsTemplate())))
                .orElseThrow(() -> new RuntimeException("Agent不存在或无权访问: " + id));
    }

    public Agent create(Long tenantId, Agent agent) {
        agent.setTenantId(tenantId);
        agent.setIsTemplate(false);
        return agentRepo.save(agent);
    }

    public Agent update(Long id, Long tenantId, Agent updates) {
        Agent agent = getByIdAndTenant(id, tenantId);
        if (Boolean.TRUE.equals(agent.getIsTemplate())) {
            throw new RuntimeException("预设模板不可修改");
        }
        agent.setName(updates.getName());
        agent.setDescription(updates.getDescription());
        agent.setAvatar(updates.getAvatar());
        agent.setSystemPrompt(updates.getSystemPrompt());
        agent.setModelId(updates.getModelId());
        agent.setToolsConfig(updates.getToolsConfig());
        return agentRepo.save(agent);
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        Agent agent = getByIdAndTenant(id, tenantId);
        if (Boolean.TRUE.equals(agent.getIsTemplate())) {
            throw new RuntimeException("预设模板不可删除");
        }
        // 删除关联的会话和消息
        var sessions = sessionRepo.findByAgentIdAndTenantIdOrderByUpdatedAtDesc(id, tenantId);
        for (var s : sessions) {
            messageRepo.deleteBySessionId(s.getId());
        }
        sessionRepo.deleteByAgentId(id);
        agentRepo.deleteById(id);
    }

    /**
     * 从模板克隆创建 Agent
     */
    public Agent cloneFromTemplate(Long templateId, Long tenantId) {
        Agent template = agentRepo.findById(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));
        Agent clone = new Agent();
        clone.setTenantId(tenantId);
        clone.setName(template.getName());
        clone.setDescription(template.getDescription());
        clone.setAvatar(template.getAvatar());
        clone.setSystemPrompt(template.getSystemPrompt());
        clone.setModelId(template.getModelId());
        clone.setToolsConfig(template.getToolsConfig());
        clone.setIsTemplate(false);
        return agentRepo.save(clone);
    }

    @Override
    public void run(String... args) {
        if (agentRepo.countByIsTemplateTrue() > 0) {
            log.debug("[Agent初始化] 已有预设模板，跳过");
            return;
        }
        log.info("[Agent初始化] 创建预设Agent模板...");
        createTemplate("联网搜索助手", "能搜索互联网获取最新信息的智能助手", "\uD83C\uDF10",
                "你是一个联网搜索助手。你可以搜索互联网获取最新信息来回答用户问题。\n\n规则：\n- 当用户问题需要最新信息时，主动搜索\n- 搜索后基于结果回答，注明信息来源\n- 如果搜索结果不足，如实说明",
                "deepseek-chat", "[\"web_search\",\"current_time\",\"url_reader\"]");

        createTemplate("知识库专家", "基于企业知识库回答问题的专家助手", "\uD83D\uDCDA",
                "你是一个企业知识库专家。你可以查询知识库获取企业内部文档信息来回答问题。\n\n规则：\n- 基于知识库内容回答，不要编造\n- 如果知识库中没有相关信息，如实说明\n- 引用具体的文档来源",
                "deepseek-chat", "[\"knowledge_base\"]");

        createTemplate("全能助手", "具备联网搜索、知识库查询和MCP远程调用能力的全能AI助手", "\uD83E\uDD16",
                "你是一个全能AI助手，同时具备联网搜索、知识库查询和MCP远程服务调用能力。\n\n规则：\n- 根据问题性质选择合适的工具\n- 企业内部问题优先查知识库，通用问题优先搜索互联网\n- 可以组合使用多个工具获取更完整的答案\n- 回答要准确、有条理",
                "deepseek-chat", "[\"web_search\",\"knowledge_base\",\"url_reader\",\"current_time\",\"mcp\"]");

        log.info("[Agent初始化] 完成，创建了 3 个预设模板");
    }

    private void createTemplate(String name, String desc, String avatar,
                                 String systemPrompt, String modelId, String toolsConfig) {
        Agent agent = new Agent();
        agent.setTenantId(0L);
        agent.setName(name);
        agent.setDescription(desc);
        agent.setAvatar(avatar);
        agent.setSystemPrompt(systemPrompt);
        agent.setModelId(modelId);
        agent.setToolsConfig(toolsConfig);
        agent.setIsTemplate(true);
        agent.setStatus(1);
        agentRepo.save(agent);
    }
}
