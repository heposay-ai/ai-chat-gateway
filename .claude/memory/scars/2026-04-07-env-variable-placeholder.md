---
date: 2026-04-07
category: config
severity: medium
tags: [spring-boot, dotenv, api-key]
---

## 问题描述
application.yml 中直接写 API Key 会被提交到 Git，存在安全风险。

## 根因
Spring Boot 默认不自动加载 .env 文件，需要额外配置。

## 解决方案
1. 添加 spring-dotenv 依赖
```xml
<dependency>
    <groupId>me.paulschwarz</groupId>
    <artifactId>spring-dotenv</artifactId>
    <version>4.0.0</version>
</dependency>
```

2. application.yml 使用占位符
```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
```

3. .env 文件配置实际值（已加入 .gitignore）
```
ANTHROPIC_API_KEY=sk-xxx
```

## 预防措施
- 新增敏感配置时，先在 .env.example 添加占位符
- .env 文件必须在 .gitignore 中
- PR 前检查是否有硬编码的密钥
