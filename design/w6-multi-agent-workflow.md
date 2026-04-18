# W6: Multi-Agent 协作与高级工作流引擎

## 概述

W5 实现了单 Agent + 线性工作流（步骤顺序执行，无分支/并行）。W6 将其升级为：
1. **高级工作流引擎** — 支持条件分支、并行执行、循环、人工审批节点
2. **Multi-Agent 协作** — 多个 Agent 在同一工作流中协作，支持角色分工和消息传递
3. **工作流可视化编排** — 前端拖拽式 DAG 编辑器（替代 JSON 手写）
4. **执行监控面板** — 实时查看工作流执行状态、每个节点的输入/输出/耗时

## 学习目标

| # | 主题 | 产出 |
|---|------|------|
| 1 | DAG 工作流引擎设计 | 理解有向无环图调度、拓扑排序、并行执行 |
| 2 | 条件分支与动态路由 | 实现 if/switch 节点，根据上一步输出决定下一步 |
| 3 | Multi-Agent 通信 | Agent 之间通过共享 context 传递信息 |
| 4 | 人工审批节点 | 工作流暂停等待人工确认后继续 |
| 5 | 前端 DAG 编排器 | Vue3 + vue-flow 拖拽式编辑工作流 |
| 6 | 执行监控与回放 | 实时展示节点状态，支持历史执行回放 |

---

## 一、后端架构设计

### 1.1 核心概念模型

```
Workflow (工作流定义)
  ├── WorkflowNode (节点定义)
  │     ├── type: AGENT | TOOL | CONDITION | PARALLEL | HUMAN_REVIEW | START | END
  │     ├── config: JSON (agent_id / tool_name / condition_expr / ...)
  │     └── position: {x, y} (前端画布坐标)
  └── WorkflowEdge (连线定义)
        ├── sourceNodeId
        ├── targetNodeId
        └── conditionLabel: String (条件分支时的标签，如 "score > 80")

WorkflowExecution (工作流执行实例)
  └── NodeExecution (节点执行记录)
        ├── status: PENDING | RUNNING | SUCCESS | FAILED | SKIPPED | WAITING_APPROVAL
        ├── input: TEXT
        ├── output: TEXT
        ├── startedAt / finishedAt
        └── errorMessage
```

### 1.2 数据库表结构

```sql
-- 工作流定义（升级版，替代 agent_workflow）
CREATE TABLE workflow_definition (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    status          SMALLINT DEFAULT 1,          -- 1=草稿 2=已发布
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- 工作流节点
CREATE TABLE workflow_node (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    node_key        VARCHAR(50) NOT NULL,         -- 前端唯一标识, 如 "node_1"
    node_type       VARCHAR(30) NOT NULL,         -- START / END / AGENT / TOOL / CONDITION / PARALLEL / HUMAN_REVIEW
    label           VARCHAR(100),                 -- 显示名称
    config          JSONB DEFAULT '{}',           -- 类型相关配置
    position_x      INT DEFAULT 0,
    position_y      INT DEFAULT 0,
    UNIQUE(workflow_id, node_key)
);

-- 工作流连线
CREATE TABLE workflow_edge (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    source_node_key VARCHAR(50) NOT NULL,
    target_node_key VARCHAR(50) NOT NULL,
    condition_expr  VARCHAR(500),                 -- 条件表达式 (SpEL)
    condition_label VARCHAR(100),                 -- 前端显示标签
    sort_order      INT DEFAULT 0
);

-- 工作流执行实例
CREATE TABLE workflow_execution (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT NOT NULL REFERENCES workflow_definition(id),
    tenant_id       BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',  -- RUNNING / COMPLETED / FAILED / PAUSED
    input           TEXT,                         -- 用户输入
    output          TEXT,                         -- 最终输出
    started_at      TIMESTAMP DEFAULT NOW(),
    finished_at     TIMESTAMP,
    error_message   TEXT
);

-- 节点执行记录
CREATE TABLE node_execution (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT NOT NULL REFERENCES workflow_execution(id) ON DELETE CASCADE,
    node_key        VARCHAR(50) NOT NULL,
    node_type       VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    input           TEXT,
    output          TEXT,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    duration_ms     BIGINT,
    error_message   TEXT,
    retry_count     INT DEFAULT 0
);

-- 人工审批记录
CREATE TABLE workflow_approval (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT NOT NULL REFERENCES workflow_execution(id),
    node_key        VARCHAR(50) NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    reviewer        VARCHAR(100),
    comment         TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    decided_at      TIMESTAMP
);
```

