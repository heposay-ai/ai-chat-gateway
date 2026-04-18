package com.example.aichat.workflow.engine.executor;

import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * HTTP 请求节点
 * config: {"method":"POST","url":"https://...","headers":{...},"body":"...","timeout":10000}
 * url 和 body 支持模板变量替换
 */
@Component
public class HttpNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpNodeExecutor.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // SSRF 防护: 禁止访问内网地址
    private static final List<String> BLOCKED_PREFIXES = List.of(
        "http://127.", "http://10.", "http://172.16.", "http://172.17.",
        "http://172.18.", "http://172.19.", "http://172.2", "http://172.3",
        "http://192.168.", "http://0.", "http://localhost"
    );

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            String method = config.has("method") ? config.get("method").asText() : "GET";
            String url = ctx.resolveTemplate(config.get("url").asText());

            validateUrl(url);

            HttpHeaders headers = new HttpHeaders();
            if (config.has("headers")) {
                Iterator<Map.Entry<String, JsonNode>> fields = config.get("headers").fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    headers.add(field.getKey(), ctx.resolveTemplate(field.getValue().asText()));
                }
            }

            String body = null;
            if (config.has("body")) {
                body = ctx.resolveTemplate(config.get("body").asText());
            }

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.valueOf(method), entity, String.class);

            log.info("HTTP 节点完成: {} {} -> {}", method, url, response.getStatusCode());
            return NodeResult.of(response.getBody());

        } catch (Exception e) {
            log.error("HTTP 节点执行失败: {}", e.getMessage());
            return NodeResult.of("HTTP 请求失败: " + e.getMessage());
        }
    }

    private void validateUrl(String url) {
        String lower = url.toLowerCase();
        for (String blocked : BLOCKED_PREFIXES) {
            if (lower.startsWith(blocked)) {
                throw new SecurityException("不允许访问内网地址: " + url);
            }
        }
    }
}
