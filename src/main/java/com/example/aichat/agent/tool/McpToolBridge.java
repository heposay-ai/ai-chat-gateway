package com.example.aichat.agent.tool;

import com.example.aichat.agent.service.McpGatewayService;
import org.springframework.stereotype.Component;

@Component
public class McpToolBridge {

    private final McpGatewayService mcpGateway;

    public McpToolBridge(McpGatewayService mcpGateway) {
        this.mcpGateway = mcpGateway;
    }

    public McpToolProxy createDefaultProxy() {
        return new McpToolProxy(mcpGateway, mcpGateway.getDefaultServerUrl());
    }

    public McpToolProxy createProxy(String serverUrl) {
        return new McpToolProxy(mcpGateway, serverUrl != null ? serverUrl : mcpGateway.getDefaultServerUrl());
    }
}
