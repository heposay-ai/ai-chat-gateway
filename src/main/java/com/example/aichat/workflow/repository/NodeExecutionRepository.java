package com.example.aichat.workflow.repository;

import com.example.aichat.workflow.model.entity.NodeExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeExecutionRepository extends JpaRepository<NodeExecution, Long> {

    List<NodeExecution> findByExecutionIdOrderByStartedAtAsc(Long executionId);
}
