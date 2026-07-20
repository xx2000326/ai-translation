# ai-translation-py PDF 解析服务操作文档

这份文档面向 Python 不太熟的开发者，说明如何安装、启动、调用和排查 `ai-translation-py`。服务当前只做 PDF 解析，不做翻译。

## 1. 目录位置

Python 服务在仓库子目录：

```text
ai-translation-py/
  ai_translation_py/        # Python 源码包
  tests/                    # 测试
  pyproject.toml            # uv/包入口配置
  requirements.txt          # 运行依赖
  requirements-dev.txt      # 测试依赖
```

进入服务目录：

```bash
cd ai-translation-py
```

## 2. 安装 uv 环境

本服务必须用 `uv` 管理环境。先确认本机能找到 uv：

```bash
uv --version
```

创建虚拟环境：

```bash
uv venv
```

安装运行依赖：

```bash
uv pip install -r requirements.txt
```

安装当前项目本身，让 `ai-translation-py` 命令可用：

```bash
uv pip install -e .
```

如需跑测试，再安装开发依赖：

```bash
uv pip install -r requirements-dev.txt
```

说明：

- `.venv/` 是 uv 创建的本地 Python 环境，不需要提交。
- `requirements.txt` 是当前运行依赖来源。
- `pyproject.toml` 负责声明命令入口 `ai-translation-py`。
- `uv pip install -e .` 的意思是“以可编辑模式安装当前项目”，源码改动后通常不需要重新安装。

## 3. 启动 API 服务

默认启动：

```bash
uv run ai-translation-py api
```

如果你还没安装命令入口，也可以用模块方式启动：

```bash
uv run python -m ai_translation_py.main api
```

默认地址：

```text
http://127.0.0.1:8010
```

健康检查：

```bash
curl http://127.0.0.1:8010/health
```

指定端口：

```bash
uv run ai-translation-py api --host 0.0.0.0 --port 8010
```

## 4. 命令行解析 PDF

不启动 API 时，可以直接解析本地 PDF：

```bash
uv run ai-translation-py parse-pdf ./sample.pdf --output ./output
```

模块方式等价命令：

```bash
uv run python -m ai_translation_py.main parse-pdf ./sample.pdf --output ./output
```

执行完成后会生成：

```text
output/result.json
output/result.md
```

同时，服务内部也会在 `data/` 下保存任务文件：

```text
data/
  uploads/{taskId}/source.pdf
  results/{taskId}/result.json
  results/{taskId}/result.md
  tasks/{taskId}.json
  work/{taskId}/
```

## 5. API 调用流程

### 5.1 创建解析任务

```bash
curl -X POST "http://127.0.0.1:8010/api/v1/pdf/parse-tasks" \
  -F "file=@./sample.pdf"
```

返回示例：

```json
{
  "taskId": "pdf_20260621_153000_ab12cd34",
  "status": "PENDING"
}
```

### 5.2 查询任务状态

```bash
curl "http://127.0.0.1:8010/api/v1/pdf/parse-tasks/pdf_20260621_153000_ab12cd34"
```

可能状态：

- `PENDING`：任务已创建，等待解析。
- `RUNNING`：正在解析。
- `SUCCEEDED`：解析成功。
- `FAILED`：解析失败。
- `CANCELED`：预留状态，当前还没有取消接口。

### 5.3 获取 JSON 结果

任务成功后：

```bash
curl "http://127.0.0.1:8010/api/v1/pdf/parse-tasks/pdf_20260621_153000_ab12cd34/result"
```

JSON 里最重要的是 `blocks`：

```json
{
  "blockId": "block_000001",
  "pageNo": 1,
  "orderNo": 1,
  "type": "paragraph",
  "text": "正文内容",
  "markdown": "正文内容",
  "bbox": null,
  "confidence": null,
  "sourceParser": "pypdf",
  "metadata": {}
}
```

如果 PDF 内有图片，结果里会额外出现 `figure` block。图片二进制不会塞进 JSON，
而是保存为资产文件，Java 后端会根据 `assetId` 再调用 asset 接口下载：

