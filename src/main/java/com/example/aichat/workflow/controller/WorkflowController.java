package com.example.aichat.workflow.controller;

import com.example.aichat.workflow.model.dto.WorkflowRunRequest;
import com.example.aichat.workflow.model.dto.WorkflowSaveRequest;
import com.example.aichat.workflow.model.entity.WorkflowDefinition;
import com.example.aichat.workflow.service.WorkflowDefinitionService;
import com.example.aichat.workflow.service.WorkflowExecutionService;
import com.example.aichat.workflow.service.WorkflowTemplateService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
@CrossOrigin(origins = "*")
public class WorkflowController {

    private final WorkflowDefinitionService definitionService;
    private final WorkflowExecutionService executionService;
    private final WorkflowTemplateService templateService;

    public WorkflowController(WorkflowDefinitionService definitionService,
                               WorkflowExecutionService executionService,
                               WorkflowTemplateService templateService) {
        this.definitionService = definitionService;
        this.executionService = executionService;
        this.templateService = templateService;
    }

    // ========== 工作流定义 CRUD ==========

    @PostMapping
    public ResponseEntity<WorkflowDefinition> create(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestBody WorkflowSaveRequest req) {
        return ResponseEntity.ok(definitionService.create(tenantId, req));
    }

    @GetMapping
    public ResponseEntity<List<WorkflowDefinition>> list(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return ResponseEntity.ok(definitionService.listByTenant(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(definitionService.getDetail(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowDefinition> update(
            @PathVariable Long id, @RequestBody WorkflowSaveRequest req) {
        return ResponseEntity.ok(definitionService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        definitionService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publish(@PathVariable Long id) {
        definitionService.publish(id);
        return ResponseEntity.ok().build();
    }

    // ========== 工作流执行 ==========

    @PostMapping(value = "/{id}/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter execute(
            @PathVariable Long id,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestBody WorkflowRunRequest req) {
        return executionService.run(id, tenantId, req.getInput());
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<?> getExecution(@PathVariable Long id) {
        return ResponseEntity.ok(executionService.getExecution(id));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<?> listExecutions(@PathVariable Long id) {
        return ResponseEntity.ok(executionService.listExecutions(id));
    }

    @GetMapping("/executions/{id}/nodes")
    public ResponseEntity<?> getNodeExecutions(@PathVariable Long id) {
        return ResponseEntity.ok(executionService.getNodeExecutions(id));
    }

    @PostMapping("/executions/{id}/cancel")
    public ResponseEntity<Void> cancelExecution(@PathVariable Long id) {
        executionService.cancelExecution(id);
        return ResponseEntity.ok().build();
    }

    // ========== 模板 ==========

    @GetMapping("/templates")
    public ResponseEntity<List<WorkflowDefinition>> templates() {
        return ResponseEntity.ok(templateService.getTemplates());
    }

    @PostMapping("/templates/{id}/clone")
    public ResponseEntity<WorkflowDefinition> cloneTemplate(
            @PathVariable Long id,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return ResponseEntity.ok(templateService.cloneFromTemplate(id, tenantId));
    }
}
