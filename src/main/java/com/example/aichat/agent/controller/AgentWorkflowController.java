package com.example.aichat.agent.controller;

import com.example.aichat.agent.model.AgentWorkflow;
import com.example.aichat.agent.service.AgentWorkflowService;
import com.example.aichat.context.RequestContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/agent-workflows")
public class AgentWorkflowController {

    private final AgentWorkflowService workflowService;

    public AgentWorkflowController(AgentWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public List<AgentWorkflow> list() {
        Long tenantId = RequestContext.get("tenantId");
        return workflowService.listByTenant(tenantId);
    }

    @PostMapping
    public AgentWorkflow create(@RequestBody AgentWorkflow workflow) {
        Long tenantId = RequestContext.get("tenantId");
        return workflowService.create(tenantId, workflow);
    }

    @PutMapping("/{id}")
    public AgentWorkflow update(@PathVariable Long id, @RequestBody AgentWorkflow workflow) {
        Long tenantId = RequestContext.get("tenantId");
        return workflowService.update(id, tenantId, workflow);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long tenantId = RequestContext.get("tenantId");
        workflowService.delete(id, tenantId);
    }

    @GetMapping(value = "/{id}/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable Long id, @RequestParam String input) {
        return workflowService.run(id, input);
    }
}
