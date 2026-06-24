package com.example.aichat.workflow.engine.executor;

import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        String userInput = ctx.getVariable("userInput");
        return NodeResult.of(userInput);
    }
}
