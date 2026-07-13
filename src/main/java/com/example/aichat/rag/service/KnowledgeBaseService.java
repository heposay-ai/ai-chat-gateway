package com.example.aichat.rag.service;

import com.example.aichat.rag.model.KnowledgeBase;
import com.example.aichat.rag.model.KnowledgeDocument;
import com.example.aichat.rag.repository.KnowledgeBaseRepository;
import com.example.aichat.rag.repository.KnowledgeDocumentRepository;
import com.example.aichat.rag.repository.RagChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepo;
    private final KnowledgeDocumentRepository docRepo;
    private final RagChatSessionRepository sessionRepo;
    private final VectorStoreService vectorStoreService;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                KnowledgeDocumentRepository docRepo,
                                RagChatSessionRepository sessionRepo,
                                VectorStoreService vectorStoreService) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.sessionRepo = sessionRepo;
        this.vectorStoreService = vectorStoreService;
    }

    public KnowledgeBase create(Long tenantId, String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTenantId(tenantId);
        kb.setName(name);
        kb.setDescription(description);
        log.info("[知识库] 创建: name={}, tenantId={}", name, tenantId);
        return kbRepo.save(kb);
    }

    public List<KnowledgeBase> listByTenant(Long tenantId) {
        List<KnowledgeBase> knowledgeBases = kbRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
        knowledgeBases.forEach(this::syncDocCount);
        return knowledgeBases;
    }

    public KnowledgeBase getByIdAndTenant(Long id, Long tenantId) {
        KnowledgeBase kb = kbRepo.findById(id)
                .filter(item -> item.getTenantId().equals(tenantId))
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        return syncDocCount(kb);
    }

    public KnowledgeBase update(Long id, Long tenantId, String name, String description) {
        KnowledgeBase kb = getByIdAndTenant(id, tenantId);
        if (name != null) kb.setName(name);
        if (description != null) kb.setDescription(description);
        log.info("[知识库] 更新: id={}, name={}", id, kb.getName());
        return kbRepo.save(kb);
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        KnowledgeBase kb = getByIdAndTenant(id, tenantId);
        var docs = docRepo.findByKbIdOrderByCreatedAtDesc(id);
        for (var doc : docs) {
            deleteDocFile(doc);
        }
        vectorStoreService.removeByKnowledgeBaseId(id);
        sessionRepo.deleteByKbId(id);
        docRepo.deleteByKbId(id);
        kbRepo.delete(kb);
        log.info("[知识库] 删除: id={}, name={}, 清理文档数={}", id, kb.getName(), docs.size());
    }

    public void refreshDocCount(Long kbId) {
        kbRepo.findById(kbId).ifPresent(kb -> {
            syncDocCount(kb);
        });
    }

    @Transactional
    public void deleteDocument(Long kbId, Long docId) {
        var doc = docRepo.findByIdAndKbId(docId, kbId)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        ensureDeletable(doc);
        deleteDocFile(doc);
        vectorStoreService.removeByDocumentId(docId);
        docRepo.deleteById(docId);
        refreshDocCount(kbId);
    }

    @Transactional
    public int deleteDocuments(Long kbId, List<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return 0;
        }
        List<Long> uniqueIds = docIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return 0;
        }

        var documents = docRepo.findByKbIdAndIdIn(kbId, uniqueIds);
        for (var doc : documents) {
            ensureDeletable(doc);
        }
        for (var doc : documents) {
            deleteDocFile(doc);
            vectorStoreService.removeByDocumentId(doc.getId());
            docRepo.delete(doc);
        }
        refreshDocCount(kbId);
        log.info("[知识库] 批量删除文档: kbId={}, requested={}, deleted={}",
                kbId, uniqueIds.size(), documents.size());
        return documents.size();
    }

    private void ensureDeletable(KnowledgeDocument doc) {
        Integer status = doc.getStatus();
        if (status == null || (status != 0 && status != 1)) {
            return;
        }
        // 索引超过30分钟没更新，允许强制删除
        if (doc.getUpdatedAt() != null
                && Duration.between(doc.getUpdatedAt(), LocalDateTime.now()).toMinutes() > 30) {
            log.warn("[知识库] 强制删除卡住的文档: id={}, status={}, updatedAt={}", doc.getId(), status, doc.getUpdatedAt());
            return;
        }
        throw new IllegalStateException("文档正在索引中，完成或失败后再删除");
    }

    private void deleteDocFile(KnowledgeDocument doc) {
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(Path.of(doc.getFilePath()));
            if (deleted) {
                log.debug("[知识库] 已清理磁盘文件: {}", doc.getFilePath());
            }
        } catch (Exception e) {
            log.warn("[知识库] 清理磁盘文件失败: path={}, error={}", doc.getFilePath(), e.getMessage());
        }
    }

    private KnowledgeBase syncDocCount(KnowledgeBase kb) {
        int actualCount = docRepo.countByKbId(kb.getId());
        if (kb.getDocCount() == null || kb.getDocCount() != actualCount) {
            kb.setDocCount(actualCount);
            return kbRepo.save(kb);
        }
        return kb;
    }

    /**
     * 开启/关闭分享
     */
    public KnowledgeBase toggleShare(Long id, Long tenantId, boolean enabled) {
        KnowledgeBase kb = getByIdAndTenant(id, tenantId);
        if (enabled) {
            kb.setVisibility("shared");
            if (kb.getShareToken() == null) {
                kb.setShareToken(UUID.randomUUID().toString().replace("-", ""));
            }
        } else {
            kb.setVisibility("private");
        }
        log.info("[知识库] 分享设置: id={}, enabled={}", id, enabled);
        return kbRepo.save(kb);
    }
}
