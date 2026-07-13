package com.example.aichat.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RagSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public RagSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureKnowledgeDocumentFileHash();
    }

    private void ensureKnowledgeDocumentFileHash() {
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'knowledge_document'
                  AND COLUMN_NAME = 'file_hash'
                """, Integer.class);
        if (columnCount == null || columnCount == 0) {
            jdbcTemplate.execute("ALTER TABLE knowledge_document ADD COLUMN file_hash VARCHAR(64) COMMENT 'file SHA-256 hash'");
            log.info("[RAG Schema] 已添加 knowledge_document.file_hash");
        }

        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'knowledge_document'
                  AND INDEX_NAME = 'idx_kb_file_hash'
                """, Integer.class);
        if (indexCount == null || indexCount == 0) {
            jdbcTemplate.execute("CREATE INDEX idx_kb_file_hash ON knowledge_document (tenant_id, kb_id, file_hash)");
            log.info("[RAG Schema] 已添加 idx_kb_file_hash");
        }
    }
}
