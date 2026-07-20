from __future__ import annotations

import re

from ai_translation_py.models.pdf_result import PdfBlock


VALID_BLOCK_TYPES = {
    "title",
    "heading",
    "paragraph",
    "list_item",
    "table",
    "figure",
    "formula",
    "header",
    "footer",
    "unknown",
}

STRUCTURED_TYPES = {"table", "figure", "formula"}
PARSER_PRIORITY = {"mineru": 0, "unstructured": 1, "pypdf": 2}


class BlockNormalizer:
    """把不同解析器输出的块整理成统一顺序和统一编号。"""

    def normalize(self, blocks: list[PdfBlock]) -> list[PdfBlock]:
        # 标准化 -> 去重 -> 排序 -> 重新编号，是后续翻译流程最依赖的一步。
        cleaned = [self._clean_block(block) for block in blocks]
        cleaned = [block for block in cleaned if self._is_usable(block)]
        deduped = self._dedupe(cleaned)
        sorted_blocks = sorted(deduped, key=self._sort_key)
        return [
            block.model_copy(update={"block_id": f"block_{index:06d}", "order_no": index})
            for index, block in enumerate(sorted_blocks, start=1)
        ]

    @staticmethod
    def _clean_block(block: PdfBlock) -> PdfBlock:
        block_type = block.type if block.type in VALID_BLOCK_TYPES else "unknown"
        text = block.text.strip() if isinstance(block.text, str) else None
        markdown = block.markdown.strip() if isinstance(block.markdown, str) else None
        bbox = _normalize_bbox(block.bbox)
        page_no = max(block.page_no, 1)
        return block.model_copy(
            update={
                "type": block_type,
                "text": text or None,
                "markdown": markdown or None,
                "bbox": bbox,
                "page_no": page_no,
            }
        )

    @staticmethod
    def _is_usable(block: PdfBlock) -> bool:
        if block.type in STRUCTURED_TYPES:
            return True
        return bool(block.text or block.markdown)

    def _dedupe(self, blocks: list[PdfBlock]) -> list[PdfBlock]:
        by_key: dict[tuple[int, str], PdfBlock] = {}
        passthrough: list[PdfBlock] = []
        for block in blocks:
            normalized_text = _normalize_text(block.text or block.markdown or "")
            if not normalized_text:
                passthrough.append(block)
                continue
            key = (block.page_no, normalized_text)
            existing = by_key.get(key)
            if existing is None or self._is_better(block, existing):
                by_key[key] = block
        return list(by_key.values()) + passthrough

    @staticmethod
    def _is_better(candidate: PdfBlock, existing: PdfBlock) -> bool:
        candidate_priority = _block_priority(candidate)
        existing_priority = _block_priority(existing)
        if candidate_priority != existing_priority:
            return candidate_priority < existing_priority
        candidate_len = len(candidate.text or candidate.markdown or "")
        existing_len = len(existing.text or existing.markdown or "")
        if candidate_len != existing_len:
            return candidate_len > existing_len
        return (candidate.confidence or 0.0) > (existing.confidence or 0.0)

    @staticmethod
    def _sort_key(block: PdfBlock) -> tuple[int, float, float, int]:
        x0, y0 = (block.bbox[0], block.bbox[1]) if block.bbox else (0.0, 0.0)
        return (block.page_no, y0, x0, block.order_no)


def _normalize_bbox(bbox: list[float] | None) -> list[float] | None:
    if not bbox or len(bbox) < 4:
        return None
    try:
        return [float(value) for value in bbox[:4]]
    except (TypeError, ValueError):
        return None


def _normalize_text(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def _block_priority(block: PdfBlock) -> tuple[int, int]:
    structured_rank = 0 if block.type in STRUCTURED_TYPES else 1
    parser_rank = PARSER_PRIORITY.get(block.source_parser, 99)
    return (structured_rank, parser_rank)
