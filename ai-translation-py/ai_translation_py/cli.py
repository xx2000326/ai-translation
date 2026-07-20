from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path
from typing import Any

import uvicorn

from ai_translation_py.config import get_settings
from ai_translation_py.core.logging import setup_logging
from ai_translation_py.services.pdf_task_service import build_pdf_task_service


def _parse_options(raw_options: str | None) -> dict[str, Any]:
    if not raw_options:
        return {}
    options_path = Path(raw_options)
    if options_path.exists():
        return json.loads(options_path.read_text(encoding="utf-8"))
    return json.loads(raw_options)


def _run_api(args: argparse.Namespace) -> int:
    settings = get_settings()
    host = args.host or settings.host
    port = args.port or settings.port
    uvicorn.run("ai_translation_py.api.app:app", host=host, port=port, reload=args.reload)
    return 0


def _parse_pdf(args: argparse.Namespace) -> int:
    service = build_pdf_task_service()
    options = _parse_options(args.options)
    # CLI 选择同步执行：命令结束时 result.json/result.md 已经写好。
    task = service.create_parse_task_from_path(Path(args.pdf_path), options=options, submit=False)
    service.run_parse_task(task.task_id)

    result = service.get_result(task.task_id)
    output_dir = Path(args.output) if args.output else None
    if output_dir:
        output_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(service.storage.result_json_path(task.task_id), output_dir / "result.json")
        shutil.copy2(service.storage.result_markdown_path(task.task_id), output_dir / "result.md")

    print(
        json.dumps(
            {
                "taskId": result.task_id,
                "status": result.status,
                "fileName": result.file_name,
                "pageCount": result.page_count,
                "blockCount": len(result.blocks),
                "resultJson": str(service.storage.result_json_path(task.task_id)),
                "resultMarkdown": str(service.storage.result_markdown_path(task.task_id)),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="ai-translation-py")
    subparsers = parser.add_subparsers(dest="command", required=True)

    api_parser = subparsers.add_parser("api", help="Start the FastAPI service.")
    api_parser.add_argument("--host", default=None)
    api_parser.add_argument("--port", type=int, default=None)
    api_parser.add_argument("--reload", action="store_true")
    api_parser.set_defaults(func=_run_api)

    parse_parser = subparsers.add_parser("parse-pdf", help="Parse a local PDF file.")
    parse_parser.add_argument("pdf_path")
    parse_parser.add_argument("--output", "-o", default=None)
    parse_parser.add_argument("--options", default=None, help="JSON string or path to JSON options.")
    parse_parser.set_defaults(func=_parse_pdf)

    return parser


def main(argv: list[str] | None = None) -> int:
    setup_logging()
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)
