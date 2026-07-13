package com.example.aichat.agent.tool;

import com.example.aichat.agent.service.McpGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class McpToolProxy {

    private static final Logger log = LoggerFactory.getLogger(McpToolProxy.class);

    private final McpGatewayService mcpGateway;
    private final String serverUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpToolProxy(McpGatewayService mcpGateway, String serverUrl) {
        this.mcpGateway = mcpGateway;
        this.serverUrl = serverUrl;
    }

    @Tool("调用远程MCP服务工具，可执行支付宝支付、退款、查询等操作。参数: toolName(工具名称), argumentsJson(JSON格式参数字符串)")
    public String callMcpTool(String toolName, String argumentsJson) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return "[MCP_ERROR] 未配置MCP服务地址";
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (argumentsJson != null && !argumentsJson.isBlank())
                    ? mapper.readValue(argumentsJson, Map.class)
                    : Map.of();
            log.info("[MCP] 调用远程工具: {} @ {} 参数: {}", toolName, serverUrl, args);
            return mcpGateway.callToolSync(serverUrl, toolName, args);
        } catch (Exception e) {
            log.error("[MCP] 工具调用失败: toolName={}", toolName, e);
            return "[MCP_ERROR] " + e.getMessage();
        }
    }
}
