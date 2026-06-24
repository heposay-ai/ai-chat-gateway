package com.example.aichat.workflow.controller;

import com.example.aichat.workflow.model.dto.ApprovalDecisionRequest;
import com.example.aichat.workflow.model.entity.WorkflowApproval;
import com.example.aichat.workflow.service.WorkflowApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows/approvals")
@CrossOrigin(origins = "*")
public class WorkflowApprovalController {

    private final WorkflowApprovalService approvalService;

    public WorkflowApprovalController(WorkflowApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<WorkflowApproval>> pending() {
        return ResponseEntity.ok(approvalService.getPendingApprovals());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalDecisionRequest req) {
        approvalService.approve(id, req != null ? req.getComment() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalDecisionRequest req) {
        approvalService.reject(id, req != null ? req.getComment() : null);
        return ResponseEntity.ok().build();
    }
}
