package com.example.aichat.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class McpGatewayService {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayService.class);
    private static final String PROTOCOL_VERSION = "0.1.0";

    @Value("${mcp.server.url:https://bailian.aliyuncs.com/mcp/alipay/sse}")
    private String defaultServerUrl;

    @Value("${mcp.auth.mode:bearer}")
    private String authMode;

    @Value("${mcp.api.key:}")
    private String apiKey;

    @Value("${DASHSCOPE_API_KEY:}")
    private String dashscopeApiKey;

    private String effectiveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("[MCP] 使用 MCP_API_KEY");
            return apiKey;
        }
        if (dashscopeApiKey != null && !dashscopeApiKey.isBlank()) {
            log.info("[MCP] 回退使用 DASHSCOPE_API_KEY: {}", dashscopeApiKey.substring(0, 10) + "...");
            return dashscopeApiKey;
        }
        log.warn("[MCP] 未找到任何 API Key (MCP_API_KEY 和 DASHSCOPE_API_KEY 均为空)");
        return null;
    }
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final Map<String, List<Map<String, Object>>> toolCache = new ConcurrentHashMap<>();

    public String getDefaultServerUrl() {
        return defaultServerUrl;
    }

    /**
     * 列出 MCP 服务器上的所有可用工具
     */
    public List<Map<String, Object>> listTools(String serverUrl) {
        try {
            JsonNode initResp = sseRoundTrip(serverUrl, "initialize", null);
            if (initResp == null) return List.of();

            JsonNode toolsResp = sseRoundTrip(serverUrl, "tools/list", null);
            if (toolsResp != null && toolsResp.has("tools")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tools = mapper.convertValue(toolsResp.get("tools"), List.class);
                return tools;
            }
            return List.of();
        } catch (Exception e) {
            log.error("[MCP] 获取工具列表失败: serverUrl={}", serverUrl, e);
            return List.of();
        }
    }

    /**
     * 同步调用 MCP 工具（供 Agent 自主决策使用）
     */
    public String callToolSync(String serverUrl, String toolName, Map<String, Object> arguments) {
        try {
            JsonNode initResp = sseRoundTrip(serverUrl, "initialize", null);
            if (initResp == null) return "[MCP_ERROR] initialize 失败";

            JsonNode result = sseRoundTrip(serverUrl, "tools/call",
                    Map.of("name", toolName, "arguments", arguments != null ? arguments : Map.of()));
            if (result != null) {
                return mapper.writeValueAsString(result);
            }
            return "[MCP_ERROR] 工具调用无结果";
        } catch (Exception e) {
            log.error("[MCP] 同步调用失败: toolName={}", toolName, e);
            return "[MCP_ERROR] " + e.getMessage();
        }
    }

    /**
     * 获取 MCP 工具列表（带缓存）
     */
    public List<Map<String, Object>> getToolListCached(String serverUrl) {
        List<Map<String, Object>> cached = toolCache.get(serverUrl);
        if (cached != null) return cached;
        List<Map<String, Object>> result = listTools(serverUrl);
        if (!result.isEmpty()) {
            toolCache.put(serverUrl, result);
        }
        return result;
    }

    public SseEmitter callToolStream(String serverUrl, String toolName, Map<String, Object> arguments) {
        SseEmitter emitter = new SseEmitter(300_000L);
        new Thread(() -> {
            try {
                JsonNode initResp = sseRoundTrip(serverUrl, "initialize", null);
                if (initResp == null) {
                    emitter.send(SseEmitter.event().data(Map.of("type", "error", "message", "initialize 失败")));
                    emitter.complete();
                    return;
                }
                emitter.send(SseEmitter.event().data(Map.of("type", "log", "message", "MCP 已连接")));

                JsonNode result = sseRoundTrip(serverUrl, "tools/call", Map.of("name", toolName, "arguments", arguments != null ? arguments : Map.of()));
                if (result != null) {
                    emitter.send(SseEmitter.event().data(Map.of("type", "result", "data", mapper.treeToValue(result, Object.class))));
                } else {
                    emitter.send(SseEmitter.event().data(Map.of("type", "error", "message", "工具调用无结果")));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("[MCP] 工具调用失败: toolName={}", toolName, e);
                try {
                    String errMsg = e.getMessage() != null ? e.getMessage() : "工具调用失败";
                    emitter.send(SseEmitter.event().data(Map.of("type", "error", "message", errMsg)));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }).start();
        return emitter;
    }

    /**
     * MCP SSE 完整往返: GET /sse → endpoint → POST message → 读 SSE response
     */
    private JsonNode sseRoundTrip(String sseUrl, String method, Map<String, Object> callParams) {
        String effectiveKey = effectiveApiKey();
        try {
            // 1. 打开 SSE 连接
            HttpURLConnection sseConn = (HttpURLConnection) URI.create(sseUrl).toURL().openConnection();
            sseConn.setRequestMethod("GET");
            sseConn.setConnectTimeout(10000);
            sseConn.setReadTimeout(30000);
            sseConn.setRequestProperty("Accept", "text/event-stream");
            if (effectiveKey != null && !effectiveKey.isBlank()) {
                sseConn.setRequestProperty("Authorization", "Bearer " + effectiveKey);
            }

            // 2. 读取 endpoint 事件
            BufferedReader sseReader = new BufferedReader(new InputStreamReader(sseConn.getInputStream()));
            String messagePath = null;
            String line;
            while ((line = sseReader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    messagePath = line.substring(5).trim();
                    break;
                }
            }
            if (messagePath == null) {
                log.error("[MCP] SSE 未收到 endpoint 事件");
                sseConn.disconnect();
                return null;
            }
            URI sseUri = URI.create(sseUrl);
            String messageUrl = sseUri.getScheme() + "://" + sseUri.getAuthority() + messagePath;
            log.info("[MCP] Session endpoint: {}", messageUrl);

            // 3. POST JSON-RPC 到 message URL
            String requestBody = mapper.writeValueAsString(mcpRequest(method, callParams));
            HttpURLConnection postConn = (HttpURLConnection) URI.create(messageUrl).toURL().openConnection();
            postConn.setRequestMethod("POST");
            postConn.setConnectTimeout(5000);
            postConn.setReadTimeout(5000);
            postConn.setDoOutput(true);
            postConn.setRequestProperty("Content-Type", "application/json");
            postConn.setRequestProperty("Accept", "application/json");
            if (effectiveKey != null && !effectiveKey.isBlank()) {
                postConn.setRequestProperty("Authorization", "Bearer " + effectiveKey);
            }
            postConn.getOutputStream().write(requestBody.getBytes());
            postConn.getOutputStream().flush();

            int postStatus = postConn.getResponseCode();
            if (postStatus != 200 && postStatus != 202) {
                log.error("[MCP] POST message 失败: status={}", postStatus);
                sseConn.disconnect(); postConn.disconnect();
                return null;
            }
            postConn.disconnect();

            // 4. 从 SSE 流读取 JSON-RPC 响应
            String dataLine = null;
            while ((line = sseReader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    dataLine = line.substring(5).trim();
                    break;
                }
            }
            sseConn.disconnect();

            if (dataLine == null || dataLine.isBlank()) {
                log.error("[MCP] SSE 响应为空");
                return null;
            }
            JsonNode node = mapper.readTree(dataLine);
            if (node.has("result")) return node.get("result");
            if (node.has("error")) { log.error("[MCP] RPC 错误: {}", node.get("error")); return null; }
            return null;
        } catch (Exception e) {
            log.error("[MCP] SSE round-trip 失败: method={}", method, e);
            return null;
        }
    }

    private ObjectNode mcpRequest(String method, Map<String, Object> callParams) {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", requestId.getAndIncrement());
        req.put("method", method);
        if ("initialize".equals(method)) {
            ObjectNode params = req.putObject("params");
            params.put("protocolVersion", PROTOCOL_VERSION);
            params.putObject("capabilities");
            ObjectNode clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "ai-chat-gateway");
            clientInfo.put("version", "1.0.0");
        } else {
            req.set("params", mapper.valueToTree(callParams != null ? callParams : Map.of()));
        }
        return req;
    }
}
