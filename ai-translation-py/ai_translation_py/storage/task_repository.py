from __future__ import annotations

import json
import threading
from datetime import UTC, datetime
from typing import Callable

from ai_translation_py.core.errors import AiTranslationPyError, ErrorCode
from ai_translation_py.models.task import TaskInfo
from ai_translation_py.storage.local_storage import LocalStorage


class TaskRepository:
    """用 JSON 文件保存任务状态。

    每个任务一个 data/tasks/{taskId}.json。读写时加锁，是为了避免同一进程
    内后台解析线程和 API 查询/更新同时操作同一个任务文件。
    """

    def __init__(self, storage: LocalStorage) -> None:
        self.storage = storage
        self._lock = threading.RLock()

    def save(self, task: TaskInfo) -> TaskInfo:
        with self._lock:
            payload = task.model_dump(mode="json", by_alias=True)
            self.storage.write_json_atomic(self.storage.task_path(task.task_id), payload)
            return task

    def get(self, task_id: str) -> TaskInfo:
        path = self.storage.task_path(task_id)
        if not path.exists():
            raise AiTranslationPyError(
                ErrorCode.PDF_TASK_NOT_FOUND,
                "PDF parse task was not found",
                status_code=404,
            )
        data = json.loads(path.read_text(encoding="utf-8"))
        return TaskInfo.model_validate(data)

    def update(self, task_id: str, updater: Callable[[TaskInfo], TaskInfo]) -> TaskInfo:
        with self._lock:
            task = self.get(task_id)
            updated = updater(task).model_copy(update={"updated_at": datetime.now(UTC)})
            return self.save(updated)