```json
{
  "blockId": "block_000012",
  "pageNo": 2,
  "orderNo": 12,
  "type": "figure",
  "text": "[image:img_p0002_0001_35]",
  "bbox": [72.0, 180.0, 420.0, 360.0],
  "sourceParser": "pymupdf_image",
  "metadata": {
    "assetId": "img_p0002_0001_35",
    "mimeType": "image/png",
    "width": 960,
    "height": 540,
    "assetPath": "data/assets/{taskId}/images/img_p0002_0001_35.png"
  }
}
```

为了适配 Java 后端当前的拆分引擎，结果里还会返回一个 `plainText` 字段。
Java 会优先使用它继续走原来的清洗、分块和落库流程；`blocks` 仍然是后续做更精细结构化集成时的主数据。

### 5.4 获取 Markdown 结果

```bash
curl "http://127.0.0.1:8010/api/v1/pdf/parse-tasks/pdf_20260621_153000_ab12cd34/result.md"
```

Markdown 主要用于人工查看和调试，不建议 Java 主服务依赖 Markdown 做业务处理。

### 5.5 获取图片资产

`figure` block 的 `metadata.assetId` 可以用来下载原图：

```bash
curl -o image.png "http://127.0.0.1:8010/api/v1/pdf/parse-tasks/pdf_20260621_153000_ab12cd34/assets/img_p0002_0001_35"
```

注意：

- 图片提取使用 PyMuPDF，依赖名是 `PyMuPDF`，代码里导入名是 `fitz`。
- 图片提取失败只会进入任务 warnings，不会让整个 PDF 解析失败。
- `plainText` 不包含图片二进制，只包含 `[PDF image: assetId]` 这种占位文本，方便 Java 继续拆分和落库。

## 6. Java 后端集成方式

Java 后端的 `/api/tasks/{id}/parse` 仍然只负责触发异步解析，实际切换点在 `TranslationPipeline.parseAsync`。

PDF 文件解析流程现在是：

1. Java 后端从文件存储下载原始 PDF。
2. 如果任务文件类型是 `PDF` 且 `PYTHON_PDF_PARSE_ENABLED=true`，先调用 Python 服务。
3. Java 最多完整重试 Python 解析 3 次，默认由 `PYTHON_PDF_PARSE_MAX_ATTEMPTS=3` 控制。
4. 3 次都失败后，Java 记录错误日志。
5. Java 自动切回原本的 PDFBox/Spring AI PDF 读取器继续解析。
6. 如果 Python 解析成功且结果里有 `figure` block，Java 会下载图片资产，保存到现有 `FileStorageService`，并写入 `translation_image`。
7. 图片翻译是独立步骤：单张图片失败只更新该图片状态为 `FAILED`，不会触发 Java PDF 解析兜底，也不会阻塞文本解析完成。

因此 Python 服务不可用时，用户不会因为 Python 链路失败而直接失去原 Java 解析能力，只是解析效果退回旧逻辑。

Java 侧相关配置：

```yaml
app:
  python:
    pdf-parse:
      enabled: true
      base-url: http://127.0.0.1:8010
      max-attempts: 3
      poll-timeout: 5m
      poll-interval: 2s
  ai:
    image-translation:
      base-url: http://127.0.0.1:8000
      api-key:
      model: image-translation-model
      endpoint-path: /v1/images/edits
      timeout: 2m
```

## 7. 解析器策略

当前按下面顺序解析：

1. `MinerU`：主解析器，目标是复杂版面、OCR、表格、公式、图片。
2. `Unstructured`：补充解析和降级解析。
3. `pypdf`：轻量文本兜底，方便本地先跑通流程。
4. `PyMuPDF`：独立提取内嵌图片，生成 `figure` block 和图片资产文件。

注意：

- `pypdf` 只能抽取可复制文本，不能处理扫描件 OCR。
- 如果 MinerU 或 Unstructured 没装好，任务不会立刻失败，会记录 warning，并尝试后续解析器。
- 如果 PyMuPDF 图片提取失败，只会记录 warning，不影响已有文本解析结果。
- 只有所有可用解析器都没有产出可用块时，任务才会失败。

