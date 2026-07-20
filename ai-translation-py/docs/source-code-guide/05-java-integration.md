# 05. Java 后端集成说明书

本篇解释 Python PDF 服务怎样被 Java 后端调用，以及结果怎样继续进入翻译流程。

相关 Java 文件：

```text
ai-translation-backend/src/main/java/com/xx/aitranslation/service/pipeline/TranslationPipeline.java
ai-translation-backend/src/main/java/com/xx/aitranslation/service/python/PythonPdfParseClient.java
ai-translation-backend/src/main/java/com/xx/aitranslation/config/PythonPdfParseProperties.java
ai-translation-backend/src/main/java/com/xx/aitranslation/service/python/dto/PythonPdfParseResult.java
ai-translation-backend/src/main/java/com/xx/aitranslation/service/python/dto/PythonPdfBlock.java
ai-translation-backend/src/main/java/com/xx/aitranslation/service/python/dto/PythonPdfParsedText.java
ai-translation-backend/src/main/java/com/xx/aitranslation/service/image/TranslationImageService.java
```

## 1. 集成定位

Python 服务只替换 PDF 文本和图片提取能力，不替换 Java 的业务流程。

也就是说：

```text
Python 负责:
  PDF -> plainText + blocks + image assets

Java 负责:
  task 状态
  chunk 分块
  document/paragraph/sentence 落库
  翻译
  审校
  图片保存和图片翻译
```

## 2. Java 总入口：`TranslationPipeline.parseAsync`

核心流程在 Java：

```java
@Async("taskExecutor")
public void parseAsync(Long taskId) {
    translationTaskService.startParseStep(taskId);
    try {
        TranslationTask task = translationTaskService.getById(taskId);
        ChunkConfig config = buildChunkConfig(task);
        ChunkResult result = null;
        PythonPdfParsedText pythonParsedText = null;
        byte[] sourceBytes;
        try (InputStream in = fileStorageService.download(task.getSourceFileKey())) {
            sourceBytes = in.readAllBytes();
        }
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
        ParsedDocument parsed = chunkParseAdapter.toParsedDocument(result);
        TranslationDocument document = documentParseService.saveParsedDocument(taskId, task, parsed);
        if (pythonParsedText != null) {
            translationImageService.savePythonPdfImages(
                    taskId,
                    document.getId(),
                    task,
                    pythonParsedText.getTaskId(),
                    pythonParsedText.getBlocks());
        }
        translationTaskService.completeParseStep(taskId);
        translationTaskService.transit(taskId, TaskStatus.PARSING, TaskStatus.PARSED);
    } catch (...) {
        ...
    }
}
```

拆成步骤：

1. Java 下载用户上传的源文件为 `byte[] sourceBytes`。
2. 判断是否应该使用 Python PDF 解析。
3. 如果使用，调用 `PythonPdfParseClient.parseWithRetry(...)`。
4. Python 成功，Java 拿 `pythonParsedText.getText()` 进入 `chunkEngine.chunkParsedText(...)`。
5. Python 失败，Java 走原有 `chunkEngine.chunk(...)`。
6. Java 把分块结果转成 `ParsedDocument`。
7. Java 落库到 document/paragraph/sentence。
8. 如果 Python 成功，再处理 Python 返回的 `figure` blocks。

## 3. 是否启用 Python PDF 解析

Java 逻辑：

```java
private boolean shouldUsePythonPdfParser(TranslationTask task) {
    return pythonPdfParseClient.enabled()
            && !ObjectUtils.isEmpty(task.getSourceFileType())
            && ChunkFileType.PDF.name().equalsIgnoreCase(task.getSourceFileType());
}
```

必须同时满足：

- 配置启用。
- 任务文件类型不为空。
- 文件类型是 PDF。

配置位置：

```yaml
app:
  python:
    pdf-parse:
      enabled: ${PYTHON_PDF_PARSE_ENABLED:true}
      base-url: ${PYTHON_PDF_PARSE_BASE_URL:http://127.0.0.1:8010}
      connect-timeout: ${PYTHON_PDF_PARSE_CONNECT_TIMEOUT:5s}
      read-timeout: ${PYTHON_PDF_PARSE_READ_TIMEOUT:30s}
      max-attempts: ${PYTHON_PDF_PARSE_MAX_ATTEMPTS:3}
      poll-timeout: ${PYTHON_PDF_PARSE_POLL_TIMEOUT:5m}
      poll-interval: ${PYTHON_PDF_PARSE_POLL_INTERVAL:2s}
```

