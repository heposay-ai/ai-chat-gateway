package com.example.aichat.workflow.engine;

import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;

/**
 * 节点执行器接口 — 策略模式
 */
public interface NodeExecutor {

    NodeResult execute(WorkflowNode node, WorkflowContext ctx);
}
