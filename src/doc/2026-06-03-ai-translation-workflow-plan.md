# AI 翻译工作流（项目化文件翻译）实现计划

> **For agentic workers:** 建议使用 subagent-driven-development 或 executing-plans 按任务逐步实现。步骤用 `- [ ]` 复选框跟踪。

**Goal:** 把"项目"从一条配置记录升级为一套可启动的 AI 文件翻译流水线：配置 → 上传 → 解析 → 人工二次修改 → AI 初翻译（可选模型）→ AI 审校循环（DeepSeek 评分≥80）→ 人工对照审校（滚动高亮联动）→ 导出 TXT/HTML。

**Architecture:** 后端用一个有状态的 `TranslationTask`（翻译任务）承载一次翻译运行，配合 `TranslationSegment`（段落级数据）。一个编排器 `TranslationPipeline`（即本计划中的 "planner"）按状态机驱动各阶段；长耗时阶段异步执行，前端轮询状态。模型选择通过 `ChatModelRouter` 路由到 DashScope/DeepSeek。文件走 `FileStorageService` 抽象（MinIO 实现 + 本地实现兜底）。

**Tech Stack:** Spring Boot 3 / SpringAI 1.1（OpenAI 兼容 + DeepSeek）/ MyBatis-Plus / PGVector / MinIO / Apache POI（docx）/ Jsoup（html）/ Vue3 + Ant Design Vue。

---

## 0. 关键决策与待确认项（Assumptions）

实现前默认以下决策，若有异议请先指出：

1. **术语更正**：领域概念 `User` 统一更名为 `Customer`（客户）。`/api/users` → `/api/customers`，所有 `userId` 字段 → `customerId`。
2. **术语库 / 历史记录可见性**：不再依赖"左侧当前用户"。两者展示**全部客户**的数据，列表带客户名列，并支持**按客户名搜索**（术语库另支持按术语搜索）。移除侧边栏"当前用户"选择器。
3. **翻译（简单翻译）**：保持现状，不关联客户，仅选风格。
4. **输入文件格式（初版）**：TXT、DOCX、HTML。**导出格式（初版）**：TXT、HTML。
5. **可选模型（手动选择）**：`qwen-plus`、`qwen-max`、`qwen-turbo`（DashScope，OpenAI 兼容）、`deepseek-chat`（DeepSeek）。**审校默认模型**：`deepseek-chat`。
6. **审校循环（已定：逐段评分）**：审校模型对**每个段落**给 0–100 分及修改建议。任一段 `< 80` 即标记该段，带建议重跑该段初翻译，再审校；最多 `3` 轮，超出则带最后结果进入人工审校。任务级 `review_score` 取所有段最低分（用于展示整体质量）。
7. **段落粒度**：以解析得到的"段落/块"为最小单元（`TranslationSegment`），用 `orderNo` 重组导出。
8b. **语言方向（已定：用户选择）**：Step1 配置时选择**源语种**与**目标语种**（初版枚举：`zh` 中文、`en` 英文，可扩展 `ja/ko/fr/...`）。Prompt 按所选目标语种翻译。
8. **长耗时阶段（解析/翻译/审校）**：异步执行（`@Async`），前端**轮询**任务状态与进度；SSE/WebSocket 作为后续增强。
9. **临时术语库**：任务级临时术语（`task_glossary`），仅作用于当前任务的 Prompt 注入；可选"另存为客户术语库"留作后续。
10. **OSS**：MinIO 尚未部署，先用 `FileStorageService` 抽象 + 配置项 `app.storage.type=local|minio`，本地实现用于联调，MinIO 实现写好待你补 endpoint/AK/SK。

**开放问题（已确认）：**
- A. 审校评分粒度 → **逐段评分 + 逐段建议**。
- B. 语言方向 → **配置时由用户选择源/目标语种**。
- C. 导出 HTML → **纯结构化 HTML，不还原原样式**。

---

## 1. 目标架构与数据模型

### 1.1 状态机（`TranslationTask.status`）

```
DRAFT            配置中（已创建任务，未上传/未确认）
FILE_UPLOADED    文件已上传
PARSING          解析中（异步）
PARSED           解析完成，待人工二次修改/确认
TRANSLATING      AI 初翻译中（异步）
TRANSLATED       初翻译完成
REVIEWING        AI 审校中（异步，含循环重翻）
REVIEW_DONE      审校通过(≥80) 或 达到最大轮次
MANUAL_REVIEW    人工对照审校中
COMPLETED        人工确认完成
EXPORTED         已导出
FAILED           异常终止（记录 errorMsg）
```

