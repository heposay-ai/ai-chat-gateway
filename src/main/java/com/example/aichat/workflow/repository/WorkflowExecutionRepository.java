package com.example.aichat.workflow.repository;

import com.example.aichat.workflow.model.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

    List<WorkflowExecution> findByWorkflowIdOrderByStartedAtDesc(Long workflowId);
}