### 1.3 节点类型详解

| 类型 | 配置示例 | 说明 |
|------|----------|------|
| `START` | `{}` | 入口节点，接收 userInput |
| `END` | `{"outputExpr": "{{lastNode_output}}"}` | 终止节点，汇总输出 |
| `AGENT` | `{"agentId": 3, "prompt": "{{prev_output}}"}` | 调用指定 Agent 对话 |
| `TOOL` | `{"toolName": "web_search", "inputTemplate": "{{userInput}}"}` | 调用单个工具 |
| `CONDITION` | `{"expr": "#{output.contains('error')}", "branches": {"true": "node_err", "false": "node_ok"}}` | SpEL 条件分支 |
| `PARALLEL` | `{"branches": ["node_a", "node_b", "node_c"]}` | 并行执行多个分支，全部完成后汇合 |
| `HUMAN_REVIEW` | `{"prompt": "请审核以下内容", "timeout": 3600}` | 暂停等待人工审批 |

### 1.4 核心包结构

```
com.example.aichat.workflow/
├── controller/
│   ├── WorkflowDefinitionController.java   -- 工作流 CRUD
│   ├── WorkflowExecutionController.java    -- 执行 & 监控
│   └── WorkflowApprovalController.java     -- 审批操作
├── model/
│   ├── entity/
│   │   ├── WorkflowDefinition.java
│   │   ├── WorkflowNode.java
│   │   ├── WorkflowEdge.java
│   │   ├── WorkflowExecution.java
│   │   ├── NodeExecution.java
│   │   └── WorkflowApproval.java
│   ├── dto/
│   │   ├── WorkflowSaveRequest.java        -- 前端提交的完整工作流 (nodes + edges)
│   │   ├── WorkflowRunRequest.java
│   │   └── ApprovalDecisionRequest.java
│   └── enums/
│       ├── NodeType.java
│       ├── ExecutionStatus.java
│       └── ApprovalStatus.java
├── repository/
│   ├── WorkflowDefinitionRepository.java
│   ├── WorkflowNodeRepository.java
│   ├── WorkflowEdgeRepository.java
│   ├── WorkflowExecutionRepository.java
│   ├── NodeExecutionRepository.java
│   └── WorkflowApprovalRepository.java
├── engine/
│   ├── WorkflowEngine.java                -- 核心调度器：拓扑排序 + 执行
│   ├── NodeExecutor.java                  -- 节点执行器接口
│   ├── NodeExecutorFactory.java           -- 根据 NodeType 创建执行器
│   ├── executor/
│   │   ├── AgentNodeExecutor.java         -- 调用 Agent
│   │   ├── ToolNodeExecutor.java          -- 调用工具
│   │   ├── ConditionNodeExecutor.java     -- 条件判断
│   │   ├── ParallelNodeExecutor.java      -- 并行执行
│   │   ├── HumanReviewNodeExecutor.java   -- 暂停等待审批
│   │   ├── StartNodeExecutor.java
│   │   └── EndNodeExecutor.java
│   └── context/
│       └── WorkflowContext.java           -- 执行上下文 (变量存储 + 节点输出)
└── service/
    ├── WorkflowDefinitionService.java     -- 定义管理
    ├── WorkflowExecutionService.java      -- 执行管理
    └── WorkflowApprovalService.java       -- 审批管理
```

---

## 二、核心实现要点

### 2.1 DAG 调度算法

```java
/**
 * WorkflowEngine 核心逻辑（伪代码）
 */
public class WorkflowEngine {

    public void execute(WorkflowExecution execution) {
        // 1. 加载工作流定义 (nodes + edges)
        List<WorkflowNode> nodes = nodeRepo.findByWorkflowId(workflowId);
        List<WorkflowEdge> edges = edgeRepo.findByWorkflowId(workflowId);

        // 2. 构建邻接表 + 入度表
        Map<String, List<String>> adjacency = buildAdjacencyList(edges);
        Map<String, Integer> inDegree = buildInDegreeMap(nodes, edges);

        // 3. 拓扑排序 + BFS 执行
        Queue<String> ready = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) ready.add(entry.getKey());
        }

        WorkflowContext ctx = new WorkflowContext(execution.getInput());

        while (!ready.isEmpty()) {
            // 收集所有可并行的节点
            List<String> batch = new ArrayList<>(ready);
            ready.clear();

            // 并行执行同一批次的节点
            List<CompletableFuture<Void>> futures = batch.stream()
                .map(nodeKey -> CompletableFuture.runAsync(() -> {
                    WorkflowNode node = findNode(nodes, nodeKey);
                    NodeExecutor executor = executorFactory.create(node.getNodeType());

                    // 执行节点，结果写入 ctx
                    executor.execute(node, ctx);
                }))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 更新入度，将新的 ready 节点加入队列
            for (String nodeKey : batch) {
                for (String next : adjacency.getOrDefault(nodeKey, List.of())) {
                    // CONDITION 节点需要检查分支条件
                    if (shouldTraverse(nodeKey, next, ctx)) {
                        int newDegree = inDegree.merge(next, -1, Integer::sum);
                        if (newDegree == 0) ready.add(next);
                    }
                }
            }
        }
    }
}
```