合法流转（关键）：`DRAFT→FILE_UPLOADED→PARSING→PARSED→TRANSLATING→TRANSLATED→(REVIEWING→REVIEW_DONE)?→MANUAL_REVIEW→COMPLETED→EXPORTED`。任意阶段异步失败 → `FAILED`。

### 1.2 MySQL DDL（新增表，追加到 `db/mysql-schema.sql`）

```sql
-- 客户表（由原 user 表更名而来；见 Task 1 迁移说明）
CREATE TABLE IF NOT EXISTS customer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL COMMENT '客户名称',
    contact VARCHAR(100) COMMENT '联系方式',
    default_role VARCHAR(50) COMMENT '默认翻译角色',
    default_style VARCHAR(50) COMMENT '默认翻译风格',
    remark VARCHAR(500) COMMENT '备注/客户要求',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

-- 翻译任务（一次项目翻译运行）
CREATE TABLE IF NOT EXISTS translation_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '所属项目',
    customer_id BIGINT COMMENT '关联客户',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '任务状态',
    requirement VARCHAR(1000) COMMENT '本次翻译的客户要求',
    description VARCHAR(1000) COMMENT '项目/任务描述',
    source_lang VARCHAR(10) DEFAULT 'zh' COMMENT '源语种',
    target_lang VARCHAR(10) DEFAULT 'en' COMMENT '目标语种',
    enable_glossary TINYINT(1) DEFAULT 1 COMMENT '是否启用术语库',
    enable_history TINYINT(1) DEFAULT 0 COMMENT '是否启用历史数据优化(RAG)',
    translate_model VARCHAR(50) COMMENT '初翻译模型',
    enable_review TINYINT(1) DEFAULT 0 COMMENT '是否启用AI审校',
    review_model VARCHAR(50) DEFAULT 'deepseek-chat' COMMENT '审校模型',
    review_score INT COMMENT '最终审校得分',
    review_round INT DEFAULT 0 COMMENT '已审校轮次',
    source_file_name VARCHAR(255) COMMENT '原始文件名',
    source_file_key VARCHAR(512) COMMENT '存储对象Key',
    source_file_type VARCHAR(20) COMMENT 'TXT/DOCX/HTML',
    error_msg VARCHAR(1000) COMMENT '失败原因',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project (project_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译任务表';

-- 段落级数据
CREATE TABLE IF NOT EXISTS translation_segment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    order_no INT NOT NULL COMMENT '段落顺序',
    block_type VARCHAR(20) DEFAULT 'paragraph' COMMENT 'paragraph/heading/list 等',
    original_text MEDIUMTEXT COMMENT '解析原文(可被人工二次修改)',
    translated_text MEDIUMTEXT COMMENT 'AI初翻译结果',
    reviewed_text MEDIUMTEXT COMMENT 'AI审校后文本',
    final_text MEDIUMTEXT COMMENT '人工确认后的最终文本',
    review_score INT COMMENT '该段审校得分(0-100)',
    review_advice VARCHAR(1000) COMMENT '该段审校建议',
    review_flag TINYINT(1) DEFAULT 0 COMMENT '是否被标记需重翻',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_order (task_id, order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译段落表';

-- 任务级临时术语
CREATE TABLE IF NOT EXISTS task_glossary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    term VARCHAR(255),
    translation VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务临时术语';
```

> 同时把现有 `glossary.user_id`、`translation_history.user_id`、`project.user_id` 语义改为 `customer_id`（字段更名，见各 Task）。

### 1.3 后端文件结构（新增/改动）

