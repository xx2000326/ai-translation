# 03. PDF 解析器层说明书

本篇解释这些文件：

```text
ai_translation_py/parsers/base.py
ai_translation_py/parsers/hybrid_pdf_parser.py
ai_translation_py/parsers/mineru_parser.py
ai_translation_py/parsers/unstructured_parser.py
ai_translation_py/parsers/text_pdf_parser.py
ai_translation_py/parsers/image_asset_parser.py
```

解析器层是 PDF 解析能力的核心。

## 1. 整体设计

项目没有把某一个 PDF 库的输出直接暴露给 Java，而是统一转成内部结构：

```text
RawParseOutput
  parser_name
  blocks: list[PdfBlock]
  markdown
  page_count
  warnings
  metadata
```

每个具体解析器都遵守同一个输入输出约定：

```text
输入:
  pdf_path: Path
  task_id: str
  work_dir: Path

输出:
  RawParseOutput
```

然后 `HybridPdfParser` 负责把多个解析器结果合并。

## 2. `base.py`

### 2.1 `RawParseOutput`

```python
@dataclass(slots=True)
class RawParseOutput:
    parser_name: str
    blocks: list[PdfBlock] = field(default_factory=list)
    markdown: str | None = None
    page_count: int | None = None
    warnings: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)
```

这是解析器输出的中间对象。

字段说明：

- `parser_name`：解析器名称，如 `mineru`、`unstructured`、`pypdf`。
- `blocks`：解析出来的结构化块。
- `markdown`：解析器直接产出的 Markdown，可选。
- `page_count`：页数，可选。
- `warnings`：非致命警告。
- `metadata`：额外信息，比如 MinerU 输出目录。

Python 写法解释：

- `@dataclass` 自动生成构造函数、repr 等方法。
- `slots=True` 减少对象内存占用，也限制动态新增属性。
- `field(default_factory=list)` 避免多个对象共享同一个默认 list。

Java 类比：

```java
public record RawParseOutput(
    String parserName,
    List<PdfBlock> blocks,
    String markdown,
    Integer pageCount,
    List<String> warnings,
    Map<String, Object> metadata
) {}
```

### 2.2 `PdfParser` Protocol

```python
class PdfParser(Protocol):
    name: str

    def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
        ...
```

`Protocol` 是 Python 的结构化接口。只要一个类有：

- `name` 属性。
- `parse(...)` 方法，签名匹配。

它就可以被认为是 `PdfParser`。

Java 类比：

```java
public interface PdfParser {
    String name();
    RawParseOutput parse(Path pdfPath, String taskId, Path workDir);
}
```

区别是 Python 不强制类显式 `implements`。

## 3. `HybridPdfParser`

### 3.1 职责

文件：

```text
ai_translation_py/parsers/hybrid_pdf_parser.py
```

它是解析器调度器，负责：

- 按配置尝试 MinerU。
- 按配置尝试 Unstructured。
- 在没有任何文本结果时尝试 pypdf 兜底。
- 尝试 PyMuPDF 图片提取。
- 合并所有 block。
- 合并 warnings。
- 计算 page_count。
- 调用 `BlockNormalizer`。
- 没有可用 block 时抛出整体失败。

### 3.2 调用关系

谁调用它：

```text
PdfParseService.run_parse_task
  -> HybridPdfParser.parse
```

它调用谁：

```text
HybridPdfParser.parse
  -> MineruParser.parse
  -> UnstructuredParser.parse
  -> PypdfTextParser.parse
  -> PdfImageAssetParser.parse
  -> BlockNormalizer.normalize
```

### 3.3 策略名称

```python
strategy_name = "mineru_unstructured_hybrid"
```

这个值最后进入 `PdfParseResult.parserStrategy`，Java 和前端可以看到当前使用的解析策略。

### 3.4 核心方法：`parse`

```python
def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
    outputs: list[RawParseOutput] = []
    warnings: list[str] = []
```

