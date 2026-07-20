# 01. 入口层、配置层、API 层说明书

本篇解释这些文件：

```text
ai_translation_py/main.py
ai_translation_py/cli.py
ai_translation_py/config.py
ai_translation_py/api/app.py
ai_translation_py/api/routes_pdf.py
ai_translation_py/api/schemas.py
```

它们负责把外部请求带进 Python 服务。外部请求有两类：

- 命令行：`ai-translation-py api` 或 `ai-translation-py parse-pdf ./x.pdf`
- HTTP：Java 后端或 curl 调 `/api/v1/pdf/parse-tasks`

## 1. `main.py`

### 职责

`main.py` 是最薄的一层入口，作用是让命令可以这样启动：

```bash
python -m ai_translation_py.main api
```

或者通过 `pyproject.toml` 中的 console script：

```toml
[project.scripts]
ai-translation-py = "ai_translation_py.main:main"
```

最终执行：

```text
ai_translation_py.main:main
  -> ai_translation_py.cli.main
```

### Java 类比

它类似一个只有 `main(String[] args)` 的启动类，然后把实际启动逻辑委托给别的类。

## 2. `cli.py`

### 职责

`cli.py` 负责命令行参数解析和命令分发。

支持两个子命令：

```bash
ai-translation-py api
ai-translation-py parse-pdf ./sample.pdf --output ./output
```

### 谁调用它

```text
main.py
  -> cli.main()
```

### 它调用谁

```text
cli._run_api
  -> uvicorn.run("ai_translation_py.api.app:app", ...)

cli._parse_pdf
  -> build_pdf_task_service()
  -> PdfTaskService.create_parse_task_from_path(...)
  -> PdfTaskService.run_parse_task(...)
  -> PdfTaskService.get_result(...)
```

### 核心函数：`build_parser`

```python
def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="ai-translation-py")
    subparsers = parser.add_subparsers(dest="command", required=True)

    api_parser = subparsers.add_parser("api", help="Start the FastAPI service.")
    api_parser.add_argument("--host", default=None)
    api_parser.add_argument("--port", type=int, default=None)
    api_parser.add_argument("--reload", action="store_true")
    api_parser.set_defaults(func=_run_api)

    parse_parser = subparsers.add_parser("parse-pdf", help="Parse a local PDF file.")
    parse_parser.add_argument("pdf_path")
    parse_parser.add_argument("--output", "-o", default=None)
    parse_parser.add_argument("--options", default=None, help="JSON string or path to JSON options.")
    parse_parser.set_defaults(func=_parse_pdf)

    return parser
```

读法：

- `argparse.ArgumentParser` 是 Python 标准库命令行解析器。
- `subparsers` 表示子命令。
- `api_parser.set_defaults(func=_run_api)` 表示用户输入 `api` 时，最后执行 `_run_api(args)`。
- `parse_parser.set_defaults(func=_parse_pdf)` 表示用户输入 `parse-pdf` 时，最后执行 `_parse_pdf(args)`。

Java 类比：

```java
switch (command) {
    case "api" -> runApi(args);
    case "parse-pdf" -> parsePdf(args);
}
```

Python 这里把函数对象放进 `args.func`，最后统一：

```python
return args.func(args)
```

### 核心函数：`_run_api`

```python
def _run_api(args: argparse.Namespace) -> int:
    settings = get_settings()
    host = args.host or settings.host
    port = args.port or settings.port
    uvicorn.run("ai_translation_py.api.app:app", host=host, port=port, reload=args.reload)
    return 0
```

解释：

- `get_settings()` 读取配置。
- 命令行传了 `--host` 就用命令行，否则用环境变量配置或默认值。
- `uvicorn.run(...)` 启动 FastAPI。
- `"ai_translation_py.api.app:app"` 表示导入 `ai_translation_py/api/app.py` 里的全局变量 `app`。

Java 类比：

类似启动内嵌 Web Server：

```java
SpringApplication.run(Application.class, args);
```

区别是 Python ASGI 服务由 Uvicorn 托管。

### 核心函数：`_parse_pdf`

```python
def _parse_pdf(args: argparse.Namespace) -> int:
    service = build_pdf_task_service()
    options = _parse_options(args.options)
    task = service.create_parse_task_from_path(Path(args.pdf_path), options=options, submit=False)
    service.run_parse_task(task.task_id)

    result = service.get_result(task.task_id)
    ...
```

解释：

- 命令行解析本地 PDF 时不走 HTTP 上传。
- `create_parse_task_from_path(...)` 复用任务创建流程。
- `submit=False` 表示不要丢给后台线程。
- 下一行直接同步调用 `run_parse_task(...)`。
- 解析结束后复制 `result.json/result.md` 到用户指定输出目录。

为什么 CLI 要同步执行：

