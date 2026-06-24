package com.example.aichat.workflow.engine.executor;

import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class EndNodeExecutor implements NodeExecutor {

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        // 获取最后一个节点的输出作为工作流最终输出
        String lastOutput = ctx.getVariable("output");
        return NodeResult.of(lastOutput != null ? lastOutput : "");
    }
}
