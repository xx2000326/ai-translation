# ai-translation-py PDF 解析服务 PRD

## 1. 文档信息

- 文档名称：ai-translation-py PDF 解析服务 PRD
- 服务名称：ai-translation-py
- 当前功能范围：PDF 文档解析
- 文档类型：产品需求文档（PRD）
- 版本：v1.0
- 日期：2026-06-21

## 2. 背景

当前项目以 AI 翻译为核心，但后续会逐步引入文档解析、内容结构化、预处理、质量检查等 Python 侧能力。为避免把 Python 能力做成单一脚本，本次需要先规划一个独立 Python 服务 `ai-translation-py`，作为后续 Python 功能的统一承载入口。

PDF 文档结构复杂，可能包含扫描件、可复制文本、表格、图片、公式、版面分栏、页眉页脚、目录、脚注等内容。单一解析器很难稳定覆盖所有场景，因此本期 PDF 解析采用 `MinerU + Unstructured` 的混合方案：

- MinerU：优先承担 PDF 版面分析、OCR、表格/公式/图片等复杂内容解析。
- Unstructured：承担通用文档元素抽取、文本块归一化、补充解析与降级兜底。

本 PRD 只定义 PDF 解析能力，不包含 DOCX、HTML、TXT、翻译、摘要、向量化、知识库入库等后续功能。

## 3. 产品目标

1. 建立一个可扩展的 Python 服务骨架，为后续 Python 能力预留统一入口。
2. 提供稳定的 PDF 上传、解析、状态查询、结果获取能力。
3. 输出面向后续翻译流程可消费的结构化内容，而不是只返回纯文本。
4. 通过 MinerU 与 Unstructured 混合解析，提高对复杂 PDF 的覆盖率。
5. 使用 `uv` 风格管理 Python 环境与依赖安装流程，同时以 `requirements.txt` 作为依赖版本控制文件。

## 4. 非目标

本期不做以下能力：

- 不做 PDF 翻译。
- 不做 DOCX、HTML、TXT 等其他文件格式解析。
- 不做向量化、RAG 入库、术语匹配。
- 不做前端页面设计。
- 不做人工校对工作台。
- 不做文件长期归档和对象存储治理。
- 不做复杂权限、租户、计费、审计系统。

## 5. 用户与使用场景

### 5.1 使用方

- Java/Spring Boot 主服务：调用 Python 服务完成 PDF 解析。
- 后续 AI 翻译流程：消费解析后的段落、标题、表格、图片占位等结构化内容。
- 开发人员：通过 API 或命令行调试 PDF 解析结果。

### 5.2 核心场景

1. 用户上传 PDF 到主服务。
2. 主服务将 PDF 文件传给 ai-translation-py。
3. ai-translation-py 创建解析任务并异步执行。
4. 解析完成后，主服务查询任务状态并拉取结构化结果。
5. 主服务将解析结果转换为翻译任务段落。

## 6. 功能需求

### 6.1 PDF 解析任务创建

系统应支持通过 API 创建 PDF 解析任务。

输入：

- PDF 文件。
- 文件名。
- 可选解析参数。

输出：

- taskId。
- 初始任务状态。

约束：

- 仅接受 `.pdf` 文件。
- 文件大小上限默认 200 MB，可配置。
- 不合法文件应返回明确错误码。

### 6.2 异步解析

PDF 解析可能耗时较长，系统应采用异步任务模型。

任务状态：

- `PENDING`：任务已创建，等待执行。
- `RUNNING`：任务解析中。
- `SUCCEEDED`：任务解析成功。
- `FAILED`：任务解析失败。
- `CANCELED`：任务被取消，预留状态。

### 6.3 混合解析策略

系统应支持 MinerU 与 Unstructured 混合解析。

默认策略：

1. 使用 MinerU 执行主解析，获取 Markdown、布局元素、OCR 文本、表格、图片等结果。
2. 使用 Unstructured 对 PDF 或 MinerU 输出内容进行元素归一化与补充解析。
3. 对两类结果进行合并、去重、排序和标准化。
4. 当 MinerU 失败时，允许 Unstructured 作为降级解析器。
5. 当 Unstructured 失败时，只要 MinerU 结果可用，任务仍可成功，但需要记录 warning。

### 6.4 解析结果结构化

解析结果应包含文档级、页级、块级信息。

文档级结果：

- taskId。
- fileName。
- pageCount。
- parserStrategy。
- status。
- warnings。
- createdAt。
- completedAt。

块级结果：

- blockId。
- pageNo。
- orderNo。
- type。
- text。
- markdown。
- bbox。
- confidence。
- sourceParser。
- metadata。

块类型至少包括：

- `title`
- `heading`
- `paragraph`
- `list_item`
- `table`
- `figure`
- `formula`
- `header`
- `footer`
- `unknown`

### 6.5 表格解析

系统应尽量保留表格结构。

表格输出优先级：

1. Markdown table。
2. HTML table。
3. CSV-like text。
4. 普通文本兜底。

