package com.example.aichat.rag.controller;

import com.example.aichat.context.RequestContext;
import com.example.aichat.rag.model.KnowledgeDocument;
import com.example.aichat.rag.model.RagResponse;
import com.example.aichat.rag.repository.KnowledgeDocumentRepository;
import com.example.aichat.rag.service.IndexingPipeline;
import com.example.aichat.rag.service.RagService;
import com.example.aichat.rag.service.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final IndexingPipeline indexingPipeline;
    private final RagService ragService;
    private final KnowledgeDocumentRepository documentRepo;
    private final VectorStoreService vectorStoreService;

    public RagController(IndexingPipeline indexingPipeline,
                         RagService ragService,
                         KnowledgeDocumentRepository documentRepo,
                         VectorStoreService vectorStoreService) {
        this.indexingPipeline = indexingPipeline;
        this.ragService = ragService;
        this.documentRepo = documentRepo;
        this.vectorStoreService = vectorStoreService;
    }

    // ========== 文档管理 ==========

    /**
     * 上传文档（自动触发 Indexing 管线）
     */
    @PostMapping("/documents/upload")
    public KnowledgeDocument upload(@RequestParam("file") MultipartFile file) {
        Long tenantId = RequestContext.get("tenantId");
        log.info("[RAG] 上传文档: name={}, size={}KB, tenantId={}",
                file.getOriginalFilename(), file.getSize() / 1024, tenantId);

        KnowledgeDocument doc = indexingPipeline.saveFile(file, tenantId);
        if (!doc.isDuplicate()) {
            indexingPipeline.processAsync(doc.getId(), doc.getFilePath());
        }
        return doc;
    }

    /**
     * 文档列表
     */
    @GetMapping("/documents")
    public List<KnowledgeDocument> listDocuments() {
        Long tenantId = RequestContext.get("tenantId");
        return documentRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * 删除文档
     */
    @Transactional
    @DeleteMapping("/documents/{id}")
    public Map<String, String> deleteDocument(@PathVariable Long id) {
        Long tenantId = RequestContext.get("tenantId");
        KnowledgeDocument doc = documentRepo.findById(id).orElse(null);
        if (doc == null || !doc.getTenantId().equals(tenantId)) {
            return Map.of("status", "not_found");
        }
        if (doc.getStatus() != null && (doc.getStatus() == 0 || doc.getStatus() == 1)) {
            return Map.of("status", "error", "message", "文档正在索引中，请稍后再试");
        }
        vectorStoreService.removeByDocumentId(id);
        deleteFileOnDisk(doc);
        documentRepo.delete(doc);
        log.info("[RAG] 删除文档: id={}, fileName={}", id, doc.getFileName());
        return Map.of("status", "deleted");
    }

    private void deleteFileOnDisk(KnowledgeDocument doc) {
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(doc.getFilePath()));
        } catch (Exception e) {
            log.warn("[RAG] 清理磁盘文件失败: path={}, error={}", doc.getFilePath(), e.getMessage());
        }
    }

    /**
     * 文档处理进度查询
     */
    @GetMapping("/documents/{id}/progress")
    public Map<String, Object> getProgress(@PathVariable Long id) {
        Long tenantId = RequestContext.get("tenantId");
        KnowledgeDocument doc = documentRepo.findById(id).orElse(null);
        if (doc == null || !doc.getTenantId().equals(tenantId)) {
            return Map.of("status", "not_found");
        }

        String statusText = switch (doc.getStatus()) {
            case 0 -> "上传中";
            case 1 -> "解析完成";
            case 2 -> "已向量化";
            case -1 -> "处理失败";
            default -> "未知";
        };

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", doc.getId());
        result.put("fileName", doc.getFileName());
        result.put("status", doc.getStatus());
        result.put("statusText", statusText);
        result.put("charCount", doc.getCharCount());
        result.put("chunkCount", doc.getChunkCount());
        result.put("errorMessage", doc.getErrorMessage());
        result.put("updatedAt", doc.getUpdatedAt());
        return result;
    }

    // ========== RAG 问答 ==========

    /**
     * RAG 问答（非流式）
     */
    @GetMapping("/chat")
    public RagResponse chat(@RequestParam String q,
                            @RequestParam(required = false) String model) {
        Long tenantId = RequestContext.get("tenantId");
        return ragService.chat(q, model, tenantId);
    }

    /**
     * RAG 问答（流式 SSE）
     * 注意：在主线程获取 tenantId 后传入，避免异步线程丢失 ThreadLocal
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String q,
                             @RequestParam(required = false) String model) {
        Long tenantId = RequestContext.get("tenantId");
        return ragService.streamChat(q, model, tenantId);
    }
}
