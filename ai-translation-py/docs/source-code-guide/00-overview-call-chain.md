# 00. 总览与完整调用链

## 1. 这个 Python 项目的定位

`ai-translation-py` 是主 Java/Spring Boot 项目旁边的独立 Python 服务。它现在只负责 PDF 解析，不负责翻译。

为什么单独做 Python 服务：

- PDF 解析、OCR、版面识别、图片提取这些生态在 Python 侧更丰富。
- MinerU、Unstructured、PyMuPDF 这类库更适合放在 Python runtime 中维护。
- Java 主服务可以继续负责业务任务、分块、落库、翻译、审校，不需要背负复杂 PDF 解析依赖。

可以把它类比成 Java 系统旁边的一个专用微服务：

```text
Java Backend
  TranslationPipeline.parseAsync
    -> PythonPdfParseClient
      -> HTTP multipart upload PDF

Python ai-translation-py
  FastAPI routes
    -> PdfTaskService
      -> PdfParseService
        -> HybridPdfParser
          -> MinerU / Unstructured / pypdf / PyMuPDF
        -> result.json / result.md

Java Backend
  poll task
  get result
  plainText -> ChunkEngine
  figure blocks -> TranslationImageService
```

## 2. 一次 PDF 解析从 HTTP 入口到结果文件

下面按真实代码调用顺序走一遍。

### 2.1 HTTP 创建任务

入口文件：

```text
ai_translation_py/api/routes_pdf.py
```

核心路由：

```python
@router.post("/parse-tasks", response_model=ParseTaskCreatedResponse)
async def create_parse_task(
    file: UploadFile = File(...),
    options: str | None = Form(default=None),
) -> ParseTaskCreatedResponse:
    service = build_pdf_task_service()
    task = await service.create_parse_task_from_upload(file, options=parse_options(options))
    return ParseTaskCreatedResponse(taskId=task.task_id, status=task.status)
```

对应 HTTP：

```text
POST /api/v1/pdf/parse-tasks
multipart/form-data
  file=xxx.pdf
  options=可选 JSON 字符串
```

这段代码做了三件事：

1. 通过 `build_pdf_task_service()` 拿到服务对象。
2. 调用 `PdfTaskService.create_parse_task_from_upload(...)`。
3. 立即返回 `taskId` 和当前状态。

Java 类比：

- `routes_pdf.py` 类似 Spring MVC 的 `@RestController`。
- `UploadFile = File(...)` 类似 `MultipartFile file`。
- `response_model=ParseTaskCreatedResponse` 类似返回 DTO，并由框架序列化成 JSON。

### 2.2 任务门面服务保存 PDF 并提交后台线程

入口文件：

```text
ai_translation_py/services/pdf_task_service.py
```

核心方法：

```python
async def create_parse_task_from_upload(
    self,
    upload_file: UploadFile,
    *,
    options: dict[str, Any] | None = None,
    submit: bool = True,
) -> TaskInfo:
    file_name = upload_file.filename or "source.pdf"
    self._validate_pdf_file_name(file_name)
    task = self._create_task_record(file_name)

    target = self.storage.upload_pdf_path(task.task_id)
    ...
    with tmp.open("wb") as handle:
        while True:
            chunk = await upload_file.read(1024 * 1024)
            ...
            handle.write(chunk)
    self._validate_pdf_magic(first_chunk)
    ...
    self.repository.save(task)
    if submit:
        self.submit_parse_task(task.task_id)
    return task
```

这段代码是任务创建的核心：

- 校验文件名必须是 `.pdf`。
- 创建 `TaskInfo`，状态是 `PENDING`。
- 分块读取上传文件，每次读 1MB，避免大 PDF 一次性占满内存。
- 校验 PDF 文件头必须以 `%PDF` 开头。
- 保存为 `data/uploads/{taskId}/source.pdf`。
- 把任务状态保存为 `data/tasks/{taskId}.json`。
- 提交后台线程开始解析。

Java 类比：

- `PdfTaskService` 类似一个 Facade Service。
- `ThreadPoolExecutor` 类似 Java 的 `ExecutorService`。
- `TaskRepository` 类似 Repository，只不过底层不是数据库，而是 JSON 文件。

后台线程提交点：

```python
def submit_parse_task(self, task_id: str) -> None:
    self.executor.submit(self.parse_service.run_parse_task, task_id)
```

这类似：

```java
executorService.submit(() -> pdfParseService.runParseTask(taskId));
```

### 2.3 PdfParseService 执行解析

入口文件：

```text
ai_translation_py/services/pdf_parse_service.py
```

核心方法：

