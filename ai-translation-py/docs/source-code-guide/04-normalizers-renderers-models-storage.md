# 04. 模型、标准化、渲染、存储说明书

本篇解释这些文件：

```text
ai_translation_py/models/task.py
ai_translation_py/models/pdf_result.py
ai_translation_py/normalizers/block_normalizer.py
ai_translation_py/normalizers/markdown_renderer.py
ai_translation_py/storage/local_storage.py
ai_translation_py/storage/task_repository.py
ai_translation_py/core/errors.py
ai_translation_py/core/task_status.py
ai_translation_py/core/logging.py
```

这些模块不直接“解析 PDF”，但它们决定了解析结果的稳定性、JSON 格式、排序规则、任务状态和错误响应。

## 1. Pydantic 模型是什么

项目使用 Pydantic v2 作为 DTO 和 JSON 序列化工具。

Java 类比：

- `BaseModel` 类似 DTO。
- `Field(alias="taskId")` 类似 Jackson 的 `@JsonProperty("taskId")`。
- `model_validate(...)` 类似 JSON 反序列化加校验。
- `model_dump(...)` 类似序列化成 Map。

## 2. `models/task.py`

### 2.1 `TaskInfo`

```python
class TaskInfo(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    task_id: str = Field(alias="taskId")
    file_name: str = Field(alias="fileName")
    status: str
    progress: int = 0
    current_step: str | None = Field(default=None, alias="currentStep")
    error_code: str | None = Field(default=None, alias="errorCode")
    error_message: str | None = Field(default=None, alias="errorMessage")
    warnings: list[str] = Field(default_factory=list)
    created_at: datetime = Field(alias="createdAt")
    updated_at: datetime = Field(alias="updatedAt")
```

字段说明：

| Python 字段 | JSON 字段 | 含义 |
| --- | --- | --- |
| `task_id` | `taskId` | 解析任务 ID |
| `file_name` | `fileName` | 原始 PDF 文件名 |
| `status` | `status` | 任务状态 |
| `progress` | `progress` | 进度，当前代码主要用 0、5、100 |
| `current_step` | `currentStep` | 当前步骤 |
| `error_code` | `errorCode` | 失败错误码 |
| `error_message` | `errorMessage` | 失败错误消息 |
| `warnings` | `warnings` | 非致命警告 |
| `created_at` | `createdAt` | 创建时间 |
| `updated_at` | `updatedAt` | 更新时间 |

### 2.2 alias 的意义

Python 代码内部习惯 snake_case：

```python
task.task_id
task.file_name
task.current_step
```

JSON 对外输出给 Java 时习惯 camelCase：

```json
{
  "taskId": "...",
  "fileName": "...",
  "currentStep": "..."
}
```

所以模型写：

```python
task_id: str = Field(alias="taskId")
```

`populate_by_name=True` 表示创建模型时既可以用字段名，也可以用 alias。

这两种都可以：

```python
TaskInfo(taskId="pdf_xxx", fileName="a.pdf", ...)
TaskInfo(task_id="pdf_xxx", file_name="a.pdf", ...)
```

### 2.3 `ParseTaskCreatedResponse`

```python
class ParseTaskCreatedResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    task_id: str = Field(alias="taskId")
    status: str
```

创建任务接口只返回最小信息：

```json
{
  "taskId": "pdf_20260621_153000_ab12cd34",
  "status": "PENDING"
}
```

## 3. `models/pdf_result.py`

### 3.1 `PdfBlock`

```python
class PdfBlock(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    block_id: str = Field(alias="blockId")
    page_no: int = Field(alias="pageNo")
    order_no: int = Field(alias="orderNo")
    type: str
    text: str | None = None
    markdown: str | None = None
    bbox: list[float] | None = None
    confidence: float | None = None
    source_parser: str = Field(alias="sourceParser")
    metadata: dict[str, Any] = Field(default_factory=dict)
```

`PdfBlock` 是整个 Python PDF 服务最核心的数据结构。所有解析器最终都要输出它。

字段说明：

