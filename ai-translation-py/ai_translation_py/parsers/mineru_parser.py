from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path

from ai_translation_py.config import Settings
from ai_translation_py.core.errors import ErrorCode, ParserUnavailableError
from ai_translation_py.models.pdf_result import PdfBlock
from ai_translation_py.parsers.base import RawParseOutput


class MineruParser:
    name = "mineru"

    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
        mineru_bin = shutil.which("mineru")
        if not mineru_bin:
            raise ParserUnavailableError(
                ErrorCode.PDF_MINERU_FAILED,
                "MinerU CLI is not available",
                detail="Install runtime dependencies with: uv pip install -r requirements.txt",
            )

        output_dir = work_dir / "mineru"
        output_dir.mkdir(parents=True, exist_ok=True)
        cmd = [
            mineru_bin,
            "-p",
            str(pdf_path),
            "-o",
            str(output_dir),
            "-b",
            self.settings.mineru_backend,
        ]
        completed = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=self.settings.mineru_timeout_seconds,
            check=False,
        )
        if completed.returncode != 0:
            raise ParserUnavailableError(
                ErrorCode.PDF_MINERU_FAILED,
                "MinerU parse failed",
                detail=(completed.stderr or completed.stdout or "").strip(),
            )

        markdown = self._read_first_file(output_dir, "*.md")
        json_data = self._read_first_json(output_dir)
        blocks = self._blocks_from_mineru_json(json_data, parser_name=self.name)
        if not blocks and markdown:
            blocks = self._blocks_from_markdown(markdown, parser_name=self.name)

        return RawParseOutput(
            parser_name=self.name,
            blocks=blocks,
            markdown=markdown,
            page_count=self._page_count_from_json(json_data),
            metadata={"outputDir": str(output_dir)},
        )

    @staticmethod
    def _read_first_file(root: Path, pattern: str) -> str | None:
        for path in root.rglob(pattern):
            if path.is_file():
                return path.read_text(encoding="utf-8", errors="ignore")
        return None

    @staticmethod
    def _read_first_json(root: Path) -> dict | list | None:
        for path in root.rglob("*.json"):
            if path.is_file():
                try:
                    return json.loads(path.read_text(encoding="utf-8", errors="ignore"))
                except json.JSONDecodeError:
                    continue
        return None

    @staticmethod
    def _page_count_from_json(data: dict | list | None) -> int | None:
        if isinstance(data, dict):
            for key in ("page_count", "pageCount", "pages"):
                value = data.get(key)
                if isinstance(value, int):
                    return value
                if isinstance(value, list):
                    return len(value)
        return None

    @staticmethod
    def _blocks_from_mineru_json(data: dict | list | None, *, parser_name: str) -> list[PdfBlock]:
        raw_blocks: list[dict] = []
        if isinstance(data, dict):
            for key in ("blocks", "elements", "pdf_info", "pages"):
                value = data.get(key)
                if isinstance(value, list):
                    raw_blocks.extend(item for item in value if isinstance(item, dict))
        elif isinstance(data, list):
            raw_blocks.extend(item for item in data if isinstance(item, dict))

        blocks: list[PdfBlock] = []
        for index, item in enumerate(raw_blocks, start=1):
            text = _first_string(item, ("text", "content", "value"))
            markdown = _first_string(item, ("markdown", "md"))
            block_type = _map_mineru_type(_first_string(item, ("type", "category", "block_type")))
            if not text and not markdown and block_type not in {"figure", "formula", "table"}:
                continue
            blocks.append(
                PdfBlock(
                    blockId=f"mineru_raw_{index:06d}",
                    pageNo=_first_int(item, ("page_no", "pageNo", "page", "page_idx"), default=1),
                    orderNo=index,
                    type=block_type,
                    text=text,
                    markdown=markdown,
                    bbox=_first_bbox(item),
                    confidence=_first_float(item, ("confidence", "score")),
                    sourceParser=parser_name,
                    metadata={"rawType": _first_string(item, ("type", "category", "block_type"))},
                )
            )
        return blocks

    @staticmethod
    def _blocks_from_markdown(markdown: str, *, parser_name: str) -> list[PdfBlock]:
        blocks: list[PdfBlock] = []
        order = 1
        for line in markdown.splitlines():
            text = line.strip()
            if not text:
                continue
            block_type = "heading" if text.startswith("#") else "paragraph"
            if text.startswith("|"):
                block_type = "table"
            blocks.append(
                PdfBlock(
                    blockId=f"mineru_md_{order:06d}",
                    pageNo=1,
                    orderNo=order,
                    type=block_type,
                    text=text.lstrip("#").strip() if block_type == "heading" else text,
                    markdown=text,
                    sourceParser=parser_name,
                    metadata={},
                )
            )
            order += 1
        return blocks


def _first_string(item: dict, keys: tuple[str, ...]) -> str | None:
    for key in keys:
        value = item.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def _first_int(item: dict, keys: tuple[str, ...], *, default: int) -> int:
    for key in keys:
        value = item.get(key)
        if isinstance(value, int):
            return value + 1 if key.endswith("idx") else value
    return default


def _first_float(item: dict, keys: tuple[str, ...]) -> float | None:
    for key in keys:
        value = item.get(key)
        if isinstance(value, (int, float)):
            return float(value)
    return None


def _first_bbox(item: dict) -> list[float] | None:
    for key in ("bbox", "bounding_box"):
        value = item.get(key)
        if isinstance(value, list) and len(value) >= 4:
            numeric = [float(v) for v in value[:4] if isinstance(v, (int, float))]
            if len(numeric) == 4:
                return numeric
    return None


def _map_mineru_type(raw_type: str | None) -> str:
    if not raw_type:
        return "paragraph"
    normalized = raw_type.lower()
    if "title" in normalized:
        return "title"
    if "header" in normalized:
        return "header"
    if "footer" in normalized:
        return "footer"
    if "table" in normalized:
        return "table"
    if "image" in normalized or "figure" in normalized:
        return "figure"
    if "formula" in normalized or "equation" in normalized:
        return "formula"
    if "list" in normalized:
        return "list_item"
    return "paragraph"
