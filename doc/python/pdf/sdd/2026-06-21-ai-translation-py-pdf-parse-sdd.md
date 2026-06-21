# ai-translation-py PDF 解析服务 SDD

## 1. 文档信息

- 文档名称：ai-translation-py PDF 解析服务 SDD
- 对应 PRD：`src/doc/py_doc/prd/2026-06-21-ai-translation-py-pdf-parse-prd.md`
- 服务名称：ai-translation-py
- 当前功能范围：PDF 文档解析
- 文档类型：软件设计文档（SDD）
- 版本：v1.0
- 日期：2026-06-21

## 2. 技术目标

ai-translation-py 是一个独立 Python 服务，本期实现 PDF 解析能力。服务需要同时满足 API 调用和本地命令行调试，解析链路采用 MinerU + Unstructured 混合策略，并输出标准化结构化结果。

核心设计目标：

- 服务可独立启动、部署和测试。
- 后续可在同一 Python 包下扩展其他能力。
- PDF 解析过程异步化，避免长耗时请求阻塞。
- 解析结果结构稳定，便于 Java 主服务消费。
- 依赖安装和运行遵循 `uv + requirements.txt`。

## 3. 技术选型

### 3.1 Runtime

- Python：建议 3.11 或 3.12。
- 包管理与运行：uv。
- 依赖版本文件：requirements.txt。
- Web 框架：FastAPI。
- ASGI Server：Uvicorn。
- 数据校验：Pydantic。

### 3.2 PDF 解析

- MinerU：复杂 PDF 主解析器。
- Unstructured：通用文档元素抽取、补充解析与降级解析器。

### 3.3 任务执行

初版采用进程内异步任务队列：

- API 接收任务后立即返回 taskId。
- 后台线程池或 asyncio task 执行解析。
- 任务状态写入本地 SQLite 或文件状态存储。

后续如任务量增大，可演进到：

- Redis Queue。
- Celery。
- Dramatiq。
- 独立 worker 容器。

### 3.4 存储

初版默认使用 MinIO，与后端 `application-local.yml` 中的 `app.storage.minio` 配置保持一致：

- `endpoint`: `http://127.0.0.1:9000`
- `bucket`: `ai-translation`
- `access-key`: `minioadmin`
- `secret-key`: 通过配置注入，开发环境参考后端本地配置。

对象 Key 建议按任务维度组织：

- `python/pdf/{taskId}/source.pdf`：原始 PDF。
- `python/pdf/{taskId}/result.json`：结构化解析结果。
- `python/pdf/{taskId}/result.md`：Markdown 调试结果。
- `python/pdf/{taskId}/assets/images/`：图片资源。
- `python/pdf/{taskId}/assets/tables/`：表格中间产物。

本地文件系统仅作为解析过程的临时工作目录使用，例如 MinerU/Unstructured 的中间文件、解压资源、临时图片等。任务最终可消费结果必须写入 MinIO，主服务通过 MinIO key 或 ai-translation-py 结果 API 获取解析产物。

## 4. 包结构设计

建议目录：

```text
ai_translation_py/
  __init__.py
  main.py
  cli.py
  config.py
  api/
    __init__.py
    app.py
    routes_pdf.py
    schemas.py
  core/
    __init__.py
    errors.py
    logging.py
    task_status.py
  services/
    __init__.py
    pdf_task_service.py
    pdf_parse_service.py
    result_service.py
  parsers/
    __init__.py
    base.py
    mineru_parser.py
    unstructured_parser.py
    hybrid_pdf_parser.py
  models/
    __init__.py
    pdf_result.py
    task.py
  storage/
    __init__.py
    local_storage.py
    task_repository.py
  normalizers/
    __init__.py
    block_normalizer.py
    markdown_renderer.py
  tests/
    test_pdf_parse_api.py
    test_block_normalizer.py
requirements.txt
requirements-dev.txt
```

## 5. 启动与依赖管理

### 5.1 requirements.txt

`requirements.txt` 负责锁定运行时依赖版本，至少包含：

```text
fastapi
uvicorn
pydantic
python-multipart
unstructured
mineru
```

实际提交时应补充明确版本号或兼容范围。

### 5.2 常用命令

```bash
uv venv
uv pip install -r requirements.txt
uv run ai-translation-py api
uv run ai-translation-py parse-pdf ./sample.pdf --output ./output
```

### 5.3 入口设计

推荐通过 console scripts 暴露命令：

- `ai-translation-py api`
- `ai-translation-py parse-pdf`

如果项目暂不引入 `pyproject.toml`，也可以先通过模块方式运行：

```bash
uv run python -m ai_translation_py.main api
uv run python -m ai_translation_py.cli parse-pdf ./sample.pdf --output ./output
```

## 6. 模块设计

### 6.1 API 层

职责：