### 2.2 条件分支节点

```java
public class ConditionNodeExecutor implements NodeExecutor {

    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        JsonNode config = objectMapper.readTree(node.getConfig());
        String expr = config.get("expr").asText();

        // 将上下文变量注入 SpEL 求值
        StandardEvaluationContext evalCtx = new StandardEvaluationContext();
        ctx.getAllVariables().forEach(evalCtx::setVariable);

        Boolean result = spelParser.parseExpression(expr).getValue(evalCtx, Boolean.class);

        // 返回分支标识，引擎据此选择后续 edge
        return NodeResult.branch(result ? "true" : "false");
    }
}
```

### 2.3 Multi-Agent 协作模式

```
场景示例：AI 研究助手工作流

START
  │
  ▼
[Agent: 研究员]  ── "请搜索并整理关于 {{topic}} 的最新资料"
  │
  ▼
[Agent: 分析师]  ── "请分析以下资料，提取关键观点: {{researcher_output}}"
  │
  ├── CONDITION: 分析质量 > 80 分?
  │       │ YES              │ NO
  │       ▼                  ▼
  │  [Agent: 写手]     [Agent: 研究员] (补充搜索)
  │  "撰写报告"          ↑ 循环回去
  │       │
  │       ▼
  │  [HUMAN_REVIEW]  ── "请审核报告"
  │       │
  │       ▼ (APPROVED)
  │     END
```

关键设计：
- 每个 AGENT 节点绑定一个已有的 Agent（复用 W5 的 agent 表）
- Agent 之间通过 `WorkflowContext` 传递数据，key 为 `{nodeKey}_output`
- 支持为每个 Agent 节点配置独立的 prompt 模板，可引用上游输出

### 2.4 人工审批节点

```java
public class HumanReviewNodeExecutor implements NodeExecutor {

    @Override
    public NodeResult execute(WorkflowNode node, WorkflowContext ctx) {
        // 1. 创建审批记录
        WorkflowApproval approval = new WorkflowApproval();
        approval.setExecutionId(ctx.getExecutionId());
        approval.setNodeKey(node.getNodeKey());
        approval.setStatus(ApprovalStatus.PENDING);
        approvalRepo.save(approval);

        // 2. 更新执行状态为 PAUSED
        ctx.pauseAtNode(node.getNodeKey());

        // 3. 通过 SSE/WebSocket 通知前端
        notificationService.notify(ctx.getTenantId(),
            "工作流等待审批: " + node.getLabel());

        // 4. 返回 PAUSED 状态，引擎暂停
        return NodeResult.paused();
    }
}

// 审批通过后，恢复执行
public class WorkflowApprovalService {
    public void approve(Long executionId, String nodeKey, String comment) {
        // 更新审批状态
        // 恢复 WorkflowEngine 从该节点继续执行
        workflowEngine.resumeFrom(executionId, nodeKey);
    }
}
```

---

## 三、API 设计

### 3.1 工作流定义 API

```
POST   /api/workflow                    -- 创建工作流
PUT    /api/workflow/{id}               -- 更新工作流 (nodes + edges 整体保存)
GET    /api/workflow                    -- 列表
GET    /api/workflow/{id}               -- 详情 (含 nodes + edges)
DELETE /api/workflow/{id}               -- 删除
POST   /api/workflow/{id}/publish       -- 发布（草稿→已发布）
POST   /api/workflow/{id}/clone         -- 克隆
```

### 3.2 执行 API

