package com.example.aichat.workflow.engine.executor;

import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowApproval;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import com.example.aichat.workflow.repository.WorkflowApprovalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HUMAN_REVIEW 人工审批节点
 * 暂停执行，创建审批记录，等待人工操作
 * config: {"assignee": "admin", "prompt": "请审核以下内容"}
 */
@Component
public class HumanReviewNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(HumanReviewNodeExecutor.class);
    private final WorkflowApprovalRepository approvalRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HumanReviewNodeExecutor(WorkflowApprovalRepository approvalRepo) {
        this.approvalRepo = approvalRepo;
    }

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            String assignee = config.has("assignee") ? config.get("assignee").asText() : "admin";

            // 创建审批记录
            WorkflowApproval approval = new WorkflowApproval();
            approval.setExecutionId(ctx.getExecutionId());
            approval.setNodeKey(node.getNodeKey());
            approval.setStatus("PENDING");
            approval.setAssignee(assignee);
            approvalRepo.save(approval);

            // 标记工作流暂停
            ctx.pauseAtNode(node.getNodeKey());

            log.info("人工审批节点: 已创建审批记录, executionId={}, nodeKey={}",
                ctx.getExecutionId(), node.getNodeKey());

            return NodeResult.paused();

        } catch (Exception e) {
            log.error("人工审批节点失败: {}", e.getMessage());
            return NodeResult.of("审批节点创建失败: " + e.getMessage());
        }
    }
}
