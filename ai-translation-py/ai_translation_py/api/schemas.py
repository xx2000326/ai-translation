from __future__ import annotations

from pydantic import BaseModel, ConfigDict


class ErrorResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    errorCode: str
    message: str
    detail: object | None = None
