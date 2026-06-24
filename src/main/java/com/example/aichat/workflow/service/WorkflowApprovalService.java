package com.example.aichat.workflow.service;

import com.example.aichat.workflow.engine.WorkflowEngine;
import com.example.aichat.workflow.model.entity.WorkflowApproval;
import com.example.aichat.workflow.model.entity.WorkflowExecution;
import com.example.aichat.workflow.repository.WorkflowApprovalRepository;
import com.example.aichat.workflow.repository.WorkflowExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class WorkflowApprovalService {

    private final WorkflowApprovalRepository approvalRepo;
    private final WorkflowExecutionRepository executionRepo;
    private final WorkflowEngine engine;

    public WorkflowApprovalService(WorkflowApprovalRepository approvalRepo,
                                    WorkflowExecutionRepository executionRepo,
                                    WorkflowEngine engine) {
        this.approvalRepo = approvalRepo;
        this.executionRepo = executionRepo;
        this.engine = engine;
    }

    public List<WorkflowApproval> getPendingApprovals() {
        return approvalRepo.findByStatusOrderByCreatedAtAsc("PENDING");
    }

    public void approve(Long approvalId, String comment) {
        WorkflowApproval approval = approvalRepo.findById(approvalId)
            .orElseThrow(() -> new RuntimeException("审批记录不存在"));
        approval.setStatus("APPROVED");
        approval.setComment(comment);
        approval.setHandledAt(LocalDateTime.now());
        approvalRepo.save(approval);

        // 恢复工作流执行
        engine.resumeFrom(approval.getExecutionId(), approval.getNodeKey());
    }

    public void reject(Long approvalId, String comment) {
        WorkflowApproval approval = approvalRepo.findById(approvalId)
            .orElseThrow(() -> new RuntimeException("审批记录不存在"));
        approval.setStatus("REJECTED");
        approval.setComment(comment);
        approval.setHandledAt(LocalDateTime.now());
        approvalRepo.save(approval);

        // 终止工作流
        WorkflowExecution execution = executionRepo.findById(approval.getExecutionId()).orElseThrow();
        execution.setStatus("FAILED");
        execution.setErrorMessage("人工审批被驳回: " + comment);
        execution.setFinishedAt(LocalDateTime.now());
        executionRepo.save(execution);
    }
}
