from __future__ import annotations

import json
import os
import shutil
from pathlib import Path
from typing import Any

from ai_translation_py.config import Settings


class LocalStorage:
    """本地文件存储。

    初版先把任务、上传文件和结果都放到 data/ 目录。后续接 MinIO 时，
    可以保持 service 层不变，只替换 storage 的实现。
    """

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.ensure_base_dirs()

    def ensure_base_dirs(self) -> None:
        for path in (
            self.settings.uploads_dir,
            self.settings.results_dir,
            self.settings.assets_dir,
            self.settings.tasks_dir,
        ):
            path.mkdir(parents=True, exist_ok=True)

    def upload_dir(self, task_id: str) -> Path:
        return self.settings.uploads_dir / task_id

    def upload_pdf_path(self, task_id: str) -> Path:
        return self.upload_dir(task_id) / "source.pdf"

    def result_dir(self, task_id: str) -> Path:
        return self.settings.results_dir / task_id

    def result_json_path(self, task_id: str) -> Path:
        return self.result_dir(task_id) / "result.json"

    def result_markdown_path(self, task_id: str) -> Path:
        return self.result_dir(task_id) / "result.md"

    def task_path(self, task_id: str) -> Path:
        return self.settings.tasks_dir / f"{task_id}.json"

    def work_dir(self, task_id: str) -> Path:
        return self.settings.data_dir / "work" / task_id

    def asset_dir(self, task_id: str) -> Path:
        return self.settings.assets_dir / task_id

    def image_asset_dir(self, task_id: str) -> Path:
        return self.asset_dir(task_id) / "images"

    def image_asset_path(self, task_id: str, asset_id: str, extension: str) -> Path:
        return self.image_asset_dir(task_id) / f"{asset_id}{extension}"

    def find_asset_path(self, task_id: str, asset_id: str) -> Path | None:
        image_dir = self.image_asset_dir(task_id)
        if not image_dir.exists():
            return None
        matches = list(image_dir.glob(f"{asset_id}.*"))
        return matches[0] if matches else None

    def copy_source_pdf(self, source_path: Path, task_id: str) -> Path:
        target = self.upload_pdf_path(task_id)
        target.parent.mkdir(parents=True, exist_ok=True)
        tmp = target.with_suffix(".pdf.tmp")
        shutil.copy2(source_path, tmp)
        os.replace(tmp, target)
        return target

    def write_json_atomic(self, path: Path, payload: dict[str, Any]) -> None:
        # Windows 上 os.replace 也能覆盖已有文件，适合做简单的原子写入。
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        os.replace(tmp, path)

    def write_text_atomic(self, path: Path, text: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(text, encoding="utf-8")
        os.replace(tmp, path)