```python
def run_parse_task(self, task_id: str) -> None:
    self.repository.update(
        task_id,
        lambda task: task.model_copy(
            update={
                "status": TaskStatus.RUNNING,
                "progress": 5,
                "current_step": "hybrid_parse",
                "error_code": None,
                "error_message": None,
            }
        ),
    )

    task = self.repository.get(task_id)
    try:
        pdf_path = self.storage.upload_pdf_path(task_id)
        work_dir = self.storage.work_dir(task_id)
        work_dir.mkdir(parents=True, exist_ok=True)
        raw_output = self.parser.parse(pdf_path, task_id=task_id, work_dir=work_dir)
        ...
        result = PdfParseResult(...)
        markdown = self.renderer.render(result)
        ...
        self.storage.write_json_atomic(...)
        self.storage.write_text_atomic(...)
        ...
        self.repository.update(... SUCCEEDED ...)
    except AiTranslationPyError as exc:
        self._mark_failed(task_id, exc.error_code, exc.message)
    except Exception as exc:
        self._mark_failed(task_id, ErrorCode.PDF_PARSE_FAILED, str(exc))
```

这层是“真正干活”的服务：

1. 把任务状态改为 `RUNNING`。
2. 找到上传 PDF 路径。
3. 创建工作目录 `data/work/{taskId}`。
4. 调用 `HybridPdfParser.parse(...)`。
5. 组装 `PdfParseResult`。
6. 渲染 Markdown。
7. 写 `result.json` 和 `result.md`。
8. 把任务状态改为 `SUCCEEDED`。
9. 如果异常，改为 `FAILED` 并记录错误码。

Java 类比：

- `try / except` 类似 Java 的 `try / catch`。
- `AiTranslationPyError` 类似业务异常。
- `model_copy(update={...})` 类似 Java 中 `copyWith` 或 builder 更新字段。

### 2.4 HybridPdfParser 调度多个解析器

入口文件：

```text
ai_translation_py/parsers/hybrid_pdf_parser.py
```

核心方法：

```python
def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
    outputs: list[RawParseOutput] = []
    warnings: list[str] = []

    if self.settings.enable_mineru:
        output, warning = self._try_parse(MineruParser(self.settings), ...)
        ...

    if self.settings.enable_unstructured:
        output, warning = self._try_parse(UnstructuredParser(), ...)
        ...

    if self.settings.enable_pypdf_fallback and not any(output.blocks for output in outputs):
        output, warning = self._try_parse(PypdfTextParser(), ...)
        ...

    image_output, image_warning = self._try_parse(PdfImageAssetParser(), ...)
    ...

    normalized_blocks = self.normalizer.normalize(all_blocks)
    if not normalized_blocks:
        raise AiTranslationPyError(...)

    return RawParseOutput(...)
```

实际策略：

1. 如果启用 MinerU，先跑 MinerU。
2. 如果启用 Unstructured，再跑 Unstructured。
3. 如果前面都没有产出文本 block，并且启用 pypdf 兜底，再跑 pypdf。
4. 无论文本解析怎样，都尝试用 PyMuPDF 提取图片。
5. 合并所有 block。
6. 交给 `BlockNormalizer` 做标准化。
7. 如果最后没有任何可用 block，任务失败。

注意：`_try_parse(...)` 会吞掉单个解析器异常，把异常转成 warning。也就是说，MinerU 失败并不代表整个任务失败，只要后面的解析器能产出结果。

### 2.5 BlockNormalizer 标准化结果

入口文件：

```text
ai_translation_py/normalizers/block_normalizer.py
```

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

它做四步：

1. 清洗字段：非法 type 改成 `unknown`，文本 strip，bbox 转 float，页码至少为 1。
2. 过滤不可用块：普通文本块必须有 text 或 markdown，表格、图片、公式可以保留占位。
3. 去重：同一页相同文本只保留一个，优先 MinerU，其次 Unstructured，最后 pypdf。
4. 排序和重编号：按页码、坐标、原始顺序排序，然后生成统一 `block_000001`。

这是后续 Java 消费最依赖的一步，因为它保证了顺序和 blockId 稳定。

### 2.6 写结果文件

解析成功后会生成：

```text
data/
  tasks/
    {taskId}.json
  uploads/
    {taskId}/source.pdf
  work/
    {taskId}/...
  results/
    {taskId}/result.json
    {taskId}/result.md
  assets/
    {taskId}/images/{assetId}.{ext}
```

写文件使用原子替换：

```python
tmp.write_text(...)
os.replace(tmp, path)
```

意义是避免调用方读到写了一半的 JSON 或 Markdown。

## 3. 命令行解析调用链

如果不走 HTTP，也可以命令行直接解析：

```bash
uv run ai-translation-py parse-pdf ./sample.pdf --output ./output
```

调用链：

```text
ai_translation_py/main.py
  -> cli.main()
    -> build_parser()
    -> _parse_pdf(args)
      -> build_pdf_task_service()
      -> create_parse_task_from_path(..., submit=False)
      -> run_parse_task(task.task_id)
      -> get_result(task.task_id)
      -> copy result.json/result.md to output dir
```

命令行和 API 复用同一套 service/parser/storage 代码。区别是：

- API 默认 `submit=True`，创建任务后放到后台线程跑。
- CLI 使用 `submit=False`，然后立刻同步调用 `run_parse_task(...)`。

## 4. Java 后端调用链

Java 后端核心入口：

```text
ai-translation-backend/src/main/java/com/xx/aitranslation/service/pipeline/TranslationPipeline.java
```

核心流程：

