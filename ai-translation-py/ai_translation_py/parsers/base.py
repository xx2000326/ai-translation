from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Protocol

from ai_translation_py.models.pdf_result import PdfBlock


@dataclass(slots=True)
class RawParseOutput:
    parser_name: str
    blocks: list[PdfBlock] = field(default_factory=list)
    markdown: str | None = None
    page_count: int | None = None
    warnings: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


class PdfParser(Protocol):
    name: str

    def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
        ...
