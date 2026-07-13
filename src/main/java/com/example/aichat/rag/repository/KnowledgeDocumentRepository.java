package com.example.aichat.rag.repository;

import com.example.aichat.rag.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<KnowledgeDocument> findByKbIdOrderByCreatedAtDesc(Long kbId);

    int countByKbId(Long kbId);

    boolean existsByIdAndKbId(Long id, Long kbId);

    Optional<KnowledgeDocument> findByIdAndKbId(Long id, Long kbId);

    List<KnowledgeDocument> findByKbIdAndIdIn(Long kbId, List<Long> ids);

    Optional<KnowledgeDocument> findFirstByTenantIdAndKbIdAndFileHashOrderByCreatedAtDesc(
            Long tenantId, Long kbId, String fileHash);

    Optional<KnowledgeDocument> findFirstByTenantIdAndKbIdIsNullAndFileHashOrderByCreatedAtDesc(
            Long tenantId, String fileHash);

    void deleteByKbId(Long kbId);
}
