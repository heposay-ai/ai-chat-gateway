package com.example.aichat.workflow.engine.executor;

import com.example.aichat.rag.model.RetrievedChunk;
import com.example.aichat.rag.service.RagRetrievalRouter;
import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库检索节点
 * 复用 W3/W4 的 VectorStoreService 进行向量检索
 * config: {"knowledgeBaseId": 1, "topK": 5, "query": "{{userInput}}"}
 */
@Component
public class KnowledgeNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeNodeExecutor.class);
    private final RagRetrievalRouter retrievalRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeNodeExecutor(RagRetrievalRouter retrievalRouter) {
        this.retrievalRouter = retrievalRouter;
    }

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            int topK = config.has("topK") ? config.get("topK").asInt() : 5;
            if (!config.hasNonNull("knowledgeBaseId")) {
                return NodeResult.of("知识库检索失败: 请先配置知识库");
            }
            Long knowledgeBaseId = config.get("knowledgeBaseId").asLong();
            String queryTemplate = config.has("queryTemplate")
                ? config.get("queryTemplate").asText()
                : config.has("query") ? config.get("query").asText() : "{{userInput}}";
            String query = ctx.resolveTemplate(queryTemplate);

            List<RetrievedChunk> matches = retrievalRouter
                .retrieveByKnowledgeBase(query, knowledgeBaseId, topK);

            String result = matches.stream()
                .map(RetrievedChunk::text)
                .collect(Collectors.joining("\n\n---\n\n"));

            log.info("知识库检索完成: query={}, 命中{}条", query, matches.size());
            return NodeResult.of(result);

        } catch (Exception e) {
            log.error("知识库检索失败: {}", e.getMessage());
            return NodeResult.of("知识库检索失败: " + e.getMessage());
        }
    }
}
