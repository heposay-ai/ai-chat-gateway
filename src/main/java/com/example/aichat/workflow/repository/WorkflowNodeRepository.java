package com.example.aichat.workflow.repository;

import com.example.aichat.workflow.model.entity.WorkflowNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, Long> {

    List<WorkflowNode> findByWorkflowId(Long workflowId);

    void deleteByWorkflowId(Long workflowId);
}
