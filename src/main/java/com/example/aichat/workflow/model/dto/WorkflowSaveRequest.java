package com.example.aichat.workflow.model.dto;

import java.util.List;

public class WorkflowSaveRequest {

    private String name;
    private String description;
    private String category;
    private String triggerType;
    private List<NodeDto> nodes;
    private List<EdgeDto> edges;

    public static class NodeDto {
        private String nodeKey;
        private String nodeType;
        private String label;
        private String config;
        private Double positionX;
        private Double positionY;

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

    public static class EdgeDto {
        private String sourceNodeKey;
        private String targetNodeKey;
        private String conditionExpression;
        private String label;

        public String getSourceNodeKey() { return sourceNodeKey; }
        public void setSourceNodeKey(String sourceNodeKey) { this.sourceNodeKey = sourceNodeKey; }
        public String getTargetNodeKey() { return targetNodeKey; }
        public void setTargetNodeKey(String targetNodeKey) { this.targetNodeKey = targetNodeKey; }
        public String getConditionExpression() { return conditionExpression; }
        public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public List<NodeDto> getNodes() { return nodes; }
    public void setNodes(List<NodeDto> nodes) { this.nodes = nodes; }
    public List<EdgeDto> getEdges() { return edges; }
    public void setEdges(List<EdgeDto> edges) { this.edges = edges; }
}
