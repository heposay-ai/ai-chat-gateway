# AI Chat Gateway

一个企业级 AI 应用开发平台，支持多模型统一网关、RAG 知识库问答、Agent 智能体和工作流编排。

## 功能特性

### W1: AI聊天网关
- 多模型统一接口（OpenAI / 通义千问 / DeepSeek / Claude）
- SSE 流式响应，打字机效果
- API Key 管理与调用频率限制
- 多轮对话上下文管理

### W2: 多模型统一平台
- Token 用量统计与计费
- 配额管理与多租户支持
- Prompt 模板引擎
- Vue3 管理后台

### W3: RAG知识库引擎
- 向量数据库 PgVector
- 多格式文档解析（PDF/Word/Excel/TXT/Markdown）
- 文本智能切片
- 混合检索（向量 + BM25）+ Rerank 重排序

### W4: 企业知识库问答系统
- 多知识库管理
- 聊天问答前端（Markdown 渲染 + 引用来源）
- 多轮对话与历史管理
- 权限控制与分享链接
- RAG 调试面板

### W5: AI Agent工具平台
- Function Calling + LangChain4j AiServices
- 8 种内置工具：
  - `web_search` - 联网搜索（Serper API）
  - `url_reader` - 网页内容读取
  - `knowledge_base` - 知识库查询
  - `current_time` - 当前时间
  - `code_interpreter` - 代码/数学表达式计算
  - `llm_summarize` - AI 总结/分析/改写
  - `file_writer` - 文件写入
  - `kb_writer` - 知识库写入
- Agent 管理 + 预设模板
- 流式对话 + 工具调用可视化
- 线性工作流编排引擎

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端 | Spring Boot 3.x, JPA, LangChain4j |
| 前端 | Vue 3, Element Plus, Vite |
| 数据库 | MySQL 8, Redis, PostgreSQL + pgvector |
| AI 模型 | OpenAI, 通义千问, DeepSeek, Claude |
| 向量化 | DashScope text-embedding-v3 |
| 搜索 | Serper API |

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 6+
- PostgreSQL 15+ with pgvector 扩展

### 1. 克隆项目

```bash
git clone https://github.com/heposay-ai/ai-chat-gateway.git
cd ai-chat-gateway
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 填入实际的 API Key 和数据库密码
```

### 3. 启动数据库（Docker）

```bash
docker-compose up -d
```

### 4. 启动后端

```bash
mvn spring-boot:run
```

### 5. 启动前端

```bash
cd admin-ui
npm install
npm run dev
```

### 6. 访问

- 前端: http://localhost:5173
- 后端 API: http://localhost:8080

## 项目结构

```
ai-chat-gateway/
├── src/main/java/com/example/aichat/
│   ├── config/          # 配置类
│   ├── controller/      # 基础 API 控制器
│   ├── service/         # 聊天服务、模型工厂
│   ├── rag/             # RAG 知识库模块
│   │   ├── controller/  # 知识库 API
│   │   ├── model/       # 实体类
│   │   ├── repository/  # JPA Repository
│   │   └── service/     # RAG 服务（解析、切片、向量化、检索）
│   └── agent/           # Agent 模块
│       ├── controller/  # Agent/Workflow API
│       ├── model/       # Agent 实体类
│       ├── service/     # Agent 服务、工具注册中心
│       └── tool/        # 8 个工具实现
├── admin-ui/            # Vue3 前端
│   ├── src/
│   │   ├── api/         # API 封装
│   │   ├── views/       # 页面组件
│   │   └── router/      # 路由配置
└── docker-compose.yml   # 数据库容器编排
```

## API 文档

项目包含 Postman Collection，可直接导入测试：

- `ai-chat-gateway.postman_collection.json` - W1 聊天网关
- `ai-chat-gateway-w2.postman_collection.json` - W2 平台管理
- `ai-chat-gateway-w3.postman_collection.json` - W3 RAG 引擎
- `ai-chat-gateway-w4.postman_collection.json` - W4 知识库问答
- `ai-chat-gateway-w5.postman_collection.json` - W5 Agent 工具

## 环境变量说明

| 变量 | 必填 | 说明 |
|------|------|------|
| `MYSQL_PASSWORD` | 是 | MySQL 密码 |
| `DASHSCOPE_API_KEY` | 是 | 通义千问 API Key（同时用于 Embedding） |
| `DEEPSEEK_API_KEY` | 否 | DeepSeek API Key |
| `OPENAI_API_KEY` | 否 | OpenAI API Key |
| `ANTHROPIC_API_KEY` | 否 | Claude API Key |
| `SERPER_API_KEY` | 否 | Serper 搜索 API Key |
| `RERANK_API_KEY` | 否 | Rerank 服务 API Key |
| `PGVECTOR_PASSWORD` | 是 | PostgreSQL 密码 |
| `DASHSCOPE_MEMORY_ENABLED` | 否 | 是否启用长期记忆，默认 `false` |
| `DASHSCOPE_MEMORY_LIBRARY_ID` | 否 | 阿里云百炼长期记忆库 ID，不填使用默认记忆库 |
| `DASHSCOPE_MEMORY_BASE_URL` | 否 | 长期记忆 API 地址，默认 `https://dashscope.aliyuncs.com/api/v2/apps/memory` |
| `DASHSCOPE_MEMORY_TOP_K` | 否 | 记忆召回数量，默认 `5` |
| `DASHSCOPE_MEMORY_MIN_SCORE` | 否 | 记忆召回最低相似度，默认 `0.3` |

## License

MIT
