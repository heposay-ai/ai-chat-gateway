package com.example.aichat.agent.controller;

import com.example.aichat.agent.service.McpGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpGatewayService mcpGateway;

    public McpController(McpGatewayService mcpGateway) {
        this.mcpGateway = mcpGateway;
    }

    /**
     * 列出 MCP 服务器上的工具
     */
    @PostMapping("/tools")
    public Map<String, Object> listTools(@RequestBody Map<String, String> body) {
        String serverUrl = body.getOrDefault("serverUrl", mcpGateway.getDefaultServerUrl());
        log.info("[MCP] 获取工具列表: serverUrl={}", serverUrl);
        List<Map<String, Object>> tools = mcpGateway.listTools(serverUrl);
        return Map.of("tools", tools, "serverUrl", serverUrl);
    }

    /**
     * 调用 MCP 工具（SSE 流式）
     */
    @PostMapping(value = "/tools/call", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter callTool(@RequestBody Map<String, Object> body) {
        String serverUrl = (String) body.getOrDefault("serverUrl", mcpGateway.getDefaultServerUrl());
        String toolName = (String) body.get("toolName");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) body.getOrDefault("arguments", Map.of());

        log.info("[MCP] 调用工具: serverUrl={}, toolName={}", serverUrl, toolName);
        return mcpGateway.callToolStream(serverUrl, toolName, arguments);
    }

    /**
     * 返回预置 MCP 服务列表
     */
    @GetMapping("/presets")
    public List<Map<String, Object>> listPresets() {
        return List.of(
            Map.of("name", "支付宝AI支付", "description", "创建/查询/退款支付宝订单", "serverUrl", mcpGateway.getDefaultServerUrl(),
                   "tools", List.of(
                       Map.of("name", "create-mobile-alipay-payment", "description", "创建支付宝手机支付订单",
                              "parameters", List.of(
                                  Map.of("name", "outTradeNo", "type", "string", "required", true, "description", "商户订单号"),
                                  Map.of("name", "totalAmount", "type", "number", "required", true, "description", "支付金额 (元)"),
                                  Map.of("name", "orderTitle", "type", "string", "required", true, "description", "订单标题")
                              )),
                       Map.of("name", "create-web-page-alipay-payment", "description", "创建支付宝网页支付订单",
                              "parameters", List.of(
                                  Map.of("name", "outTradeNo", "type", "string", "required", true, "description", "商户订单号"),
                                  Map.of("name", "totalAmount", "type", "number", "required", true, "description", "支付金额 (元)"),
                                  Map.of("name", "orderTitle", "type", "string", "required", true, "description", "订单标题")
                              )),
                       Map.of("name", "query-alipay-payment", "description", "查询支付宝订单状态",
                              "parameters", List.of(
                                  Map.of("name", "outTradeNo", "type", "string", "required", true, "description", "商户订单号")
                              )),
                       Map.of("name", "refund-alipay-payment", "description", "发起支付宝退款",
                              "parameters", List.of(
                                  Map.of("name", "outTradeNo", "type", "string", "required", true, "description", "商户订单号"),
                                  Map.of("name", "refundAmount", "type", "number", "required", true, "description", "退款金额 (元)"),
                                  Map.of("name", "outRequestNo", "type", "string", "required", true, "description", "退款请求号"),
                                  Map.of("name", "refundReason", "type", "string", "required", false, "description", "退款原因")
                              )),
                       Map.of("name", "query-alipay-refund", "description", "查询支付宝退款状态",
                              "parameters", List.of(
                                  Map.of("name", "outRequestNo", "type", "string", "required", true, "description", "退款请求号"),
                                  Map.of("name", "outTradeNo", "type", "string", "required", true, "description", "商户订单号")
                              ))
                   )
            )
        );
    }
}
