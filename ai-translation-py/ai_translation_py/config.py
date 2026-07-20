from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """集中读取服务配置。

    BaseSettings 会自动从环境变量读取值，例如字段 `port` 对应
    `AI_TRANSLATION_PY_PORT`。这样本地、测试、生产可以用同一套代码，
    只通过环境变量调整行为。
    """

    model_config = SettingsConfigDict(
        env_prefix="AI_TRANSLATION_PY_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    host: str = "127.0.0.1"
    port: int = 8010
    data_dir: Path = Path("./data")
    max_file_mb: int = 200
    workers: int = 2
    enable_mineru: bool = True
    enable_unstructured: bool = True
    enable_pypdf_fallback: bool = True
    ocr_lang: str = "ch,en"
    result_ttl_hours: int = 72

    mineru_backend: str = "pipeline"
    mineru_timeout_seconds: int = Field(default=1800, ge=1)

    @property
    def max_file_bytes(self) -> int:
        # 上传接口校验用字节数，配置里保留 MB 更方便人阅读。
        return self.max_file_mb * 1024 * 1024

    @property
    def uploads_dir(self) -> Path:
        return self.data_dir / "uploads"

    @property
    def results_dir(self) -> Path:
        return self.data_dir / "results"

    @property
    def assets_dir(self) -> Path:
        return self.data_dir / "assets"

    @property
    def tasks_dir(self) -> Path:
        return self.data_dir / "tasks"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
