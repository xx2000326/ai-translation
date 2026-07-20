# 06. 给 Java 开发者的 Python 读码笔记

这篇不重复业务流程，专门解释你读这段 Python 代码时会遇到的语法和写法。

## 1. Python 包和 Java 包的类比

Python 目录：

```text
ai_translation_py/
  services/
    pdf_task_service.py
```

类似 Java：

```text
com.xx.aitranslation.services.PdfTaskService
```

但 Python 的一个 `.py` 文件就是一个模块，模块里可以放类、函数、常量。

导入：

```python
from ai_translation_py.services.pdf_task_service import build_pdf_task_service
```

类似 Java：

```java
import com.xx.aitranslation.services.PdfTaskService;
```

## 2. `from __future__ import annotations`

很多文件开头有：

```python
from __future__ import annotations
```

作用是推迟类型注解求值，让类型注解更灵活，也能减少循环引用问题。

你读代码时可以把它理解为：让类型标注更好用，不影响运行主逻辑。

## 3. 类型注解

示例：

```python
def get_result(self, task_id: str) -> PdfParseResult:
    ...
```

含义：

- `task_id: str`：参数是字符串。
- `-> PdfParseResult`：返回 `PdfParseResult`。

Python 类型注解默认不是强制运行时检查，更多是给 IDE、静态检查器和读代码的人看。

Pydantic 模型会在创建和校验时做运行时检查。

## 4. 可空类型

```python
markdown: str | None = None
```

表示：

```text
markdown 可以是 str，也可以是 None
```

Java 类比：

```java
@Nullable String markdown;
```

老一点的 Python 写法是：

```python
Optional[str]
```

当前项目用 Python 3.11，所以使用 `str | None`。

## 5. list 和 dict 类型

```python
blocks: list[PdfBlock]
metadata: dict[str, Any]
```

Java 类比：

```java
List<PdfBlock> blocks;
Map<String, Object> metadata;
```

## 6. `self`

Python 实例方法第一个参数必须显式写 `self`：

```python
class ResultService:
    def __init__(self, storage: LocalStorage, repository: TaskRepository) -> None:
        self.storage = storage
        self.repository = repository

    def get_result(self, task_id: str) -> PdfParseResult:
        ...
```

Java 中 `this` 是隐式的：

```java
class ResultService {
    private LocalStorage storage;

    PdfParseResult getResult(String taskId) {
        this.storage...
    }
}
```

Python 中实例字段通常在 `__init__` 中赋值：

```python
self.storage = storage
```

## 7. `__init__`

```python
def __init__(self, settings: Settings) -> None:
    self.settings = settings
```

就是构造函数。

Java 类比：

```java
public LocalStorage(Settings settings) {
    this.settings = settings;
}
```

## 8. `@staticmethod`

```python
@staticmethod
def _validate_pdf_file_name(file_name: str) -> None:
    ...
```

表示不需要 `self`，可以通过类或实例调用。

Java 类比：

```java
private static void validatePdfFileName(String fileName) { ... }
```

## 9. 下划线方法

```python
def _create_task_record(...)
def _validate_pdf_magic(...)
```

单下划线表示“内部使用”的约定，类似 Java 的 private。但 Python 不会强制阻止外部调用。

## 10. `async def` 和 `await`

```python
async def create_parse_task_from_upload(...):
    chunk = await upload_file.read(1024 * 1024)
```

这是异步函数。只有异步函数里才能 `await`。

在本项目里，异步主要用于读取 FastAPI 上传文件。

注意：PDF 解析本身没有用 async，而是用 `ThreadPoolExecutor` 后台线程执行。

## 11. `Path`

项目大量使用：

```python
from pathlib import Path
```

路径拼接：

```python
self.data_dir / "uploads" / task_id
```

Java 类比：

```java
dataDir.resolve("uploads").resolve(taskId)
```

读写文件：

```python
path.read_text(encoding="utf-8")
path.write_text(text, encoding="utf-8")
path.write_bytes(image_bytes)
```

Java 类比：

```java
Files.readString(path, StandardCharsets.UTF_8)
Files.writeString(path, text, StandardCharsets.UTF_8)
Files.write(path, bytes)
```

## 12. with 上下文管理器

```python
with tmp.open("wb") as handle:
    handle.write(chunk)
```

`with` 会自动关闭文件。

Java 类比：

```java
try (OutputStream out = Files.newOutputStream(tmp)) {
    out.write(chunk);
}
```

## 13. 列表推导式

```python
cleaned = [self._clean_block(block) for block in blocks]
```

Java Stream 类比：

```java
List<PdfBlock> cleaned = blocks.stream()
    .map(this::cleanBlock)
    .toList();
```

过滤：

```python
cleaned = [block for block in cleaned if self._is_usable(block)]
```

Java：

```java
cleaned.stream().filter(this::isUsable).toList();
```

## 14. 字典

Python dict：

```python
metadata = {"assetId": asset_id, "mimeType": mime_type}
```

Java Map：

```java
Map<String, Object> metadata = Map.of("assetId", assetId, "mimeType", mimeType);
```

取值：

```python
asset_id = block.metadata.get("assetId")
```

Java：

```java
Object assetId = metadata.get("assetId");
```

## 15. f-string

```python
task_id = f"pdf_{now:%Y%m%d_%H%M%S}_{uuid.uuid4().hex[:8]}"
```

类似 Java：

```java
String taskId = "pdf_" + formattedNow + "_" + uuid.substring(0, 8);
```

格式化数字：

```python
f"block_{index:06d}"
```

结果：

```text
block_000001
block_000002
```

## 16. `or` 默认值

```python
file_name = upload_file.filename or "source.pdf"
```