- 命令行通常希望命令结束时结果已经生成。
- 方便本地调试和自动化测试。

### 核心函数：`_parse_options`

```python
def _parse_options(raw_options: str | None) -> dict[str, Any]:
    if not raw_options:
        return {}
    options_path = Path(raw_options)
    if options_path.exists():
        return json.loads(options_path.read_text(encoding="utf-8"))
    return json.loads(raw_options)
```

它支持两种写法：

```bash
--options '{"foo":"bar"}'
--options ./options.json
```

如果参数看起来是一个存在的文件路径，就读取文件；否则当作 JSON 字符串。

## 3. `config.py`

### 职责

`config.py` 负责集中读取服务配置。

核心类：

```python
class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AI_TRANSLATION_PY_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    host: str = "127.0.0.1"
    port: int = 8010
    data_dir: Path = Path("./data")
    max_file_mb: int = 200
    workers: int = 2
    enable_mineru: bool = True
    enable_unstructured: bool = True
    enable_pypdf_fallback: bool = True
    ocr_lang: str = "ch,en"
    result_ttl_hours: int = 72
    mineru_backend: str = "pipeline"
    mineru_timeout_seconds: int = Field(default=1800, ge=1)
```

### 配置来源

因为使用了 Pydantic Settings：

```python
env_prefix="AI_TRANSLATION_PY_"
```

字段会自动映射到环境变量：

```text
host                      -> AI_TRANSLATION_PY_HOST
port                      -> AI_TRANSLATION_PY_PORT
data_dir                  -> AI_TRANSLATION_PY_DATA_DIR
max_file_mb               -> AI_TRANSLATION_PY_MAX_FILE_MB
enable_mineru             -> AI_TRANSLATION_PY_ENABLE_MINERU
mineru_timeout_seconds    -> AI_TRANSLATION_PY_MINERU_TIMEOUT_SECONDS
```

Java 类比：

```java
@ConfigurationProperties(prefix = "ai.translation.py")
public class Settings {
    private String host = "127.0.0.1";
    private Integer port = 8010;
}
```

### 计算属性

```python
@property
def max_file_bytes(self) -> int:
    return self.max_file_mb * 1024 * 1024
```

`@property` 让方法像字段一样访问：

```python
settings.max_file_bytes
```

Java 类比：

```java
public int getMaxFileBytes() {
    return maxFileMb * 1024 * 1024;
}
```

其他目录属性也是同样思路：

```python
@property
def uploads_dir(self) -> Path:
    return self.data_dir / "uploads"
```

`Path / "uploads"` 是 Python `pathlib` 的路径拼接写法，类似 Java：

```java
dataDir.resolve("uploads")
```

### 单例缓存：`get_settings`

```python
@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
```

`@lru_cache(maxsize=1)` 表示这个函数第一次调用时创建 `Settings`，后续直接返回缓存对象。

Java 类比：

- Spring 单例 Bean。
- 或者 lazy singleton。

为什么这样做：

- 避免每次请求都重新解析环境变量和 `.env`。
- 所有服务层拿到的是同一份配置。

## 4. `api/app.py`

### 职责

创建 FastAPI 应用，挂载路由，注册异常处理器。

核心代码：

```python
def create_app() -> FastAPI:
    setup_logging()
    app = FastAPI(title="ai-translation-py", version="0.1.0")
    app.include_router(pdf_router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.exception_handler(AiTranslationPyError)
    async def handle_ai_translation_py_error(
        request: Request,
        exc: AiTranslationPyError,
    ) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=exc.to_response())

    return app

app = create_app()
```

### 谁调用它

Uvicorn 启动时导入：

```text
uvicorn.run("ai_translation_py.api.app:app", ...)
```

### 它调用谁

```text
create_app
  -> setup_logging
  -> include_router(pdf_router)
  -> register health endpoint
  -> register AiTranslationPyError handler
```

### FastAPI 装饰器读法

```python
@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}
```

这个装饰器类似 Java：

```java
@GetMapping("/health")
public Map<String, String> health() {
    return Map.of("status", "UP");
}
```

区别是 Python 装饰器会在函数定义时注册路由。

### 统一异常处理

```python
@app.exception_handler(AiTranslationPyError)
async def handle_ai_translation_py_error(...):
    return JSONResponse(status_code=exc.status_code, content=exc.to_response())
```