- 接收 PDF 上传。
- 参数校验。
- 创建任务。
- 查询任务状态。
- 返回解析结果。

主要文件：

- `api/app.py`
- `api/routes_pdf.py`
- `api/schemas.py`

### 6.2 Task Service

职责：

- 生成 taskId。
- 保存原始文件。
- 初始化任务状态。
- 提交后台解析任务。
- 更新任务进度、错误、warning。

核心方法：

```python
class PdfTaskService:
    def create_parse_task(self, file, options) -> TaskInfo: ...
    def get_task(self, task_id: str) -> TaskInfo: ...
    def get_result(self, task_id: str) -> PdfParseResult: ...
```

### 6.3 Hybrid Parser

职责：

- 调度 MinerU 与 Unstructured。
- 执行主解析、补充解析、降级解析。
- 合并和标准化结果。

核心流程：

```text
load pdf
  -> try MinerU
  -> try Unstructured
  -> merge parser outputs
  -> normalize blocks
  -> render markdown
  -> persist result
```

### 6.4 MinerU Parser

职责：

- 调用 MinerU 对 PDF 执行复杂版面解析。
- 获取 Markdown、布局块、OCR、表格、公式、图片资源。
- 将 MinerU 原始结果转换为内部 `RawParseOutput`。

失败处理：

- 捕获异常。
- 记录 warning。
- 不直接终止任务，交给 Hybrid Parser 决策。

### 6.5 Unstructured Parser

职责：

- 调用 Unstructured 抽取文档元素。
- 将元素映射为统一 block 类型。
- 在 MinerU 失败时作为降级解析器。

失败处理：

- 捕获异常。
- 记录 warning。
- 当 MinerU 也失败时，由 Hybrid Parser 抛出整体解析失败。

### 6.6 Normalizer

职责：

- 对解析块排序。
- 去重。
- 统一 block type。
- 统一 bbox 格式。
- 生成 blockId。
- 修正空文本块。

排序规则：

1. pageNo 升序。
2. bbox 的 y 坐标升序。
3. bbox 的 x 坐标升序。
4. parser 原始 orderNo 兜底。

## 7. 数据模型

### 7.1 TaskInfo

```python
class TaskInfo(BaseModel):
    task_id: str
    file_name: str
    status: str
    progress: int
    current_step: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    warnings: list[str] = []
    created_at: datetime
    updated_at: datetime
```

### 7.2 PdfBlock

```python
class PdfBlock(BaseModel):
    block_id: str
    page_no: int
    order_no: int
    type: str
    text: str | None = None
    markdown: str | None = None
    bbox: list[float] | None = None
    confidence: float | None = None
    source_parser: str
    metadata: dict = {}
```

### 7.3 PdfParseResult

```python
class PdfParseResult(BaseModel):
    task_id: str
    file_name: str
    page_count: int | None = None
    parser_strategy: str
    status: str
    warnings: list[str] = []
    blocks: list[PdfBlock]
    markdown: str | None = None
    created_at: datetime
    completed_at: datetime | None = None
```

## 8. API 设计

### 8.1 创建任务

`POST /api/v1/pdf/parse-tasks`

请求：

- `multipart/form-data`
- `file`: PDF 文件。
- `options`: JSON 字符串，可选。

处理：

1. 校验文件后缀和 MIME。
2. 校验文件大小。
3. 保存原始 PDF。
4. 创建任务状态。
5. 提交后台解析。
6. 返回 taskId。

### 8.2 查询任务

`GET /api/v1/pdf/parse-tasks/{taskId}`

处理：

1. 从 TaskRepository 读取任务。
2. 不存在返回 404。
3. 存在则返回当前状态。

### 8.3 获取 JSON 结果

`GET /api/v1/pdf/parse-tasks/{taskId}/result`

处理：

1. 校验任务存在。
2. 如果未成功，返回 `PDF_RESULT_NOT_READY`。
3. 读取 result.json。
4. 返回结构化结果。

### 8.4 获取 Markdown 结果

`GET /api/v1/pdf/parse-tasks/{taskId}/result.md`

处理：

1. 校验任务存在。
2. 如果未成功，返回 `PDF_RESULT_NOT_READY`。
3. 读取 result.md。
4. 返回 `text/markdown`。

## 9. 状态流转

```text
PENDING
  -> RUNNING
  -> SUCCEEDED
  -> FAILED

PENDING
  -> CANCELED

RUNNING
  -> CANCELED
```

状态更新节点：

- `PENDING`：文件保存完成。
- `RUNNING`：后台解析开始。
- `SUCCEEDED`：result.json 和 result.md 均写入成功。
- `FAILED`：两个解析器均失败，或结果写入失败。

## 10. 混合解析合并策略

### 10.1 主数据来源

默认以 MinerU 为主结果来源。

原因：

- PDF 复杂版面、扫描件、表格、公式、图片处理能力更符合本期目标。

