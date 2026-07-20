from datetime import UTC, datetime

from ai_translation_py.models.pdf_result import PdfBlock, PdfParseResult
from ai_translation_py.normalizers.markdown_renderer import MarkdownRenderer


def test_markdown_renderer_outputs_blocks():
    result = PdfParseResult(
        taskId="pdf_test",
        fileName="sample.pdf",
        pageCount=1,
        parserStrategy="mineru_unstructured_hybrid",
        status="SUCCEEDED",
        blocks=[
            PdfBlock(blockId="b1", pageNo=1, orderNo=1, type="title", text="Title", sourceParser="pypdf"),
            PdfBlock(blockId="b2", pageNo=1, orderNo=2, type="paragraph", text="Body", sourceParser="pypdf"),
        ],
        createdAt=datetime.now(UTC),
        completedAt=datetime.now(UTC),
    )

    markdown = MarkdownRenderer().render(result)

    assert "# sample.pdf" in markdown
    assert "# Title" in markdown
    assert "Body" in markdown
