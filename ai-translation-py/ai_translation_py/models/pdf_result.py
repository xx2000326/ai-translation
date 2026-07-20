from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class PdfBlock(BaseModel):
    """PDF 解析后的最小内容块。

    这里用 alias 保持 API 输出为 camelCase，例如 Python 里是
    `block_id`，JSON 输出是 `blockId`，便于 Java/Spring 服务消费。
    """

    model_config = ConfigDict(populate_by_name=True)

    block_id: str = Field(alias="blockId")
    page_no: int = Field(alias="pageNo")
    order_no: int = Field(alias="orderNo")
    type: str
    text: str | None = None
    markdown: str | None = None
    bbox: list[float] | None = None
    confidence: float | None = None
    source_parser: str = Field(alias="sourceParser")
    metadata: dict[str, Any] = Field(default_factory=dict)


class PdfParseResult(BaseModel):
    """一次 PDF 解析任务的完整结果。"""

    model_config = ConfigDict(populate_by_name=True)

    task_id: str = Field(alias="taskId")
    file_name: str = Field(alias="fileName")
    page_count: int | None = Field(default=None, alias="pageCount")
    parser_strategy: str = Field(alias="parserStrategy")
    status: str
    warnings: list[str] = Field(default_factory=list)
    blocks: list[PdfBlock] = Field(default_factory=list)
    markdown: str | None = None
    plain_text: str | None = Field(default=None, alias="plainText")
    created_at: datetime = Field(alias="createdAt")
    completed_at: datetime | None = Field(default=None, alias="completedAt")
