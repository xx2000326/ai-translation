from __future__ import annotations

from pathlib import Path

from ai_translation_py.config import Settings
from ai_translation_py.core.errors import AiTranslationPyError, ErrorCode
from ai_translation_py.models.pdf_result import PdfBlock
from ai_translation_py.normalizers.block_normalizer import BlockNormalizer
from ai_translation_py.parsers.base import RawParseOutput
from ai_translation_py.parsers.image_asset_parser import PdfImageAssetParser
from ai_translation_py.parsers.mineru_parser import MineruParser
from ai_translation_py.parsers.text_pdf_parser import PypdfTextParser
from ai_translation_py.parsers.unstructured_parser import UnstructuredParser


class HybridPdfParser:
    """混合解析调度器。

    设计原则来自 PRD/SDD：
    1. MinerU 是主解析器，负责复杂版面、OCR、表格、公式、图片。
    2. Unstructured 做补充和降级。
    3. 本地开发环境可能没有完整重型依赖，所以保留 pypdf 文本兜底。
    """

    strategy_name = "mineru_unstructured_hybrid"

    def __init__(self, settings: Settings, normalizer: BlockNormalizer | None = None) -> None:
        self.settings = settings
        self.normalizer = normalizer or BlockNormalizer()

    def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
        outputs: list[RawParseOutput] = []
        warnings: list[str] = []

        if self.settings.enable_mineru:
            output, warning = self._try_parse(MineruParser(self.settings), pdf_path, task_id=task_id, work_dir=work_dir)
            if output:
                outputs.append(output)
            else:
                warnings.append(warning or "MinerU parse failed or is unavailable.")

        if self.settings.enable_unstructured:
            output, warning = self._try_parse(UnstructuredParser(), pdf_path, task_id=task_id, work_dir=work_dir)
            if output:
                outputs.append(output)
            else:
                warnings.append(warning or "Unstructured parse failed or is unavailable.")

        if self.settings.enable_pypdf_fallback and not any(output.blocks for output in outputs):
            output, warning = self._try_parse(PypdfTextParser(), pdf_path, task_id=task_id, work_dir=work_dir)
            if output:
                outputs.append(output)
                warnings.extend(output.warnings)
            elif warning:
                warnings.append(warning)

        image_output, image_warning = self._try_parse(PdfImageAssetParser(), pdf_path, task_id=task_id, work_dir=work_dir)
        if image_output:
            outputs.append(image_output)
        elif image_warning:
            warnings.append(image_warning)

        all_blocks: list[PdfBlock] = []
        markdown_parts: list[str] = []
        page_count: int | None = None
        for output in outputs:
            all_blocks.extend(output.blocks)
            warnings.extend(output.warnings)
            if output.markdown:
                markdown_parts.append(output.markdown)
            if output.page_count is not None:
                page_count = max(page_count or output.page_count, output.page_count)

        normalized_blocks = self.normalizer.normalize(all_blocks)
        if not normalized_blocks:
            raise AiTranslationPyError(
                ErrorCode.PDF_PARSE_FAILED,
                "PDF parse failed",
                detail="MinerU, Unstructured, and fallback parser produced no usable blocks.",
                status_code=500,
            )

        return RawParseOutput(
            parser_name=self.strategy_name,
            blocks=normalized_blocks,
            markdown="\n\n".join(markdown_parts) or None,
            page_count=page_count,
            warnings=_dedupe_warnings(warnings),
        )

    @staticmethod
    def _try_parse(
        parser: object,
        pdf_path: Path,
        *,
        task_id: str,
        work_dir: Path,
    ) -> tuple[RawParseOutput | None, str | None]:
        try:
            return parser.parse(pdf_path, task_id=task_id, work_dir=work_dir), None  # type: ignore[attr-defined]
        except AiTranslationPyError as exc:
            detail = f": {exc.detail}" if exc.detail else ""
            return None, f"{exc.error_code} {exc.message}{detail}"
        except Exception as exc:
            return None, f"{parser.__class__.__name__} failed: {exc}"


def _dedupe_warnings(warnings: list[str]) -> list[str]:
    seen: set[str] = set()
    deduped: list[str] = []
    for warning in warnings:
        if warning and warning not in seen:
            seen.add(warning)
            deduped.append(warning)
    return deduped
