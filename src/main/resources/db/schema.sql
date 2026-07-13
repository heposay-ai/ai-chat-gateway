-- 租户表
CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '租户名称',
    contact_email VARCHAR(200) COMMENT '联系邮箱',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    monthly_quota BIGINT DEFAULT 1000000 COMMENT '月配额(tokens)',
    daily_quota BIGINT DEFAULT 100000 COMMENT '日配额(tokens)',
    monthly_used BIGINT DEFAULT 0 COMMENT '月已用',
    daily_used BIGINT DEFAULT 0 COMMENT '日已用',
    quota_reset_day INT DEFAULT 1 COMMENT '月配额重置日',
    last_daily_reset DATE COMMENT '上次日配额重置时间',
    last_monthly_reset DATE COMMENT '上次月配额重置时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认租户
INSERT IGNORE INTO tenant (id, name, status) VALUES (1, 'Default Tenant', 1);

-- API Key 表
CREATE TABLE IF NOT EXISTS api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_id VARCHAR(32) NOT NULL UNIQUE COMMENT '展示用ID: ak-xxxx',
    key_secret VARCHAR(128) NOT NULL COMMENT '加密后的密钥',
    key_prefix VARCHAR(10) NOT NULL COMMENT '明文前缀，用于展示: sk-abc***',
    name VARCHAR(100) COMMENT 'Key 名称（用户自定义）',
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    rate_limit INT DEFAULT 60 COMMENT '每分钟最大请求数',
    allowed_models VARCHAR(500) COMMENT '允许使用的模型列表，逗号分隔，空=全部',
    expires_at DATETIME COMMENT '过期时间，null=永不过期',
    last_used_at DATETIME COMMENT '最后使用时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status),
    INDEX idx_prefix_status (key_prefix, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- API 调用日志表
CREATE TABLE IF NOT EXISTS api_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_id VARCHAR(32) NOT NULL,
    tenant_id BIGINT NOT NULL,
    model VARCHAR(50) NOT NULL,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    duration_ms INT COMMENT '响应耗时(ms)',
    status VARCHAR(20) COMMENT 'success/error',
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_key_time (key_id, created_at),
    INDEX idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(36) PRIMARY KEY COMMENT 'UUID',
    tenant_id BIGINT NOT NULL,
    title VARCHAR(200) COMMENT '会话标题',
    model VARCHAR(50) NOT NULL COMMENT '使用的模型',
    system_prompt TEXT COMMENT '系统提示词',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'system/user/assistant',
    content TEXT NOT NULL,
    token_count INT DEFAULT 0 COMMENT '该条消息的 token 数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W3: 知识文档表 (W4: 增加 kb_id 字段)
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    kb_id BIGINT COMMENT '所属知识库（W4新增）',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) COMMENT '存储路径',
    file_size BIGINT COMMENT '文件大小(bytes)',
    file_hash VARCHAR(64) COMMENT '文件SHA-256哈希',
    content_type VARCHAR(100) COMMENT 'MIME类型',
    char_count INT DEFAULT 0 COMMENT '提取的字符数',
    chunk_count INT DEFAULT 0 COMMENT '切片数量',
    status INT DEFAULT 0 COMMENT '0上传中 1解析完成 2已向量化 -1失败',
    error_message VARCHAR(1000) COMMENT '失败原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_kb (kb_id),
    INDEX idx_kb_file_hash (tenant_id, kb_id, file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W4: 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    name VARCHAR(200) NOT NULL COMMENT '知识库名称',
    description VARCHAR(500) COMMENT '描述',
    doc_count INT DEFAULT 0 COMMENT '文档数量',
    visibility VARCHAR(20) DEFAULT 'private' COMMENT 'private/shared',
    share_token VARCHAR(64) COMMENT '分享令牌',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    UNIQUE INDEX idx_share_token (share_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W4: RAG 对话会话表
CREATE TABLE IF NOT EXISTS rag_chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id BIGINT NOT NULL COMMENT '所属知识库',
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    title VARCHAR(200) COMMENT '会话标题（首条问题截取）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_tenant (kb_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W4: RAG 对话消息表
CREATE TABLE IF NOT EXISTS rag_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '所属会话',
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    rewritten_query VARCHAR(500) COMMENT '改写后的查询（仅 user 消息）',
    sources TEXT COMMENT '引用来源 JSON（仅 assistant 消息）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W5: Agent 定义表
CREATE TABLE IF NOT EXISTS agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    name VARCHAR(100) NOT NULL COMMENT 'Agent名称',
    description VARCHAR(500) COMMENT '描述',
    avatar VARCHAR(50) COMMENT '图标(emoji)',
    system_prompt TEXT NOT NULL COMMENT '系统提示词',
    model_id VARCHAR(50) NOT NULL DEFAULT 'deepseek-chat' COMMENT '使用的模型ID',
    tools_config JSON COMMENT '启用的工具列表 ["web_search","knowledge_base:3"]',
    is_template BIT(1) DEFAULT 0 COMMENT '1=预设模板 0=用户自建',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_template (is_template)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W5: Agent 对话会话表
CREATE TABLE IF NOT EXISTS agent_chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL COMMENT '所属Agent',
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    title VARCHAR(200) COMMENT '会话标题',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent_tenant (agent_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W5: Agent 对话消息表（含工具调用记录）
CREATE TABLE IF NOT EXISTS agent_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '所属会话',
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant/tool',
    content TEXT COMMENT '消息内容',
    tool_calls JSON COMMENT 'assistant消息中的工具调用请求',
    tool_name VARCHAR(50) COMMENT 'tool消息: 工具名称',
    tool_input TEXT COMMENT 'tool消息: 工具输入参数',
    tool_output TEXT COMMENT 'tool消息: 工具返回结果',
    tool_duration_ms INT COMMENT 'tool消息: 执行耗时',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 租户表
CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '租户名称',
    contact_email VARCHAR(200) COMMENT '联系邮箱',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    monthly_quota BIGINT DEFAULT 1000000 COMMENT '月配额',
    daily_quota BIGINT DEFAULT 100000 COMMENT '日配额',
    monthly_used BIGINT DEFAULT 0 COMMENT '月已用',
    daily_used BIGINT DEFAULT 0 COMMENT '日已用',
    quota_reset_day INT DEFAULT 1 COMMENT '月配额重置日',
    last_daily_reset DATE COMMENT '上次日重置时间',
    last_monthly_reset DATE COMMENT '上次月重置时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 提示词模板表
CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) COMMENT '描述',
    category VARCHAR(50) COMMENT '分类',
    content TEXT NOT NULL COMMENT '模板内容',
    variables JSON COMMENT '变量定义',
    version INT DEFAULT 1 COMMENT '当前版本号',
    is_public BIT(1) DEFAULT 0 COMMENT '是否公开',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 提示词模板版本表
CREATE TABLE IF NOT EXISTS prompt_template_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL COMMENT '所属模板',
    version INT NOT NULL COMMENT '版本号',
    content TEXT NOT NULL COMMENT '模板内容',
    variables JSON COMMENT '变量定义',
    change_note VARCHAR(500) COMMENT '变更说明',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 模板收藏表
CREATE TABLE IF NOT EXISTS template_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_tenant_template (tenant_id, template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W5: 工作流定义表
CREATE TABLE IF NOT EXISTS agent_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    name VARCHAR(100) NOT NULL COMMENT '工作流名称',
    description VARCHAR(500) COMMENT '描述',
    steps JSON NOT NULL COMMENT '步骤定义 [{stepNo,toolName,inputTemplate,description}]',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 代理池子系统 (Proxy Pool Subsystem)
-- ============================================================

-- 原始 IP 列表
CREATE TABLE IF NOT EXISTS proxy_ip (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip VARCHAR(255) NOT NULL COMMENT 'IP地址(支持IPv6)',
    port INT NOT NULL COMMENT '端口',
    protocol VARCHAR(10) DEFAULT 'http' COMMENT 'http/https',
    source VARCHAR(100) COMMENT '来源标识',
    region VARCHAR(50) COMMENT '地区',
    tags VARCHAR(500) COMMENT '标签(逗号分隔)',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/scanning/active/inactive',
    last_scan_at DATETIME COMMENT '最后扫描时间',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 扫描脚本配置
CREATE TABLE IF NOT EXISTS scan_script (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '脚本名称',
    description VARCHAR(500) COMMENT '描述',
    script_type VARCHAR(20) NOT NULL DEFAULT 'python' COMMENT 'python/shell',
    script_path VARCHAR(500) COMMENT '脚本文件路径',
    default_params JSON COMMENT '默认参数',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 账号池
CREATE TABLE IF NOT EXISTS proxy_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) COMMENT '账号名称/别名',
    endpoint_url VARCHAR(500) NOT NULL COMMENT 'API端点URL',
    api_key VARCHAR(256) COMMENT 'API Key',
    auth_header VARCHAR(100) COMMENT '认证头信息',
    supported_models JSON COMMENT '支持的模型列表',
    provider VARCHAR(50) COMMENT '供应商标识',
    health_status VARCHAR(20) NOT NULL DEFAULT 'unknown' COMMENT 'healthy/unhealthy/unknown',
    health_check_at DATETIME COMMENT '最后健康检查时间',
    health_message VARCHAR(500) COMMENT '健康检查详情',
    total_requests BIGINT DEFAULT 0 COMMENT '累计请求数',
    total_tokens_used BIGINT DEFAULT 0 COMMENT '累计token用量',
    total_cost DECIMAL(12,4) DEFAULT 0 COMMENT '累计费用',
    last_used_at DATETIME COMMENT '最后使用时间',
    source_ip_id BIGINT COMMENT '关联原始IP',
    weight INT DEFAULT 1 COMMENT '权重(负载均衡)',
    max_rpm INT COMMENT '最大RPM',
    extra_info JSON COMMENT '扩展信息',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_health (health_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 代理请求日志
CREATE TABLE IF NOT EXISTS proxy_request_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT COMMENT '关联账号',
    account_name VARCHAR(100) COMMENT '账号名称(冗余)',
    model VARCHAR(50) COMMENT '请求模型',
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    estimated_cost DECIMAL(10,6) DEFAULT 0 COMMENT '估算费用',
    duration_ms INT COMMENT '耗时(ms)',
    status VARCHAR(20) COMMENT 'success/error',
    error_message TEXT COMMENT '错误信息',
    client_ip VARCHAR(45) COMMENT '客户端IP',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_time (account_id, created_at),
    INDEX idx_model_time (model, created_at),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 扫描任务
CREATE TABLE IF NOT EXISTS scan_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT COMMENT '关联脚本',
    target_ips LONGTEXT COMMENT '目标IP列表',
    params JSON COMMENT '执行参数',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/completed/failed',
    result_summary JSON COMMENT '结果摘要',
    log_output LONGTEXT COMMENT '执行日志',
    started_at DATETIME COMMENT '开始时间',
    completed_at DATETIME COMMENT '完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_script (script_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- W6: 工作流引擎数据库表
-- ============================================================

-- 工作流定义
CREATE TABLE IF NOT EXISTS workflow_definition (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL COMMENT '工作流名称',
    description     VARCHAR(500) COMMENT '描述',
    status          VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    is_template     TINYINT(1) DEFAULT 0 COMMENT '是否模板',
    category        VARCHAR(50) COMMENT '分类',
    version         INT DEFAULT 1 COMMENT '版本号',
    trigger_type    VARCHAR(20) DEFAULT 'MANUAL' COMMENT 'MANUAL/CRON/WEBHOOK',
    trigger_config  JSON COMMENT '触发器配置',
    webhook_token   VARCHAR(64) UNIQUE COMMENT 'Webhook令牌',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wf_def_tenant (tenant_id),
    INDEX idx_wf_def_template (is_template)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工作流节点
CREATE TABLE IF NOT EXISTS workflow_node (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT NOT NULL COMMENT '所属工作流',
    node_key        VARCHAR(50) NOT NULL COMMENT '节点标识',
    node_type       VARCHAR(20) NOT NULL COMMENT 'START/END/AGENT/TOOL/CONDITION/HTTP/KNOWLEDGE/CODE/PARALLEL/HUMAN_REVIEW',
    label           VARCHAR(100) COMMENT '节点标签',
    config          JSON COMMENT '节点配置',
    position_x      DOUBLE DEFAULT 0 COMMENT 'X坐标',
    position_y      DOUBLE DEFAULT 0 COMMENT 'Y坐标',
    UNIQUE KEY uk_wf_node (workflow_id, node_key),
    INDEX idx_wf_node_workflow (workflow_id),
    FOREIGN KEY (workflow_id) REFERENCES workflow_definition(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工作流边（连线）
CREATE TABLE IF NOT EXISTS workflow_edge (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id          BIGINT NOT NULL COMMENT '所属工作流',
    source_node_key      VARCHAR(50) NOT NULL COMMENT '源节点',
    target_node_key      VARCHAR(50) NOT NULL COMMENT '目标节点',
    condition_expression VARCHAR(500) COMMENT 'SpEL条件表达式',
    label                VARCHAR(100) COMMENT '���线标签',
    INDEX idx_wf_edge_workflow (workflow_id),
    FOREIGN KEY (workflow_id) REFERENCES workflow_definition(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工作流执行记录
CREATE TABLE IF NOT EXISTS workflow_execution (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT NOT NULL COMMENT '所属工作流',
    tenant_id       BIGINT NOT NULL COMMENT '所属租户',
    status          VARCHAR(20) DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED/PAUSED/CANCELLED',
    input           LONGTEXT COMMENT '输入参数',
    output          LONGTEXT COMMENT '输出结果',
    trigger_type    VARCHAR(20) COMMENT '触发方式',
    started_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    finished_at     DATETIME COMMENT '完成时间',
    error_message   TEXT COMMENT '错误信息',
    INDEX idx_wf_exec_workflow (workflow_id),
    INDEX idx_wf_exec_tenant (tenant_id),
    FOREIGN KEY (workflow_id) REFERENCES workflow_definition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 节点执行记录
CREATE TABLE IF NOT EXISTS node_execution (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id    BIGINT NOT NULL COMMENT '所属执行',
    node_key        VARCHAR(50) NOT NULL COMMENT '节点标识',
    node_type       VARCHAR(20) NOT NULL COMMENT '节点类型',
    status          VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED/SKIPPED',
    input           LONGTEXT COMMENT '输入',
    output          LONGTEXT COMMENT '输出',
    started_at      DATETIME COMMENT '开始时间',
    finished_at     DATETIME COMMENT '完成时间',
    duration_ms     BIGINT COMMENT '耗时(ms)',
    error_message   TEXT COMMENT '错误信息',
    INDEX idx_node_exec_execution (execution_id),
    FOREIGN KEY (execution_id) REFERENCES workflow_execution(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 人工审批记录
CREATE TABLE IF NOT EXISTS workflow_approval (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id    BIGINT NOT NULL COMMENT '所属执行',
    node_key        VARCHAR(50) NOT NULL COMMENT '节点标识',
    status          VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    assignee        VARCHAR(100) COMMENT '审批人',
    comment         TEXT COMMENT '审批意见',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    handled_at      DATETIME COMMENT '处理时间',
    INDEX idx_wf_approval_execution (execution_id),
    FOREIGN KEY (execution_id) REFERENCES workflow_execution(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