`outputs` 收集成功解析器的结果。

`warnings` 收集非致命失败和提示。

#### MinerU

```python
if self.settings.enable_mineru:
    output, warning = self._try_parse(MineruParser(self.settings), pdf_path, task_id=task_id, work_dir=work_dir)
    if output:
        outputs.append(output)
    else:
        warnings.append(warning or "MinerU parse failed or is unavailable.")
```

如果配置启用 MinerU：

- 尝试运行 MinerU。
- 成功则加入 `outputs`。
- 失败则只记录 warning。

#### Unstructured

```python
if self.settings.enable_unstructured:
    output, warning = self._try_parse(UnstructuredParser(), pdf_path, task_id=task_id, work_dir=work_dir)
    if output:
        outputs.append(output)
    else:
        warnings.append(warning or "Unstructured parse failed or is unavailable.")
```

Unstructured 是补充和降级解析器。

#### pypdf 兜底

```python
if self.settings.enable_pypdf_fallback and not any(output.blocks for output in outputs):
    output, warning = self._try_parse(PypdfTextParser(), ...)
```

注意条件：

```python
not any(output.blocks for output in outputs)
```

只有当前没有任何解析器产出 block，才跑 pypdf。也就是说：

- MinerU 或 Unstructured 有结果时，不跑 pypdf。
- pypdf 是兜底，不是默认叠加。

#### 图片提取

```python
image_output, image_warning = self._try_parse(PdfImageAssetParser(), ...)
if image_output:
    outputs.append(image_output)
elif image_warning:
    warnings.append(image_warning)
```

图片提取独立于文本解析。即使 MinerU 和 Unstructured 已经成功，也会尝试提取内嵌图片。

### 3.5 合并输出

```python
all_blocks: list[PdfBlock] = []
markdown_parts: list[str] = []
page_count: int | None = None
for output in outputs:
    all_blocks.extend(output.blocks)
    warnings.extend(output.warnings)
    if output.markdown:
        markdown_parts.append(output.markdown)
    if output.page_count is not None:
        page_count = max(page_count or output.page_count, output.page_count)
```

逐项解释：

- `all_blocks.extend(output.blocks)`：把多个解析器 block 拼到一起。
- `warnings.extend(output.warnings)`：收集解析器 warning。
- `markdown_parts`：保留解析器产出的 Markdown。
- `page_count`：取最大页数。

Java 类比：

```java
List<PdfBlock> allBlocks = new ArrayList<>();
for (RawParseOutput output : outputs) {
    allBlocks.addAll(output.blocks());
}
```

### 3.6 标准化

```python
normalized_blocks = self.normalizer.normalize(all_blocks)
if not normalized_blocks:
    raise AiTranslationPyError(...)
```

合并后一定要标准化。否则不同解析器的排序、ID、去重规则都不一致。

如果标准化后没有任何 block，整个解析任务失败。

### 3.7 `_try_parse`

```python
@staticmethod
def _try_parse(
    parser: object,
    pdf_path: Path,
    *,
    task_id: str,
    work_dir: Path,
) -> tuple[RawParseOutput | None, str | None]:
    try:
        return parser.parse(pdf_path, task_id=task_id, work_dir=work_dir), None
    except AiTranslationPyError as exc:
        detail = f": {exc.detail}" if exc.detail else ""
        return None, f"{exc.error_code} {exc.message}{detail}"
    except Exception as exc:
        return None, f"{parser.__class__.__name__} failed: {exc}"
```

这是混合策略的关键。

它把：

- 成功解析器结果返回为 `(output, None)`。
- 业务异常返回为 `(None, warning)`。
- 未知异常也返回为 `(None, warning)`。

这样单个解析器失败不会中断整体流程。

## 4. `MineruParser`

### 4.1 职责

文件：

```text
ai_translation_py/parsers/mineru_parser.py
```

