from __future__ import annotations

import json

from ai_translation_py.core.errors import AiTranslationPyError, ErrorCode
from ai_translation_py.core.task_status import TaskStatus
from ai_translation_py.models.pdf_result import PdfParseResult
from ai_translation_py.storage.local_storage import LocalStorage
from ai_translation_py.storage.task_repository import TaskRepository


class ResultService:
    def __init__(self, storage: LocalStorage, repository: TaskRepository) -> None:
        self.storage = storage
        self.repository = repository

    def get_result(self, task_id: str) -> PdfParseResult:
        task = self.repository.get(task_id)
        if task.status != TaskStatus.SUCCEEDED:
            raise AiTranslationPyError(
                ErrorCode.PDF_RESULT_NOT_READY,
                "PDF parse result is not ready",
                status_code=409,
            )
        path = self.storage.result_json_path(task_id)
        if not path.exists():
            raise AiTranslationPyError(
                ErrorCode.PDF_RESULT_NOT_READY,
                "PDF parse result is not ready",
                status_code=409,
            )
        return PdfParseResult.model_validate(json.loads(path.read_text(encoding="utf-8")))

    def get_markdown(self, task_id: str) -> str:
        task = self.repository.get(task_id)
        if task.status != TaskStatus.SUCCEEDED:
            raise AiTranslationPyError(
                ErrorCode.PDF_RESULT_NOT_READY,
                "PDF parse result is not ready",
                status_code=409,
            )
        path = self.storage.result_markdown_path(task_id)
        if not path.exists():
            raise AiTranslationPyError(
                ErrorCode.PDF_RESULT_NOT_READY,
                "PDF parse result is not ready",
                status_code=409,
            )
        return path.read_text(encoding="utf-8")