## 4. `PythonPdfParseClient`

### 4.1 职责

这个类是 Java 调 Python 服务的 HTTP Client。

它负责：

- 上传 PDF 创建 Python 任务。
- 轮询 Python 任务状态。
- 获取 Python 解析结果。
- 把 Python blocks 转成 Java 可用的纯文本。
- 下载 Python 提取的图片资产。
- 自动重试。

### 4.2 构造函数

```java
public PythonPdfParseClient(PythonPdfParseProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.getConnectTimeout());
    factory.setReadTimeout(properties.getReadTimeout());
    this.restClient = RestClient.builder()
            .baseUrl(stripTrailingSlash(properties.getBaseUrl()))
            .requestFactory(factory)
            .build();
}
```

使用 Spring `RestClient`，baseUrl 默认：

```text
http://127.0.0.1:8010
```

### 4.3 重试入口：`parseWithRetry`

```java
public PythonPdfParsedText parseWithRetry(byte[] pdfBytes, String fileName) {
    int maxAttempts = Math.max(1, properties.getMaxAttempts());
    List<String> errors = new ArrayList<>();
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            PythonPdfParsedText parsed = parseOnce(pdfBytes, fileName);
            return parsed;
        } catch (Exception e) {
            ...
        }
    }
    throw new PythonPdfParseException("Python PDF parse failed after " + maxAttempts
            + " attempts: " + String.join("; ", errors));
}
```

默认最多 3 次。

如果 Python 连续失败，抛 `PythonPdfParseException`。

上层 `TranslationPipeline.tryPythonPdfParse(...)` 会捕获这个异常，然后回退 Java 原生解析。

### 4.4 单次调用：`parseOnce`

```java
private PythonPdfParsedText parseOnce(byte[] pdfBytes, String fileName) {
    PythonPdfTaskResponse created = createTask(pdfBytes, fileName);
    if (ObjectUtils.isEmpty(created) || ObjectUtils.isEmpty(created.getTaskId())) {
        throw new PythonPdfParseException("Python service returned empty taskId");
    }

    PythonPdfTaskStatusResponse status = waitUntilFinished(created.getTaskId());
    if (!"SUCCEEDED".equals(status.getStatus())) {
        throw new PythonPdfParseException("Python task failed: " + status.getErrorCode()
                + " " + status.getErrorMessage());
    }

    PythonPdfParseResult result = getResult(created.getTaskId());
    String text = toPlainText(result);
    if (ObjectUtils.isEmpty(text)) {
        throw new PythonPdfParseException("Python parse result has no translatable text");
    }
    return PythonPdfParsedText.builder()
            .taskId(result.getTaskId())
            .text(text)
            .pageCount(result.getPageCount())
            .warnings(result.getWarnings())
            .blocks(result.getBlocks())
            .build();
}
```

对应 Python API：

```text
createTask              -> POST /api/v1/pdf/parse-tasks
waitUntilFinished       -> GET  /api/v1/pdf/parse-tasks/{taskId}
getResult               -> GET  /api/v1/pdf/parse-tasks/{taskId}/result
```

### 4.5 创建 Python 任务

```java
private PythonPdfTaskResponse createTask(byte[] pdfBytes, String fileName) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", new NamedByteArrayResource(pdfBytes, fileName));
    return restClient.post()
            .uri("/api/v1/pdf/parse-tasks")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(PythonPdfTaskResponse.class);
}
```

这正好对应 Python：

```python
async def create_parse_task(file: UploadFile = File(...), options: str | None = Form(default=None))
```

### 4.6 轮询状态

```java
private PythonPdfTaskStatusResponse waitUntilFinished(String taskId) {
    Instant deadline = Instant.now().plus(properties.getPollTimeout());
    PythonPdfTaskStatusResponse status = null;
    while (Instant.now().isBefore(deadline)) {
        status = getStatus(taskId);
        if ("SUCCEEDED".equals(status.getStatus()) || "FAILED".equals(status.getStatus())
                || "CANCELED".equals(status.getStatus())) {
            return status;
        }
        sleep(properties.getPollInterval());
    }
    throw new PythonPdfParseException("Python task polling timed out, taskId=" + taskId
            + ", lastStatus=" + (status == null ? null : status.getStatus()));
}
```

默认：

- 总等待 5 分钟。
- 每 2 秒查一次。

Python 任务状态来自：

```text
data/tasks/{pythonTaskId}.json
```

### 4.7 获取结果