它负责调用 MinerU CLI，并把 MinerU 输出转成 `RawParseOutput`。

MinerU 是主解析器，目标是处理复杂 PDF：

- 复杂版面。
- OCR。
- 表格。
- 公式。
- 图片。
- Markdown 输出。

### 4.2 调用 MinerU CLI

```python
mineru_bin = shutil.which("mineru")
if not mineru_bin:
    raise ParserUnavailableError(...)
```

`shutil.which("mineru")` 会在 PATH 中找 `mineru` 命令。

找不到说明 MinerU 没装或没进 PATH。

### 4.3 构建命令

```python
output_dir = work_dir / "mineru"
output_dir.mkdir(parents=True, exist_ok=True)
cmd = [
    mineru_bin,
    "-p",
    str(pdf_path),
    "-o",
    str(output_dir),
    "-b",
    self.settings.mineru_backend,
]
```

最终命令类似：

```bash
mineru -p data/uploads/{taskId}/source.pdf -o data/work/{taskId}/mineru -b pipeline
```

`mineru_backend` 默认是 `pipeline`。

### 4.4 执行子进程

```python
completed = subprocess.run(
    cmd,
    capture_output=True,
    text=True,
    timeout=self.settings.mineru_timeout_seconds,
    check=False,
)
```

解释：

- `capture_output=True`：捕获 stdout/stderr。
- `text=True`：输出按字符串处理，不是 bytes。
- `timeout=...`：超时终止。
- `check=False`：命令失败时不自动抛异常，由代码检查 returncode。

失败处理：

```python
if completed.returncode != 0:
    raise ParserUnavailableError(
        ErrorCode.PDF_MINERU_FAILED,
        "MinerU parse failed",
        detail=(completed.stderr or completed.stdout or "").strip(),
    )
```

### 4.5 读取 MinerU 输出

```python
markdown = self._read_first_file(output_dir, "*.md")
json_data = self._read_first_json(output_dir)
blocks = self._blocks_from_mineru_json(json_data, parser_name=self.name)
if not blocks and markdown:
    blocks = self._blocks_from_markdown(markdown, parser_name=self.name)
```

优先级：

1. 找 JSON。
2. 从 JSON 转 blocks。
3. 如果 JSON 没转出 block，但有 Markdown，则从 Markdown 简单转 blocks。

这种写法是为了适配 MinerU 输出格式可能变化。

### 4.6 `_blocks_from_mineru_json`

```python
def _blocks_from_mineru_json(data: dict | list | None, *, parser_name: str) -> list[PdfBlock]:
    raw_blocks: list[dict] = []
    if isinstance(data, dict):
        for key in ("blocks", "elements", "pdf_info", "pages"):
            value = data.get(key)
            if isinstance(value, list):
                raw_blocks.extend(item for item in value if isinstance(item, dict))
    elif isinstance(data, list):
        raw_blocks.extend(item for item in data if isinstance(item, dict))
```

它会尝试多个可能字段名：

```text
blocks
elements
pdf_info
pages
```

目的是兼容不同 MinerU JSON 结构。

然后每个 dict 转成 `PdfBlock`：

```python
PdfBlock(
    blockId=f"mineru_raw_{index:06d}",
    pageNo=_first_int(item, ("page_no", "pageNo", "page", "page_idx"), default=1),
    orderNo=index,
    type=block_type,
    text=text,
    markdown=markdown,
    bbox=_first_bbox(item),
    confidence=_first_float(item, ("confidence", "score")),
    sourceParser=parser_name,
    metadata={"rawType": _first_string(item, ("type", "category", "block_type"))},
)
```

这里先生成临时 blockId，例如：

```text
mineru_raw_000001
```

后面 `BlockNormalizer` 会统一改成：

```text
block_000001
```

### 4.7 类型映射

