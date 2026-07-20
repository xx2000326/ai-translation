# ai-translation-py

Independent Python service for AI Translation. The first capability is PDF parsing with a MinerU + Unstructured hybrid strategy and a lightweight pypdf text fallback for local development.

## Setup

```bash
uv venv
uv pip install -r requirements.txt
uv pip install -e .
```

## Run API

```bash
uv run ai-translation-py api
```

The default API address is `http://127.0.0.1:8010`.

If you do not want to install the console script during early debugging, use:

```bash
uv run python -m ai_translation_py.main api
```

## Parse PDF Locally

```bash
uv run ai-translation-py parse-pdf ./sample.pdf --output ./output
```

## Main Endpoints

- `POST /api/v1/pdf/parse-tasks`
- `GET /api/v1/pdf/parse-tasks/{taskId}`
- `GET /api/v1/pdf/parse-tasks/{taskId}/result`
- `GET /api/v1/pdf/parse-tasks/{taskId}/result.md`
- `GET /api/v1/pdf/parse-tasks/{taskId}/assets/{assetId}`

PDF images are returned as `figure` blocks in the JSON result. Use `metadata.assetId` with the asset endpoint to download the original image bytes.

## Environment Variables

- `AI_TRANSLATION_PY_HOST`
- `AI_TRANSLATION_PY_PORT`
- `AI_TRANSLATION_PY_DATA_DIR`
- `AI_TRANSLATION_PY_MAX_FILE_MB`
- `AI_TRANSLATION_PY_WORKERS`
- `AI_TRANSLATION_PY_ENABLE_MINERU`
- `AI_TRANSLATION_PY_ENABLE_UNSTRUCTURED`
- `AI_TRANSLATION_PY_ENABLE_PYPDF_FALLBACK`
- `AI_TRANSLATION_PY_OCR_LANG`
- `AI_TRANSLATION_PY_RESULT_TTL_HOURS`
