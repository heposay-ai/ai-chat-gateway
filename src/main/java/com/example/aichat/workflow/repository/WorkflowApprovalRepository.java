package com.example.aichat.workflow.repository;

import com.example.aichat.workflow.model.entity.WorkflowApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowApprovalRepository extends JpaRepository<WorkflowApproval, Long> {

    List<WorkflowApproval> findByStatusOrderByCreatedAtAsc(String status);

    Optional<WorkflowApproval> findByExecutionIdAndNodeKey(Long executionId, String nodeKey);
}
