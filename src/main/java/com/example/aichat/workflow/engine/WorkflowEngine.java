package com.example.aichat.workflow.engine;

import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.*;
import com.example.aichat.workflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工作流执行引擎
 * 核心逻辑: BFS 拓扑排序 + 条件分支路由 + 并行执行
 */
@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final NodeExecutorFactory executorFactory;
    private final WorkflowNodeRepository nodeRepo;
    private final WorkflowEdgeRepository edgeRepo;
    private final WorkflowExecutionRepository executionRepo;
    private final NodeExecutionRepository nodeExecRepo;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(8);

    public WorkflowEngine(NodeExecutorFactory executorFactory,
                          WorkflowNodeRepository nodeRepo,
                          WorkflowEdgeRepository edgeRepo,
                          WorkflowExecutionRepository executionRepo,
                          NodeExecutionRepository nodeExecRepo) {
        this.executorFactory = executorFactory;
        this.nodeRepo = nodeRepo;
        this.edgeRepo = edgeRepo;
        this.executionRepo = executionRepo;
        this.nodeExecRepo = nodeExecRepo;
    }

    /**
     * 执行工作流（SSE 流式推送节点状态）
     */
    public void execute(WorkflowExecution execution, SseEmitter emitter) {
        Long workflowId = execution.getWorkflowId();
        List<WorkflowNode> nodes = nodeRepo.findByWorkflowId(workflowId);
        List<WorkflowEdge> edges = edgeRepo.findByWorkflowId(workflowId);

        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();
        nodes.forEach(n -> nodeMap.put(n.getNodeKey(), n));

        // 每条入边都需要被解析为“选中”或“跳过”，汇合节点才能正确推进
        Map<String, List<WorkflowEdge>> outgoingEdges = buildOutgoingEdges(edges);
        Map<String, Integer> unresolvedIncoming = buildInDegreeMap(nodeMap.keySet(), edges);
        Map<String, Integer> activeIncoming = new HashMap<>();

        // 初始化执行上下文
        WorkflowContext ctx = new WorkflowContext(
            execution.getId(), execution.getTenantId(), execution.getInput());

        sendEvent(emitter, "execution_start",
            "{\"executionId\":" + execution.getId() + ",\"workflowName\":\"workflow\"}");

        Set<String> executedNodes = ConcurrentHashMap.newKeySet();
        Set<String> skippedNodes = new HashSet<>();

        // BFS 拓扑排序执行
        Queue<String> ready = new LinkedList<>();
        for (var entry : unresolvedIncoming.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        while (!ready.isEmpty() && !ctx.isPaused()) {
            List<String> batch = new ArrayList<>(ready);
            ready.clear();

            // 并行执行同批次节点
            List<CompletableFuture<Void>> futures = batch.stream()
                .map(nodeKey -> CompletableFuture.runAsync(() -> {
                    WorkflowNode node = nodeMap.get(nodeKey);
                    executeNode(node, ctx, execution, emitter);
                    executedNodes.add(nodeKey);
                }, threadPool).exceptionally(ex -> {
                    log.error("节点执行异常: nodeKey={}, error={}", nodeKey, ex.getMessage());
                    return null;
                }))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            if (ctx.isPaused()) {
                break;
            }

            // 解析所有出边。未命中的条件分支也必须释放下游汇合节点的依赖。
            for (String nodeKey : batch) {
                for (WorkflowEdge edge : outgoingEdges.getOrDefault(nodeKey, List.of())) {
                    boolean active = shouldTraverse(nodeKey, ctx, nodeMap, edge);
                    resolveIncomingEdge(edge.getTargetNodeKey(), active, unresolvedIncoming,
                        activeIncoming, outgoingEdges, ready, skippedNodes);
                }
            }
        }

        // 标记跳过的节点
        markSkippedNodes(execution, nodes, executedNodes, emitter);

        // 更新执行状态
        if (ctx.isPaused()) {
            execution.setStatus("PAUSED");
            sendEvent(emitter, "execution_paused",
                "{\"nodeKey\":\"" + ctx.getPausedAtNodeKey() + "\",\"message\":\"等待人工审批\"}");
        } else {
            execution.setStatus("COMPLETED");
            execution.setOutput(ctx.getVariable("output"));
            execution.setFinishedAt(LocalDateTime.now());
            sendEvent(emitter, "execution_complete",
                "{\"executionId\":" + execution.getId() + ",\"status\":\"COMPLETED\"}");
        }
        executionRepo.save(execution);

        try { emitter.complete(); } catch (Exception ignored) {}
    }

    /**
     * 从暂停节点恢复执行
     */
    public void resumeFrom(Long executionId, String fromNodeKey) {
        WorkflowExecution execution = executionRepo.findById(executionId)
            .orElseThrow(() -> new RuntimeException("执行记录不存在: " + executionId));

        execution.setStatus("RUNNING");
        executionRepo.save(execution);

        // 简化实现: 重新执行整个工作流（生产环境应从断点恢复）
        log.info("工作流恢复执行: executionId={}, fromNode={}", executionId, fromNodeKey);
    }

    private void executeNode(WorkflowNode node, WorkflowContext ctx,
                              WorkflowExecution execution, SseEmitter emitter) {
        String nodeKey = node.getNodeKey();
        String nodeType = node.getNodeType();

        // 发送 node_start 事件
        sendEvent(emitter, "node_start",
            "{\"nodeKey\":\"" + nodeKey + "\",\"nodeType\":\"" + nodeType +
            "\",\"label\":\"" + (node.getLabel() != null ? node.getLabel() : nodeKey) + "\"}");

        // 创建节点执行记录
        NodeExecution nodeExec = new NodeExecution();
        nodeExec.setExecutionId(execution.getId());
        nodeExec.setNodeKey(nodeKey);
        nodeExec.setNodeType(nodeType);
        nodeExec.setStatus("RUNNING");
        nodeExec.setStartedAt(LocalDateTime.now());
        nodeExec.setInput(ctx.getVariable("output"));
        nodeExecRepo.save(nodeExec);

        long startMs = System.currentTimeMillis();

        try {
            NodeExecutor executor = executorFactory.create(nodeType);
            NodeResult result = executor.execute(node, ctx);

            long durationMs = System.currentTimeMillis() - startMs;

            // 处理结果
            if (result.isPaused()) {
                nodeExec.setStatus("WAITING_APPROVAL");
            } else if (result.getBranch() != null) {
                ctx.setNodeOutput(nodeKey, result.getBranch());
                nodeExec.setStatus("COMPLETED");
                nodeExec.setOutput("\"branch: " + result.getBranch() + "\"");
            } else {
                ctx.setNodeOutput(nodeKey, result.getOutput());
                nodeExec.setStatus("COMPLETED");
                nodeExec.setOutput(truncate(result.getOutput(), 2000));
            }

            nodeExec.setDurationMs(durationMs);
            nodeExec.setFinishedAt(LocalDateTime.now());
            nodeExecRepo.save(nodeExec);

            // 发送 node_end 事件
            String eventData = "{\"nodeKey\":\"" + nodeKey +
                "\",\"status\":\"" + nodeExec.getStatus() +
                "\",\"durationMs\":" + durationMs;
            if (result.getBranch() != null) {
                eventData += ",\"branch\":\"" + result.getBranch() + "\"";
            }
            if (result.getOutput() != null) {
                eventData += ",\"outputPreview\":\"" +
                    truncate(result.getOutput(), 100).replace("\"", "\\\"") + "\"";
            }
            eventData += "}";
            sendEvent(emitter, "node_end", eventData);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            nodeExec.setStatus("FAILED");
            nodeExec.setDurationMs(durationMs);
            nodeExec.setErrorMessage(e.getMessage());
            nodeExec.setFinishedAt(LocalDateTime.now());
            nodeExecRepo.save(nodeExec);

            sendEvent(emitter, "node_end",
                "{\"nodeKey\":\"" + nodeKey + "\",\"status\":\"FAILED\"" +
                ",\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");

            log.error("节点执行失败: nodeKey={}, error={}", nodeKey, e.getMessage());
        }
    }

    private boolean shouldTraverse(String sourceKey,
                                   WorkflowContext ctx,
                                   Map<String, WorkflowNode> nodeMap,
                                   WorkflowEdge edge) {
        WorkflowNode sourceNode = nodeMap.get(sourceKey);
        if (!"CONDITION".equals(sourceNode.getNodeType())) {
            return true;
        }

        String branchResult = ctx.getNodeOutput(sourceKey);
        String edgeCondition = edge.getConditionExpression();
        if (edgeCondition != null) {
            return branchResult != null && branchResult.equals(edgeCondition);
        }
        String edgeLabel = edge.getLabel();
        if (edgeLabel != null) {
            return branchResult != null && branchResult.equals(edgeLabel);
        }
        return true;
    }

    private void resolveIncomingEdge(String targetKey,
                                     boolean active,
                                     Map<String, Integer> unresolvedIncoming,
                                     Map<String, Integer> activeIncoming,
                                     Map<String, List<WorkflowEdge>> outgoingEdges,
                                     Queue<String> ready,
                                     Set<String> skippedNodes) {
        if (active) {
            activeIncoming.merge(targetKey, 1, Integer::sum);
        }
        int unresolved = unresolvedIncoming.merge(targetKey, -1, Integer::sum);
        if (unresolved != 0) {
            return;
        }

        if (activeIncoming.getOrDefault(targetKey, 0) > 0) {
            ready.add(targetKey);
            return;
        }

        skipNodeAndResolveDownstream(targetKey, unresolvedIncoming, activeIncoming,
            outgoingEdges, ready, skippedNodes);
    }

    private void skipNodeAndResolveDownstream(String nodeKey,
                                              Map<String, Integer> unresolvedIncoming,
                                              Map<String, Integer> activeIncoming,
                                              Map<String, List<WorkflowEdge>> outgoingEdges,
                                              Queue<String> ready,
                                              Set<String> skippedNodes) {
        if (!skippedNodes.add(nodeKey)) {
            return;
        }
        for (WorkflowEdge edge : outgoingEdges.getOrDefault(nodeKey, List.of())) {
            resolveIncomingEdge(edge.getTargetNodeKey(), false, unresolvedIncoming,
                activeIncoming, outgoingEdges, ready, skippedNodes);
        }
    }

    private Map<String, List<WorkflowEdge>> buildOutgoingEdges(List<WorkflowEdge> edges) {
        Map<String, List<WorkflowEdge>> outgoing = new HashMap<>();
        for (WorkflowEdge edge : edges) {
            outgoing.computeIfAbsent(edge.getSourceNodeKey(), key -> new ArrayList<>()).add(edge);
        }
        return outgoing;
    }

    private Map<String, Integer> buildInDegreeMap(Set<String> nodeKeys, List<WorkflowEdge> edges) {
        Map<String, Integer> inDegree = new HashMap<>();
        nodeKeys.forEach(k -> inDegree.put(k, 0));
        for (WorkflowEdge edge : edges) {
            inDegree.merge(edge.getTargetNodeKey(), 1, Integer::sum);
        }
        return inDegree;
    }

    private void markSkippedNodes(WorkflowExecution execution,
                                   List<WorkflowNode> allNodes,
                                   Set<String> executedNodes,
                                   SseEmitter emitter) {
        for (WorkflowNode node : allNodes) {
            if (!executedNodes.contains(node.getNodeKey())) {
                NodeExecution skipped = new NodeExecution();
                skipped.setExecutionId(execution.getId());
                skipped.setNodeKey(node.getNodeKey());
                skipped.setNodeType(node.getNodeType());
                skipped.setStatus("SKIPPED");
                nodeExecRepo.save(skipped);

                sendEvent(emitter, "node_skipped",
                    "{\"nodeKey\":\"" + node.getNodeKey() + "\",\"reason\":\"条件分支未命中\"}");
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            log.warn("SSE 发送失败: event={}", event);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
