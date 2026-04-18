package com.example.aichat.workflow.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_edge")
public class WorkflowEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "source_node_key", nullable = false, length = 50)
    private String sourceNodeKey;

    @Column(name = "target_node_key", nullable = false, length = 50)
    private String targetNodeKey;

    @Column(name = "condition_expression", length = 500)
    private String conditionExpression;

    @Column(length = 100)
    private String label;

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkflowId() { return workflowId; }
    public void setWorkflowId(Long workflowId) { this.workflowId = workflowId; }

    public String getSourceNodeKey() { return sourceNodeKey; }
    public void setSourceNodeKey(String sourceNodeKey) { this.sourceNodeKey = sourceNodeKey; }

    public String getTargetNodeKey() { return targetNodeKey; }
    public void setTargetNodeKey(String targetNodeKey) { this.targetNodeKey = targetNodeKey; }

    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
