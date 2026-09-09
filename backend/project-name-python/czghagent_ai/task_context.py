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

#: Java 侧现铸并转呈的 Atlas S2S 票，以及票里解出的租户。
#:
#: 票不在这个进程里铸：铸票凭据就是本产品的 OIDC client 对，把它复制进来
#: 意味着产品身份凭据有了第二份副本和第二个轮换点。票只活 300 秒，
#: 也确实没有别的用法——它不可能被粘进任何配置文件。
S2S_TOKEN_HEADER = "X-Vxture-S2S-Token"
TENANT_HEADER = "X-Vxture-Tenant-Id"

# 契约上限。超长视为不合法而不是截断——静默截断会让两侧的键对不上，
# 而对不上的表现是「查得到调用、查不到它属于哪个任务」。
MAX_LENGTH = 128

_task_id: ContextVar[str | None] = ContextVar("vxture_task_id", default=None)
_s2s_token: ContextVar[str | None] = ContextVar("vxture_s2s_token", default=None)
_tenant_id: ContextVar[str | None] = ContextVar("vxture_tenant_id", default=None)


def current_task_id() -> str | None:
    """当前请求的 task_id；调用方没送时为 None。

    为空是合法状态，不要在这里兜底成一个新 UUID：那会造出一个别人查不到的
    假聚合键，比空值更难排查。
    """
    return _task_id.get()


def current_atlas_identity() -> tuple[str | None, str | None]:
    """本次请求可用的 (S2S 票, 租户 id)。

    两者都可能为空：Java 侧没配平台凭据时铸不出票。返回空让调用点显式拒绝，
    <b>不要</b>在这里兜底——一次没有票的 Atlas 调用会被 401 挡住，
    而那个错误看起来像凭据配错了，不像根本没带。
    """
    return _s2s_token.get(), _tenant_id.get()


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
        task = _task_id.set(_normalize(request.headers.get(HEADER)))
        # 票不做长度归一：它是 JWT，本来就长，而 MAX_LENGTH 是给 task_id 的
        # 契约上限。用同一个函数处理会把每一张票都清成 None，
        # 表现是「配了平台凭据但每次调用都说没带票」。
        s2s = _s2s_token.set(_blank_to_none(request.headers.get(S2S_TOKEN_HEADER)))
        tenant = _tenant_id.set(_blank_to_none(request.headers.get(TENANT_HEADER)))
        try:
            return await call_next(request)
        finally:
            _task_id.reset(task)
            _s2s_token.reset(s2s)
            _tenant_id.reset(tenant)


def _blank_to_none(supplied: str | None) -> str | None:
    trimmed = (supplied or "").strip()
    return trimmed or None
