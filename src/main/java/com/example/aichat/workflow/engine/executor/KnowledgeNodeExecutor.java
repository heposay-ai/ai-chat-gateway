package com.example.aichat.workflow.engine.executor;

import com.example.aichat.rag.service.VectorStoreService;
import com.example.aichat.workflow.engine.NodeExecutor;
import com.example.aichat.workflow.engine.NodeResult;
import com.example.aichat.workflow.engine.context.WorkflowContext;
import com.example.aichat.workflow.model.entity.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库检索节点
 * 复用 W3/W4 的 VectorStoreService 进行向量检索
 * config: {"topK": 5, "queryTemplate": "{{userInput}}"}
 */
@Component
public class KnowledgeNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeNodeExecutor.class);
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeNodeExecutor(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            int topK = config.has("topK") ? config.get("topK").asInt() : 5;
            String queryTemplate = config.has("queryTemplate")
                ? config.get("queryTemplate").asText() : "{{userInput}}";
            String query = ctx.resolveTemplate(queryTemplate);

            List<EmbeddingMatch<TextSegment>> matches = vectorStoreService.search(query, topK, 0.5);

            String result = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

            log.info("知识库检索完成: query={}, 命中{}条", query, matches.size());
            return NodeResult.of(result);

        } catch (Exception e) {
            log.error("知识库检索失败: {}", e.getMessage());
            return NodeResult.of("知识库检索失败: " + e.getMessage());
        }
    }
}
