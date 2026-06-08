# AI Translation Assistant

基于 **Spring AI + RAG + 术语库** 构建的智能翻译助手，支持多语言文档翻译、术语一致性管理、翻译记忆召回与人工审校等完整工作流。

---

## ✨ 功能特性

- **多模型路由**：支持阿里云 DashScope（通义千问）、DeepSeek 等多模型按需切换
- **RAG 翻译记忆**：基于 PGVector 向量检索，复用历史翻译片段，保持风格一致
- **术语库管理**：项目级术语绑定，翻译时强制应用领域专属术语
- **文档格式解析**：支持 `.txt`、`.docx`、`.html` 文件上传与句子级翻译（Okapi + SRX 分句）
- **翻译流水线**：`译前准备 → AI 翻译 → AI 审校 → 人工审核 → 导出` 全链路
- **多存储支持**：本地磁盘 / MinIO 对象存储二选一
- **国际化**：后端错误信息支持中英双语（`messages.properties`）

> 📖 **完整工作流说明（HTML）**：[doc/workflow/translation-workflow.html](doc/workflow/translation-workflow.html) — 浏览器直接打开，含业务流、状态机与技术附录。  
> 📖 **AI 翻译阶段深度解析**：[doc/workflow/ai-translation-phase.html](doc/workflow/ai-translation-phase.html) — 初翻 / 审校循环 / 风格统一 / RAG / 并发编排。

---

## 🛠 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5 + Java 21 |
| AI 框架 | Spring AI 1.1.0 |
| 向量存储 | PostgreSQL + pgvector |
| 业务数据库 | MySQL 8.0 + MyBatis-Plus 3.5 |
| 文件存储 | 本地磁盘 / MinIO |
| 文档解析 | Okapi（txt/html）+ Apache POI（docx）+ SRX 分句 |
| 前端框架 | Vue 3 + Vite + Ant Design Vue |

---

## 📁 项目结构

```
ai-translation/
├── ai-translation-backend/          # Spring Boot 后端
│   └── src/main/
│       ├── java/com/xx/aitranslation/
│       │   ├── agent/               # AI Agent（翻译流水线编排）
│       │   ├── config/              # 配置类（数据源、SpringAI、存储等）
│       │   ├── controller/          # REST 接口层
│       │   ├── dto/                 # 请求/响应对象
│       │   ├── entity/              # 数据库实体
│       │   ├── enums/               # 枚举（语言、模型、任务状态等）
│       │   ├── mapper/              # MyBatis-Plus Mapper
│       │   └── service/             # 业务逻辑
│       │       ├── ai/              # LLM 调用（翻译 / 审校）
│       │       ├── export/          # 文档导出（txt / html）
│       │       ├── parse/           # 文档解析（txt / docx / html）
│       │       ├── pipeline/        # 翻译流水线
│       │       └── storage/         # 文件存储（本地 / MinIO）
│       └── resources/
│           ├── db/                  # 数据库初始化 SQL
│           ├── prompts/             # Prompt 模板
│           └── application.yml      # 主配置文件
├── ai-translation-frontend/         # Vue3 前端
│   └── src/
│       ├── components/              # 业务组件（翻译工作流、步骤配置等）
│       ├── api.js                   # 接口封装
│       ├── App.vue
│       └── store.js
└── doc/                             # 设计文档与工作流 HTML
    ├── V1/                          # V1 实现说明
    └── workflow/                    # 翻译工作流可视化文档（浏览器打开 .html）
```

---

## 🚀 快速开始

### 环境要求

- Java 21+
- Maven 3.9+
- MySQL 8.0+
- PostgreSQL 15+（需安装 pgvector 扩展）
- Node.js 18+（前端）
- MinIO（可选，默认使用本地存储）

### 1. 数据库初始化

**MySQL（业务库）：**
```sql
CREATE DATABASE ai_translation CHARACTER SET utf8mb4;
-- 执行 SQL 脚本
source ai-translation-backend/src/main/resources/db/mysql-schema.sql
```

**MySQL 升级脚本**（已有库按时间顺序执行，执行前请备份）：

| 更新时间 | 脚本 | 说明 |
|----------|------|------|
| 2026.06.05 | `ai-translation-backend/src/main/resources/db/mysql-migration-2026-06-05-segment-to-sentence.sql` | `translation_segment` 拆分为 document / paragraph / sentence 三表（会删除旧段落数据） |

```bash
# 示例（MySQL 客户端内）
source ai-translation-backend/src/main/resources/db/mysql-migration-2026-06-05-segment-to-sentence.sql
```

**PostgreSQL + pgvector（向量库）：**
```sql
CREATE DATABASE ai_translation_vector;
-- 连接到该库后执行：
CREATE EXTENSION IF NOT EXISTS vector;
-- 执行 SQL 脚本
\i ai-translation-backend/src/main/resources/db/pgvector-schema.sql
```

### 2. 配置环境变量

复制配置文件并填入实际值：
```bash
# 关键环境变量（也可直接修改 application.yml）
export DASHSCOPE_API_KEY=your-dashscope-api-key    # 阿里云 DashScope API Key
export DEEPSEEK_API_KEY=your-deepseek-api-key       # DeepSeek API Key（可选）
export MYSQL_URL=jdbc:mysql://127.0.0.1:3306/ai_translation?...
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=your-password
export PGVECTOR_URL=jdbc:postgresql://127.0.0.1:5432/ai_translation_vector
export PGVECTOR_USERNAME=postgres
export PGVECTOR_PASSWORD=your-password
```

### 3. 启动后端

```bash
cd ai-translation-backend
mvn spring-boot:run
# 或打包后运行
mvn package -DskipTests
java -jar target/ai-translation-backend-*.jar
```

后端服务默认运行在 `http://localhost:8088`

### 4. 启动前端

```bash
cd ai-translation-frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`

---

## ⚙️ 主要配置说明

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|---------|--------|------|
| AI 模型 | `DASHSCOPE_API_KEY` | — | 阿里云 DashScope API Key |
| DeepSeek | `DEEPSEEK_API_KEY` | — | DeepSeek API Key（可选） |
| MySQL | `MYSQL_URL` | localhost:3306 | 业务数据库 |
| PGVector | `PGVECTOR_URL` | localhost:5432 | 向量数据库 |
| 存储类型 | `STORAGE_TYPE` | `local` | `local` 或 `minio` |
| 本地存储目录 | `STORAGE_LOCAL_DIR` | `./upload-files` | 本地文件上传目录 |

---

## 📄 License

[Apache 2.0](LICENSE)
