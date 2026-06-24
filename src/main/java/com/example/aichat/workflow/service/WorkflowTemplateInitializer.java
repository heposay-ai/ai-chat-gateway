package com.example.aichat.workflow.service;

import com.example.aichat.workflow.model.entity.*;
import com.example.aichat.workflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预设 3 个工作流模板（启动时自动初始化）
 */
@Component
public class WorkflowTemplateInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTemplateInitializer.class);

    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowNodeRepository nodeRepo;
    private final WorkflowEdgeRepository edgeRepo;

    public WorkflowTemplateInitializer(WorkflowDefinitionRepository defRepo,
                                        WorkflowNodeRepository nodeRepo,
                                        WorkflowEdgeRepository edgeRepo) {
        this.defRepo = defRepo;
        this.nodeRepo = nodeRepo;
        this.edgeRepo = edgeRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!defRepo.findByIsTemplateTrueOrderByCreatedAtAsc().isEmpty()) {
            log.info("工作流模板已存在，跳过初始化");
            return;
        }
        initCustomerServiceTemplate();
        initDocSummaryTemplate();
        initDataAnalysisTemplate();
        log.info("已初始化 3 个工作流模板");
    }

    private void initCustomerServiceTemplate() {
        WorkflowDefinition def = createDef("智能客服流程",
            "用户提问 → 意图分类 → 知识库/Agent 分流处理", "customer_service");

        addNode(def.getId(), "start", "START", "用户提问", "{}", 100, 300);
        addNode(def.getId(), "classify", "AGENT", "意图分类", "{\"agentId\":1,\"prompt\":\"请将用户消息分类（技术问题/售前咨询/投诉建议），只返回类别名称:\\n\\n{{userInput}}\"}", 300, 300);
        addNode(def.getId(), "check", "CONDITION", "问题类型", "{\"expr\":\"#classify_output.contains('技术')\"}", 500, 300);
        addNode(def.getId(), "kb_search", "TOOL", "知识库搜索", "{\"toolName\":\"knowledge_base\",\"inputTemplate\":\"{{userInput}}\"}", 700, 200);
        addNode(def.getId(), "tech_answer", "AGENT", "技术回答", "{\"agentId\":1,\"prompt\":\"基于知识库回答:\\n{{kb_search_output}}\\n\\n问题: {{userInput}}\"}", 900, 200);
        addNode(def.getId(), "general_answer", "AGENT", "通用回答", "{\"agentId\":1,\"prompt\":\"{{userInput}}\"}", 700, 400);
        addNode(def.getId(), "end", "END", "回复用户", "{}", 1100, 300);

        addEdge(def.getId(), "start", "classify", null, null);
        addEdge(def.getId(), "classify", "check", null, null);
        addEdge(def.getId(), "check", "kb_search", "true", "技术问题");
        addEdge(def.getId(), "check", "general_answer", "false", "其他");
        addEdge(def.getId(), "kb_search", "tech_answer", null, null);
        addEdge(def.getId(), "tech_answer", "end", null, null);
        addEdge(def.getId(), "general_answer", "end", null, null);
    }

    private void initDocSummaryTemplate() {
        WorkflowDefinition def = createDef("文档摘要流程",
            "知识库检索 → AI 分析 → 人工审核 → 输出", "content");

        addNode(def.getId(), "start", "START", "输入主题", "{}", 100, 300);
        addNode(def.getId(), "kb", "KNOWLEDGE", "知识库检索", "{\"topK\":5,\"queryTemplate\":\"{{userInput}}\"}", 300, 300);
        addNode(def.getId(), "summarize", "AGENT", "AI 摘要", "{\"agentId\":1,\"prompt\":\"请根据以下资料撰写摘要:\\n{{kb_output}}\"}", 500, 300);
        addNode(def.getId(), "review", "HUMAN_REVIEW", "人工审核", "{\"assignee\":\"admin\",\"prompt\":\"请审核摘要内容\"}", 700, 300);
        addNode(def.getId(), "end", "END", "输出结果", "{}", 900, 300);

        addEdge(def.getId(), "start", "kb", null, null);
        addEdge(def.getId(), "kb", "summarize", null, null);
        addEdge(def.getId(), "summarize", "review", null, null);
        addEdge(def.getId(), "review", "end", null, null);
    }

    private void initDataAnalysisTemplate() {
        WorkflowDefinition def = createDef("数据分析流程",
            "搜索数据 → 代码处理 → AI 分析 → HTTP 推送", "data_analysis");

        addNode(def.getId(), "start", "START", "分析主题", "{}", 100, 300);
        addNode(def.getId(), "search", "TOOL", "联网搜索", "{\"toolName\":\"web_search\",\"inputTemplate\":\"{{userInput}} 数据统计\"}", 300, 300);
        addNode(def.getId(), "transform", "CODE", "数据处理", "{\"language\":\"javascript\",\"code\":\"var data = variables['search_output']; var result = '数据摘要: ' + (data ? data.substring(0, 200) : '无数据');\"}", 500, 300);
        addNode(def.getId(), "analyze", "AGENT", "AI 分析", "{\"agentId\":1,\"prompt\":\"请分析以下数据并给出洞察:\\n{{transform_output}}\"}", 700, 300);
        addNode(def.getId(), "end", "END", "输出报告", "{}", 900, 300);

        addEdge(def.getId(), "start", "search", null, null);
        addEdge(def.getId(), "search", "transform", null, null);
        addEdge(def.getId(), "transform", "analyze", null, null);
        addEdge(def.getId(), "analyze", "end", null, null);
    }

    private WorkflowDefinition createDef(String name, String desc, String category) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setTenantId(0L);
        def.setName(name);
        def.setDescription(desc);
        def.setCategory(category);
        def.setIsTemplate(true);
        def.setStatus("PUBLISHED");
        return defRepo.save(def);
    }

    private void addNode(Long wfId, String key, String type, String label,
                          String config, double x, double y) {
        WorkflowNode node = new WorkflowNode();
        node.setWorkflowId(wfId);
        node.setNodeKey(key);
        node.setNodeType(type);
        node.setLabel(label);
        node.setConfig(config);
        node.setPositionX(x);
        node.setPositionY(y);
        nodeRepo.save(node);
    }

    private void addEdge(Long wfId, String source, String target,
                          String condition, String label) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setWorkflowId(wfId);
        edge.setSourceNodeKey(source);
        edge.setTargetNodeKey(target);
        edge.setConditionExpression(condition);
        edge.setLabel(label);
        edgeRepo.save(edge);
    }
}
