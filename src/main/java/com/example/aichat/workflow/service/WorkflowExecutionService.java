package com.example.aichat.workflow.service;

import com.example.aichat.workflow.engine.WorkflowEngine;
import com.example.aichat.workflow.model.entity.NodeExecution;
import com.example.aichat.workflow.model.entity.WorkflowExecution;
import com.example.aichat.workflow.repository.NodeExecutionRepository;
import com.example.aichat.workflow.repository.WorkflowExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final WorkflowEngine engine;
    private final WorkflowExecutionRepository executionRepo;
    private final NodeExecutionRepository nodeExecRepo;
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    public WorkflowExecutionService(WorkflowEngine engine,
                                     WorkflowExecutionRepository executionRepo,
                                     NodeExecutionRepository nodeExecRepo) {
        this.engine = engine;
        this.executionRepo = executionRepo;
        this.nodeExecRepo = nodeExecRepo;
    }

    /**
     * 执行工作流（SSE 流式返回）
     */
    public SseEmitter run(Long workflowId, Long tenantId, String input) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflowId);
        execution.setTenantId(tenantId);
        execution.setInput(input);
        execution.setTriggerType("API");
        executionRepo.save(execution);

        asyncExecutor.submit(() -> {
            try {
                engine.execute(execution, emitter);
            } catch (Exception e) {
                log.error("工作流执行异常: {}", e.getMessage(), e);
                execution.setStatus("FAILED");
                execution.setErrorMessage(e.getMessage());
                executionRepo.save(execution);
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    /**
     * 异步执行（Webhook / 定时触发，不返回 SSE）
     */
    public Long asyncRun(Long workflowId, Long tenantId, String input) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflowId);
        execution.setTenantId(tenantId);
        execution.setInput(input);
        execution.setTriggerType("WEBHOOK");
        executionRepo.save(execution);

        asyncExecutor.submit(() -> {
            SseEmitter dummyEmitter = new SseEmitter(300_000L);
            try {
                engine.execute(execution, dummyEmitter);
            } catch (Exception e) {
                log.error("异步工作流执行失败: {}", e.getMessage());
                execution.setStatus("FAILED");
                execution.setErrorMessage(e.getMessage());
                executionRepo.save(execution);
            }
        });

        return execution.getId();
    }

    public WorkflowExecution getExecution(Long executionId) {
        return executionRepo.findById(executionId)
            .orElseThrow(() -> new RuntimeException("执行记录不存在: " + executionId));
    }

    public List<WorkflowExecution> listExecutions(Long workflowId) {
        return executionRepo.findByWorkflowIdOrderByStartedAtDesc(workflowId);
    }

    public List<NodeExecution> getNodeExecutions(Long executionId) {
        return nodeExecRepo.findByExecutionIdOrderByStartedAtAsc(executionId);
    }

    public void cancelExecution(Long executionId) {
        WorkflowExecution execution = getExecution(executionId);
        execution.setStatus("CANCELLED");
        executionRepo.save(execution);
    }
}