```java
if (shouldUsePythonPdfParser(task)) {
    pythonParsedText = tryPythonPdfParse(taskId, task, sourceBytes);
    if (pythonParsedText != null) {
        result = chunkEngine.chunkParsedText(
                pythonParsedText.getText(),
                task.getSourceFileName(),
                ChunkFileType.PDF,
                config);
    }
}
if (result == null) {
    result = chunkEngine.chunk(new ByteArrayInputStream(sourceBytes), task.getSourceFileName(), config);
}
```

逻辑：

1. 如果文件类型是 PDF，并且配置启用了 Python PDF 解析，则先调用 Python。
2. Python 成功，拿 `plainText` 进入 Java `ChunkEngine`。
3. Python 失败，Java 回退到原来的 PDF 解析方式。
4. Python 返回的 `figure` block 会交给 `TranslationImageService` 保存图片资产。

Python 不是替代整个 Java 解析落库流程，而是替代“从 PDF 抽取可翻译文本和图片资产”这一步。

## 5. 源码目录地图

```text
ai-translation-py/
  pyproject.toml
    Python 包配置，依赖声明，console script 声明

  requirements.txt
    运行依赖

  ai_translation_py/
    __init__.py
      包标记文件

    main.py
      python -m ai_translation_py.main 时进入这里

    cli.py
      命令行参数解析

    config.py
      读取环境变量配置

    api/
      app.py
        FastAPI app 工厂
      routes_pdf.py
        HTTP API 路由
      schemas.py
        API 错误响应 DTO

    core/
      errors.py
        统一异常和错误码
      task_status.py
        任务状态枚举
      logging.py
        logging 基础配置

    models/
      task.py
        TaskInfo、ParseTaskCreatedResponse
      pdf_result.py
        PdfBlock、PdfParseResult

    services/
      pdf_task_service.py
        创建任务、保存上传文件、提交后台执行
      pdf_parse_service.py
        执行解析、写结果、更新状态
      result_service.py
        查询 result.json/result.md

    parsers/
      base.py
        RawParseOutput、PdfParser 协议
      hybrid_pdf_parser.py
        混合解析调度
      mineru_parser.py
        MinerU CLI 适配
      unstructured_parser.py
        Unstructured 适配
      text_pdf_parser.py
        pypdf 文本兜底
      image_asset_parser.py
        PyMuPDF 图片提取

    normalizers/
      block_normalizer.py
        清洗、去重、排序、重编号
      markdown_renderer.py
        result.md 渲染

    storage/
      local_storage.py
        路径规划、复制 PDF、原子写结果
      task_repository.py
        JSON 文件读写任务状态
```

## 6. 读源码时的主线

建议先只抓住一条主线：

```text
routes_pdf.create_parse_task
  -> PdfTaskService.create_parse_task_from_upload
  -> PdfTaskService.submit_parse_task
  -> PdfParseService.run_parse_task
  -> HybridPdfParser.parse
  -> BlockNormalizer.normalize
  -> MarkdownRenderer.render
  -> LocalStorage.write_json_atomic/write_text_atomic
```

这条线读通后，再补充：

- CLI 入口怎么复用同一套 service。
- 每个具体 parser 怎么把第三方库输出转成 `PdfBlock`。
- Java 后端怎么消费 `PdfParseResult`。

## 7. 关键设计取舍

### 7.1 用文件系统保存任务，而不是数据库

当前 Python 服务用：

```text
data/tasks/{taskId}.json
```

保存任务状态。优点是简单、本地调试方便，不需要额外数据库。

代价是：

- 多进程部署时不够可靠。
- 没有任务 TTL 清理实现。
- 查询能力弱，只适合按 taskId 查。

如果以后任务量大，可以把 `TaskRepository` 换成数据库或 Redis。

### 7.2 多解析器输出统一成 PdfBlock

MinerU、Unstructured、pypdf、PyMuPDF 输出格式完全不同。项目没有让 Java 直接处理这些原始格式，而是统一成：

```text
PdfBlock
  blockId
  pageNo
  orderNo
  type
  text
  markdown
  bbox
  confidence
  sourceParser
  metadata
```

这是整个服务最重要的领域模型。

### 7.3 单个解析器失败不直接失败任务

`HybridPdfParser._try_parse(...)` 会把异常转成 warning。

这样做的好处是：

- MinerU 没装，本地仍可用 pypdf 跑通流程。
- Unstructured 失败，不影响 MinerU 结果。
- 图片提取失败，不影响文本解析。

真正失败条件是：所有可用解析器都没有产出任何可用 block。

## 8. 常见调试断点

如果你想跟代码调试，可以按这些点打断点：

```text
routes_pdf.py
  create_parse_task

pdf_task_service.py
  create_parse_task_from_upload
  submit_parse_task

pdf_parse_service.py
  run_parse_task

hybrid_pdf_parser.py
  parse
  _try_parse

mineru_parser.py
  parse

unstructured_parser.py
  parse

text_pdf_parser.py
  parse

image_asset_parser.py
  parse

block_normalizer.py
  normalize
  _dedupe

markdown_renderer.py
  render
```

