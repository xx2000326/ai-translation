from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from ai_translation_py.api.routes_pdf import router as pdf_router
from ai_translation_py.core.errors import AiTranslationPyError
from ai_translation_py.core.logging import setup_logging


def create_app() -> FastAPI:
    setup_logging()
    app = FastAPI(title="ai-translation-py", version="0.1.0")
    app.include_router(pdf_router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.exception_handler(AiTranslationPyError)
    async def handle_ai_translation_py_error(
        request: Request,
        exc: AiTranslationPyError,
    ) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=exc.to_response())

    return app


app = create_app()
