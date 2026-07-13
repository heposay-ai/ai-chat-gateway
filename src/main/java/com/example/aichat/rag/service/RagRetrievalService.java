package com.example.aichat.rag.service;

import com.example.aichat.rag.model.RetrievedChunk;

import java.util.List;

public interface RagRetrievalService {

    List<RetrievedChunk> retrieveByTenant(String query, Long tenantId, int candidateCount);

    List<RetrievedChunk> retrieveByKnowledgeBase(String query, Long kbId, int candidateCount);
}
