# ai-translation-py PDF 解析源码导读

这组文档面向熟悉 Java、刚开始读 Python 项目的人。它不是安装手册，安装和 API 调用可以先看上一层的 `python-pdf-service-guide.md`。这里重点解释源码：

- 每个 Python 包负责什么。
- 每个模块从哪里被调用。
- 核心方法怎么串起来。
- Python 写法可以怎样类比 Java。
- PDF 解析结果怎样进入 Java 后端翻译流程。

## 推荐阅读顺序

1. [00-overview-call-chain.md](00-overview-call-chain.md)

   先看整体架构、完整调用链、目录地图、Java 类比。读完这一篇再进源码会轻松很多。

2. [01-entrypoints-config-api.md](01-entrypoints-config-api.md)

   解释 `main.py`、`cli.py`、`config.py`、`api/app.py`、`api/routes_pdf.py`。这是 HTTP 和命令行入口。

3. [02-services-task-flow.md](02-services-task-flow.md)

   解释 `PdfTaskService`、`PdfParseService`、`ResultService`。这是任务创建、异步执行、结果读取的核心服务层。

4. [03-parsers-hybrid-strategy.md](03-parsers-hybrid-strategy.md)

   解释 `HybridPdfParser`、`MineruParser`、`UnstructuredParser`、`PypdfTextParser`、`PdfImageAssetParser`。这是 PDF 解析核心。

5. [04-normalizers-renderers-models-storage.md](04-normalizers-renderers-models-storage.md)

   解释 Pydantic 模型、解析块标准化、Markdown 渲染、本地文件存储、任务 JSON 仓库。

6. [05-java-integration.md](05-java-integration.md)

   解释 Java 后端怎么调用 Python 服务，Python 输出怎样进入 Java 分块、落库、图片翻译流程。

7. [06-python-reading-notes-for-java-developers.md](06-python-reading-notes-for-java-developers.md)

   单独整理 Python 语法和项目写法，按 Java 背景做类比。

## 一句话总览

`ai-translation-py` 是一个独立 Python PDF 解析服务。FastAPI 接收 PDF 上传后创建任务，后台线程执行混合解析策略：

```text
MinerU 主解析
  + Unstructured 补充解析
  + pypdf 文本兜底
  + PyMuPDF 图片资产提取
  -> BlockNormalizer 统一清洗、去重、排序、重编号
  -> result.json / result.md
```

Java 后端上传 PDF 到这个 Python 服务，轮询任务状态，成功后拿 `plainText` 和 `blocks`。`plainText` 继续走 Java 原有分块和句子落库流程，`figure` 类型 block 交给图片资产和图片翻译流程。

## 重要源码入口

```text
ai_translation_py/
  main.py                         控制台入口，实际转调 cli.main
  cli.py                          命令行入口，支持 api 和 parse-pdf
  config.py                       环境变量配置
  api/
    app.py                        FastAPI 应用创建、异常处理、health
    routes_pdf.py                 PDF HTTP 路由
  services/
    pdf_task_service.py           任务门面服务，创建任务、保存文件、提交后台线程
    pdf_parse_service.py          真正执行解析并写结果
    result_service.py             查询解析结果
  parsers/
    hybrid_pdf_parser.py          混合解析调度器，核心策略入口
    mineru_parser.py              调用 MinerU CLI
    unstructured_parser.py        调用 unstructured.partition.pdf
    text_pdf_parser.py            pypdf 纯文本兜底
    image_asset_parser.py         PyMuPDF 提取图片资产
    base.py                       解析器协议和 RawParseOutput
  normalizers/
    block_normalizer.py           清洗、去重、排序、重编号
    markdown_renderer.py          渲染 result.md
  models/
    task.py                       任务状态 DTO
    pdf_result.py                 PDF 解析结果 DTO
  storage/
    local_storage.py              本地目录和原子写文件
    task_repository.py            JSON 文件任务仓库
```

## 当前文档和操作手册的区别

- `python-pdf-service-guide.md`：怎么安装、启动、curl 调接口、排查环境问题。
- `source-code-guide/*`：代码怎么组织、调用链怎么走、核心代码怎么读。

