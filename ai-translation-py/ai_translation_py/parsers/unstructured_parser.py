from __future__ import annotations

from pathlib import Path

from ai_translation_py.core.errors import ErrorCode, ParserUnavailableError
from ai_translation_py.models.pdf_result import PdfBlock
from ai_translation_py.parsers.base import RawParseOutput


class UnstructuredParser:
    name = "unstructured"

    def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
        try:
            from unstructured.partition.pdf import partition_pdf
        except Exception as exc:  # pragma: no cover - depends on optional runtime install
            raise ParserUnavailableError(
                ErrorCode.PDF_UNSTRUCTURED_FAILED,
                "Unstructured PDF parser is not available",
                detail=str(exc),
            ) from exc

        try:
            elements = partition_pdf(filename=str(pdf_path), strategy="auto")
        except Exception as exc:
            raise ParserUnavailableError(
                ErrorCode.PDF_UNSTRUCTURED_FAILED,
                "Unstructured parse failed",
                detail=str(exc),
            ) from exc

        blocks: list[PdfBlock] = []
        max_page: int | None = None
        for index, element in enumerate(elements, start=1):
            metadata = getattr(element, "metadata", None)
            page_no = int(getattr(metadata, "page_number", 1) or 1)
            max_page = max(max_page or page_no, page_no)
            text = str(element).strip()
            category = getattr(element, "category", element.__class__.__name__)
            block_type = _map_unstructured_type(str(category))
            markdown = _element_markdown(element, block_type)
            if not text and not markdown:
                continue
            blocks.append(
                PdfBlock(
                    blockId=f"unstructured_raw_{index:06d}",
                    pageNo=page_no,
                    orderNo=index,
                    type=block_type,
                    text=text or None,
                    markdown=markdown,
                    bbox=_element_bbox(metadata),
                    sourceParser=self.name,
                    metadata={"category": str(category)},
                )
            )

        return RawParseOutput(
            parser_name=self.name,
            blocks=blocks,
            page_count=max_page,
        )


def _map_unstructured_type(category: str) -> str:
    normalized = category.lower()
    if normalized in {"title"}:
        return "heading"
    if "header" in normalized:
        return "header"
    if "footer" in normalized:
        return "footer"
    if "list" in normalized:
        return "list_item"
    if "table" in normalized:
        return "table"
    if "figure" in normalized or "image" in normalized:
        return "figure"
    if "formula" in normalized or "equation" in normalized:
        return "formula"
    if "narrative" in normalized or "text" in normalized:
        return "paragraph"
    return "unknown"


def _element_markdown(element: object, block_type: str) -> str | None:
    metadata = getattr(element, "metadata", None)
    html = getattr(metadata, "text_as_html", None)
    if block_type == "table" and isinstance(html, str) and html.strip():
        return html.strip()
    text = str(element).strip()
    if block_type == "heading" and text:
        return f"## {text}"
    if block_type == "list_item" and text:
        return f"- {text}"
    return text or None


def _element_bbox(metadata: object | None) -> list[float] | None:
    coordinates = getattr(metadata, "coordinates", None)
    points = getattr(coordinates, "points", None)
    if not points:
        return None
    xs = [float(point[0]) for point in points if len(point) >= 2]
    ys = [float(point[1]) for point in points if len(point) >= 2]
    if not xs or not ys:
        return None
    return [min(xs), min(ys), max(xs), max(ys)]