| 字段 | 含义 |
| --- | --- |
| `blockId` | 块 ID，标准化后为 `block_000001` |
| `pageNo` | PDF 页码，从 1 开始 |
| `orderNo` | 全文顺序，从 1 开始 |
| `type` | 块类型，如 `paragraph`、`table`、`figure` |
| `text` | 纯文本 |
| `markdown` | Markdown/HTML 表示，表格和图片常用 |
| `bbox` | 坐标框 `[x0, y0, x1, y1]` |
| `confidence` | 置信度，可选 |
| `sourceParser` | 来源解析器 |
| `metadata` | 扩展信息，图片 assetId 等 |

### 3.2 block type

合法类型在 `BlockNormalizer` 中定义：

```python
VALID_BLOCK_TYPES = {
    "title",
    "heading",
    "paragraph",
    "list_item",
    "table",
    "figure",
    "formula",
    "header",
    "footer",
    "unknown",
}
```

每种类型的含义：

| 类型 | 含义 | 是否进入 plainText |
| --- | --- | --- |
| `title` | 文档标题 | 是，渲染成 `# title` |
| `heading` | 章节标题 | 是，渲染成 `## heading` |
| `paragraph` | 正文段落 | 是 |
| `list_item` | 列表项 | 是，保证带 `- ` |
| `table` | 表格 | 是 |
| `figure` | 图片 | 是，占位为 `[PDF image: assetId]` |
| `formula` | 公式 | 否，当前 plainText 不翻译公式 |
| `header` | 页眉 | 是 |
| `footer` | 页脚 | 是 |
| `unknown` | 未识别类型 | 是 |

### 3.3 `PdfParseResult`

```python
class PdfParseResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    task_id: str = Field(alias="taskId")
    file_name: str = Field(alias="fileName")
    page_count: int | None = Field(default=None, alias="pageCount")
    parser_strategy: str = Field(alias="parserStrategy")
    status: str
    warnings: list[str] = Field(default_factory=list)
    blocks: list[PdfBlock] = Field(default_factory=list)
    markdown: str | None = None
    plain_text: str | None = Field(default=None, alias="plainText")
    created_at: datetime = Field(alias="createdAt")
    completed_at: datetime | None = Field(default=None, alias="completedAt")
```

这是 `/result` 接口返回的完整结果。

重点字段：

- `blocks`：结构化主结果。
- `plainText`：Java 后端继续分块落库的输入。
- `markdown`：调试和人工查看。

## 4. `normalizers/block_normalizer.py`

### 4.1 职责

`BlockNormalizer` 把不同解析器输出的 block 整理成统一顺序和统一编号。

核心方法：

```python
def normalize(self, blocks: list[PdfBlock]) -> list[PdfBlock]:
    cleaned = [self._clean_block(block) for block in blocks]
    cleaned = [block for block in cleaned if self._is_usable(block)]
    deduped = self._dedupe(cleaned)
    sorted_blocks = sorted(deduped, key=self._sort_key)
    return [
        block.model_copy(update={"block_id": f"block_{index:06d}", "order_no": index})
        for index, block in enumerate(sorted_blocks, start=1)
    ]
```

四步：

```text
清洗字段 -> 过滤不可用块 -> 去重 -> 排序并重编号
```

### 4.2 `_clean_block`

```python
def _clean_block(block: PdfBlock) -> PdfBlock:
    block_type = block.type if block.type in VALID_BLOCK_TYPES else "unknown"
    text = block.text.strip() if isinstance(block.text, str) else None
    markdown = block.markdown.strip() if isinstance(block.markdown, str) else None
    bbox = _normalize_bbox(block.bbox)
    page_no = max(block.page_no, 1)
    return block.model_copy(
        update={
            "type": block_type,
            "text": text or None,
            "markdown": markdown or None,
            "bbox": bbox,
            "page_no": page_no,
        }
    )
```

做的事：

- 非法类型改成 `unknown`。
- 去掉文本首尾空白。
- 空字符串转成 `None`。
- bbox 统一成 float list。
- 页码最小是 1。

为什么用 `model_copy`：

- 不直接修改原对象。
- 返回一个更新后的新模型。

### 4.3 `_is_usable`

```python
STRUCTURED_TYPES = {"table", "figure", "formula"}

def _is_usable(block: PdfBlock) -> bool:
    if block.type in STRUCTURED_TYPES:
        return True
    return bool(block.text or block.markdown)
```

普通文本块必须有内容。

结构化块即使没有文本也保留，因为表格、图片、公式可能通过 markdown 或 metadata 表达。

### 4.4 `_dedupe`