```
com.xx.aitranslation
├── controller
│   ├── CustomerController.java        (原 UserController 更名)
│   ├── ProjectController.java         (+ /{id}/start 启动任务)
│   ├── TranslationTaskController.java (新)  任务全生命周期接口
│   └── ...
├── service
│   ├── CustomerService.java           (原 UserService 更名)
│   ├── TranslationTaskService.java    (新) 任务CRUD/状态流转/段落读写
│   ├── pipeline/TranslationPipeline.java (新, "planner" 编排器)
│   ├── ai/ChatModelRouter.java        (新) 模型路由
│   ├── ai/TranslationLlmService.java  (新) 初翻译/审校 LLM 调用
│   ├── parse/DocumentParser.java      (新, 接口)
│   ├── parse/TxtDocumentParser.java   (新)
│   ├── parse/HtmlDocumentParser.java  (新)
│   ├── parse/DocxDocumentParser.java  (新)
│   ├── parse/DocumentParserFactory.java(新)
│   ├── export/DocumentExporter.java   (新, 接口) + Txt/Html 实现
│   └── storage/FileStorageService.java(新, 接口) + Minio/Local 实现
├── entity     Customer/Project/TranslationTask/TranslationSegment/TaskGlossary ...
├── mapper     对应 BaseMapper
├── dto        TaskConfigRequest/StartTranslateRequest/SegmentUpdateRequest/...
├── enums      TaskStatus/ModelCode/FileType/ExportFormat/Language
└── config     AsyncConfig/StorageConfig/ChatModelConfig
```

### 1.4 模型路由（核心代码）

新增 pom 依赖（`ai-translation-backend/pom.xml`），参考 `springai` 模块已验证可用的写法：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-autoconfigure-model-deepseek</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-deepseek</artifactId>
</dependency>
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.12</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.18.1</version>
</dependency>
```

`ModelCode` 枚举与路由：

```java
public enum ModelCode {
    QWEN_PLUS("qwen-plus", Provider.DASHSCOPE),
    QWEN_MAX("qwen-max", Provider.DASHSCOPE),
    QWEN_TURBO("qwen-turbo", Provider.DASHSCOPE),
    DEEPSEEK_CHAT("deepseek-chat", Provider.DEEPSEEK);
    enum Provider { DASHSCOPE, DEEPSEEK }
    // code/provider getter、fromCode(code)（找不到默认 QWEN_PLUS）
}
```

```java
@Component
@RequiredArgsConstructor
public class ChatModelRouter {
    private final OpenAiChatModel openAiChatModel;   // DashScope 兼容
    private final DeepSeekChatModel deepSeekChatModel;

    /** 按模型 code 返回一个已绑定该模型/参数的 ChatClient */
    public ChatClient client(String modelCode) {
        ModelCode mc = ModelCode.fromCode(modelCode);
        return switch (mc.getProvider()) {
            case DASHSCOPE -> ChatClient.builder(openAiChatModel)
                    .defaultOptions(OpenAiChatOptions.builder().model(mc.getCode()).temperature(0.3).build())
                    .build();
            case DEEPSEEK -> ChatClient.builder(deepSeekChatModel)
                    .defaultOptions(DeepSeekChatOptions.builder().model(mc.getCode()).temperature(0.2).build())
                    .build();
        };
    }
}
```

`application.yml` 追加 DeepSeek 与存储配置（含 TODO）：

```yaml
spring:
  ai:
    deepseek:
      api-key: @deepseek.api.key@
      chat:
        options:
          model: deepseek-chat
app:
  storage:
    type: local            # local | minio
    local-dir: ./upload-files
    minio:
      endpoint: http://127.0.0.1:9000   # TODO: 部署 MinIO 后补充
      access-key: TODO_minio_ak
      secret-key: TODO_minio_sk
      bucket: ai-translation