```python
def _map_mineru_type(raw_type: str | None) -> str:
    if not raw_type:
        return "paragraph"
    normalized = raw_type.lower()
    if "title" in normalized:
        return "title"
    if "header" in normalized:
        return "header"
    if "footer" in normalized:
        return "footer"
    if "table" in normalized:
        return "table"
    if "image" in normalized or "figure" in normalized:
        return "figure"
    if "formula" in normalized or "equation" in normalized:
        return "formula"
    if "list" in normalized:
        return "list_item"
    return "paragraph"
```

作用是把 MinerU 原始类型归一到项目内部类型。

## 5. `UnstructuredParser`

### 5.1 职责

文件：

```text
ai_translation_py/parsers/unstructured_parser.py
```

它负责调用 Unstructured：

```python
from unstructured.partition.pdf import partition_pdf
elements = partition_pdf(filename=str(pdf_path), strategy="auto")
```

Unstructured 会返回一组元素，每个元素可能是标题、段落、表格、列表等。

### 5.2 解析流程

```python
blocks: list[PdfBlock] = []
max_page: int | None = None
for index, element in enumerate(elements, start=1):
    metadata = getattr(element, "metadata", None)
    page_no = int(getattr(metadata, "page_number", 1) or 1)
    max_page = max(max_page or page_no, page_no)
    text = str(element).strip()
    category = getattr(element, "category", element.__class__.__name__)
    block_type = _map_unstructured_type(str(category))
    markdown = _element_markdown(element, block_type)
    if not text and not markdown:
        continue
    blocks.append(PdfBlock(...))
```

几个 Python 写法：

- `getattr(obj, "attr", default)`：安全读取属性，不存在则返回 default。
- `enumerate(elements, start=1)`：遍历时同时给 index，从 1 开始。
- `element.__class__.__name__`：拿对象类名。

### 5.3 类型映射

```python
def _map_unstructured_type(category: str) -> str:
    normalized = category.lower()
    if normalized in {"title"}:
        return "heading"
    if "header" in normalized:
        return "header"
    if "footer" in normalized:
        return "footer"
    if "list" in normalized:
        return "list_item"
    if "table" in normalized:
        return "table"
    if "figure" in normalized or "image" in normalized:
        return "figure"
    if "formula" in normalized or "equation" in normalized:
        return "formula"
    if "narrative" in normalized or "text" in normalized:
        return "paragraph"
    return "unknown"
```

注意 Unstructured 的 `Title` 被映射成 `heading`，不是 `title`。原因是 PDF 第一个主标题更适合由其他逻辑或 MinerU 判定，Unstructured 的 Title 往往代表各种标题元素。

### 5.4 表格 Markdown/HTML

```python
def _element_markdown(element: object, block_type: str) -> str | None:
    metadata = getattr(element, "metadata", None)
    html = getattr(metadata, "text_as_html", None)
    if block_type == "table" and isinstance(html, str) and html.strip():
        return html.strip()
```

如果 Unstructured 的表格元素带 `text_as_html`，项目会优先保留 HTML 表格。

### 5.5 bbox 坐标

```python
def _element_bbox(metadata: object | None) -> list[float] | None:
    coordinates = getattr(metadata, "coordinates", None)
    points = getattr(coordinates, "points", None)
    if not points:
        return None
    xs = [float(point[0]) for point in points if len(point) >= 2]
    ys = [float(point[1]) for point in points if len(point) >= 2]
    if not xs or not ys:
        return None
    return [min(xs), min(ys), max(xs), max(ys)]
```

Unstructured 坐标通常是多边形点。这里转成统一 bbox：

```text
[x0, y0, x1, y1]
```

后面排序会用 bbox 的 y/x 坐标。

## 6. `PypdfTextParser`

### 6.1 职责

文件：

```text
ai_translation_py/parsers/text_pdf_parser.py
```

它是轻量文本兜底解析器。只适合可复制文本 PDF，不支持 OCR、复杂表格、图片、公式。

### 6.2 解析流程