```
POST   /api/workflow/{id}/run           -- 执行工作流 (SSE 流式返回节点状态)
GET    /api/workflow/execution          -- 执行历史列表
GET    /api/workflow/execution/{id}     -- 执行详情 (含所有 node_execution)
DELETE /api/workflow/execution/{id}     -- 删除执行记录
GET    /api/workflow/execution/{id}/stream  -- SSE 实时监控
```

### 3.3 审批 API

```
GET    /api/workflow/approval/pending   -- 待审批列表
POST   /api/workflow/approval/{id}/approve  -- 通过
POST   /api/workflow/approval/{id}/reject   -- 驳回
```

### 3.4 请求/响应示例

**保存工作流 (PUT /api/workflow/{id})**
```json
{
  "name": "AI 研究助手",
  "description": "搜索 → 分析 → 写报告 → 人工审批",
  "nodes": [
    {"nodeKey": "start", "nodeType": "START", "label": "开始", "positionX": 100, "positionY": 200, "config": {}},
    {"nodeKey": "researcher", "nodeType": "AGENT", "label": "研究员", "positionX": 300, "positionY": 200,
     "config": {"agentId": 1, "prompt": "搜索 {{userInput}} 的最新资料"}},
    {"nodeKey": "analyst", "nodeType": "AGENT", "label": "分析师", "positionX": 500, "positionY": 200,
     "config": {"agentId": 2, "prompt": "分析以下内容: {{researcher_output}}"}},
    {"nodeKey": "check", "nodeType": "CONDITION", "label": "质量检查", "positionX": 700, "positionY": 200,
     "config": {"expr": "#{analyst_output.length() > 500}", "branches": {"true": "writer", "false": "researcher"}}},
    {"nodeKey": "writer", "nodeType": "AGENT", "label": "写手", "positionX": 900, "positionY": 100,
     "config": {"agentId": 3, "prompt": "根据分析结果撰写报告: {{analyst_output}}"}},
    {"nodeKey": "review", "nodeType": "HUMAN_REVIEW", "label": "人工审核", "positionX": 1100, "positionY": 100,
     "config": {"prompt": "请审核以下报告", "timeout": 7200}},
    {"nodeKey": "end", "nodeType": "END", "label": "结束", "positionX": 1300, "positionY": 200,
     "config": {"outputExpr": "{{writer_output}}"}}
  ],
  "edges": [
    {"sourceNodeKey": "start", "targetNodeKey": "researcher"},
    {"sourceNodeKey": "researcher", "targetNodeKey": "analyst"},
    {"sourceNodeKey": "analyst", "targetNodeKey": "check"},
    {"sourceNodeKey": "check", "targetNodeKey": "writer", "conditionLabel": "通过"},
    {"sourceNodeKey": "check", "targetNodeKey": "researcher", "conditionLabel": "不足，补充"},
    {"sourceNodeKey": "writer", "targetNodeKey": "review"},
    {"sourceNodeKey": "review", "targetNodeKey": "end"}
  ]
}
```

**SSE 执行事件流 (POST /api/workflow/{id}/run)**
```
event: execution_start
data: {"executionId": 42, "workflowName": "AI 研究助手"}

event: node_start
data: {"nodeKey": "researcher", "nodeType": "AGENT", "label": "研究员"}

event: node_token
data: {"nodeKey": "researcher", "content": "根据搜索结果..."}

event: node_end
data: {"nodeKey": "researcher", "status": "SUCCESS", "durationMs": 3200, "outputPreview": "找到 15 篇相关..."}

event: node_start
data: {"nodeKey": "analyst", "nodeType": "AGENT", "label": "分析师"}

event: node_end
data: {"nodeKey": "analyst", "status": "SUCCESS", "durationMs": 5100}

event: node_start
data: {"nodeKey": "check", "nodeType": "CONDITION", "label": "质量检查"}

event: node_end
data: {"nodeKey": "check", "status": "SUCCESS", "branch": "true"}

event: node_start
data: {"nodeKey": "writer", "nodeType": "AGENT", "label": "写手"}

event: node_end
data: {"nodeKey": "writer", "status": "SUCCESS", "durationMs": 8300}

event: node_start
data: {"nodeKey": "review", "nodeType": "HUMAN_REVIEW", "label": "人工审核"}

event: execution_paused
data: {"nodeKey": "review", "approvalId": 7, "message": "等待人工审批"}
```

---

## 四、前端实现

### 4.1 技术选型

