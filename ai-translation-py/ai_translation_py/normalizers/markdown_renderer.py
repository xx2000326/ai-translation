from __future__ import annotations

from ai_translation_py.models.pdf_result import PdfBlock, PdfParseResult


class MarkdownRenderer:
    """把结构化结果渲染成 Markdown，主要给开发调试和人工查看使用。"""

    def render(self, result: PdfParseResult) -> str:
        lines: list[str] = [
            f"# {result.file_name}",
            "",
            f"- Task: {result.task_id}",
            f"- Status: {result.status}",
            f"- Parser strategy: {result.parser_strategy}",
        ]
        if result.page_count is not None:
            lines.append(f"- Pages: {result.page_count}")
        if result.warnings:
            lines.append("- Warnings:")
            lines.extend(f"  - {warning}" for warning in result.warnings)
        lines.append("")

        last_page: int | None = None
        for block in result.blocks:
            if block.page_no != last_page:
                lines.extend(["", f"<!-- page {block.page_no} -->", ""])
                last_page = block.page_no
            lines.extend(self._render_block(block))
            lines.append("")
        return "\n".join(lines).strip() + "\n"

    @staticmethod
    def _render_block(block: PdfBlock) -> list[str]:
        text = block.text or block.markdown or ""
        if block.type == "title":
            return [block.markdown or f"# {text}"]
        if block.type == "heading":
            return [block.markdown or f"## {text}"]
        if block.type == "list_item":
            return [block.markdown or f"- {text}"]
        if block.type == "table":
            return [block.markdown or text or "[table]"]
        if block.type == "figure":
            return [block.markdown or f"[figure: {text or block.block_id}]"]
        if block.type == "formula":
            return [block.markdown or f"$$\n{text or block.block_id}\n$$"]
        if block.type == "header":
            return [f"> [header] {text}"]
        if block.type == "footer":
            return [f"> [footer] {text}"]
        return [text]
