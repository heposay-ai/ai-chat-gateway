package com.example.aichat.workflow.engine;

import com.example.aichat.workflow.engine.executor.*;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 节点执行器工厂 — 根据 NodeType 返回对应执行器
 */
@Component
public class NodeExecutorFactory {

    private final Map<String, NodeExecutor> executors;

    public NodeExecutorFactory(
            StartNodeExecutor startExec,
            EndNodeExecutor endExec,
            AgentNodeExecutor agentExec,
            ToolNodeExecutor toolExec,
            ConditionNodeExecutor conditionExec,
            HttpNodeExecutor httpExec,
            KnowledgeNodeExecutor knowledgeExec,
            CodeNodeExecutor codeExec,
            ParallelNodeExecutor parallelExec,
            HumanReviewNodeExecutor humanReviewExec) {
        this.executors = Map.of(
            "START", startExec,
            "END", endExec,
            "AGENT", agentExec,
            "TOOL", toolExec,
            "CONDITION", conditionExec,
            "HTTP", httpExec,
            "KNOWLEDGE", knowledgeExec,
            "CODE", codeExec,
            "PARALLEL", parallelExec,
            "HUMAN_REVIEW", humanReviewExec
        );
    }

    public NodeExecutor create(String nodeType) {
        NodeExecutor executor = executors.get(nodeType);
        if (executor == null) {
            throw new IllegalArgumentException("未知节点类型: " + nodeType);
        }
        return executor;
    }
}
