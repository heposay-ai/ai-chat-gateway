package com.example.aichat.workflow.service;

import com.example.aichat.workflow.model.entity.*;
import com.example.aichat.workflow.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WorkflowTemplateService {

    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowNodeRepository nodeRepo;
    private final WorkflowEdgeRepository edgeRepo;

    public WorkflowTemplateService(WorkflowDefinitionRepository definitionRepo,
                                    WorkflowNodeRepository nodeRepo,
                                    WorkflowEdgeRepository edgeRepo) {
        this.definitionRepo = definitionRepo;
        this.nodeRepo = nodeRepo;
        this.edgeRepo = edgeRepo;
    }

    public List<WorkflowDefinition> getTemplates() {
        return definitionRepo.findByIsTemplateTrueOrderByCreatedAtAsc();
    }

    /**
     * 从模板克隆为新工作流（深拷贝 nodes + edges）
     */
    public WorkflowDefinition cloneFromTemplate(Long templateId, Long tenantId) {
        WorkflowDefinition template = definitionRepo.findById(templateId)
            .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));

        WorkflowDefinition newDef = new WorkflowDefinition();
        newDef.setTenantId(tenantId);
        newDef.setName(template.getName() + " (副本)");
        newDef.setDescription(template.getDescription());
        newDef.setCategory(template.getCategory());
        newDef.setStatus("DRAFT");
        newDef.setIsTemplate(false);
        definitionRepo.save(newDef);

        // 复制节点
        List<WorkflowNode> templateNodes = nodeRepo.findByWorkflowId(templateId);
        for (WorkflowNode tn : templateNodes) {
            WorkflowNode newNode = new WorkflowNode();
            newNode.setWorkflowId(newDef.getId());
            newNode.setNodeKey(tn.getNodeKey());
            newNode.setNodeType(tn.getNodeType());
            newNode.setLabel(tn.getLabel());
            newNode.setConfig(tn.getConfig());
            newNode.setPositionX(tn.getPositionX());
            newNode.setPositionY(tn.getPositionY());
            nodeRepo.save(newNode);
        }

        // 复制连线
        List<WorkflowEdge> templateEdges = edgeRepo.findByWorkflowId(templateId);
        for (WorkflowEdge te : templateEdges) {
            WorkflowEdge newEdge = new WorkflowEdge();
            newEdge.setWorkflowId(newDef.getId());
            newEdge.setSourceNodeKey(te.getSourceNodeKey());
            newEdge.setTargetNodeKey(te.getTargetNodeKey());
            newEdge.setConditionExpression(te.getConditionExpression());
            newEdge.setLabel(te.getLabel());
            edgeRepo.save(newEdge);
        }

        return newDef;
    }
}
