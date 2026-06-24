package com.example.aichat.workflow.engine.executor;

import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PARALLEL 并行节点
 * 并行执行由引擎层控制（BFS 同一层级的节点并行执行）
 * 此执行器仅作为占位，实际并行调度在 WorkflowEngine 中实现
 */
@Component
public class ParallelNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ParallelNodeExecutor.class);

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        log.info("并行节点通过: {}", node.getNodeKey());
        return NodeResult.of("parallel");
    }
}
