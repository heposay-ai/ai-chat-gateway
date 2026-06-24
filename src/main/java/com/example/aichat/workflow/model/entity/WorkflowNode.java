package com.example.aichat.workflow.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_node",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_id", "node_key"}))
public class WorkflowNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "node_key", nullable = false, length = 50)
    private String nodeKey;

    @Column(name = "node_type", nullable = false, length = 20)
    private String nodeType;

    @Column(length = 100)
    private String label;

    @Column(columnDefinition = "jsonb")
    private String config = "{}";

    @Column(name = "position_x")
    private Double positionX = 0.0;

    @Column(name = "position_y")
    private Double positionY = 0.0;

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkflowId() { return workflowId; }
    public void setWorkflowId(Long workflowId) { this.workflowId = workflowId; }

    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public Double getPositionX() { return positionX; }
    public void setPositionX(Double positionX) { this.positionX = positionX; }

    public Double getPositionY() { return positionY; }
    public void setPositionY(Double positionY) { this.positionY = positionY; }
}