```python
from pypdf import PdfReader
reader = PdfReader(str(pdf_path))

blocks: list[PdfBlock] = []
order = 1
for page_index, page in enumerate(reader.pages, start=1):
    text = page.extract_text() or ""
    for chunk in _split_text_blocks(text):
        block_type = _guess_block_type(chunk, page_index=page_index, order=order)
        blocks.append(PdfBlock(...))
        order += 1
```

逻辑：

- `PdfReader` 读取 PDF。
- 遍历每一页。
- `page.extract_text()` 抽文本。
- `_split_text_blocks` 按空行拆块。
- `_guess_block_type` 粗略猜 title/heading/list/paragraph。

### 6.3 文本拆块

```python
def _split_text_blocks(text: str) -> list[str]:
    lines = [line.strip() for line in text.replace("\r\n", "\n").split("\n")]
    chunks: list[str] = []
    current: list[str] = []
    for line in lines:
        if not line:
            if current:
                chunks.append(" ".join(current).strip())
                current = []
            continue
        current.append(line)
    if current:
        chunks.append(" ".join(current).strip())
    return [chunk for chunk in chunks if chunk]
```

思路：

- 换行统一成 `\n`。
- 空行表示一个块结束。
- 同一块里的多行用空格拼起来。
- 最后过滤空字符串。

### 6.4 类型猜测

```python
def _guess_block_type(text: str, *, page_index: int, order: int) -> str:
    if page_index == 1 and order == 1 and len(text) <= 120:
        return "title"
    if text[:2] in {"- ", "* "} or text[:3].startswith(("1.", "2.", "3.")):
        return "list_item"
    if len(text) <= 80 and not text.endswith((".", ",", ";", ":")):
        return "heading"
    return "paragraph"
```

这是启发式规则：

- 第一页第一个短块当作标题。
- `- `、`* `、`1.`、`2.`、`3.` 开头像列表。
- 短文本且没有句末标点，当作标题。
- 其他当作段落。

这不是准确版面识别，只是本地兜底。

### 6.5 warning

```python
warnings=["Used pypdf text fallback; OCR, tables, images, and formulas may be incomplete."]
```

pypdf 兜底成功也会带 warning，提醒结果可能不完整。

## 7. `PdfImageAssetParser`

### 7.1 职责

文件：

```text
ai_translation_py/parsers/image_asset_parser.py
```

它使用 PyMuPDF，也就是代码中的 `fitz`，提取 PDF 内嵌图片并生成 `figure` block。

注意：

- 它只负责图片资产。
- 不负责文本。
- 它失败时返回 warning，不让整个解析失败。

### 7.2 导入 PyMuPDF

```python
try:
    import fitz
except Exception as exc:
    return RawParseOutput(
        parser_name=self.name,
        warnings=[f"PyMuPDF is not available for image extraction: {exc}"],
    )
```

依赖包名是 `PyMuPDF`，导入名是 `fitz`。

如果没装，直接返回 warning。

### 7.3 图片资产目录

```python
data_dir = work_dir.parent.parent
asset_dir = data_dir / "assets" / task_id / "images"
asset_dir.mkdir(parents=True, exist_ok=True)
```

假设：

```text
work_dir = data/work/{taskId}
```

那么：

```text
work_dir.parent.parent = data
asset_dir = data/assets/{taskId}/images
```

这个写法依赖当前目录结构。以后如果 `LocalStorage.work_dir` 改了，这里也要注意。

### 7.4 打开 PDF 并遍历图片

```python
document = fitz.open(pdf_path)
...
for page_index, page in enumerate(document, start=1):
    for image_index, image_info in enumerate(page.get_images(full=True), start=1):
        xref = image_info[0]
        extracted = document.extract_image(xref)
        image_bytes = extracted.get("image")
        extension = _normalize_extension(extracted.get("ext"))
```

概念：

