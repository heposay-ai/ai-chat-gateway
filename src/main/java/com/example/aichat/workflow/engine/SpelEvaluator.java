package com.example.aichat.workflow.engine;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SpEL 表达式求值工具
 * 用于条件分支节点的表达式计算
 */
@Component
public class SpelEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    public boolean evaluateBoolean(String expression, Map<String, String> variables) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        variables.forEach(ctx::setVariable);
        Boolean result = parser.parseExpression(expression).getValue(ctx, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public String evaluateString(String expression, Map<String, String> variables) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        variables.forEach(ctx::setVariable);
        return parser.parseExpression(expression).getValue(ctx, String.class);
    }
}
