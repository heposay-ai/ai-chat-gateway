# AI Chat Gateway 项目规范

## 项目概述
- 技术栈：Spring Boot + Vue 3 + PostgreSQL + PgVector
- 核心功能：AI 对话网关、Agent 工具平台、工作流引擎

## 工作原则（融合 Meta_Kim 四条铁律）

### 任务分类
| 类型 | 判断条件 | 处理方式 |
|------|----------|----------|
| Query | 纯问答，无文件改动 | 直接回答 |
| Simple | 单文件/2-3文件，逻辑清晰 | 直接执行 + 简要说明 |
| Complex | 3+文件/跨模块/需设计 | 先在 design/ 写方案再实现 |

### 复杂任务流程（八阶段简化版）
```
澄清 → 搜索现有 → 设计方案 → 分步执行 → 自测验证 → 记录经验
```

1. **澄清** - 需求模糊时追问，不猜
2. **搜索** - 先看现有代码/组件能否复用
3. **设计** - 3+文件改动时，先写 `design/{模块名}.md`
4. **执行** - 小步改动，每步可运行
5. **验证** - 给出验证命令/步骤
6. **沉淀** - 踩坑记录写入 `.claude/memory/scars/`

### 并行意识
- 独立子任务并行执行，不无意义串行
- 前后端独立部分可同时改动
- 测试数据准备和代码编写可并行

## 目录约定

```
ai-chat-gateway/
├── .claude/
│   └── memory/
│       ├── scars/          # 踩坑记录（按日期或功能命名）
│       └── patterns/       # 可复用模式
├── design/                 # 复杂功能设计文档
│   └── {模块名}.md
├── src/                    # 后端代码
└── admin-ui/               # 前端代码
```

## 技术规范

### 后端（Spring Boot）
- 包结构：`com.linhaibo.gateway.{模块}.{controller|service|repository|model|dto}`
- 统一响应：`Result<T>` 或 `PageResult<T>`
- 异常处理：使用全局异常处理器
- 日志：关键操作打印日志，敏感信息脱敏

### 前端（Vue 3）
- 组件目录：`src/views/{模块}/` 或 `src/components/`
- API 封装：`src/api/{模块}.js`
- 状态管理：Pinia（如需）

### 数据库
- 表名：小写下划线，如 `agent_tool`
- 字段：小写下划线
- 时间字段：`created_at`, `updated_at`

## 输出格式

```
1. 我将做什么（1-3 条要点）
2. 我做了什么（变更文件列表）
3. 如何验证（命令或步骤）
4. 踩坑/注意事项（如有，同步记录到 .claude/memory/scars/）
```

## 经验沉淀

### 何时记录踩坑
- 调试超过 10 分钟的问题
- 框架/库的非直觉行为
- 环境配置相关问题
- 第三方 API 的坑

### 踩坑记录格式
```yaml
# .claude/memory/scars/{日期}-{简述}.md
---
date: 2026-04-08
category: spring-ai | vue | database | env
severity: low | medium | high
---
## 问题描述
## 根因
## 解决方案
## 预防措施
```

### 何时记录模式
- 同类问题出现 2+ 次
- 发现可复用的代码结构
- 总结出最佳实践

## 环境
- 开发：dev | 测试：test | 预发：pre | 生产：prod
- 运行：`mvn spring-boot:run` 或 `./mvnw spring-boot:run`
- 构建：`mvn clean package -DskipTests`
- 前端：`cd admin-ui && npm run dev`
