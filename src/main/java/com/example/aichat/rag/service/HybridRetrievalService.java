package com.example.aichat.rag.service;

import com.example.aichat.rag.model.HybridMatch;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 混合检索：向量检索 + BM25 关键词检索，加权合并
 */
@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);

    private final VectorStoreService vectorStoreService;
    private final Bm25RetrievalService bm25Service;

    @Value("${rag.hybrid.vector-weight:0.7}")
    private double vectorWeight;

    @Value("${rag.hybrid.bm25-weight:0.3}")
    private double bm25Weight;

    @Value("${rag.retrieval.min-score:0.0}")
    private double minScore;

    public HybridRetrievalService(VectorStoreService vectorStoreService,
                                   Bm25RetrievalService bm25Service) {
        this.vectorStoreService = vectorStoreService;
        this.bm25Service = bm25Service;
    }

    /**
     * 混合检索 = 向量检索 + BM25 关键词检索
     *
     * @param query    查询文本
     * @param topK     返回结果数
     * @param tenantId 租户 ID
     * @return 按总分降序排列的混合结果
     */
    public List<HybridMatch> hybridSearch(String query, int topK, Long tenantId) {
        log.info("[混合检索] query='{}', topK={}, tenantId={}", query, topK, tenantId);

        // 1. 向量检索（取较多结果用于合并）
        int vectorTopK = topK * 2;
        List<EmbeddingMatch<TextSegment>> vectorResults =
                vectorStoreService.search(query, vectorTopK, minScore, tenantId);

        return mergeWithBm25(query, topK, vectorResults);
    }

    /**
     * 按知识库 ID 混合检索，供 W4 知识库问答使用。
     */
    public List<HybridMatch> hybridSearchByKb(String query, int topK, Long kbId) {
        log.info("[混合检索] query='{}', topK={}, kbId={}", query, topK, kbId);

        int vectorTopK = topK * 2;
        List<EmbeddingMatch<TextSegment>> vectorResults =
                vectorStoreService.searchByKb(query, vectorTopK, minScore, kbId);

        return mergeWithBm25(query, topK, vectorResults);
    }

    private List<HybridMatch> mergeWithBm25(String query, int topK,
                                            List<EmbeddingMatch<TextSegment>> vectorResults) {
        // 2. BM25 关键词检索（用向量检索的结果作为语料库 — 因为 BM25 是内存实现）
        // 这里把所有向量检索到的片段作为 BM25 的候选集
        List<TextSegment> corpus = vectorResults.stream()
                .map(EmbeddingMatch::embedded)
                .toList();

        // 3. 合并结果
        Map<String, HybridMatch> merged = new LinkedHashMap<>();

        // 向量结果归一化
        double maxVectorScore = vectorResults.isEmpty() ? 1 :
                vectorResults.stream().mapToDouble(EmbeddingMatch::score).max().orElse(1);

        for (EmbeddingMatch<TextSegment> match : vectorResults) {
            String text = match.embedded().text();
            String key = String.valueOf(text.hashCode());
            double normalizedScore = match.score() / maxVectorScore;

            merged.computeIfAbsent(key, k -> new HybridMatch(text, match.embedded().metadata()))
                    .addVectorScore(normalizedScore * vectorWeight);
        }

        // BM25 得分归一化并合并
        if (!corpus.isEmpty()) {
            Map<String, Double> bm25Scores = bm25Service.searchWithScores(query, corpus, topK);
            double maxBm25 = bm25Scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);

            for (EmbeddingMatch<TextSegment> match : vectorResults) {
                String text = match.embedded().text();
                String key = String.valueOf(text.hashCode());
                Double bm25Score = bm25Scores.get(key);

                if (bm25Score != null && bm25Score > 0) {
                    double normalized = bm25Score / maxBm25;
                    merged.computeIfAbsent(key, k -> new HybridMatch(text, match.embedded().metadata()))
                            .addBm25Score(normalized * bm25Weight);
                }
            }
        }

        // 4. 按总分排序
        List<HybridMatch> results = merged.values().stream()
                .sorted((a, b) -> Double.compare(b.totalScore(), a.totalScore()))
                .limit(topK)
                .toList();

        log.info("[混合检索] 完成: vectorResults={}, mergedResults={}",
                vectorResults.size(), results.size());
        return results;
    }
}