- `xref` 是 PDF 内部对象引用 ID。
- `page.get_images(full=True)` 返回当前页引用的图片。
- `document.extract_image(xref)` 提取图片字节和元数据。

### 7.5 生成 assetId 和文件

```python
asset_id = f"img_p{page_index:04d}_{image_index:04d}_{xref}"
asset_path = asset_dir / f"{asset_id}{extension}"
asset_path.write_bytes(image_bytes)
```

示例：

```text
img_p0002_0001_35.png
```

含义：

- 第 2 页。
- 当前页第 1 张图片。
- PDF xref 是 35。

### 7.6 生成 figure block

```python
blocks.append(
    PdfBlock(
        blockId=f"figure_raw_{order:06d}",
        pageNo=page_index,
        orderNo=order,
        type="figure",
        text=f"[image:{asset_id}]",
        markdown=f"![{asset_id}]({asset_path.as_posix()})",
        bbox=_image_bbox(page, xref),
        sourceParser=self.name,
        metadata={
            "assetId": asset_id,
            "assetPath": str(asset_path),
            "mimeType": _mime_type(extension),
            "width": extracted.get("width"),
            "height": extracted.get("height"),
            "xref": xref,
        },
    )
)
```

重要 metadata：

- `assetId`：Java 下载图片时用。
- `assetPath`：Python 本地路径。
- `mimeType`：图片类型。
- `width/height`：尺寸。
- `xref`：PDF 内部引用。

### 7.7 bbox

```python
def _image_bbox(page, xref: int) -> list[float] | None:
    rects = page.get_image_rects(xref)
    if not rects:
        return None
    rect = rects[0]
    return [float(rect.x0), float(rect.y0), float(rect.x1), float(rect.y1)]
```

如果能拿到图片在页面上的矩形位置，就返回 `[x0, y0, x1, y1]`。

## 8. 解析器结果合并的核心规则

### 8.1 文本解析器优先级

当前 `BlockNormalizer` 中：

```python
PARSER_PRIORITY = {"mineru": 0, "unstructured": 1, "pypdf": 2}
```

重复文本时优先保留：

```text
MinerU > Unstructured > pypdf
```

### 8.2 结构化块优先

```python
STRUCTURED_TYPES = {"table", "figure", "formula"}
```

表格、图片、公式即使文本为空，也会被保留，因为它们可能依靠 markdown 或 metadata 表达内容。

### 8.3 图片提取不会阻断文本

`PdfImageAssetParser` 即使失败也只产生 warning。

这意味着：

- 文本解析成功，图片失败，任务仍成功。
- 图片解析成功，文本解析失败，如果只有图片 block，也可能形成可用结果。

## 9. 什么时候会整体失败

在 `HybridPdfParser.parse` 最后：

```python
if not normalized_blocks:
    raise AiTranslationPyError(
        ErrorCode.PDF_PARSE_FAILED,
        "PDF parse failed",
        detail="MinerU, Unstructured, and fallback parser produced no usable blocks.",
        status_code=500,
    )
```

也就是说，单个解析器失败不是问题。只有所有解析器最终都没有产出可用 block，才算整个 PDF 解析失败。

## 10. 新增一个解析器应该怎么做

如果以后想加一个新解析器，比如 `PdfPlumberParser`，建议步骤：

1. 新增文件：

   ```text
   ai_translation_py/parsers/pdfplumber_parser.py
   ```

2. 实现同样接口：

   ```python
   class PdfPlumberParser:
       name = "pdfplumber"

       def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
           ...
           return RawParseOutput(parser_name=self.name, blocks=blocks, page_count=page_count)
   ```

3. 每个内容块转成 `PdfBlock`。

4. 在 `HybridPdfParser.parse` 中接入。

5. 在 `BlockNormalizer.PARSER_PRIORITY` 中增加优先级。

6. 添加单元测试，至少覆盖：

   - block 类型映射。
   - bbox 转换。
   - 解析器失败时 warning 行为。

