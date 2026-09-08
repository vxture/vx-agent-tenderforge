# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-08
"""task_id 的请求作用域传递（产品接入通则 X-2）。

``task_id`` 是<b>跨产品唯一的聚合键</b>。这个服务本身不面向 agent，但它是
调用链中间的一环：Java 侧收到的 task_id 必须原样传到这里，再原样带上
出站的模型调用——链条上任何一处换成自产的 request id，整条链就断在那里，
而断掉的表现不是报错，是<b>汇总时那一段消耗归不到任务上</b>。

用 ``ContextVar`` 而不是显式参数：它要穿过十几个业务函数才能到达出站调用点，
把它写进每一层的签名，等于让每个纯业务函数都认识一个传输层概念。
"""

from __future__ import annotations

from contextvars import ContextVar

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

HEADER = "X-Vxture-Task-Id"

# 契约上限。超长视为不合法而不是截断——静默截断会让两侧的键对不上，
# 而对不上的表现是「查得到调用、查不到它属于哪个任务」。
MAX_LENGTH = 128

_task_id: ContextVar[str | None] = ContextVar("vxture_task_id", default=None)


def current_task_id() -> str | None:
    """当前请求的 task_id；调用方没送时为 None。

    为空是合法状态，不要在这里兜底成一个新 UUID：那会造出一个别人查不到的
    假聚合键，比空值更难排查。
    """
    return _task_id.get()


def _normalize(supplied: str | None) -> str | None:
    if supplied is None:
        return None
    trimmed = supplied.strip()
    if not trimmed or len(trimmed) > MAX_LENGTH:
        return None
    return trimmed


class TaskIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        token = _task_id.set(_normalize(request.headers.get(HEADER)))
        try:
            return await call_next(request)
        finally:
            _task_id.reset(token)