### 6.6 图片与公式处理

本期不要求图片和公式内容可编辑，但应保留占位和元数据。

要求：

- 图片块保留页码、顺序、bbox、资源路径或资源 key。
- 公式块保留文本化结果或 LaTeX 结果；无法识别时保留占位。
- 解析结果中不得静默丢弃图片和公式。

### 6.7 页眉页脚处理

系统应识别页眉、页脚，并在输出中保留类型标记。

是否参与后续翻译由调用方决定，本服务只负责解析和标注。

### 6.8 任务状态查询

系统应支持查询任务状态。

返回内容：

- taskId。
- status。
- progress。
- currentStep。
- errorCode。
- errorMessage。
- warnings。
- createdAt。
- updatedAt。

### 6.9 结果获取

系统应支持获取完整解析结果。

返回格式：

- JSON：主格式，供系统消费。
- Markdown：可选格式，供人工查看与调试。

### 6.10 本地调试命令

系统应提供命令行调试入口，方便开发人员在不启动 API 服务时验证解析效果。

示例：

```bash
uv run ai-translation-py parse-pdf ./sample.pdf --output ./output
```

## 7. 依赖与版本管理要求

ai-translation-py 必须采用 `uv` 风格进行 Python 环境和依赖安装。

依赖版本控制采用 `requirements.txt`：

- `requirements.txt` 是运行时依赖版本的唯一提交文件。
- 安装依赖使用 `uv pip install -r requirements.txt`。
- 本地运行使用 `uv run`。
- 后续如需要区分开发依赖，可新增 `requirements-dev.txt`。
- 不在 PRD 中固定具体版本号，具体版本以仓库中的 `requirements.txt` 为准。

建议命令：

```bash
uv venv
uv pip install -r requirements.txt
uv run ai-translation-py api
uv run ai-translation-py parse-pdf ./sample.pdf --output ./output
```

## 8. API 需求

### 8.1 创建解析任务

`POST /api/v1/pdf/parse-tasks`

请求：

- `multipart/form-data`
- file：PDF 文件
- options：可选 JSON 字符串

响应：

```json
{
  "taskId": "pdf_20260621_000001",
  "status": "PENDING"
}
```

### 8.2 查询任务状态

`GET /api/v1/pdf/parse-tasks/{taskId}`

响应：

```json
{
  "taskId": "pdf_20260621_000001",
  "status": "RUNNING",
  "progress": 45,
  "currentStep": "mineru_parse",
  "warnings": []
}
```

### 8.3 获取 JSON 结果

`GET /api/v1/pdf/parse-tasks/{taskId}/result`

响应：

```json
{
  "taskId": "pdf_20260621_000001",
  "fileName": "sample.pdf",
  "pageCount": 12,
  "parserStrategy": "mineru_unstructured_hybrid",
  "blocks": []
}
```

### 8.4 获取 Markdown 结果

`GET /api/v1/pdf/parse-tasks/{taskId}/result.md`

响应：

- `text/markdown`

## 9. 配置需求

服务应支持通过环境变量或配置文件配置：

- 服务端口。
- 上传文件大小限制。
- 临时文件目录。
- 解析结果目录。
- MinerU 是否启用。
- Unstructured 是否启用。
- OCR 语言。
- 解析任务并发数。
- 任务结果保留时间。

## 10. 错误处理

错误码：

- `PDF_INVALID_TYPE`：文件类型不是 PDF。
- `PDF_FILE_TOO_LARGE`：文件超过大小限制。
- `PDF_PARSE_FAILED`：解析失败。
- `PDF_MINERU_FAILED`：MinerU 解析失败。
- `PDF_UNSTRUCTURED_FAILED`：Unstructured 解析失败。
- `PDF_RESULT_NOT_READY`：结果尚未生成。
- `PDF_TASK_NOT_FOUND`：任务不存在。

错误响应示例：

```json
{
  "errorCode": "PDF_PARSE_FAILED",
  "message": "PDF parse failed",
  "detail": "MinerU and Unstructured both failed"
}
```

## 11. 验收标准

1. 可以通过 API 上传 PDF 并创建解析任务。
2. 可以查询解析任务状态和进度。
3. 可以获取 JSON 格式结构化解析结果。
4. 可以获取 Markdown 调试结果。
5. 对文本型 PDF 能输出标题、段落、列表、表格等基础结构。
6. 对扫描型 PDF 能通过 OCR 输出可用文本。
7. MinerU 或 Unstructured 单个解析器失败时，系统能按策略降级或给出明确失败原因。
8. 依赖安装与运行方式符合 `uv + requirements.txt` 约定。

## 12. 后续扩展方向

后续可在 `ai-translation-py` 服务下新增独立 PRD：

- DOCX 解析。
- HTML 解析。
- TXT 解析。
- 文档清洗与段落切分。
- 文档摘要。
- 文档质量检测。
- 表格结构优化。
- 图片 OCR 独立服务。
- 与 AI 翻译任务的深度集成。
