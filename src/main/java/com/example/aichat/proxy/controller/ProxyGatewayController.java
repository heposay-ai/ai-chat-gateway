package com.example.aichat.proxy.controller;

import com.example.aichat.proxy.service.ProxyGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proxy/gateway")
public class ProxyGatewayController {

    private final ProxyGatewayService gatewayService;

    public ProxyGatewayController(ProxyGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return gatewayService.getHealthInfo();
    }

    @GetMapping("/v1/models")
    public Map<String, Object> listModels() {
        List<Map<String, Object>> models = gatewayService.listModels();
        return Map.of("object", "list", "data", models);
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<Flux<String>> chatCompletions(
            @RequestBody String body,
            HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        return gatewayService.forwardChatCompletions(body, clientIp);
    }
}