### 10.2 Unstructured 补充

Unstructured 结果用于：

- 补充 MinerU 漏掉的纯文本块。
- 辅助识别标题、列表等通用元素。
- MinerU 失败时兜底输出可用文本。

### 10.3 去重规则

两个块满足以下条件时认为重复：

- pageNo 相同。
- 文本归一化后相似度高。
- bbox 高度重叠，或 orderNo 接近。

去重优先级：

1. 保留 MinerU 的结构化块。
2. 表格、公式、图片优先保留结构更完整的一方。
3. 文本块保留内容更长且置信度更高的一方。

## 11. 文件存储设计

任务目录结构：

```text
data/
  uploads/
    {taskId}/source.pdf
  results/
    {taskId}/result.json
    {taskId}/result.md
  assets/
    {taskId}/images/
    {taskId}/tables/
  tasks/
    {taskId}.json
```

写入要求：

- result 文件先写临时文件，再原子替换。
- 任务状态更新应避免半写入。
- 解析失败也要保留错误信息，便于排查。

## 12. 错误与异常设计

统一异常：

```python
class AiTranslationPyError(Exception):
    error_code: str
    message: str
```

错误码：

- `PDF_INVALID_TYPE`
- `PDF_FILE_TOO_LARGE`
- `PDF_PARSE_FAILED`
- `PDF_MINERU_FAILED`
- `PDF_UNSTRUCTURED_FAILED`
- `PDF_RESULT_NOT_READY`
- `PDF_TASK_NOT_FOUND`

API 错误响应：

```json
{
  "errorCode": "PDF_RESULT_NOT_READY",
  "message": "PDF parse result is not ready"
}
```

## 13. 日志设计

日志字段：

- taskId。
- fileName。
- parser。
- status。
- step。
- elapsedMs。
- errorCode。
- exception。

关键日志点：

- 创建任务。
- 解析开始。
- MinerU 开始和结束。
- Unstructured 开始和结束。
- 合并开始和结束。
- 结果写入。
- 任务失败。

## 14. 配置设计

配置项建议：

```text
AI_TRANSLATION_PY_HOST=0.0.0.0
AI_TRANSLATION_PY_PORT=8010
AI_TRANSLATION_PY_DATA_DIR=./data
AI_TRANSLATION_PY_MAX_FILE_MB=200
AI_TRANSLATION_PY_WORKERS=2
AI_TRANSLATION_PY_ENABLE_MINERU=true
AI_TRANSLATION_PY_ENABLE_UNSTRUCTURED=true
AI_TRANSLATION_PY_OCR_LANG=ch,en
AI_TRANSLATION_PY_RESULT_TTL_HOURS=72
```

## 15. 测试设计

### 15.1 单元测试

- block normalizer 排序测试。
- block type 映射测试。
- Markdown renderer 测试。
- 错误码映射测试。

### 15.2 集成测试

- 上传 PDF 创建任务。
- 查询任务状态。
- 获取 JSON 结果。
- 获取 Markdown 结果。
- MinerU 失败时 Unstructured 降级。
- Unstructured 失败时 MinerU 结果仍成功。

### 15.3 样例文件

建议准备以下 PDF：

- 纯文本 PDF。
- 扫描件 PDF。
- 含表格 PDF。
- 含图片 PDF。
- 多栏排版 PDF。
- 页眉页脚明显的 PDF。

## 16. 部署设计

初版部署方式：

```bash
uv venv
uv pip install -r requirements.txt
uv run ai-translation-py api
```

生产建议：

```bash
uv run uvicorn ai_translation_py.api.app:app --host 0.0.0.0 --port 8010
```

后续可容器化：

- 镜像内安装 uv。
- 使用 requirements.txt 安装依赖。
- 挂载 data 目录。
- 暴露 8010 端口。

## 17. 与主服务集成

Java/Spring Boot 主服务调用顺序：

1. 上传 PDF 到 ai-translation-py 创建解析任务。
2. 轮询 `/api/v1/pdf/parse-tasks/{taskId}`。
3. 任务成功后拉取 `/result`。
4. 将 blocks 转换为翻译段落。
5. 按 `pageNo + orderNo` 保持原文顺序。

建议主服务只依赖 ai-translation-py 的标准 JSON 输出，不依赖 MinerU 或 Unstructured 原始结果。

## 18. 演进路线

### Phase 1

- 完成 FastAPI 服务。
- 完成任务创建、状态查询、结果获取。
- 接入 MinerU 和 Unstructured。
- 输出 JSON 和 Markdown。

### Phase 2

- 增加 SQLite 或 Redis 状态存储。
- 增加任务取消。
- 增加结果 TTL 清理。
- 增加更多样例 PDF 回归测试。

### Phase 3

- 接入对象存储。
- 拆分 worker。
- 支持更多文档能力 PRD 中定义的新功能。
