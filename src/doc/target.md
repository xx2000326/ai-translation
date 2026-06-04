# AI 翻译助手（SpringAI + RAG + 术语库）

## 项目定位

AI 翻译助手是一个基于大模型能力的智能翻译系统，支持多角色翻译（专业文档 / 文学 / 广告）、多风格控制（正式 / 简洁 / 创意 / 情绪化）、术语库增强翻译一致性（MySQL）、RAG 记忆增强上下文一致性（PGVector）、翻译历史记录与回溯。

目标：构建一个可扩展的 AI Agent 翻译系统，用于展示 SpringAI 实战能力、RAG 架构设计能力和企业级后端工程能力。

## 技术架构

### 技术栈
- 后端：Spring Boot 3.x
- AI框架：SpringAI
- 数据库：MySQL 8.x（业务数据）
- 向量数据库：PostgreSQL + PGVector（RAG）
- ORM：MyBatis / JPA（二选一）
- 构建工具：Maven
- 前端（可选）：Vue3 / 简单 HTML

### 系统架构图
```
用户 
 ||
前端页面
 ||
Spring Boot API
 ||
SpringAI Agent 层
 ||
MySQL	PGVector
用户信息	翻译记忆
术语库	向量检索
翻译历史	
```


## 核心功能模块设计

### 用户模块

功能：
- 用户注册 / 登录
- 保存默认翻译角色
- 保存默认翻译风格

表结构：

```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    default_role VARCHAR(50),
    default_style VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#翻译模块（核心）

功能：

支持文本翻译（单句或段落）
角色（Role）：professional（专业文档）、literature（文学）、advertisement（广告）
风格（Style）：formal（正式）、concise（简洁）、creative（创意）、emotional（情绪化）

翻译流程：

用户输入文本
选择 Role + Style
查询 MySQL 术语库（glossary）
查询 PGVector 相似翻译（RAG）
构建 Prompt
SpringAI 调用 LLM
返回翻译结果
写入 MySQL history
写入 PGVector memory

表结构：
```sql
CREATE TABLE translation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    original_text TEXT,
    translated_text TEXT,
    role VARCHAR(50),
    style VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
#术语库模块（MySQL）

功能：

存储行业术语
翻译前增强一致性
支持分类管理

表结构：
```sql
CREATE TABLE glossary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term VARCHAR(255),
    translation VARCHAR(255),
    category VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
使用方式：

查询术语并注入 Prompt 优先遵循术语翻译规则
示例：
请优先遵循以下术语翻译规则：
term -> translation

RAG 记忆模块（PGVector）

功能：

存储历史翻译向量
检索相似语义翻译
增强一致性与上下文能力

表结构：
```sql
CREATE TABLE translation_memory (
id BIGSERIAL PRIMARY KEY,
content TEXT,
content_vector VECTOR(1536),
role VARCHAR(50),
style VARCHAR(50),
create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

RAG 流程：

用户输入文本
Embedding 向量化
PGVector 相似度检索
返回 TopK 历史翻译
拼接 Prompt
LLM 生成新翻译
SpringAI Agent 设计

核心职责：

管理 Prompt 模板
选择翻译角色策略
注入 RAG + 术语上下文
调用 LLM

Prompt 模板示例：
```
你是一个专业翻译AI。
翻译角色：{role}
翻译风格：{style}
术语规则：
{glossary}
历史参考翻译：
{rag_context}
请将以下文本翻译为目标语言：
{input_text}
```
API 设计
翻译接口

POST /api/translate

请求：

{
"userId": 1,
"text": "Hello world",
"role": "professional",
"style": "formal"
}

响应：

{
"translatedText": "你好，世界"
}
角色列表

GET /api/roles

响应：

[
"professional",
"literature",
"advertisement"
]
风格列表

GET /api/styles

响应：

[
"formal",
"concise",
"creative",
"emotional"
]
术语接口

GET /api/glossary?term=xxx
POST /api/glossary（添加术语）

历史记录

GET /api/history?userId=1

系统执行流程（核心面试点）
用户输入文本
API接收请求
查询 MySQL 术语库
查询 PGVector RAG
构建 Prompt
SpringAI 调用 LLM
返回结果
写入 MySQL history
写入 PGVector memory
项目分层结构建议
```
com.ai.translate
├── controller
├── service
│    ├── TranslationService
│    ├── GlossaryService
│    ├── RagService
├── repository
├── entity
├── config
│    ├── SpringAIConfig
│    ├── PgVectorConfig
├── agent
│    ├── TranslationAgent
├── prompt
│    ├── translation_prompt.txt
```
#初版开发计划


Phase 1（核心）

SpringBoot 初始化
MySQL 连接
翻译 API
SpringAI 调用

Phase 2

术语库
翻译历史

Phase 3

PGVector RAG

Phase 4

前端页面
可扩展方向（面试加分）
多轮上下文翻译
文档翻译（PDF / Word）
翻译质量评分（LLM judge）
用户自定义 Prompt 模板
多模型切换（OpenAI / Claude / 本地模型）
翻译记忆优化（向量压缩 + 去重）
备注

该项目核心目标不是“翻译工具”，而是一个具备 RAG + Agent + 业务控制能力的 AI 应用系统，重点体现 AI 工程能力、架构设计能力和 SpringAI 落地能力。