```java
private PythonPdfParseResult getResult(String taskId) {
    return restClient.get()
            .uri("/api/v1/pdf/parse-tasks/{taskId}/result", taskId)
            .retrieve()
            .body(PythonPdfParseResult.class);
}
```

对应 Python 返回：

```python
PdfParseResult
```

Java DTO：

```java
public class PythonPdfParseResult {
    private String taskId;
    private String fileName;
    private Integer pageCount;
    private String parserStrategy;
    private String status;
    private List<String> warnings;
    private List<PythonPdfBlock> blocks;
    private String markdown;
    private String plainText;
}
```

字段名和 Python JSON alias 完全匹配。

## 5. `plainText` 怎样进入 Java 分块

Python 成功后，Java 走：

```java
result = chunkEngine.chunkParsedText(
        pythonParsedText.getText(),
        task.getSourceFileName(),
        ChunkFileType.PDF,
        config);
```

这里的 `pythonParsedText.getText()` 来自：

```java
String text = toPlainText(result);
```

优先使用 Python 返回的 `plainText`：

```java
if (!ObjectUtils.isEmpty(result.getPlainText())) {
    return result.getPlainText();
}
```

如果 Python 结果没有 `plainText`，Java 会自己从 blocks 拼：

```java
return result.getBlocks().stream()
        .filter(block -> block != null && TRANSLATABLE_TYPES.contains(normalizeType(block.getType())))
        .map(this::blockText)
        .filter(text -> !ObjectUtils.isEmpty(text))
        .collect(Collectors.joining("\n\n"));
```

这是一层兼容兜底。

## 6. Python block 到 Java text 的规则

Java 中：

```java
private String blockText(PythonPdfBlock block) {
    String type = normalizeType(block.getType());
    String text = ObjectUtils.isEmpty(block.getText()) ? block.getMarkdown() : block.getText();
    if (ObjectUtils.isEmpty(text)) {
        return null;
    }
    if ("title".equals(type)) {
        return "# " + text.strip();
    }
    if ("heading".equals(type)) {
        return "## " + text.strip();
    }
    if ("list_item".equals(type)) {
        String stripped = text.strip();
        return stripped.startsWith("-") || stripped.startsWith("*") ? stripped : "- " + stripped;
    }
    if ("figure".equals(type)) {
        Object assetId = block.getMetadata() == null ? null : block.getMetadata().get("assetId");
        return "[PDF image: " + (assetId == null ? block.getBlockId() : assetId) + "]";
    }
    return text.strip();
}
```

这和 Python `_render_plain_text` 基本一致。

意义：

- 标题保留 Markdown 级别。
- 列表保留列表标记。
- 图片保留占位符。
- 其他内容直接作为段落文本。

## 7. 图片集成流程

Python 图片 block 长这样：

```json
{
  "blockId": "block_000010",
  "pageNo": 2,
  "orderNo": 10,
  "type": "figure",
  "text": "[image:img_p0002_0001_35]",
  "markdown": "![img_p0002_0001_35](data/assets/...)",
  "sourceParser": "pymupdf_image",
  "metadata": {
    "assetId": "img_p0002_0001_35",
    "assetPath": "...",
    "mimeType": "image/png",
    "width": 800,
    "height": 600,
    "xref": 35
  }
}
```

Java 在 `TranslationPipeline.parseAsync` 落库完成后调用：

```java
translationImageService.savePythonPdfImages(
        taskId,
        document.getId(),
        task,
        pythonParsedText.getTaskId(),
        pythonParsedText.getBlocks());
```

### 7.1 `TranslationImageService.savePythonPdfImages`

```java
public void savePythonPdfImages(Long taskId, Long documentId, TranslationTask task, String pythonTaskId,
                                List<PythonPdfBlock> blocks) {
    if (ObjectUtils.isEmpty(blocks)) {
        return;
    }
    for (PythonPdfBlock block : blocks) {
        if (!"figure".equalsIgnoreCase(block.getType())) {
            continue;
        }
        saveOneImage(taskId, documentId, task, pythonTaskId, block);
    }
}
```

只处理 `figure` 类型 block。

### 7.2 下载 Python 图片资产

```java
byte[] original = pythonPdfParseClient.downloadAsset(pythonTaskId, assetId);
```

对应 Python：

```text
GET /api/v1/pdf/parse-tasks/{pythonTaskId}/assets/{assetId}
```

Python 会从：

```text
data/assets/{pythonTaskId}/images/{assetId}.{ext}
```

找到图片并返回。

### 7.3 Java 存储图片

```java
String originalKey = fileStorageService.upload(
        new ByteArrayInputStream(original),
        originalName,
        image.getMimeType());
image.setOriginalFileKey(originalKey);
```