如果 `upload_file.filename` 是空字符串或 None，就用 `"source.pdf"`。

Java 类比：

```java
String fileName = hasText(uploadFile.getFilename()) ? uploadFile.getFilename() : "source.pdf";
```

## 17. 异常链 `from exc`

```python
raise ParserUnavailableError(...) from exc
```

表示抛出新的异常，同时保留原始异常作为 cause。

Java 类比：

```java
throw new ParserUnavailableException("...", exc);
```

## 18. `try / except / finally`

```python
try:
    document = fitz.open(pdf_path)
except Exception as exc:
    return RawParseOutput(...)
finally:
    document.close()
```

类似 Java：

```java
try {
    ...
} catch (Exception e) {
    ...
} finally {
    document.close();
}
```

## 19. `getattr`

```python
metadata = getattr(element, "metadata", None)
page_no = int(getattr(metadata, "page_number", 1) or 1)
```

`getattr(obj, "name", default)` 表示安全读取属性。

Java 类比不完全一样，可以理解成反射或 Map 安全取值。但在 Python 中很多第三方库对象字段不固定，所以常用 `getattr` 防御。

## 20. `isinstance`

```python
if isinstance(value, int):
    return value
```

Java：

```java
if (value instanceof Integer) {
    return (Integer) value;
}
```

Python 也支持多个类型：

```python
isinstance(value, (int, float))
```

## 21. `enumerate`

```python
for index, page in enumerate(reader.pages, start=1):
    ...
```

同时拿下标和值，下标从 1 开始。

Java 类比：

```java
int index = 1;
for (Page page : pages) {
    ...
    index++;
}
```

## 22. `Protocol`

```python
class PdfParser(Protocol):
    name: str
    def parse(...) -> RawParseOutput:
        ...
```

这是结构化接口。类不需要显式继承，只要方法和属性匹配即可。

Java 中接口需要显式：

```java
class MineruParser implements PdfParser { ... }
```

Python 中 `MineruParser` 没有写继承，但它有 `name` 和 `parse`，所以逻辑上符合。

## 23. `dataclass`

```python
@dataclass(slots=True)
class RawParseOutput:
    parser_name: str
    blocks: list[PdfBlock] = field(default_factory=list)
```

自动生成构造函数。

等价于手写：

```python
def __init__(self, parser_name, blocks=None, ...):
    self.parser_name = parser_name
    self.blocks = blocks or []
```

Java 类比：

- record。
- Lombok `@Data` 加构造器。

## 24. Pydantic `BaseModel`

```python
class PdfBlock(BaseModel):
    block_id: str = Field(alias="blockId")
```

Pydantic 提供：

- 构造时字段校验。
- JSON 序列化。
- alias。
- 嵌套对象转换。

常用方法：

```python
PdfParseResult.model_validate(data)
result.model_dump(mode="json", by_alias=True)
result.model_copy(update={"markdown": markdown})
```

Java 类比：

- Jackson 反序列化。
- Bean Validation 一部分能力。
- Lombok DTO 一部分能力。

## 25. `@lru_cache(maxsize=1)`

```python
@lru_cache(maxsize=1)
def build_pdf_task_service() -> PdfTaskService:
    ...
```

第一次调用执行函数，之后返回缓存。

在本项目里用它模拟单例 Bean。

Java 类比：

- Spring singleton Bean。
- `private static volatile instance` 单例。

## 26. `ThreadPoolExecutor`

```python
executor = ThreadPoolExecutor(max_workers=settings.workers, thread_name_prefix="pdf-parser")
executor.submit(self.parse_service.run_parse_task, task_id)
```

Java 类比：

```java
ExecutorService executor = Executors.newFixedThreadPool(workers);
executor.submit(() -> parseService.runParseTask(taskId));
```

## 27. `model_dump(by_alias=True)` 为什么重要

Python 内部字段：

```python
task_id
file_name
page_count
```

Java 需要 JSON：

```json
{
  "taskId": "...",
  "fileName": "...",
  "pageCount": 12
}
```

所以写文件时必须：

```python
result.model_dump(mode="json", by_alias=True)
```

如果漏掉 `by_alias=True`，JSON 会变成：

```json
{
  "task_id": "...",
  "file_name": "...",
  "page_count": 12
}
```

Java DTO 就接不上。

## 28. `None`、空字符串、空列表的真假值

Python 中这些都被视为 False：

```python
None
""
[]
{}
0
```

所以代码常写：

```python
if not blocks:
    ...
```

含义是 blocks 是 None 或空列表。

在本项目中，多数类型已经标明 `list[...]`，所以通常表示空列表。

## 29. 读这套代码的建议姿势

先不要从每个第三方解析器细节开始读。建议顺序：

1. `routes_pdf.create_parse_task`
2. `PdfTaskService.create_parse_task_from_upload`
3. `PdfParseService.run_parse_task`
4. `HybridPdfParser.parse`
5. `BlockNormalizer.normalize`
6. `PdfParseResult`
7. Java `PythonPdfParseClient.parseOnce`
8. Java `TranslationPipeline.parseAsync`

读完这条线，你就知道这套代码的骨架了。

然后再进入：

- MinerU 具体怎么调用 CLI。
- Unstructured 怎么映射 element。
- pypdf 怎么兜底。
- PyMuPDF 怎么提图片。

## 30. 最核心的三个对象

如果只能先记三个对象，记这三个：

```text
TaskInfo
  任务状态

PdfBlock
  PDF 内容块

PdfParseResult
  一次解析最终结果
```

如果只能先记三个服务，记这三个：

```text
PdfTaskService
  任务门面

PdfParseService
  执行解析

HybridPdfParser
  调度多个解析器
```

