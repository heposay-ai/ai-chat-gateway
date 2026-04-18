package com.example.aichat.workflow.controller;

import com.example.aichat.workflow.model.entity.WorkflowDefinition;
import com.example.aichat.workflow.repository.WorkflowDefinitionRepository;
import com.example.aichat.workflow.service.WorkflowExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/workflow")
@CrossOrigin(origins = "*")
public class WorkflowWebhookController {

    private final WorkflowExecutionService executionService;
    private final WorkflowDefinitionRepository definitionRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkflowWebhookController(WorkflowExecutionService executionService,
                                      WorkflowDefinitionRepository definitionRepo) {
        this.executionService = executionService;
        this.definitionRepo = definitionRepo;
    }

    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> trigger(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> payload) {
        WorkflowDefinition workflow = definitionRepo.findByWebhookToken(token)
            .orElseThrow(() -> new RuntimeException("无效的 Webhook Token"));

        String input;
        try {
            input = payload != null && payload.containsKey("input")
                ? payload.get("input").toString()
                : objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            input = payload != null ? payload.toString() : "";
        }

        Long executionId = executionService.asyncRun(
            workflow.getId(), workflow.getTenantId(), input);

        return ResponseEntity.ok(Map.of(
            "executionId", executionId,
            "message", "工作流已触发"
        ));
    }
}