| 库 | 用途 | 说明 |
|---|------|------|
| `@vue-flow/core` | DAG 编辑器 | Vue3 生态最成熟的流程图库 |
| `@vue-flow/background` | 画布背景 | 网格/点阵背景 |
| `@vue-flow/controls` | 缩放控制 | 画布缩放/居中 |
| `@vue-flow/minimap` | 小地图 | 大工作流导航 |
| Element Plus | UI 组件 | 复用已有 |

### 4.2 前端页面结构

```
admin-ui/src/views/workflow/
├── WorkflowList.vue              -- 工作流列表页
├── WorkflowEditor.vue            -- 拖拽编排页（核心）
├── WorkflowExecution.vue         -- 执行监控页
├── components/
│   ├── NodePalette.vue           -- 左侧节点面板（拖拽源）
│   ├── FlowCanvas.vue            -- 中间画布（vue-flow）
│   ├── NodeConfigPanel.vue       -- 右侧节点配置面板
│   ├── nodes/                    -- 自定义节点组件
│   │   ├── StartNode.vue
│   │   ├── EndNode.vue
│   │   ├── AgentNode.vue
│   │   ├── ToolNode.vue
│   │   ├── ConditionNode.vue
│   │   ├── ParallelNode.vue
│   │   └── HumanReviewNode.vue
│   └── ExecutionTimeline.vue     -- 执行历史时间线
├── composables/
│   ├── useWorkflowEditor.js      -- 编辑器逻辑 (add/remove/connect nodes)
│   └── useWorkflowExecution.js   -- 执行 SSE 监听
```

### 4.3 编辑器交互设计

```
┌──────────────────────────────────────────────────────────────┐
│  ← 返回  │  AI 研究助手  │           [保存] [发布] [运行]   │
├──────────┬───────────────────────────────────┬───────────────┤
│ 节点面板  │          画布 (vue-flow)          │  节点配置     │
│          │                                   │              │
│ ▣ 开始   │   [开始] → [研究员] → [分析师]    │  选中: 研究员  │
│ ▣ Agent  │              ↓                    │              │
│ ▣ 工具   │         [质量检查]                 │  Agent: ___  │
│ ▣ 条件   │          ↙    ↘                   │  模型: ___   │
│ ▣ 并行   │     [写手]  [研究员]              │  Prompt:     │
│ ▣ 审批   │       ↓                           │  ┌────────┐  │
│ ▣ 结束   │  [人工审核] → [结束]              │  │ ...    │  │
│          │                                   │  └────────┘  │
└──────────┴───────────────────────────────────┴───────────────┘
```

### 4.4 执行监控页

```
┌──────────────────────────────────────────────────────────────┐
│  执行 #42  │  AI 研究助手  │  状态: ⏸ 等待审批  │ 耗时 16.6s │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  [开始] ✅ → [研究员] ✅ 3.2s → [分析师] ✅ 5.1s            │
│                    ↓                                         │
│              [质量检查] ✅ → 通过                             │
│                    ↓                                         │
│               [写手] ✅ 8.3s                                 │
│                    ↓                                         │
│             [人工审核] ⏸ 等待中                              │
│                    ↓                                         │
│               [结束] ⬜ 待执行                               │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│  节点详情: 写手                                               │
│  输入: 根据分析结果撰写报告: 关键发现有3点...                  │
│  输出: # AI 行业分析报告\n\n## 1. 概述\n...                   │
│  耗时: 8.3s  Token: 1,240                                    │
└──────────────────────────────────────────────────────────────┘
```

---

## 五、分步实现计划

### Phase 1: 后端工作流引擎（3-4 天）

| 步骤 | 内容 | 文件 |
|------|------|------|
| 1.1 | 创建数据库表 | `src/main/resources/db/w6_workflow.sql` |
| 1.2 | Entity + Repository | `workflow/model/entity/*.java`, `workflow/repository/*.java` |
| 1.3 | DTO + Enum | `workflow/model/dto/*.java`, `workflow/model/enums/*.java` |
| 1.4 | WorkflowDefinitionService + Controller | 工作流 CRUD |
| 1.5 | WorkflowContext | 执行上下文，变量读写 |
| 1.6 | NodeExecutor 接口 + Factory | 策略模式 |
| 1.7 | StartNodeExecutor / EndNodeExecutor | 最简节点 |
| 1.8 | ToolNodeExecutor | 复用 W5 的工具 |
| 1.9 | AgentNodeExecutor | 复用 W5 的 AgentChatService |
| 1.10 | WorkflowEngine (拓扑排序 + 顺序执行) | 核心调度 |
| 1.11 | WorkflowExecutionController (SSE) | 流式返回 |

