from __future__ import annotations

from pathlib import Path

from ai_translation_py.core.errors import ErrorCode, ParserUnavailableError
from ai_translation_py.models.pdf_result import PdfBlock
from ai_translation_py.parsers.base import RawParseOutput


class PypdfTextParser:
    name = "pypdf"

    def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
        try:
            from pypdf import PdfReader
        except Exception as exc:  # pragma: no cover - depends on optional runtime install
            raise ParserUnavailableError(
                ErrorCode.PDF_PARSE_FAILED,
                "pypdf fallback parser is not available",
                detail=str(exc),
            ) from exc

        try:
            reader = PdfReader(str(pdf_path))
        except Exception as exc:
            raise ParserUnavailableError(
                ErrorCode.PDF_PARSE_FAILED,
                "pypdf failed to read PDF",
                detail=str(exc),
            ) from exc

        blocks: list[PdfBlock] = []
        order = 1
        for page_index, page in enumerate(reader.pages, start=1):
            text = page.extract_text() or ""
            for chunk in _split_text_blocks(text):
                block_type = _guess_block_type(chunk, page_index=page_index, order=order)
                blocks.append(
                    PdfBlock(
                        blockId=f"pypdf_raw_{order:06d}",
                        pageNo=page_index,
                        orderNo=order,
                        type=block_type,
                        text=chunk,
                        markdown=_markdown_for_text(chunk, block_type),
                        sourceParser=self.name,
                        metadata={},
                    )
                )
                order += 1

        return RawParseOutput(
            parser_name=self.name,
            blocks=blocks,
            page_count=len(reader.pages),
            warnings=["Used pypdf text fallback; OCR, tables, images, and formulas may be incomplete."],
        )


def _split_text_blocks(text: str) -> list[str]:
    lines = [line.strip() for line in text.replace("\r\n", "\n").split("\n")]
    chunks: list[str] = []
    current: list[str] = []
    for line in lines:
        if not line:
            if current:
                chunks.append(" ".join(current).strip())
                current = []
            continue
        current.append(line)
    if current:
        chunks.append(" ".join(current).strip())
    return [chunk for chunk in chunks if chunk]


def _guess_block_type(text: str, *, page_index: int, order: int) -> str:
    if page_index == 1 and order == 1 and len(text) <= 120:
        return "title"
    if text[:2] in {"- ", "* "} or text[:3].startswith(("1.", "2.", "3.")):
        return "list_item"
    if len(text) <= 80 and not text.endswith((".", ",", ";", ":")):
        return "heading"
    return "paragraph"


def _markdown_for_text(text: str, block_type: str) -> str:
    if block_type == "title":
        return f"# {text}"
    if block_type == "heading":
        return f"## {text}"
    if block_type == "list_item":
        return text if text.startswith(("-", "*")) else f"- {text}"
    return text
