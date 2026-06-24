package com.example.aichat.workflow.engine.executor;

import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * 代码执行节点（JavaScript 沙箱）
 * config: {"language": "javascript", "code": "var result = ..."}
 * 代码中可通过 variables 对象访问工作流变量
 * 代码必须将结果赋给 result 变量
 */
@Component
public class CodeNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CodeNodeExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            String code = config.get("code").asText();

            // 使用 Nashorn/GraalJS ScriptEngine
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("js");
            if (engine == null) {
                engine = manager.getEngineByName("nashorn");
            }
            if (engine == null) {
                return NodeResult.of("JavaScript 引擎不可用，请添加 GraalJS 依赖");
            }

            // 注入工作流变量
            engine.put("variables", ctx.getAllVariables());
            engine.put("userInput", ctx.getVariable("userInput"));
            engine.put("output", ctx.getVariable("output"));

            // 执行代码
            engine.eval(code);
            Object result = engine.get("result");

            String output = result != null ? result.toString() : "";
            log.info("Code 节点完成: outputLen={}", output.length());
            return NodeResult.of(output);

        } catch (Exception e) {
            log.error("Code 节点执行失败: {}", e.getMessage());
            return NodeResult.of("代码执行失败: " + e.getMessage());
        }
    }
}