```python
def _dedupe(self, blocks: list[PdfBlock]) -> list[PdfBlock]:
    by_key: dict[tuple[int, str], PdfBlock] = {}
    passthrough: list[PdfBlock] = []
    for block in blocks:
        normalized_text = _normalize_text(block.text or block.markdown or "")
        if not normalized_text:
            passthrough.append(block)
            continue
        key = (block.page_no, normalized_text)
        existing = by_key.get(key)
        if existing is None or self._is_better(block, existing):
            by_key[key] = block
    return list(by_key.values()) + passthrough
```

去重 key 是：

```text
(page_no, normalized_text)
```

也就是说，同一页、归一化后文本相同，认为重复。

`_normalize_text`：

```python
def _normalize_text(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()
```

它会：

- 去掉所有空白。
- 转小写。

示例：

```text
"Hello World" -> "helloworld"
"hello   world" -> "helloworld"
```

### 4.5 `_is_better`

```python
def _is_better(candidate: PdfBlock, existing: PdfBlock) -> bool:
    candidate_priority = _block_priority(candidate)
    existing_priority = _block_priority(existing)
    if candidate_priority != existing_priority:
        return candidate_priority < existing_priority
    candidate_len = len(candidate.text or candidate.markdown or "")
    existing_len = len(existing.text or existing.markdown or "")
    if candidate_len != existing_len:
        return candidate_len > existing_len
    return (candidate.confidence or 0.0) > (existing.confidence or 0.0)
```

判断重复块时保留哪个。

优先级：

1. 结构化块优先。
2. 解析器优先级更高的优先。
3. 文本更长的优先。
4. 置信度更高的优先。

解析器优先级：

```python
PARSER_PRIORITY = {"mineru": 0, "unstructured": 1, "pypdf": 2}
```

数字越小优先级越高。

### 4.6 `_sort_key`

```python
def _sort_key(block: PdfBlock) -> tuple[int, float, float, int]:
    x0, y0 = (block.bbox[0], block.bbox[1]) if block.bbox else (0.0, 0.0)
    return (block.page_no, y0, x0, block.order_no)
```

排序规则：

1. 页码。
2. y 坐标，从上到下。
3. x 坐标，从左到右。
4. 原始 orderNo 兜底。

如果没有 bbox，则 x/y 都当作 0。

### 4.7 单元测试

已有测试：

```text
tests/test_block_normalizer.py
```

覆盖：

- 按 bbox 排序。
- 生成统一 blockId。
- 重复文本优先保留 MinerU。

## 5. `normalizers/markdown_renderer.py`

### 5.1 职责

把 `PdfParseResult` 渲染成 `result.md`，主要给开发调试和人工查看。

### 5.2 `render`

```python
def render(self, result: PdfParseResult) -> str:
    lines: list[str] = [
        f"# {result.file_name}",
        "",
        f"- Task: {result.task_id}",
        f"- Status: {result.status}",
        f"- Parser strategy: {result.parser_strategy}",
    ]
```

开头输出文件名和任务元信息。

如果有页数：

```python
if result.page_count is not None:
    lines.append(f"- Pages: {result.page_count}")
```

如果有 warning：

```python
if result.warnings:
    lines.append("- Warnings:")
    lines.extend(f"  - {warning}" for warning in result.warnings)
```

遍历 block 时按页插入注释：

```python
last_page: int | None = None
for block in result.blocks:
    if block.page_no != last_page:
        lines.extend(["", f"<!-- page {block.page_no} -->", ""])
        last_page = block.page_no
    lines.extend(self._render_block(block))
    lines.append("")
```

### 5.3 `_render_block`

```python
if block.type == "title":
    return [block.markdown or f"# {text}"]
if block.type == "heading":
    return [block.markdown or f"## {text}"]
if block.type == "list_item":
    return [block.markdown or f"- {text}"]
if block.type == "table":
    return [block.markdown or text or "[table]"]
if block.type == "figure":
    return [block.markdown or f"[figure: {text or block.block_id}]"]
if block.type == "formula":
    return [block.markdown or f"$$\n{text or block.block_id}\n$$"]
if block.type == "header":
    return [f"> [header] {text}"]
if block.type == "footer":
    return [f"> [footer] {text}"]
return [text]
```

这是把不同 block type 转成 Markdown 表达。

