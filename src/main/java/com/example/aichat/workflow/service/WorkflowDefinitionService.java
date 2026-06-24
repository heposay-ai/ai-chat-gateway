package com.example.aichat.workflow.service;

import com.example.aichat.workflow.model.dto.WorkflowSaveRequest;
import com.example.aichat.workflow.model.entity.*;
import com.example.aichat.workflow.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class WorkflowDefinitionService {

    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowNodeRepository nodeRepo;
    private final WorkflowEdgeRepository edgeRepo;

    public WorkflowDefinitionService(WorkflowDefinitionRepository definitionRepo,
                                      WorkflowNodeRepository nodeRepo,
                                      WorkflowEdgeRepository edgeRepo) {
        this.definitionRepo = definitionRepo;
        this.nodeRepo = nodeRepo;
        this.edgeRepo = edgeRepo;
    }

    public List<WorkflowDefinition> listByTenant(Long tenantId) {
        return definitionRepo.findByTenantIdAndIsTemplateFalseOrderByUpdatedAtDesc(tenantId);
    }

    public Map<String, Object> getDetail(Long id) {
        WorkflowDefinition def = definitionRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("工作流不存在: " + id));
        List<WorkflowNode> nodes = nodeRepo.findByWorkflowId(id);
        List<WorkflowEdge> edges = edgeRepo.findByWorkflowId(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("definition", def);
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    public WorkflowDefinition create(Long tenantId, WorkflowSaveRequest req) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setTenantId(tenantId);
        def.setName(req.getName());
        def.setDescription(req.getDescription());
        def.setCategory(req.getCategory());
        def.setTriggerType(req.getTriggerType() != null ? req.getTriggerType() : "MANUAL");
        definitionRepo.save(def);

        saveNodesAndEdges(def.getId(), req);
        return def;
    }

    public WorkflowDefinition update(Long id, WorkflowSaveRequest req) {
        WorkflowDefinition def = definitionRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("工作流不存在: " + id));
        def.setName(req.getName());
        def.setDescription(req.getDescription());
        def.setCategory(req.getCategory());
        def.setUpdatedAt(LocalDateTime.now());
        definitionRepo.save(def);

        // 先删旧的再保存新的
        nodeRepo.deleteByWorkflowId(id);
        edgeRepo.deleteByWorkflowId(id);
        saveNodesAndEdges(id, req);

        return def;
    }

    public void delete(Long id) {
        definitionRepo.deleteById(id);
    }

    public void publish(Long id) {
        WorkflowDefinition def = definitionRepo.findById(id).orElseThrow();
        def.setStatus("PUBLISHED");
        def.setWebhookToken(UUID.randomUUID().toString().replace("-", ""));
        def.setUpdatedAt(LocalDateTime.now());
        definitionRepo.save(def);
    }

    private void saveNodesAndEdges(Long workflowId, WorkflowSaveRequest req) {
        if (req.getNodes() != null) {
            for (WorkflowSaveRequest.NodeDto nd : req.getNodes()) {
                WorkflowNode node = new WorkflowNode();
                node.setWorkflowId(workflowId);
                node.setNodeKey(nd.getNodeKey());
                node.setNodeType(nd.getNodeType());
                node.setLabel(nd.getLabel());
                node.setConfig(nd.getConfig() != null ? nd.getConfig() : "{}");
                node.setPositionX(nd.getPositionX() != null ? nd.getPositionX() : 0.0);
                node.setPositionY(nd.getPositionY() != null ? nd.getPositionY() : 0.0);
                nodeRepo.save(node);
            }
        }
        if (req.getEdges() != null) {
            for (WorkflowSaveRequest.EdgeDto ed : req.getEdges()) {
                WorkflowEdge edge = new WorkflowEdge();
                edge.setWorkflowId(workflowId);
                edge.setSourceNodeKey(ed.getSourceNodeKey());
                edge.setTargetNodeKey(ed.getTargetNodeKey());
                edge.setConditionExpression(ed.getConditionExpression());
                edge.setLabel(ed.getLabel());
                edgeRepo.save(edge);
            }
        }
    }
}
