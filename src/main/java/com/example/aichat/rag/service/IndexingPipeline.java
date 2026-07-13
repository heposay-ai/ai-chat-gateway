package com.example.aichat.rag.service;

import com.example.aichat.rag.model.KnowledgeDocument;
import com.example.aichat.rag.repository.KnowledgeDocumentRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class IndexingPipeline {

    private static final Logger log = LoggerFactory.getLogger(IndexingPipeline.class);

    private final DocumentParseService parseService;
    private final ChunkService chunkService;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeDocumentRepository documentRepo;

    @Value("${rag.document.upload-dir:./uploads}")
    private String uploadDir;

    public IndexingPipeline(DocumentParseService parseService,
                            ChunkService chunkService,
                            VectorStoreService vectorStoreService,
                            KnowledgeDocumentRepository documentRepo) {
        this.parseService = parseService;
        this.chunkService = chunkService;
        this.vectorStoreService = vectorStoreService;
        this.documentRepo = documentRepo;
    }

    /**
     * 保存上传文件并创建数据库记录（关联知识库）
     */
    public KnowledgeDocument saveFile(MultipartFile file, Long tenantId, Long kbId) {
        return saveFileInternal(file, tenantId, kbId);
    }

    /**
     * 保存上传文件并创建数据库记录（向后兼容，不关联知识库）
     */
    public KnowledgeDocument saveFile(MultipartFile file, Long tenantId) {
        return saveFileInternal(file, tenantId, null);
    }

    private synchronized KnowledgeDocument saveFileInternal(MultipartFile file, Long tenantId, Long kbId) {
        // 保存文件到本地（转绝对路径，避免 Windows 下被解析到 Tomcat 临时目录）
        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + file.getOriginalFilename();
        Path filePath = Path.of(uploadDir, storedName).toAbsolutePath().normalize();
        String fileHash;
        try {
            Files.createDirectories(filePath.getParent());
            fileHash = copyAndHash(file, filePath);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }

        Optional<KnowledgeDocument> duplicate = findDuplicate(tenantId, kbId, fileHash);
        if (duplicate.isPresent()) {
            deleteQuietly(filePath);
            KnowledgeDocument existing = duplicate.get();
            existing.setDuplicate(true);
            log.info("[Indexing] 检测到重复文档，跳过入库: tenantId={}, kbId={}, fileName={}, duplicateId={}",
                    tenantId, kbId, file.getOriginalFilename(), existing.getId());
            return existing;
        }

        // 创建数据库记录
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTenantId(tenantId);
        doc.setKbId(kbId);
        doc.setFileName(file.getOriginalFilename());
        doc.setFilePath(filePath.toString());
        doc.setFileSize(file.getSize());
        doc.setFileHash(fileHash);
        doc.setContentType(file.getContentType());
        doc.setStatus(0);
        return documentRepo.save(doc);
    }

    private Optional<KnowledgeDocument> findDuplicate(Long tenantId, Long kbId, String fileHash) {
        if (kbId != null) {
            return documentRepo.findFirstByTenantIdAndKbIdAndFileHashOrderByCreatedAtDesc(tenantId, kbId, fileHash);
        }
        return documentRepo.findFirstByTenantIdAndKbIdIsNullAndFileHashOrderByCreatedAtDesc(tenantId, fileHash);
    }

    private String copyAndHash(MultipartFile file, Path filePath) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = file.getInputStream();
             DigestInputStream digestInput = new DigestInputStream(in, digest)) {
            Files.copy(digestInput, filePath);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private void deleteQuietly(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("[Indexing] 删除重复上传文件失败: {}", filePath, e);
        }
    }

    /**
     * 异步执行完整 Indexing 管线：解析 → 切片 → 向量化入库
     * 注意：接收 filePath 而非 MultipartFile，因为 @Async 在新线程执行时 MultipartFile 流已关闭
     */
    @Async("ragIndexingExecutor")
    public void processAsync(Long docId, String filePath) {
        processDocument(docId, filePath, false);
    }

    /**
     * 重建索引：先清理该文档旧向量，再按当前解析/切片配置重新入库。
     */
    @Async("ragIndexingExecutor")
    public void reindexAsync(Long docId) {
        KnowledgeDocument doc = documentRepo.findById(docId).orElse(null);
        if (doc == null) return;
        processDocument(docId, doc.getFilePath(), true);
    }

    private void processDocument(Long docId, String filePath, boolean cleanupExisting) {
        KnowledgeDocument doc = documentRepo.findById(docId).orElse(null);
        if (doc == null) return;
        List<String> storedEmbeddingIds = List.of();
        try {
            log.info("[Indexing] 开始处理文档: id={}, name={}", docId, doc.getFileName());
            doc.setStatus(0);
            doc.setErrorMessage(null);
            doc.setCharCount(0);
            doc.setChunkCount(0);
            documentRepo.save(doc);

            if (cleanupExisting) {
                vectorStoreService.removeByDocumentId(docId);
            }

            // Step 1: 构造基础 metadata（PDF 会按页继承并补充 page）
            Map<String, String> metadata = new java.util.HashMap<>(Map.of(
                    "doc_id", String.valueOf(doc.getId()),
                    "tenant_id", String.valueOf(doc.getTenantId()),
                    "file_name", doc.getFileName()
            ));
            if (doc.getKbId() != null) {
                metadata.put("kb_id", String.valueOf(doc.getKbId()));
            }

            // Step 2: 从磁盘路径解析文档。PDF 按页生成 Document(page=n)，非 PDF 保持单文档。
            List<Document> parsedDocuments = parseService.parseFileToDocuments(Path.of(filePath), metadata);
            int charCount = parsedDocuments.stream().mapToInt(parsed -> parsed.text().length()).sum();
            doc.setCharCount(charCount);
            doc.setStatus(1);
            documentRepo.save(doc);
            log.info("[Indexing] 解析完成: id={}, 文档单元={}, 字符数={}", docId, parsedDocuments.size(), charCount);

            // Step 3: 文本切片（metadata 中带 tenant_id/kb_id/page 用于检索隔离和引用定位）
            List<TextSegment> segments = new ArrayList<>();
            for (Document parsed : parsedDocuments) {
                segments.addAll(chunkService.split(parsed));
            }
            doc.setChunkCount(segments.size());
            documentRepo.save(doc);
            log.info("[Indexing] 切片完成: id={}, 切片数={}", docId, segments.size());

            // Step 4: 向量化并存入 PgVector
            storedEmbeddingIds = vectorStoreService.storeAll(segments);

            // Step 5: 更新状态
            doc.setStatus(2);
            documentRepo.save(doc);
            log.info("[Indexing] 文档处理完成: id={}, name={}", docId, doc.getFileName());

        } catch (Exception e) {
            log.error("[Indexing] 文档处理失败: id={}, name={}", docId, doc.getFileName(), e);
            doc.setStatus(-1);
            doc.setErrorMessage(e.getMessage() != null ?
                    e.getMessage().substring(0, Math.min(e.getMessage().length(), 900)) : "未知错误");
            documentRepo.save(doc);

            // 清理已入库的向量片段（避免孤儿数据）
            if (!storedEmbeddingIds.isEmpty()) {
                try {
                    vectorStoreService.removeByIds(storedEmbeddingIds);
                    log.info("[Indexing] 已清理 {} 个孤儿向量", storedEmbeddingIds.size());
                } catch (Exception cleanupErr) {
                    log.warn("[Indexing] 清理孤儿向量失败", cleanupErr);
                }
            }
        }
    }
}
