from ai_translation_py.models.pdf_result import PdfBlock
from ai_translation_py.normalizers.block_normalizer import BlockNormalizer


def test_normalizer_sorts_and_generates_block_ids():
    blocks = [
        PdfBlock(blockId="b2", pageNo=1, orderNo=2, type="paragraph", text="second", sourceParser="pypdf", bbox=[0, 20, 10, 30]),
        PdfBlock(blockId="b1", pageNo=1, orderNo=1, type="paragraph", text="first", sourceParser="pypdf", bbox=[0, 10, 10, 20]),
    ]

    normalized = BlockNormalizer().normalize(blocks)

    assert [block.text for block in normalized] == ["first", "second"]
    assert [block.block_id for block in normalized] == ["block_000001", "block_000002"]
    assert [block.order_no for block in normalized] == [1, 2]


def test_normalizer_prefers_mineru_for_duplicate_text():
    blocks = [
        PdfBlock(blockId="u", pageNo=1, orderNo=1, type="paragraph", text="same text", sourceParser="unstructured"),
        PdfBlock(blockId="m", pageNo=1, orderNo=2, type="paragraph", text="same text", sourceParser="mineru"),
    ]

    normalized = BlockNormalizer().normalize(blocks)

    assert len(normalized) == 1
    assert normalized[0].source_parser == "mineru"
