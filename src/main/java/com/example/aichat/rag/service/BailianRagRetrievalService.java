package com.example.aichat.rag.service;

import com.aliyun.bailian20231229.Client;
import com.aliyun.bailian20231229.models.RetrieveRequest;
import com.aliyun.bailian20231229.models.RetrieveResponse;
import com.aliyun.bailian20231229.models.RetrieveResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.example.aichat.rag.model.RetrievedChunk;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BailianRagRetrievalService implements RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(BailianRagRetrievalService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.bailian.access-key-id:}")
    private String accessKeyId;

    @Value("${rag.bailian.access-key-secret:}")
    private String accessKeySecret;

    @Value("${rag.bailian.endpoint:bailian.cn-beijing.aliyuncs.com}")
    private String endpoint;

    @Value("${rag.bailian.workspace-id:}")
    private String workspaceId;

    @Value("${rag.bailian.index-id:}")
    private String defaultIndexId;

    @Value("${rag.bailian.dense-top-k:30}")
    private int denseTopK;

    @Value("${rag.bailian.sparse-top-k:30}")
    private int sparseTopK;

    @Value("${rag.bailian.enable-reranking:true}")
    private boolean enableReranking;

    @Value("${rag.bailian.enable-rewrite:false}")
    private boolean enableRewrite;

    @Value("${rag.bailian.rerank-top-n:8}")
    private int rerankTopN;

    @Value("${rag.bailian.rerank-min-score:0.2}")
    private double rerankMinScore;

    @Value("${rag.bailian.rerank-model:qwen3-rerank-hybrid}")
    private String rerankModel;

    @Override
    public List<RetrievedChunk> retrieveByTenant(String query, Long tenantId, int candidateCount) {
        return retrieve(query, defaultIndexId, candidateCount);
    }

    @Override
    public List<RetrievedChunk> retrieveByKnowledgeBase(String query, Long kbId, int candidateCount) {
        return retrieve(query, defaultIndexId, candidateCount);
    }

    private List<RetrievedChunk> retrieve(String query, String indexId, int candidateCount) {
        if (!isConfigured(indexId)) {
            throw new IllegalStateException("阿里云百炼 RAG 未配置完整，请检查 RAG_BAILIAN_* 环境变量");
        }

        log.info("[百炼RAG] 开始检索: workspaceId={}, indexId={}, query={}", workspaceId, indexId, query);
        try {
            RetrieveRequest request = new RetrieveRequest()
                    .setQuery(query)
                    .setIndexId(indexId)
                    .setDenseSimilarityTopK(Math.min(denseTopK, candidateCount))
                    .setSparseSimilarityTopK(Math.min(sparseTopK, candidateCount))
                    .setEnableReranking(enableReranking)
                    .setEnableRewrite(enableRewrite)
                    .setRerankTopN(Math.min(rerankTopN, candidateCount))
                    .setRerankMinScore((float) rerankMinScore)
                    .setSaveRetrieverHistory(false);

            if (enableReranking) {
                List<RetrieveRequest.RetrieveRequestRerank> rerank = List.of(
                        new RetrieveRequest.RetrieveRequestRerank()
                                .setModelName(rerankModel)
                                .setRerankMode("qa")
                );
                request.setRerank(rerank);
            }

            RetrieveResponse response = createClient().retrieveWithOptions(
                    workspaceId, request, new HashMap<>(), new RuntimeOptions());
            RetrieveResponseBody body = response.getBody();
            if (body == null || Boolean.FALSE.equals(body.getSuccess())) {
                throw new IllegalStateException(body != null ? body.getMessage() : "百炼 Retrieve API 响应为空");
            }

            List<RetrievedChunk> chunks = toChunks(body.getData(), candidateCount);
            log.info("[百炼RAG] Retrieve完成: indexId={}, candidates={}, selected={}",
                    indexId, chunks.size(), Math.min(chunks.size(), candidateCount));
            return chunks;
        } catch (com.aliyun.tea.TeaException e) {
            log.error("[百炼RAG] Retrieve 失败 - Alibaba Cloud 返回:\n" +
                    "  HTTP状态: {}\n" +
                    "  错误码: {}\n" +
                    "  错误信息: {}\n" +
                    "  RequestId: {}\n" +
                    "  Data: {}\n" +
                    "  当前请求 -> workspaceId={}, indexId={}, endpoint={}",
                    e.getStatusCode(), e.getCode(), e.getMessage(),
                    e.getData() != null ? e.getData().get("requestId") : "N/A",
                    e.getData(), workspaceId, indexId, endpoint);
            throw new RuntimeException("调用阿里云百炼 Retrieve API 失败", e);
        } catch (Exception e) {
            log.error("[百炼RAG] Retrieve 失败: query={}, indexId={}, workspaceId={}, error={}",
                    query, indexId, workspaceId, e.toString());
            throw new RuntimeException("调用阿里云百炼 Retrieve API 失败", e);
        }
    }

    private Client createClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = endpoint;
        return new Client(config);
    }

    private List<RetrievedChunk> toChunks(RetrieveResponseBody.RetrieveResponseBodyData data, int limit) {
        if (data == null || data.getNodes() == null) {
            return List.of();
        }

        List<RetrievedChunk> chunks = new ArrayList<>();
        for (RetrieveResponseBody.RetrieveResponseBodyDataNodes node : data.getNodes()) {
            if (node == null || node.getText() == null || node.getText().isBlank()) {
                continue;
            }
            chunks.add(new RetrievedChunk(
                    node.getText(),
                    toMetadata(node.getMetadata()),
                    node.getScore() != null ? node.getScore() : 0
            ));
            if (chunks.size() >= limit) {
                break;
            }
        }
        return chunks;
    }

    private Metadata toMetadata(Object rawMetadata) {
        Map<String, Object> metadataMap = parseMetadata(rawMetadata);
        Metadata metadata = new Metadata();
        putMetadata(metadata, "file_name", firstNonBlank(metadataMap, "doc_name", "title", "file_name"));
        putMetadata(metadata, "doc_id", firstNonBlank(metadataMap, "doc_id", "nid", "_id"));
        putMetadata(metadata, "workspace_id", firstNonBlank(metadataMap, "workspace_id"));
        putMetadata(metadata, "source", "bailian");
        return metadata;
    }

    private Map<String, Object> parseMetadata(Object rawMetadata) {
        if (rawMetadata == null) {
            return Map.of();
        }
        if (rawMetadata instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (rawMetadata instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, new TypeReference<>() {});
            } catch (Exception e) {
                log.debug("[百炼RAG] metadata 解析失败: {}", e.getMessage());
            }
        }
        return Map.of();
    }

    private String firstNonBlank(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private void putMetadata(Metadata metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private boolean isConfigured(String indexId) {
        return notBlank(accessKeyId)
                && notBlank(accessKeySecret)
                && notBlank(workspaceId)
                && notBlank(indexId);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
