# Database Migration Memory

线上或已有数据卷环境改数据库字段时，不能只改 JPA Entity 或 `schema.sql`。

## 原则

- `src/main/resources/db/schema.sql` 只负责新库初始化；已有 MySQL volume 不会重新执行它。
- JPA `ddl-auto: update` 不能作为线上迁移手段；它不可审计、不可控，也不保证索引和复杂变更正确。
- 每次表结构变更都要有明确的迁移 SQL、验证 SQL 和回滚/降级说明。
- 迁移要向后兼容：优先新增可空字段、补数据、代码切流，最后再收紧约束。
- 禁止在高峰期直接执行可能长时间锁表的 DDL；大表变更要评估在线 DDL、分批回填和维护窗口。

## 标准流程

1. 设计变更：说明新增/修改/删除的表、字段、索引和原因。
2. 编写 migration SQL：使用可重复执行或先检查后执行的 SQL。
3. 本地验证：在已有数据的库上执行迁移，再启动应用验证接口。
4. 上线顺序：先执行数据库迁移，再发布依赖新字段的应用代码。
5. 验证上线：检查字段存在、索引存在、关键接口不再 500。
6. 记录结果：把执行过的 SQL、时间和影响范围写入变更记录。

## 推荐 SQL 模板

添加字段前检查：

```sql
SELECT COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'knowledge_document'
  AND COLUMN_NAME = 'file_hash';
```

添加字段：

```sql
ALTER TABLE knowledge_document
  ADD COLUMN file_hash VARCHAR(64) COMMENT 'file SHA-256 hash';
```

添加索引前检查：

```sql
SELECT COUNT(*)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'knowledge_document'
  AND INDEX_NAME = 'idx_kb_file_hash';
```

添加索引：

```sql
CREATE INDEX idx_kb_file_hash
  ON knowledge_document (tenant_id, kb_id, file_hash);
```

## Docker 本地环境注意事项

- `docker-compose.yml` 挂载的 `schema.sql` 只会在 `mysql_data` 首次创建时执行。
- 如果已有 volume，修改 `schema.sql` 不会改变当前库。
- 本地可用以下方式执行迁移，密码以当前 `.env` 或容器环境为准：

```powershell
docker exec ai-chat-mysql mysql -uroot -p123 ai_chat -e "ALTER TABLE knowledge_document ADD COLUMN file_hash VARCHAR(64);"
```

## 本次事故记录

- 时间：2026-06-26
- 现象：知识库文档列表接口返回 500。
- 原因：代码新增 `KnowledgeDocument.fileHash` 后，当前 MySQL 表缺少 `file_hash` 列。
- 修复：手动添加 `file_hash` 列和 `idx_kb_file_hash` 索引，并补充启动兼容迁移。
- 教训：以后任何 Entity 字段变更都必须同步准备已有库迁移方案。

### 2026-06-30 工作流执行文本列

- 现象：工作流执行接口返回 500，MySQL 报 `Invalid JSON text`。
- 原因：`workflow_execution` 和 `node_execution` 的 `input/output` 被定义为 JSON，实际保存的是用户输入和模型输出文本。
- 修复：执行 `src/main/resources/db/migrate_workflow_execution_text.sql`，将四个字段迁移为 `LONGTEXT`。
- 验证：普通中文输入和 Agent 文本输出均可写入，不再要求内容满足 JSON 语法。
