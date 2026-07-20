from __future__ import annotations

from pathlib import Path

from ai_translation_py.models.pdf_result import PdfBlock
from ai_translation_py.parsers.base import RawParseOutput


class PdfImageAssetParser:
    """使用 PyMuPDF 提取 PDF 内嵌图片并生成 figure block。

    这个解析器只负责图片资产，不参与文本抽取。即使图片提取失败，Hybrid
    Parser 也会把错误作为 warning 处理，不影响文本解析兜底。
    """

    name = "pymupdf_image"

    def parse(self, pdf_path: Path, *, task_id: str, work_dir: Path) -> RawParseOutput:
        try:
            import fitz
        except Exception as exc:  # pragma: no cover - depends on optional runtime install
            return RawParseOutput(
                parser_name=self.name,
                warnings=[f"PyMuPDF is not available for image extraction: {exc}"],
            )

        data_dir = work_dir.parent.parent
        asset_dir = data_dir / "assets" / task_id / "images"
        asset_dir.mkdir(parents=True, exist_ok=True)

        blocks: list[PdfBlock] = []
        warnings: list[str] = []
        try:
            document = fitz.open(pdf_path)
        except Exception as exc:
            return RawParseOutput(
                parser_name=self.name,
                warnings=[f"PyMuPDF failed to open PDF for image extraction: {exc}"],
            )

        try:
            order = 1
            for page_index, page in enumerate(document, start=1):
                for image_index, image_info in enumerate(page.get_images(full=True), start=1):
                    try:
                        xref = image_info[0]
                        extracted = document.extract_image(xref)
                        image_bytes = extracted.get("image")
                        extension = _normalize_extension(extracted.get("ext"))
                        if not image_bytes:
                            continue
                        asset_id = f"img_p{page_index:04d}_{image_index:04d}_{xref}"
                        asset_path = asset_dir / f"{asset_id}{extension}"
                        asset_path.write_bytes(image_bytes)
                        blocks.append(
                            PdfBlock(
                                blockId=f"figure_raw_{order:06d}",
                                pageNo=page_index,
                                orderNo=order,
                                type="figure",
                                text=f"[image:{asset_id}]",
                                markdown=f"![{asset_id}]({asset_path.as_posix()})",
                                bbox=_image_bbox(page, xref),
                                sourceParser=self.name,
                                metadata={
                                    "assetId": asset_id,
                                    "assetPath": str(asset_path),
                                    "mimeType": _mime_type(extension),
                                    "width": extracted.get("width"),
                                    "height": extracted.get("height"),
                                    "xref": xref,
                                },
                            )
                        )
                        order += 1
                    except Exception as exc:
                        warnings.append(f"Failed to extract image on page {page_index}: {exc}")
        finally:
            document.close()

        return RawParseOutput(
            parser_name=self.name,
            blocks=blocks,
            page_count=None,
            warnings=warnings,
        )


def _normalize_extension(raw_ext: str | None) -> str:
    ext = (raw_ext or "png").lower().strip(".")
    if ext == "jpeg":
        ext = "jpg"
    return f".{ext}"


def _mime_type(extension: str) -> str:
    return {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
        ".bmp": "image/bmp",
        ".tif": "image/tiff",
        ".tiff": "image/tiff",
    }.get(extension.lower(), "application/octet-stream")


def _image_bbox(page, xref: int) -> list[float] | None:
    rects = page.get_image_rects(xref)
    if not rects:
        return None
    rect = rects[0]
    return [float(rect.x0), float(rect.y0), float(rect.x1), float(rect.y1)]