Java 类比：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AiTranslationPyException.class)
    public ResponseEntity<?> handle(...) { ... }
}
```

## 5. `api/routes_pdf.py`

### 职责

定义 PDF 解析相关 HTTP 接口。

路由前缀：

```python
router = APIRouter(prefix="/api/v1/pdf", tags=["pdf"])
```

所以本文件里的：

```python
@router.post("/parse-tasks")
```

完整路径是：

```text
POST /api/v1/pdf/parse-tasks
```

### 接口清单

```text
POST /api/v1/pdf/parse-tasks
GET  /api/v1/pdf/parse-tasks/{task_id}
GET  /api/v1/pdf/parse-tasks/{task_id}/result
GET  /api/v1/pdf/parse-tasks/{task_id}/result.md
GET  /api/v1/pdf/parse-tasks/{task_id}/assets/{asset_id}
```

### 创建解析任务

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

参数解释：

- `file: UploadFile = File(...)`：要求 multipart 表单里必须有 `file`。
- `options: str | None = Form(default=None)`：表单里的可选字段 `options`。
- `async def`：异步路由函数，可以 `await upload_file.read(...)`。

调用链：

```text
create_parse_task
  -> parse_options(options)
  -> build_pdf_task_service()
  -> PdfTaskService.create_parse_task_from_upload
```

### 查询任务状态

```python
@router.get("/parse-tasks/{task_id}", response_model=TaskInfo)
def get_parse_task(task_id: str) -> TaskInfo:
    return build_pdf_task_service().get_task(task_id)
```

读法：

- `{task_id}` 是路径变量。
- FastAPI 自动把路径变量传给函数参数 `task_id`。
- 返回 `TaskInfo`，FastAPI 使用 Pydantic 序列化。

### 查询 JSON 结果

```python
@router.get("/parse-tasks/{task_id}/result", response_model=PdfParseResult)
def get_parse_result(task_id: str) -> PdfParseResult:
    return build_pdf_task_service().get_result(task_id)
```

如果任务还没成功，`ResultService` 会抛 `PDF_RESULT_NOT_READY`，异常处理器转成 HTTP 409。

### 查询 Markdown 结果

```python
@router.get("/parse-tasks/{task_id}/result.md", response_class=PlainTextResponse)
def get_parse_result_markdown(task_id: str) -> PlainTextResponse:
    markdown = build_pdf_task_service().get_markdown(task_id)
    return PlainTextResponse(markdown, media_type="text/markdown; charset=utf-8")
```

这里没有返回 Pydantic DTO，而是直接返回文本响应。

### 下载图片资产

```python
@router.get("/parse-tasks/{task_id}/assets/{asset_id}")
def get_parse_asset(task_id: str, asset_id: str) -> FileResponse:
    service = build_pdf_task_service()
    service.get_task(task_id)
    path = service.storage.find_asset_path(task_id, asset_id)
    if path is None or not path.exists():
        raise AiTranslationPyError("PDF_ASSET_NOT_FOUND", "PDF parse asset was not found", status_code=404)
    return FileResponse(path, media_type=_asset_media_type(path.name), filename=path.name)
```

它做了：

1. 确认任务存在。
2. 在 `data/assets/{taskId}/images/` 下找 `assetId.*`。
3. 找不到返回 404。
4. 找到则用 `FileResponse` 把图片文件返回给调用方。

Java 后端图片服务会调用这个接口下载原图。

### `_asset_media_type`

```python
def _asset_media_type(name: str) -> str:
    suffix = name.rsplit(".", 1)[-1].lower() if "." in name else ""
    return {
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "png": "image/png",
        ...
    }.get(suffix, "application/octet-stream")
```

这是按文件后缀推断 Content-Type。

Java 类比：

```java
return switch (suffix) {
    case "jpg", "jpeg" -> "image/jpeg";
    case "png" -> "image/png";
    default -> "application/octet-stream";
};
```

## 6. `api/schemas.py`

### 职责

目前只有错误响应模型：

```python
class ErrorResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    errorCode: str
    message: str
    detail: object | None = None
```

这个模型现在主要用于表达 API 错误响应结构。实际异常响应由 `AiTranslationPyError.to_response()` 生成。

## 7. 入口层常见问题

### 为什么很多地方都调用 `build_pdf_task_service()`

`build_pdf_task_service()` 在 `pdf_task_service.py` 中被 `@lru_cache(maxsize=1)` 缓存。虽然路由里每次都写：

```python
build_pdf_task_service()
```

实际返回的是同一个服务对象。

这类似 Spring 每次注入同一个 singleton bean。

### 为什么创建任务接口是 async，查询接口不是 async

上传文件读取用：

```python
chunk = await upload_file.read(...)
```

所以创建任务函数必须是 `async def`。查询任务只是读本地 JSON 文件，没有 await，因此普通 `def` 就够。

### 为什么 API 创建任务后马上返回

PDF 解析可能很慢。创建接口只负责接收 PDF、创建任务、提交后台线程。调用方拿到 taskId 后轮询状态。

这和 Java 中常见的异步任务接口一样：

```text
POST /tasks -> 202 Accepted + taskId
GET /tasks/{id} -> status
GET /tasks/{id}/result -> result
```

