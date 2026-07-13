package com.example.aichat.rag.service;

import com.example.aichat.rag.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
public class RagRetrievalRouter implements RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalRouter.class);

    private final LocalRagRetrievalService localRetrievalService;
    private final BailianRagRetrievalService bailianRetrievalService;

    @Value("${rag.retrieval.provider:local}")
    private String provider;

    public RagRetrievalRouter(LocalRagRetrievalService localRetrievalService,
                              BailianRagRetrievalService bailianRetrievalService) {
        this.localRetrievalService = localRetrievalService;
        this.bailianRetrievalService = bailianRetrievalService;
    }

    @PostConstruct
    void logStartupProvider() {
        if ("bailian".equalsIgnoreCase(provider) || "aliyun".equalsIgnoreCase(provider)) {
            log.warn("========================================");
            log.warn("  RAG 检索: 百炼云端 (bailian)");
            log.warn("========================================");
        } else {
            log.info("RAG 检索: 本地 pgvector (local)");
        }
    }

    @Override
    public List<RetrievedChunk> retrieveByTenant(String query, Long tenantId, int candidateCount) {
        RagRetrievalService delegate = delegate();
        log.info("[RAG检索路由] provider={}, scope=tenant, candidateCount={}", provider, candidateCount);
        return delegate.retrieveByTenant(query, tenantId, candidateCount);
    }

    @Override
    public List<RetrievedChunk> retrieveByKnowledgeBase(String query, Long kbId, int candidateCount) {
        RagRetrievalService delegate = delegate();
        log.info("[RAG检索路由] provider={}, scope=kb, candidateCount={}", provider, candidateCount);
        return delegate.retrieveByKnowledgeBase(query, kbId, candidateCount);
    }

    private RagRetrievalService delegate() {
        if ("bailian".equalsIgnoreCase(provider) || "aliyun".equalsIgnoreCase(provider)) {
            return bailianRetrievalService;
        }
        return localRetrievalService;
    }
}
