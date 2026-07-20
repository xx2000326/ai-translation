from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class TaskInfo(BaseModel):
    """解析任务状态。

    API 创建任务后先返回 PENDING，后台线程会持续更新 progress/status。
    """

    model_config = ConfigDict(populate_by_name=True)

    task_id: str = Field(alias="taskId")
    file_name: str = Field(alias="fileName")
    status: str
    progress: int = 0
    current_step: str | None = Field(default=None, alias="currentStep")
    error_code: str | None = Field(default=None, alias="errorCode")
    error_message: str | None = Field(default=None, alias="errorMessage")
    warnings: list[str] = Field(default_factory=list)
    created_at: datetime = Field(alias="createdAt")
    updated_at: datetime = Field(alias="updatedAt")


class ParseTaskCreatedResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    task_id: str = Field(alias="taskId")
    status: str