## 8. 常用配置

配置通过环境变量传入：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_TRANSLATION_PY_HOST` | `127.0.0.1` | API 监听地址 |
| `AI_TRANSLATION_PY_PORT` | `8010` | API 端口 |
| `AI_TRANSLATION_PY_DATA_DIR` | `./data` | 上传文件、结果、任务状态目录 |
| `AI_TRANSLATION_PY_MAX_FILE_MB` | `200` | 最大 PDF 文件大小 |
| `AI_TRANSLATION_PY_WORKERS` | `2` | 后台解析线程数 |
| `AI_TRANSLATION_PY_ENABLE_MINERU` | `true` | 是否启用 MinerU |
| `AI_TRANSLATION_PY_ENABLE_UNSTRUCTURED` | `true` | 是否启用 Unstructured |
| `AI_TRANSLATION_PY_ENABLE_PYPDF_FALLBACK` | `true` | 是否启用 pypdf 文本兜底 |
| `AI_TRANSLATION_PY_OCR_LANG` | `ch,en` | OCR 语言预留配置 |

Windows PowerShell 示例：

```powershell
$env:AI_TRANSLATION_PY_PORT="8010"
$env:AI_TRANSLATION_PY_ENABLE_MINERU="false"
uv run ai-translation-py api
```

Linux/macOS 示例：

```bash
AI_TRANSLATION_PY_PORT=8010 AI_TRANSLATION_PY_ENABLE_MINERU=false uv run ai-translation-py api
```

## 9. 源码阅读顺序

建议按这个顺序看代码：

1. `ai_translation_py/api/routes_pdf.py`：API 路由入口。
2. `ai_translation_py/services/pdf_task_service.py`：创建任务、校验 PDF、提交后台线程。
3. `ai_translation_py/services/pdf_parse_service.py`：执行解析、写结果、更新状态。
4. `ai_translation_py/parsers/hybrid_pdf_parser.py`：MinerU、Unstructured、pypdf 和图片提取的调度策略。
5. `ai_translation_py/parsers/image_asset_parser.py`：用 PyMuPDF 提取图片、保存资产、生成 `figure` block。
6. `ai_translation_py/normalizers/block_normalizer.py`：排序、去重、统一 blockId/orderNo。
7. `ai_translation_py/storage/local_storage.py`：本地 data 目录读写。
8. `ai_translation_py/models/`：API 输入输出的数据结构。

## 10. 运行测试

安装开发依赖后：

```bash
uv run pytest
```

如果只是检查 Python 语法，不想写 `__pycache__`：

```bash
$env:PYTHONDONTWRITEBYTECODE="1"
uv run python -m py_compile ai_translation_py/main.py
```

## 11. 常见问题

### uv run 找不到 ai-translation-py

先确认当前目录是 `ai-translation-py/`，并且已经安装当前项目：

```bash
uv pip install -e .
```

临时绕过方式是使用模块入口：

```bash
uv run python -m ai_translation_py.main --help
```

### MinerU 或 Unstructured 报错

先用本地兜底跑通流程：

```powershell
$env:AI_TRANSLATION_PY_ENABLE_MINERU="false"
$env:AI_TRANSLATION_PY_ENABLE_UNSTRUCTURED="false"
uv run ai-translation-py parse-pdf ./sample.pdf --output ./output
```

如果这样能成功，说明服务框架和 pypdf 文本抽取是通的，问题集中在重型解析依赖或系统组件。

### result 接口返回 PDF_RESULT_NOT_READY

说明任务还不是 `SUCCEEDED`。先查状态接口：

```bash
curl "http://127.0.0.1:8010/api/v1/pdf/parse-tasks/{taskId}"
```

如果状态是 `FAILED`，看返回里的 `errorCode` 和 `errorMessage`。

### data 目录越来越大

当前 Phase 1 还没有做 TTL 清理。开发阶段可以停止服务后删除：

```text
ai-translation-py/data/
```

后续 Phase 2 会补任务取消和结果保留时间清理。
