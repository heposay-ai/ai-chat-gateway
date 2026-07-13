package com.example.aichat.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Value("${rag.embedding.batch-size:6}")
    private int embeddingBatchSize;

    @Value("${rag.embedding.batch-delay-ms:300}")
    private long embeddingBatchDelayMs;

    @Value("${rag.embedding.max-retries:3}")
    private int embeddingMaxRetries;

    @Value("${rag.embedding.retry-base-delay-ms:1000}")
    private long embeddingRetryBaseDelayMs;

    public VectorStoreService(EmbeddingStore<TextSegment> embeddingStore,
                              EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 批量向量化并存入，返回所有存入的 embedding ID（用于失败时清理）
     */
    public List<String> storeAll(List<TextSegment> segments) {
        log.info("[向量存储] 开始向量化 {} 个片段", segments.size());
        List<String> allIds = new ArrayList<>();

        // 分批处理，DashScope embedding API 限制每批最多 10 条，默认取 6 留余量。
        int batchSize = Math.max(1, embeddingBatchSize);
        for (int i = 0; i < segments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(i, end);

            try {
                throttleBeforeBatch(i);
                List<Embedding> embeddings = embedBatchWithRetry(batch, i, end);
                List<String> ids = embeddingStore.addAll(embeddings, batch);
                allIds.addAll(ids);
                log.debug("[向量存储] 已处理 {}/{} 个片段", end, segments.size());
            } catch (Exception e) {
                log.error("[向量存储] 第 {}-{} 批次失败，已完成 {} 个", i, end, allIds.size(), e);
                throw new RuntimeException("向量化失败（已处理 " + allIds.size() + "/" + segments.size() + "）", e);
            }
        }

        log.info("[向量存储] 全部向量化完成: {} 个片段", segments.size());
        return allIds;
    }

    private List<Embedding> embedBatchWithRetry(List<TextSegment> batch, int start, int end) {
        int maxRetries = Math.max(0, embeddingMaxRetries);
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return embeddingModel.embedAll(batch).content();
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt >= maxRetries) {
                    break;
                }
                long delay = retryDelay(attempt);
                log.warn("[向量存储] 第 {}-{} 批次向量化失败，{}ms 后重试({}/{}): {}",
                        start, end, delay, attempt + 1, maxRetries, e.getMessage());
                sleep(delay);
            }
        }
        throw lastError != null ? lastError : new RuntimeException("向量化失败");
    }

    private void throttleBeforeBatch(int start) {
        if (start > 0 && embeddingBatchDelayMs > 0) {
            sleep(embeddingBatchDelayMs);
        }
    }

    private long retryDelay(int attempt) {
        long baseDelay = Math.max(0, embeddingRetryBaseDelayMs);
        if (baseDelay == 0) {
            return 0;
        }
        long multiplier = 1L << Math.min(attempt, 6);
        return baseDelay * multiplier;
    }

    private void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("向量化等待被中断", e);
        }
    }

    /**
     * 按租户 ID 过滤的相似度检索
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults, double minScore, Long tenantId) {
        log.debug("[向量检索] query='{}', maxResults={}, minScore={}, tenantId={}", query, maxResults, minScore, tenantId);

        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(new MetadataFilterBuilder("tenant_id").isEqualTo(String.valueOf(tenantId)))
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        log.info("[向量检索] 找到 {} 个匹配结果 (tenantId={})", matches.size(), tenantId);
        return matches;
    }

    /**
     * 按知识库 ID 过滤的相似度检索（W4）
     */
    public List<EmbeddingMatch<TextSegment>> searchByKb(String query, int maxResults, double minScore, Long kbId) {
        log.debug("[向量检索] query='{}', maxResults={}, minScore={}, kbId={}", query, maxResults, minScore, kbId);

        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(new MetadataFilterBuilder("kb_id").isEqualTo(String.valueOf(kbId)))
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        log.info("[向量检索] 找到 {} 个匹配结果 (kbId={})", matches.size(), kbId);
        return matches;
    }

    /**
     * 不带租户过滤的检索（向后兼容）
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults, double minScore) {
        log.debug("[向量检索] query='{}', maxResults={}, minScore={}", query, maxResults, minScore);

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.findRelevant(queryEmbedding, maxResults, minScore);

        log.info("[向量检索] 找到 {} 个匹配结果", matches.size());
        return matches;
    }

    /**
     * 按 ID 列表删除向量
     */
    public void removeByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        embeddingStore.removeAll(new ArrayList<>(ids));
        log.info("[向量存储] 已删除 {} 个向量", ids.size());
    }

    /**
     * 按文档 ID 删除向量。用于文档重建索引和删除清理。
     */
    public void removeByDocumentId(Long docId) {
        if (docId == null) return;
        embeddingStore.removeAll(new MetadataFilterBuilder("doc_id").isEqualTo(String.valueOf(docId)));
        log.info("[向量存储] 已按 doc_id={} 删除向量", docId);
    }

    /**
     * 按知识库 ID 删除向量。用于删除知识库时清理 PgVector 残留。
     */
    public void removeByKnowledgeBaseId(Long kbId) {
        if (kbId == null) return;
        embeddingStore.removeAll(new MetadataFilterBuilder("kb_id").isEqualTo(String.valueOf(kbId)));
        log.info("[向量存储] 已按 kb_id={} 删除向量", kbId);
    }
}
