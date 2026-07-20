from __future__ import annotations

import json
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime
from functools import lru_cache
from pathlib import Path
from typing import Any

from fastapi import UploadFile

from ai_translation_py.config import Settings, get_settings
from ai_translation_py.core.errors import AiTranslationPyError, ErrorCode
from ai_translation_py.core.task_status import TaskStatus
from ai_translation_py.models.pdf_result import PdfParseResult
from ai_translation_py.models.task import TaskInfo
from ai_translation_py.services.pdf_parse_service import PdfParseService
from ai_translation_py.services.result_service import ResultService
from ai_translation_py.storage.local_storage import LocalStorage
from ai_translation_py.storage.task_repository import TaskRepository


class PdfTaskService:
    """任务门面服务。

    API 和 CLI 都从这里进入：负责校验文件、创建任务、保存原始 PDF，
    然后把真正耗时的解析工作交给 PdfParseService。
    """

    def __init__(
        self,
        settings: Settings,
        storage: LocalStorage,
        repository: TaskRepository,
        parse_service: PdfParseService,
        result_service: ResultService,
        executor: ThreadPoolExecutor,
    ) -> None:
        self.settings = settings
        self.storage = storage
        self.repository = repository
        self.parse_service = parse_service
        self.result_service = result_service
        self.executor = executor

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
        target.parent.mkdir(parents=True, exist_ok=True)
        tmp = target.with_suffix(".pdf.tmp")
        size = 0
        first_chunk = b""
        try:
            # 分块读取可以避免一次性把 200MB PDF 全部放进内存。
            with tmp.open("wb") as handle:
                while True:
                    chunk = await upload_file.read(1024 * 1024)
                    if not chunk:
                        break
                    if not first_chunk:
                        first_chunk = chunk
                    size += len(chunk)
                    if size > self.settings.max_file_bytes:
                        raise AiTranslationPyError(
                            ErrorCode.PDF_FILE_TOO_LARGE,
                            f"PDF file exceeds {self.settings.max_file_mb} MB",
                            status_code=413,
                        )
                    handle.write(chunk)
            self._validate_pdf_magic(first_chunk)
        except Exception:
            if tmp.exists():
                tmp.unlink()
            raise

        if size == 0:
            if tmp.exists():
                tmp.unlink()
            raise AiTranslationPyError(
                ErrorCode.PDF_INVALID_TYPE,
                "Uploaded PDF file is empty",
                status_code=400,
            )

        target.unlink(missing_ok=True)
        tmp.replace(target)

        self.repository.save(task)
        if submit:
            self.submit_parse_task(task.task_id)
        return task

    def create_parse_task_from_path(
        self,
        source_path: Path,
        *,
        options: dict[str, Any] | None = None,
        submit: bool = True,
    ) -> TaskInfo:
        # CLI 调试入口复用同一套任务流程，避免 API 和命令行行为不一致。
        source_path = source_path.resolve()
        if not source_path.exists():
            raise AiTranslationPyError(ErrorCode.PDF_INVALID_TYPE, "PDF file does not exist")
        self._validate_pdf_file_name(source_path.name)
        if source_path.stat().st_size > self.settings.max_file_bytes:
            raise AiTranslationPyError(
                ErrorCode.PDF_FILE_TOO_LARGE,
                f"PDF file exceeds {self.settings.max_file_mb} MB",
                status_code=413,
            )
        with source_path.open("rb") as handle:
            self._validate_pdf_magic(handle.read(8))

        task = self._create_task_record(source_path.name)
        self.storage.copy_source_pdf(source_path, task.task_id)
        self.repository.save(task)
        if submit:
            self.submit_parse_task(task.task_id)
        return task

    def submit_parse_task(self, task_id: str) -> None:
        # FastAPI 请求线程马上返回，解析在线程池里继续跑。
        self.executor.submit(self.parse_service.run_parse_task, task_id)

    def run_parse_task(self, task_id: str) -> None:
        self.parse_service.run_parse_task(task_id)

    def get_task(self, task_id: str) -> TaskInfo:
        return self.repository.get(task_id)

    def get_result(self, task_id: str) -> PdfParseResult:
        return self.result_service.get_result(task_id)

    def get_markdown(self, task_id: str) -> str:
        return self.result_service.get_markdown(task_id)

    def _create_task_record(self, file_name: str) -> TaskInfo:
        now = datetime.now(UTC)
        task_id = f"pdf_{now:%Y%m%d_%H%M%S}_{uuid.uuid4().hex[:8]}"
        return TaskInfo(
            taskId=task_id,
            fileName=file_name,
            status=TaskStatus.PENDING,
            progress=0,
            currentStep="pending",
            warnings=[],
            createdAt=now,
            updatedAt=now,
        )

    @staticmethod
    def _validate_pdf_file_name(file_name: str) -> None:
        if not file_name.lower().endswith(".pdf"):
            raise AiTranslationPyError(
                ErrorCode.PDF_INVALID_TYPE,
                "Only PDF files are accepted",
                status_code=400,
            )

    @staticmethod
    def _validate_pdf_magic(first_bytes: bytes) -> None:
        if not first_bytes.startswith(b"%PDF"):
            raise AiTranslationPyError(
                ErrorCode.PDF_INVALID_TYPE,
                "Uploaded file is not a valid PDF",
                status_code=400,
            )


def parse_options(options: str | None) -> dict[str, Any]:
    """把 multipart 表单里的 options 字符串解析成字典。"""

    if not options:
        return {}
    try:
        data = json.loads(options)
    except json.JSONDecodeError as exc:
        raise AiTranslationPyError("INVALID_OPTIONS", "Options must be valid JSON", status_code=400) from exc
    if not isinstance(data, dict):
        raise AiTranslationPyError("INVALID_OPTIONS", "Options must be a JSON object", status_code=400)
    return data


@lru_cache(maxsize=1)
def build_pdf_task_service() -> PdfTaskService:
    settings = get_settings()
    storage = LocalStorage(settings)
    repository = TaskRepository(storage)
    parse_service = PdfParseService(settings, storage, repository)
    result_service = ResultService(storage, repository)
    executor = ThreadPoolExecutor(max_workers=settings.workers, thread_name_prefix="pdf-parser")
    return PdfTaskService(settings, storage, repository, parse_service, result_service, executor)
