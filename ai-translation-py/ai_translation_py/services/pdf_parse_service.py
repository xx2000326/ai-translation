from __future__ import annotations

import logging
from datetime import UTC, datetime

from ai_translation_py.config import Settings
from ai_translation_py.core.errors import AiTranslationPyError, ErrorCode
from ai_translation_py.core.task_status import TaskStatus
from ai_translation_py.models.pdf_result import PdfParseResult
from ai_translation_py.normalizers.markdown_renderer import MarkdownRenderer
from ai_translation_py.parsers.hybrid_pdf_parser import HybridPdfParser
from ai_translation_py.storage.local_storage import LocalStorage
from ai_translation_py.storage.task_repository import TaskRepository

logger = logging.getLogger(__name__)


class PdfParseService:
    """真正执行 PDF 解析的服务。

    这个类只关心一个已保存的 task_id：读取 source.pdf，调用混合解析器，
    写 result.json/result.md，并更新任务状态。
    """

    def __init__(
        self,
        settings: Settings,
        storage: LocalStorage,
        repository: TaskRepository,
        parser: HybridPdfParser | None = None,
        renderer: MarkdownRenderer | None = None,
    ) -> None:
        self.settings = settings
        self.storage = storage
        self.repository = repository
        self.parser = parser or HybridPdfParser(settings)
        self.renderer = renderer or MarkdownRenderer()

    def run_parse_task(self, task_id: str) -> None:
        logger.info("pdf_parse_start taskId=%s", task_id)
        self.repository.update(
            task_id,
            lambda task: task.model_copy(
                update={
                    "status": TaskStatus.RUNNING,
                    "progress": 5,
                    "current_step": "hybrid_parse",
                    "error_code": None,
                    "error_message": None,
                }
            ),
        )

        task = self.repository.get(task_id)
        try:
            pdf_path = self.storage.upload_pdf_path(task_id)
            work_dir = self.storage.work_dir(task_id)
            work_dir.mkdir(parents=True, exist_ok=True)
            # HybridPdfParser 会按 MinerU -> Unstructured -> pypdf 兜底的顺序尝试。
            raw_output = self.parser.parse(pdf_path, task_id=task_id, work_dir=work_dir)

            completed_at = datetime.now(UTC)
            result = PdfParseResult(
                taskId=task_id,
                fileName=task.file_name,
                pageCount=raw_output.page_count,
                parserStrategy=HybridPdfParser.strategy_name,
                status=TaskStatus.SUCCEEDED,
                warnings=[*task.warnings, *raw_output.warnings],
                blocks=raw_output.blocks,
                markdown=None,
                plainText=_render_plain_text(raw_output.blocks),
                createdAt=task.created_at,
                completedAt=completed_at,
            )
            markdown = self.renderer.render(result)
            result = result.model_copy(update={"markdown": markdown})

            # 先写临时文件再原子替换，避免调用方读到半截 JSON/Markdown。
            self.storage.write_json_atomic(
                self.storage.result_json_path(task_id),
                result.model_dump(mode="json", by_alias=True),
            )
            self.storage.write_text_atomic(self.storage.result_markdown_path(task_id), markdown)

            self.repository.update(
                task_id,
                lambda existing: existing.model_copy(
                    update={
                        "status": TaskStatus.SUCCEEDED,
                        "progress": 100,
                        "current_step": "completed",
                        "warnings": result.warnings,
                    }
                ),
            )
            logger.info("pdf_parse_succeeded taskId=%s blockCount=%s", task_id, len(result.blocks))
        except AiTranslationPyError as exc:
            self._mark_failed(task_id, exc.error_code, exc.message)
            logger.exception("pdf_parse_failed taskId=%s errorCode=%s", task_id, exc.error_code)
        except Exception as exc:
            self._mark_failed(task_id, ErrorCode.PDF_PARSE_FAILED, str(exc))
            logger.exception("pdf_parse_failed taskId=%s", task_id)

    def _mark_failed(self, task_id: str, error_code: str, error_message: str) -> None:
        self.repository.update(
            task_id,
            lambda task: task.model_copy(
                update={
                    "status": TaskStatus.FAILED,
                    "progress": 100,
                    "current_step": "failed",
                    "error_code": error_code,
                    "error_message": error_message,
                }
            ),
        )


def _render_plain_text(blocks) -> str:
    """渲染给 Java 拆分引擎使用的纯文本。

    JSON 仍保留 blocks 作为主结构；plainText 是一个适配字段，方便 Java 在
    Python 解析成功后继续复用现有清洗/分块/落库流程。
    """

    translatable_types = {
        "title",
        "heading",
        "paragraph",
        "list_item",
        "table",
        "figure",
        "header",
        "footer",
        "unknown",
    }
    parts: list[str] = []
    for block in blocks:
        if block.type not in translatable_types:
            continue
        text = block.text or block.markdown
        if not text:
            continue
        text = text.strip()
        if not text:
            continue
        if block.type == "title":
            parts.append(f"# {text}")
        elif block.type == "heading":
            parts.append(f"## {text}")
        elif block.type == "list_item":
            parts.append(text if text.startswith(("-", "*")) else f"- {text}")
        elif block.type == "figure":
            asset_id = block.metadata.get("assetId") if block.metadata else None
            parts.append(f"[PDF image: {asset_id or block.block_id}]")
        else:
            parts.append(text)
    return "\n\n".join(parts)