**Phase 1 验收**: 能通过 API 创建一个 START → TOOL → AGENT → END 的工作流并执行

### Phase 2: 条件分支 + 并行（2 天）

| 步骤 | 内容 |
|------|------|
| 2.1 | ConditionNodeExecutor (SpEL 表达式) |
| 2.2 | 引擎支持条件分支路由 |
| 2.3 | ParallelNodeExecutor (CompletableFuture) |
| 2.4 | 引擎支持并行执行 + 汇合 |

**Phase 2 验收**: 能执行带 if 分支和并行的工作流

### Phase 3: 人工审批（1-2 天）

| 步骤 | 内容 |
|------|------|
| 3.1 | HumanReviewNodeExecutor |
| 3.2 | WorkflowApprovalService |
| 3.3 | 引擎暂停/恢复机制 |
| 3.4 | ApprovalController |

**Phase 3 验收**: 工作流能暂停等待审批，审批后继续执行

### Phase 4: 前端 DAG 编辑器（3-4 天）

| 步骤 | 内容 |
|------|------|
| 4.1 | 安装 vue-flow，搭建 WorkflowEditor 骨架 |
| 4.2 | NodePalette（左侧面板，拖拽节点到画布） |
| 4.3 | 自定义节点组件（7 种类型各一个 .vue） |
| 4.4 | NodeConfigPanel（选中节点时右侧显示配置） |
| 4.5 | 连线交互 + 条件标签编辑 |
| 4.6 | 保存/加载（序列化 nodes + edges → API） |
| 4.7 | WorkflowList 页面 |

**Phase 4 验收**: 能在前端拖拽创建工作流并保存

### Phase 5: 执行监控前端（2 天）

| 步骤 | 内容 |
|------|------|
| 5.1 | WorkflowExecution 页面 + SSE 监听 |
| 5.2 | 节点实时状态渲染（颜色/图标变化） |
| 5.3 | 节点详情面板（input/output/耗时） |
| 5.4 | 审批操作面板（通过/驳回按钮） |
| 5.5 | 执行历史列表 + 回放 |

**Phase 5 验收**: 能实时看到工作流执行过程，能完成人工审批

### Phase 6: 联调 + 完善（1-2 天）

| 步骤 | 内容 |
|------|------|
| 6.1 | 前后端联调 |
| 6.2 | 错误处理：节点失败重试、超时处理 |
| 6.3 | 预设工作流模板（研究助手、内容生产、数据分析） |
| 6.4 | 路由配置 + 菜单集成 |

---

## 六、关键技术点

### 6.1 SpEL 表达式引擎
Spring 内置的 SpEL 用于条件分支求值，避免引入额外依赖。
- 变量引用: `#variableName`
- 字符串方法: `#output.contains('error')`
- 比较: `#score > 80`
- 逻辑: `#a > 10 and #b < 5`

### 6.2 并行执行
`CompletableFuture.allOf()` 实现并行节点同时执行，所有完成后才推进到下一批。
注意线程池配置，避免线程耗尽。

### 6.3 执行暂停/恢复
人工审批节点将执行状态持久化到数据库（`PAUSED`），审批后通过 `resumeFrom()` 从断点恢复。
- 暂停时保存完整的 `WorkflowContext` 到 `workflow_execution.context_snapshot`
- 恢复时重建上下文，从审批节点的下一个节点继续

### 6.4 与 W5 的复用关系
| W5 组件 | W6 复用方式 |
|---------|------------|
| `Agent` 实体 | AgentNode 引用 agentId |
| `AgentChatService` | AgentNodeExecutor 内部调用 |
| `ToolRegistry` + 8 个工具 | ToolNodeExecutor 内部调用 |
| `ChatModelFactory` | 通过 Agent 的 modelId 获取模型 |

---

## 七、注意事项

1. **循环检测**: 保存工作流时校验 DAG 无环（拓扑排序能遍历所有节点）
2. **并发安全**: WorkflowContext 需要线程安全（ConcurrentHashMap）
3. **超时控制**: 每个节点执行设超时（默认 60s），整个工作流设总超时（默认 300s）
4. **幂等性**: 审批操作需幂等，防止重复提交
5. **数据量**: node_execution 记录可能很多，考虑定期清理或分表
6. **安全**: SpEL 表达式需做白名单校验，防止注入危险方法调用
