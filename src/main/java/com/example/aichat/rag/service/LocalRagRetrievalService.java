package com.example.aichat.rag.service;

import com.example.aichat.rag.model.HybridMatch;
import com.example.aichat.rag.model.RetrievedChunk;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocalRagRetrievalService implements RagRetrievalService {

    private final VectorStoreService vectorStoreService;
    private final HybridRetrievalService hybridRetrievalService;

    @Value("${rag.hybrid.enabled:false}")
    private boolean hybridEnabled;

    @Value("${rag.retrieval.min-score:0.0}")
    private double minScore;

    public LocalRagRetrievalService(VectorStoreService vectorStoreService,
                                    HybridRetrievalService hybridRetrievalService) {
        this.vectorStoreService = vectorStoreService;
        this.hybridRetrievalService = hybridRetrievalService;
    }

    @Override
    public List<RetrievedChunk> retrieveByTenant(String query, Long tenantId, int candidateCount) {
        if (hybridEnabled) {
            return hybridRetrievalService.hybridSearch(query, candidateCount, tenantId).stream()
                    .map(this::fromHybridMatch)
                    .toList();
        }

        return vectorStoreService.search(query, candidateCount, minScore, tenantId).stream()
                .map(this::fromEmbeddingMatch)
                .toList();
    }

    @Override
    public List<RetrievedChunk> retrieveByKnowledgeBase(String query, Long kbId, int candidateCount) {
        if (hybridEnabled) {
            return hybridRetrievalService.hybridSearchByKb(query, candidateCount, kbId).stream()
                    .map(this::fromHybridMatch)
                    .toList();
        }

        return vectorStoreService.searchByKb(query, candidateCount, minScore, kbId).stream()
                .map(this::fromEmbeddingMatch)
                .toList();
    }

    private RetrievedChunk fromHybridMatch(HybridMatch match) {
        return new RetrievedChunk(match.getText(), match.getMetadata(), match.totalScore());
    }

    private RetrievedChunk fromEmbeddingMatch(EmbeddingMatch<TextSegment> match) {
        return RetrievedChunk.fromSegment(match.embedded(), match.score());
    }
}
