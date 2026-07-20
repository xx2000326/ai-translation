from __future__ import annotations

from typing import Any


class ErrorCode:
    PDF_INVALID_TYPE = "PDF_INVALID_TYPE"
    PDF_FILE_TOO_LARGE = "PDF_FILE_TOO_LARGE"
    PDF_PARSE_FAILED = "PDF_PARSE_FAILED"
    PDF_MINERU_FAILED = "PDF_MINERU_FAILED"
    PDF_UNSTRUCTURED_FAILED = "PDF_UNSTRUCTURED_FAILED"
    PDF_RESULT_NOT_READY = "PDF_RESULT_NOT_READY"
    PDF_TASK_NOT_FOUND = "PDF_TASK_NOT_FOUND"


class AiTranslationPyError(Exception):
    def __init__(
        self,
        error_code: str,
        message: str,
        *,
        detail: Any | None = None,
        status_code: int = 400,
    ) -> None:
        super().__init__(message)
        self.error_code = error_code
        self.message = message
        self.detail = detail
        self.status_code = status_code

    def to_response(self) -> dict[str, Any]:
        body: dict[str, Any] = {
            "errorCode": self.error_code,
            "message": self.message,
        }
        if self.detail is not None:
            body["detail"] = self.detail
        return body


class ParserUnavailableError(AiTranslationPyError):
    pass