```

### 1.5 API 契约（新增）

| 方法 | 路径 | 说明 | 请求 → 响应 |
|---|---|---|---|
| POST | `/api/projects/{id}/start` | 启动项目→创建任务(DRAFT) | – → `TranslationTask` |
| POST | `/api/tasks/{id}/config` | Step1 保存配置 | `TaskConfigRequest` → `TranslationTask` |
| POST | `/api/tasks/{id}/file` | 上传文件(multipart) | file → `{fileKey,fileName,fileType}` |
| POST | `/api/tasks/{id}/glossary` | 增临时术语 | `{term,translation}` → `TaskGlossary` |
| GET | `/api/tasks/{id}/glossary` | 列临时术语 | – → `TaskGlossary[]` |
| POST | `/api/tasks/{id}/parse` | 触发解析(异步) | – → `{status}` |
| GET | `/api/tasks/{id}` | 任务详情+状态轮询 | – → `TranslationTask` |
| GET | `/api/tasks/{id}/segments` | 段落列表 | – → `TranslationSegment[]` |
| PUT | `/api/tasks/{id}/segments` | 人工二次修改原文(批量) | `SegmentUpdateRequest[]` → ok |
| POST | `/api/tasks/{id}/translate` | 触发初翻译(异步) | `StartTranslateRequest{model,enableReview,reviewModel}` → `{status}` |
| PUT | `/api/tasks/{id}/segments/{sid}/final` | 人工审校改译文 | `{finalText}` → ok |
| POST | `/api/tasks/{id}/complete` | 人工确认完成 | – → `{status}` |
| GET | `/api/tasks/{id}/export?format=txt|html` | 导出下载 | – → 文件流 |
| GET | `/api/models` | 可选模型列表 | – → `string[]` |

---

## 2. 任务拆解（按阶段）

> 每个 Task 给出涉及文件、关键代码/契约与验证方式。建议每个 Task 完成后单独提交（中文 commit）。

### Phase 0 — 术语更正与可见性修正

#### Task 1：领域更名 User → Customer
**Files:**
- 重命名 `entity/User.java`→`entity/Customer.java`（表 `customer`，增 `contact`/`remark`，去 `password_hash`）
- `mapper/UserMapper`→`CustomerMapper`；`service/UserService`→`CustomerService`；`controller/UserController`→`CustomerController`（`/api/customers`）
- `dto/UserRequest`→`CustomerRequest`（字段：name, contact, defaultRole, defaultStyle, remark）
- 改 `Glossary.userId`→`customerId`、`TranslationHistory.userId`→`customerId`、`Project.userId`→`customerId`、`TranslateRequest.userId`→`customerId`
- `db/mysql-schema.sql`：用 `customer` 表替换 `user`；`glossary/translation_history/project` 列改 `customer_id`
- i18n：`messages*.properties` 增 `customer.*` 文案

- [ ] 重命名实体与三层、改 `/api/customers` 路由
- [ ] 全仓替换 `userId`→`customerId`（注意 RagService metadata key `userId`→`customerId`）
- [ ] 更新 DDL 与 i18n
- [ ] 启动应用确认无 Bean/编译错误（用户规则：不跑 mvn，由用户验证）

#### Task 2：术语库 / 历史"看全部 + 按客户名搜索"
**Files:**
- `GlossaryService.queryByTerm` → `query(customerName, term)`：联表客户名（或先查 customer 再 in 查询）；返回带 `customerName` 字段（用 VO `GlossaryVO`）
- `HistoryService.listByUser`→`list(customerName)`：同样支持按客户名模糊搜索，返回带 `customerName`
- `GlossaryController`/`HistoryController` 入参改 `customerName`
- 新增 VO：`GlossaryVO`(含 customerName)、`TranslationHistoryVO`(含 customerName)

- [ ] Service 改为可按客户名过滤、返回客户名
- [ ] Controller 入参调整
- [ ] 验证：不传条件返回全部；传客户名能搜索

#### Task 3：前端去除"当前用户"，客户管理 + 全量术语/历史
**Files:**
- `store.js`：移除 `currentUserId`/`currentUser`，保留 `roles/styles/labels`；新增 `customers` 列表与 `loadCustomers()`
- `App.vue`：移除侧边栏用户选择器；菜单 `用户管理`→`客户管理`
- `components/UsersPanel.vue`→`CustomersPanel.vue`：CRUD 客户（name/contact/默认角色风格/备注）
- `GlossaryPanel.vue`：去掉"需选当前用户"，列表显示客户名列 + 客户名搜索框 + 新增时下拉选客户
- `HistoryPanel.vue`：显示客户名列 + 客户名搜索
- `api.js`：`getUsers`→`getCustomers` 等；`getGlossary(customerName,term)`、`getHistory(customerName)`

- [ ] store/App/菜单调整
- [ ] CustomersPanel CRUD
- [ ] 术语库/历史改为全量+客户名搜索
- [ ] lint 通过

---

### Phase 1 — 基础设施：任务模型 / 存储 / 模型路由

#### Task 4：实体、枚举、Mapper、状态服务
**Files:**
- `enums/TaskStatus.java`、`enums/ModelCode.java`、`enums/FileType.java`、`enums/ExportFormat.java`
- `entity/TranslationTask.java`、`entity/TranslationSegment.java`、`entity/TaskGlossary.java`
- 对应 `mapper/*`
- `service/TranslationTaskService.java`：`createFromProject(projectId)`、`get(id)`、`updateStatus(id,from,to)`（校验合法流转）、`saveConfig`、段落读写

关键：状态流转校验（防止并发越级）：

```java
public TranslationTask transit(Long id, TaskStatus expected, TaskStatus next) {
    TranslationTask t = mapper.selectById(id);
    if (t == null) throw new BizException("task.not.found");
    if (expected != null && t.getStatus() != expected)
        throw new BizException("task.status.illegal");
    t.setStatus(next);
    mapper.updateById(t);
    return t;
}
```

- [ ] 枚举 + 实体 + mapper
- [ ] TranslationTaskService 基础方法与状态机
- [ ] DDL 追加上述新表

#### Task 5：文件存储抽象（MinIO + 本地）
**Files:**
- `service/storage/FileStorageService.java`（接口：`String upload(InputStream, name, contentType)`、`InputStream download(key)`、`String filename(key)`）
- `service/storage/LocalFileStorageService.java`（`@ConditionalOnProperty app.storage.type=local`）
- `service/storage/MinioFileStorageService.java`（`@ConditionalOnProperty app.storage.type=minio`，启动时 `makeBucket` 幂等）
- `config/StorageConfig.java`（MinioClient Bean）

- [ ] 接口 + 两实现 + 条件装配
- [ ] 配置项与 TODO 注释
- [ ] 验证：local 模式上传到 `./upload-files`、可下载

#### Task 6：模型路由与可选模型接口
**Files:**
- pom 增 deepseek 依赖；`application.yml` 增 deepseek 配置
- `service/ai/ChatModelRouter.java`（见 1.4）
- `controller/ModelController.java`：`GET /api/models` 返回 `ModelCode.codes()`

- [ ] 依赖 + 配置
- [ ] ChatModelRouter
- [ ] /api/models

---

### Phase 2 — Step1 配置 + 上传 + 临时术语

#### Task 7：启动项目 → 创建任务 + 保存配置
**Files:**
- `ProjectController`：`POST /api/projects/{id}/start` → `TranslationTaskService.createFromProject`（带出 project 的 customerId/role/style/enableGlossary 作默认）
- `dto/TaskConfigRequest.java`（requirement, description, **sourceLang, targetLang**, enableGlossary, enableHistory, translateModel, enableReview, reviewModel）
- `enums/Language.java`（zh/en，含 `code`、`label`，`GET /api/languages` 返回列表）
- `TranslationTaskController`：`POST /api/tasks/{id}/config`

- [ ] start 创建 DRAFT 任务
- [ ] config 保存配置

#### Task 8：文件上传 + 临时术语
**Files:**
- `TranslationTaskController`：`POST /api/tasks/{id}/file`（`MultipartFile`，校验后缀→`FileType`，存储得 key，写回任务，状态→FILE_UPLOADED）
- `task_glossary` 增/查接口 + service
- `application.yml`/`AsyncConfig`：`spring.servlet.multipart.max-file-size` 调大

- [ ] 上传接口（含类型校验）
- [ ] 临时术语增删查
- [ ] 验证：上传后任务记录 fileKey/type

---

### Phase 3 — Step2 解析 + 前端渲染 + 人工二次修改

#### Task 9：文档解析器
**Files:**
- `service/parse/DocumentParser.java`：`List<ParsedBlock> parse(InputStream in)`；`ParsedBlock{int order; String type; String text}`
- `TxtDocumentParser`：按空行/换行切段
- `HtmlDocumentParser`：Jsoup 取 `p,h1-h6,li` 等块级文本，记录 type
- `DocxDocumentParser`：POI `XWPFDocument` 遍历段落
- `DocumentParserFactory`：按 `FileType` 选实现

- [ ] 三个解析器 + 工厂
- [ ] 单测：给定样例 TXT/HTML/DOCX 段数与顺序正确

#### Task 10：解析触发（异步）+ 段落落库 + 查询/修改
**Files:**
- `AsyncConfig`（`@EnableAsync` + 线程池）
- `TranslationPipeline.parseAsync(taskId)`：download→parse→批量写 `translation_segment`(original_text)→状态 PARSED；异常→FAILED+errorMsg
- `TranslationTaskController`：`POST /parse`（置 PARSING 并异步触发）、`GET /segments`、`PUT /segments`（批量改 original_text）

- [ ] 异步解析编排 + 状态流转
- [ ] 段落查询/批量修改接口

#### Task 11：前端工作流入口 + Step1/Step2 UI
**Files:**
- `ProjectsPanel.vue`：操作列加"启动项目"→进入工作流（路由/视图切换）
- 新增 `components/workflow/TranslationWorkflow.vue`（`a-steps` 容器，按任务状态决定步骤；轮询 `GET /api/tasks/{id}`）
- `components/workflow/StepConfig.vue`：客户要求/描述、**源/目标语种下拉**、启用术语库、**启用历史数据优化**开关、临时术语表、`a-upload` 上传
- `components/workflow/StepParse.vue`：段落列表（可编辑 `a-textarea`/可编辑表格）、"重新解析"、"确认并下一步"
- `api.js`：补任务相关接口

- [ ] 工作流容器 + 步骤驱动 + 轮询
- [ ] Step1 配置/上传/临时术语
- [ ] Step2 段落渲染 + 人工二次修改 + 确认

---

### Phase 4 — AI 初翻译（手动选模型）

#### Task 12：初翻译 LLM 服务
**Files:**
- `service/ai/TranslationLlmService.java`：
  - `String translate(String text, role, style, glossaryRules, ragContext, modelCode)`：用 `ChatModelRouter.client(modelCode)` + 复用现有 `translation-prompt.st`
  - 复用 `GlossaryService.buildGlossaryRules(customerId,text)` + 任务级临时术语合并
  - `enableHistory` 为真时用 `RagService.buildRagContext`
- prompt 模板复用，必要时新增 `translation-prompt.st` 变量已具备

- [ ] translate 方法（模型可选、术语+RAG 注入）

#### Task 13：初翻译编排（异步）
**Files:**
- `TranslationPipeline.translateAsync(taskId, model, enableReview, reviewModel)`：
  - 校验状态 PARSED→TRANSLATING
  - 逐段翻译写 `translated_text`（顺序或并发受限线程池；建议小并发=3）
  - 写 RAG 记忆（按 customer）
  - 完 → 若 enableReview 进入 `reviewLoop`，否则状态 TRANSLATED→MANUAL_REVIEW
- `TranslationTaskController`：`POST /translate`

- [ ] 逐段初翻译 + 进度（可用已完成段数/总段数）
- [ ] 串接审校或直达人工审校

#### Task 14：前端 Step3（翻译）
**Files:**
- `components/workflow/StepTranslate.vue`：模型下拉（`GET /api/models`）、是否 AI 审校开关、审校模型下拉（默认 deepseek-chat）、"开始 AI 翻译"、进度展示（轮询）

- [ ] Step3 UI + 触发 + 进度轮询

---

### Phase 5 — AI 审校循环（DeepSeek ≥80）

#### Task 15：审校 LLM 服务（结构化输出）
**Files:**
- `service/ai/ReviewLlmService.java`：
  - `ReviewResult review(List<Segment> segs, requirement, reviewModel)`，返回结构化结果
  - 用 SpringAI `.call().entity(ReviewResult.class)` 让审校模型产出 JSON
- `dto/ReviewResult.java`（**逐段评分**）：

```java
public record ReviewResult(
    List<SegmentReview> segments) {      // 每段一条
  public record SegmentReview(int orderNo, int score, String advice) {}
}
```

- 审校 system prompt（新增 `prompts/review-prompt.st`）：要求模型对照原文/译文，**对每个段落**给 0–100 分及修改建议，强调术语一致、忠实、流畅；输出严格 JSON 以便 `.entity(ReviewResult.class)` 解析。

- [ ] ReviewResult 记录
- [ ] review-prompt.st + 结构化解析

#### Task 16：审校循环编排
**Files:**
- `TranslationPipeline.reviewLoop(taskId, reviewModel, translateModel)`：

```
round=0
while round < MAX_ROUND(3):
    status=REVIEWING
    r = reviewLlmService.review(segments, requirement, reviewModel)   // 逐段评分
    for sr in r.segments: seg.reviewScore=sr.score; seg.advice=sr.advice
    task.reviewScore = min(seg.reviewScore)   // 整体取最低段
    task.reviewRound = ++round
    flagged = segments where reviewScore < 80
    if flagged.isEmpty(): break
    // 带建议重跑被标记段的初翻译
    for seg in flagged:
        seg.reviewFlag=1
        seg.translated = translate(... + "上一轮审校建议:"+seg.advice ...)
status=REVIEW_DONE -> MANUAL_REVIEW
把最终 translated_text 复制到 reviewed_text 作为人工审校初值
```

- [ ] 循环 + 评分 + 重翻 + 上限保护
- [ ] 失败兜底（异常→FAILED）

---

### Phase 6 — 人工对照审校（滚动高亮联动）

#### Task 17：人工审校接口
**Files:**
- `TranslationTaskController`：`PUT /segments/{sid}/final`（写 `final_text`）、`POST /complete`（MANUAL_REVIEW→COMPLETED；将未改段 `final_text` 兜底为 reviewed/translated）

- [ ] 改译接口 + 完成接口

#### Task 18：前端 Step4 对照审校 + 滚动高亮
**Files:**
- `components/workflow/StepReview.vue`：左原文/右译文双栏，逐段渲染（每段带 `data-seg="orderNo"`）；译文段 `a-textarea`/可编辑可改并 `PUT final`
- 滚动/悬停联动（核心逻辑）：

```js
// 左栏滚动时，找到视口中部对应段，给右栏同 orderNo 段加高亮
function onLeftScroll() {
  const mid = leftEl.scrollTop + leftEl.clientHeight / 2
  const target = [...leftEl.querySelectorAll('[data-seg]')]
    .find(el => el.offsetTop <= mid && el.offsetTop + el.offsetHeight >= mid)
  if (target) highlight(target.dataset.seg)
}
function highlight(seg) {
  document.querySelectorAll('.seg-hl').forEach(e => e.classList.remove('seg-hl'))
  rightEl.querySelector(`[data-seg="${seg}"]`)?.classList.add('seg-hl')
  // 可选：右栏同步滚动到该段
}
// 鼠标移到左侧某段也触发 highlight(seg)
```

- [ ] 双栏渲染 + 可编辑译文 + 保存
- [ ] 滚动/悬停高亮联动
- [ ] "确认完成"→complete

---

### Phase 7 — 导出 TXT / HTML

#### Task 19：导出服务 + 接口
**Files:**
- `service/export/DocumentExporter.java`（`byte[] export(List<Segment>)` + contentType/扩展名）
- `TxtDocumentExporter`（按 order_no 用 `final_text` 拼接，段间空行）
- `HtmlDocumentExporter`（按 block_type 包 `<p>/<h2>/<li>`，输出完整 HTML 文档）
- `TranslationTaskController`：`GET /export?format=txt|html` → `ResponseEntity<byte[]>` 带 `Content-Disposition`；置状态 EXPORTED

- [ ] 两个导出器
- [ ] 导出接口（下载）

#### Task 20：前端 Step5 导出
**Files:**
- `components/workflow/StepExport.vue`：选择 TXT/HTML，触发下载（`window.open`/blob 下载）

- [ ] 导出 UI + 下载

---

## 3. 自检（Spec 覆盖核对）

- 客户而非用户 ✔ Task1 / 术语库+历史看全部并按客户名搜索 ✔ Task2-3
- 项目新增与客户绑定 ✔（现有 + Task1 改 customerId）/ 操作"启动项目" ✔ Task7/11
- Step1 配置基本信息+客户要求 ✔ / 临时术语 ✔ / 是否启用历史数据优化 ✔ / 描述 ✔ / 上传(OSS) ✔ Task5/8/11
- Step2 文档解析→前端渲染→人工二次修改→确认 ✔ Task9-11
- AI 初翻译+手动选模型 ✔ Task12-14
- AI 审校(可选)+DeepSeek 默认+**逐段评分**<80给建议→重翻→循环至≥80 ✔ Task15-16
- 人工审校(左原文/右译文/滚动高亮联动/可改) ✔ Task17-18
- 确认→导出 TXT/HTML(纯结构化) ✔ Task19-20
- 语种选择(源/目标) ✔ Task7/11

**开放问题 A/B/C 均已确认（见第 0 节）。**

---

## 4. 执行方式

计划已保存。两种推进方式：

1. **子代理逐任务（推荐）**：每个 Task 派发独立子代理实现，任务间复核，迭代快。
2. **本会话内分阶段执行**：按 Phase 批量实现 + 检查点复核。

也建议按 Phase 增量交付（Phase 0/1 先打底，再做 2→7），每阶段可独立运行验证。请告诉我：先确认开放问题 A/B/C，以及选择哪种执行方式。
```