注意：这个 Markdown 主要是调试输出，不是 Java 后端的主输入。Java 后端主要用 `plainText`。

## 6. `storage/local_storage.py`

### 6.1 职责

本地文件存储适配器，负责：

- 创建基础目录。
- 统一生成路径。
- 复制源 PDF。
- 原子写 JSON。
- 原子写文本。
- 查找图片资产。

### 6.2 基础目录

```python
def ensure_base_dirs(self) -> None:
    for path in (
        self.settings.uploads_dir,
        self.settings.results_dir,
        self.settings.assets_dir,
        self.settings.tasks_dir,
    ):
        path.mkdir(parents=True, exist_ok=True)
```

启动服务对象时会创建：

```text
data/uploads
data/results
data/assets
data/tasks
```

### 6.3 路径方法

```python
def upload_pdf_path(self, task_id: str) -> Path:
    return self.upload_dir(task_id) / "source.pdf"

def result_json_path(self, task_id: str) -> Path:
    return self.result_dir(task_id) / "result.json"

def result_markdown_path(self, task_id: str) -> Path:
    return self.result_dir(task_id) / "result.md"

def task_path(self, task_id: str) -> Path:
    return self.settings.tasks_dir / f"{task_id}.json"
```

这些方法让上层服务不需要手写路径字符串。

Java 类比：

```java
Path uploadPdfPath(String taskId) {
    return uploadsDir.resolve(taskId).resolve("source.pdf");
}
```

### 6.4 图片资产查找

```python
def find_asset_path(self, task_id: str, asset_id: str) -> Path | None:
    image_dir = self.image_asset_dir(task_id)
    if not image_dir.exists():
        return None
    matches = list(image_dir.glob(f"{asset_id}.*"))
    return matches[0] if matches else None
```

因为 assetId 不带扩展名，所以用 glob 找：

```text
{assetId}.*
```

### 6.5 原子写 JSON

```python
def write_json_atomic(self, path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, path)
```

关键点：

- 先写临时文件。
- 再 `os.replace(tmp, path)` 替换正式文件。
- Windows 上 `os.replace` 也能覆盖已有文件。

这可以降低读到半文件的概率。

## 7. `storage/task_repository.py`

### 7.1 职责

用 JSON 文件保存任务状态。

每个任务一个文件：

```text
data/tasks/{taskId}.json
```

### 7.2 锁

```python
self._lock = threading.RLock()
```

`RLock` 是可重入锁。

为什么需要锁：

- API 查询线程和后台解析线程可能同时读写任务文件。
- 同一进程内用锁避免并发写导致文件状态混乱。

注意：这个锁只保护同一个 Python 进程。如果多进程部署，需要数据库、Redis 或文件锁。

### 7.3 保存任务

```python
def save(self, task: TaskInfo) -> TaskInfo:
    with self._lock:
        payload = task.model_dump(mode="json", by_alias=True)
        self.storage.write_json_atomic(self.storage.task_path(task.task_id), payload)
        return task
```

`with self._lock` 类似 Java：

```java
lock.lock();
try {
    ...
} finally {
    lock.unlock();
}
```

### 7.4 获取任务

```python
def get(self, task_id: str) -> TaskInfo:
    path = self.storage.task_path(task_id)
    if not path.exists():
        raise AiTranslationPyError(
            ErrorCode.PDF_TASK_NOT_FOUND,
            "PDF parse task was not found",
            status_code=404,
        )
    data = json.loads(path.read_text(encoding="utf-8"))
    return TaskInfo.model_validate(data)
```

读取 JSON 后交给 Pydantic 校验。

### 7.5 更新任务

```python
def update(self, task_id: str, updater: Callable[[TaskInfo], TaskInfo]) -> TaskInfo:
    with self._lock:
        task = self.get(task_id)
        updated = updater(task).model_copy(update={"updated_at": datetime.now(UTC)})
        return self.save(updated)
```

这是一个函数式更新模式。

调用方传一个 updater：

```python
lambda task: task.model_copy(update={"status": TaskStatus.RUNNING})
```

Repository 负责：

1. 加锁。
2. 读取当前任务。
3. 调用 updater 生成新任务。
4. 自动刷新 `updated_at`。
5. 保存。

Java 类比：

