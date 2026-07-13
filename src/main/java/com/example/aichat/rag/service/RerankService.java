package com.example.aichat.rag.service;

import com.example.aichat.rag.model.RerankResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Rerank 重排序服务
 * 支持 Cohere API 和本地 TEI（Text Embeddings Inference）
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    @Value("${rag.rerank.provider:cohere}")
    private String provider;

    @Value("${rag.rerank.api-key:}")
    private String apiKey;

    @Value("${rag.rerank.base-url:https://api.cohere.com}")
    private String baseUrl;

    @Value("${rag.rerank.model:rerank-v3.5}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return "local".equals(provider) || (apiKey != null && !apiKey.isBlank());
    }

    /**
     * 对候选文档重排序
     *
     * @param query     查询文本
     * @param documents 候选文档列表
     * @param topN      返回 Top N
     * @return 重排序后的结果
     */
    @SuppressWarnings("unchecked")
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        log.info("[Rerank] provider={}, docs={}, topN={}", provider, documents.size(), topN);

        try {
            if ("local".equals(provider)) {
                return rerankLocal(query, documents, topN);
            } else {
                return rerankCohere(query, documents, topN);
            }
        } catch (Exception e) {
            log.warn("[Rerank] 重排序失败，交由调用方使用本地重排兜底: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Cohere Rerank API
     */
    @SuppressWarnings("unchecked")
    private List<RerankResult> rerankCohere(String query, List<String> documents, int topN) {
        String url = baseUrl + "/v1/rerank";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", model,
                "query", query,
                "documents", documents,
                "top_n", topN,
                "return_documents", true
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");

        List<RerankResult> reranked = results.stream()
                .map(r -> new RerankResult(
                        ((Number) r.get("index")).intValue(),
                        ((Number) r.get("relevance_score")).doubleValue(),
                        (String) ((Map<String, Object>) r.get("document")).get("text")
                ))
                .toList();

        log.info("[Rerank] Cohere 重排序完成: {} -> {} 个结果", documents.size(), reranked.size());
        return reranked;
    }

    /**
     * 本地 TEI Rerank（HuggingFace Text Embeddings Inference）
     */
    @SuppressWarnings("unchecked")
    private List<RerankResult> rerankLocal(String query, List<String> documents, int topN) {
        String url = baseUrl + "/rerank";

        Map<String, Object> body = Map.of(
                "query", query,
                "texts", documents,
                "truncate", true
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body);
        ResponseEntity<List> response = restTemplate.postForEntity(url, entity, List.class);

        List<Map<String, Object>> results = response.getBody();

        List<RerankResult> reranked = results.stream()
                .map(r -> new RerankResult(
                        ((Number) r.get("index")).intValue(),
                        ((Number) r.get("score")).doubleValue(),
                        documents.get(((Number) r.get("index")).intValue())
                ))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topN)
                .toList();

        log.info("[Rerank] Local TEI 重排序完成: {} -> {} 个结果", documents.size(), reranked.size());
        return reranked;
    }
}
