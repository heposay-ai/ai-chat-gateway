package com.example.aichat.workflow.repository;

import com.example.aichat.workflow.model.entity.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

    List<WorkflowDefinition> findByTenantIdAndIsTemplateFalseOrderByUpdatedAtDesc(Long tenantId);

    List<WorkflowDefinition> findByIsTemplateTrueOrderByCreatedAtAsc();

    Optional<WorkflowDefinition> findByWebhookToken(String webhookToken);
}
