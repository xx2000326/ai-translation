from __future__ import annotations

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import FileResponse, PlainTextResponse

from ai_translation_py.models.pdf_result import PdfParseResult
from ai_translation_py.models.task import ParseTaskCreatedResponse, TaskInfo
from ai_translation_py.services.pdf_task_service import build_pdf_task_service, parse_options
from ai_translation_py.core.errors import AiTranslationPyError

router = APIRouter(prefix="/api/v1/pdf", tags=["pdf"])


@router.post("/parse-tasks", response_model=ParseTaskCreatedResponse)
async def create_parse_task(
    file: UploadFile = File(...),
    options: str | None = Form(default=None),
) -> ParseTaskCreatedResponse:
    # multipart/form-data: file 是 PDF，options 是可选 JSON 字符串。
    service = build_pdf_task_service()
    task = await service.create_parse_task_from_upload(file, options=parse_options(options))
    return ParseTaskCreatedResponse(taskId=task.task_id, status=task.status)


@router.get("/parse-tasks/{task_id}", response_model=TaskInfo)
def get_parse_task(task_id: str) -> TaskInfo:
    return build_pdf_task_service().get_task(task_id)


@router.get("/parse-tasks/{task_id}/result", response_model=PdfParseResult)
def get_parse_result(task_id: str) -> PdfParseResult:
    return build_pdf_task_service().get_result(task_id)


@router.get("/parse-tasks/{task_id}/result.md", response_class=PlainTextResponse)
def get_parse_result_markdown(task_id: str) -> PlainTextResponse:
    markdown = build_pdf_task_service().get_markdown(task_id)
    return PlainTextResponse(markdown, media_type="text/markdown; charset=utf-8")


@router.get("/parse-tasks/{task_id}/assets/{asset_id}")
def get_parse_asset(task_id: str, asset_id: str) -> FileResponse:
    service = build_pdf_task_service()
    service.get_task(task_id)
    path = service.storage.find_asset_path(task_id, asset_id)
    if path is None or not path.exists():
        raise AiTranslationPyError("PDF_ASSET_NOT_FOUND", "PDF parse asset was not found", status_code=404)
    return FileResponse(path, media_type=_asset_media_type(path.name), filename=path.name)


def _asset_media_type(name: str) -> str:
    suffix = name.rsplit(".", 1)[-1].lower() if "." in name else ""
    return {
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "png": "image/png",
        "webp": "image/webp",
        "bmp": "image/bmp",
        "tif": "image/tiff",
        "tiff": "image/tiff",
    }.get(suffix, "application/octet-stream")