```java
public TaskInfo update(String taskId, Function<TaskInfo, TaskInfo> updater) {
    lock.lock();
    try {
        TaskInfo task = get(taskId);
        TaskInfo updated = updater.apply(task).withUpdatedAt(now());
        return save(updated);
    } finally {
        lock.unlock();
    }
}
```

## 8. `core/errors.py`

### 8.1 错误码

```python
class ErrorCode:
    PDF_INVALID_TYPE = "PDF_INVALID_TYPE"
    PDF_FILE_TOO_LARGE = "PDF_FILE_TOO_LARGE"
    PDF_PARSE_FAILED = "PDF_PARSE_FAILED"
    PDF_MINERU_FAILED = "PDF_MINERU_FAILED"
    PDF_UNSTRUCTURED_FAILED = "PDF_UNSTRUCTURED_FAILED"
    PDF_RESULT_NOT_READY = "PDF_RESULT_NOT_READY"
    PDF_TASK_NOT_FOUND = "PDF_TASK_NOT_FOUND"
```

这里用普通 class 存常量，没有用 Enum。

### 8.2 业务异常

```python
class AiTranslationPyError(Exception):
    def __init__(
        self,
        error_code: str,
        message: str,
        *,
        detail: Any | None = None,
        status_code: int = 400,
    ) -> None:
        super().__init__(message)
        self.error_code = error_code
        self.message = message
        self.detail = detail
        self.status_code = status_code
```

相当于 Java 里的自定义业务异常：

```java
public class AiTranslationPyException extends RuntimeException {
    private final String errorCode;
    private final String message;
    private final Object detail;
    private final int statusCode;
}
```

### 8.3 转 HTTP 响应

```python
def to_response(self) -> dict[str, Any]:
    body: dict[str, Any] = {
        "errorCode": self.error_code,
        "message": self.message,
    }
    if self.detail is not None:
        body["detail"] = self.detail
    return body
```

由 `api/app.py` 的异常处理器调用。

### 8.4 `ParserUnavailableError`

```python
class ParserUnavailableError(AiTranslationPyError):
    pass
```

它没有新增字段，只是表达语义：某个解析器不可用或执行失败。

`HybridPdfParser._try_parse` 捕获它后转成 warning。

## 9. `core/task_status.py`

```python
class TaskStatus(StrEnum):
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELED = "CANCELED"
```

`StrEnum` 的特点是枚举值本身也是字符串。

所以：

```python
TaskStatus.PENDING == "PENDING"
```

这对 JSON 序列化友好。

## 10. `core/logging.py`

```python
def setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
```

设置基础日志格式。

调用点：

- `cli.main()`
- `api.app.create_app()`

## 11. 数据文件示例

### 11.1 task JSON

```json
{
  "taskId": "pdf_20260621_153000_ab12cd34",
  "fileName": "sample.pdf",
  "status": "SUCCEEDED",
  "progress": 100,
  "currentStep": "completed",
  "errorCode": null,
  "errorMessage": null,
  "warnings": [],
  "createdAt": "2026-06-21T07:30:00Z",
  "updatedAt": "2026-06-21T07:30:10Z"
}
```

### 11.2 result JSON

```json
{
  "taskId": "pdf_20260621_153000_ab12cd34",
  "fileName": "sample.pdf",
  "pageCount": 12,
  "parserStrategy": "mineru_unstructured_hybrid",
  "status": "SUCCEEDED",
  "warnings": [],
  "blocks": [
    {
      "blockId": "block_000001",
      "pageNo": 1,
      "orderNo": 1,
      "type": "title",
      "text": "Document Title",
      "markdown": "# Document Title",
      "bbox": [10.0, 20.0, 500.0, 60.0],
      "confidence": null,
      "sourceParser": "mineru",
      "metadata": {}
    }
  ],
  "markdown": "# sample.pdf\n...",
  "plainText": "# Document Title\n\n...",
  "createdAt": "2026-06-21T07:30:00Z",
  "completedAt": "2026-06-21T07:30:10Z"
}
```

## 12. 这一层的核心价值

如果只看解析器，你会看到很多第三方库细节。真正让系统稳定的是这一层：

- Pydantic 模型保证 JSON 字段稳定。
- Normalizer 保证 block 顺序、类型和 ID 稳定。
- Storage 保证路径统一和结果文件原子写入。
- Repository 保证任务状态有一致读写入口。
- Error 模型保证 API 错误格式稳定。

