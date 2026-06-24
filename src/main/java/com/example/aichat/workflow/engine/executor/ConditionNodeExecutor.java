package com.example.aichat.workflow.engine.executor;

import com.example.aichat.workflow.engine.*;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CONDITION 条件分支节点
 * 根据 SpEL 表达式求值结果选择后续路径
 * 出边通过 conditionExpression 字段（值为 "true"/"false"）匹配
 */
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConditionNodeExecutor.class);
    private final SpelEvaluator spelEvaluator;
    private final SpelSecurityValidator securityValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConditionNodeExecutor(SpelEvaluator spelEvaluator,
                                  SpelSecurityValidator securityValidator) {
        this.spelEvaluator = spelEvaluator;
        this.securityValidator = securityValidator;
    }

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            String expr = config.get("expr").asText();

            securityValidator.validate(expr);

            boolean result = spelEvaluator.evaluateBoolean(expr, ctx.getAllVariables());
            log.info("条件求值: expr={}, result={}", expr, result);

            return NodeResult.branch(result ? "true" : "false");

        } catch (Exception e) {
            log.error("条件节点执行失败: {}", e.getMessage());
            return NodeResult.branch("false");
        }
    }
}