Java 会把图片存到自己的文件存储系统，本地或 MinIO。

Python 的本地图片资产只是中间产物，Java 保存后才进入主业务存储。

### 7.4 图片翻译

如果任务没有启用图片翻译：

```java
if (!Boolean.TRUE.equals(task.getEnableImageTranslation())) {
    image.setStatus(STATUS_SKIPPED);
    imageMapper.insert(image);
    return;
}
```

如果启用：

```java
byte[] translated = imageTranslationClient.translate(...);
String translatedKey = fileStorageService.upload(...);
image.setTranslatedFileKey(translatedKey);
image.setStatus(STATUS_TRANSLATED);
```

单张图片翻译失败不会影响文本解析完成，只会把这张图片状态标记为 `FAILED`。

## 8. Python 失败时的回退

Java：

```java
private PythonPdfParsedText tryPythonPdfParse(Long taskId, TranslationTask task, byte[] sourceBytes) {
    try {
        PythonPdfParsedText parsedText = pythonPdfParseClient.parseWithRetry(sourceBytes, task.getSourceFileName());
        return parsedText;
    } catch (PythonPdfParseException e) {
        log.error("Python PDF 解析 3 次失败，切换 Java 原生解析", ...);
        return null;
    } catch (Exception e) {
        log.error("Python PDF 解析异常，切换 Java 原生解析", ...);
        return null;
    }
}
```

上层：

```java
if (result == null) {
    result = chunkEngine.chunk(new ByteArrayInputStream(sourceBytes), task.getSourceFileName(), config);
}
```

也就是说：

- Python 失败不会直接导致整个 Java 解析任务失败。
- Java 会自动回退原 PDF 解析链路。
- 只有 Java 原链路也失败，任务才失败。

## 9. Python 和 Java DTO 对照

### 9.1 PdfBlock

Python：

```python
class PdfBlock(BaseModel):
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

Java：

```java
public class PythonPdfBlock {
    private String blockId;
    private Integer pageNo;
    private Integer orderNo;
    private String type;
    private String text;
    private String markdown;
    private List<Double> bbox;
    private String sourceParser;
    private Map<String, Object> metadata;
}
```

### 9.2 PdfParseResult

Python：

```python
class PdfParseResult(BaseModel):
    task_id: str = Field(alias="taskId")
    file_name: str = Field(alias="fileName")
    page_count: int | None = Field(default=None, alias="pageCount")
    parser_strategy: str = Field(alias="parserStrategy")
    status: str
    warnings: list[str] = Field(default_factory=list)
    blocks: list[PdfBlock] = Field(default_factory=list)
    markdown: str | None = None
    plain_text: str | None = Field(default=None, alias="plainText")
```

Java：

```java
public class PythonPdfParseResult {
    private String taskId;
    private String fileName;
    private Integer pageCount;
    private String parserStrategy;
    private String status;
    private List<String> warnings;
    private List<PythonPdfBlock> blocks;
    private String markdown;
    private String plainText;
}
```

## 10. 集成排查清单

### 10.1 Java 连不上 Python

检查：

```text
PYTHON_PDF_PARSE_BASE_URL
Python 服务是否启动
GET http://127.0.0.1:8010/health
connect-timeout
```

### 10.2 Java 轮询超时

检查：

```text
PYTHON_PDF_PARSE_POLL_TIMEOUT
PYTHON_PDF_PARSE_POLL_INTERVAL
Python data/tasks/{taskId}.json
Python 日志
MinerU 是否卡住
```

### 10.3 Python 成功但 Java 说 no translatable text

检查 Python `/result`：

- `plainText` 是否为空。
- `blocks` 是否为空。
- block 类型是否都不在 Java 的 `TRANSLATABLE_TYPES` 中。

Java 可翻译类型：

```java
private static final Set<String> TRANSLATABLE_TYPES = Set.of(
        "title", "heading", "paragraph", "list_item", "table", "figure", "header", "footer", "unknown");
```

### 10.4 图片没有入库

检查：

- Python result 里是否有 `type=figure` block。
- figure block 的 `metadata.assetId` 是否存在。
- Python `/assets/{assetId}` 是否能下载。
- Java `TranslationImageService` 日志。
- 任务是否启用图片翻译只影响翻译，不影响原图保存。

### 10.5 Python 失败后 Java 没有失败

这是预期行为。Java 会回退：

```text
Python parse failed -> Java native PDF parse
```

只有回退链路也失败时，Java 任务才失败。

