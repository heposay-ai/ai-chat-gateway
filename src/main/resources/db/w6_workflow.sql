-- ============================================================
-- W6: 工作流引擎数据库表（PostgreSQL）
-- ============================================================

-- 1. 工作流定义
CREATE TABLE IF NOT EXISTS workflow_definition (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    status          VARCHAR(20) DEFAULT 'DRAFT',    -- DRAFT / PUBLISHED / ARCHIVED
    is_template     BOOLEAN DEFAULT FALSE,
    category        VARCHAR(50),                    -- customer_service / content / data_analysis / research / custom
    version         INT DEFAULT 1,
    trigger_type    VARCHAR(20) DEFAULT 'MANUAL',   -- MANUAL / CRON / WEBHOOK
    trigger_config  JSONB,
    webhook_token   VARCHAR(64) UNIQUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_wf_def_tenant ON workflow_definition(tenant_id);
CREATE INDEX idx_wf_def_template ON workflow_definition(is_template);
CREATE INDEX idx_wf_def_webhook ON workflow_definition(webhook_token);

-- 2. 工作流节点
CREATE TABLE IF NOT EXISTS workflow_node (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    node_key        VARCHAR(50) NOT NULL,
    node_type       VARCHAR(20) NOT NULL,           -- START/END/AGENT/TOOL/CONDITION/HTTP/KNOWLEDGE/CODE/PARALLEL/HUMAN_REVIEW
    label           VARCHAR(100),
    config          JSONB DEFAULT '{}',
    position_x      DOUBLE PRECISION DEFAULT 0,
    position_y      DOUBLE PRECISION DEFAULT 0,
    UNIQUE(workflow_id, node_key)
);

CREATE INDEX idx_wf_node_workflow ON workflow_node(workflow_id);

-- 3. 工作流边（连线）
CREATE TABLE IF NOT EXISTS workflow_edge (
    id                   BIGSERIAL PRIMARY KEY,
    workflow_id          BIGINT NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    source_node_key      VARCHAR(50) NOT NULL,
    target_node_key      VARCHAR(50) NOT NULL,
    condition_expression VARCHAR(500),              -- SpEL 条件表达式
    label                VARCHAR(100)
);

CREATE INDEX idx_wf_edge_workflow ON workflow_edge(workflow_id);

-- 4. 工作流执行记录
CREATE TABLE IF NOT EXISTS workflow_execution (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT NOT NULL REFERENCES workflow_definition(id),
    tenant_id       BIGINT NOT NULL,
    status          VARCHAR(20) DEFAULT 'RUNNING',  -- RUNNING / COMPLETED / FAILED / PAUSED / CANCELLED
    input           TEXT,
    output          TEXT,
    trigger_type    VARCHAR(20),
    started_at      TIMESTAMP DEFAULT NOW(),
    finished_at     TIMESTAMP,
    error_message   TEXT
);

CREATE INDEX idx_wf_exec_workflow ON workflow_execution(workflow_id);
CREATE INDEX idx_wf_exec_tenant ON workflow_execution(tenant_id);

-- 5. 节点执行记录
CREATE TABLE IF NOT EXISTS node_execution (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT NOT NULL REFERENCES workflow_execution(id) ON DELETE CASCADE,
    node_key        VARCHAR(50) NOT NULL,
    node_type       VARCHAR(20) NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',  -- PENDING / RUNNING / COMPLETED / FAILED / SKIPPED
    input           TEXT,
    output          TEXT,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    duration_ms     BIGINT,
    error_message   TEXT
);

CREATE INDEX idx_node_exec_execution ON node_execution(execution_id);

-- 6. 人工审批记录
CREATE TABLE IF NOT EXISTS workflow_approval (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT NOT NULL REFERENCES workflow_execution(id),
    node_key        VARCHAR(50) NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    assignee        VARCHAR(100),
    comment         TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    handled_at      TIMESTAMP
);

CREATE INDEX idx_wf_approval_execution ON workflow_approval(execution_id);
