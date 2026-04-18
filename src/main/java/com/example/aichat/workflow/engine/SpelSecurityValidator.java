package com.example.aichat.workflow.engine;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SpEL 安全校验器
 * 防止用户在条件表达式中注入危险方法调用
 */
@Component
public class SpelSecurityValidator {

    private static final List<String> BLACKLIST = List.of(
        "T(", "getClass", "forName", "Runtime",
        "ProcessBuilder", "exec(", "System.",
        "java.io", "java.net", "java.lang.reflect"
    );

    public void validate(String expression) {
        for (String keyword : BLACKLIST) {
            if (expression.contains(keyword)) {
                throw new IllegalArgumentException(
                    "表达式包含不允许的操作: " + keyword);
            }
        }
    }
}
