# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-09
"""Atlas 客户端：发出去的是什么，收回来怎么读，什么时候该退避。

这一组盯的都是<b>不会报错的错法</b>：把产品码当 tenantId 送出去，
把上游没报用量的三个 0 当成真实消耗累加，把一个只有运营能解决的授权缺失
当成可重试的容量问题——三者都不抛异常，只会让账目或排查方向静默地错掉。
"""

from __future__ import annotations

import asyncio
import json
from collections.abc import Callable, Iterator
from typing import Any

import httpx
import pytest
from pydantic import BaseModel

from czghagent_ai.services.ai_provider import (
    AiProviderError,
    AiProviderNotConfiguredError,
    AiProviderOutputError,
)
from czghagent_ai.services.atlas_endpoints import (
    DEFAULT_ENDPOINT_CODE,
    ENDPOINT_REQUIREMENTS,
    endpoint_for,
    required_endpoint_codes,
)
from czghagent_ai.services.atlas_provider import (
    AtlasNotEntitledError,
    AtlasProvider,
    AtlasTaskIdMissingError,
    AtlasTokenRejectedError,
)
from czghagent_ai.task_context import _s2s_token, _task_id, _tenant_id

TENANT_UUID = "11111111-2222-3333-4444-555555555555"


class _Answer(BaseModel):
    value: str


@pytest.fixture
def caller() -> Iterator[None]:
    """一个带齐 task_id / 票 / 租户的请求上下文。"""
    task = _task_id.set("task-1")
    token = _s2s_token.set("minted.jwt.value")
    tenant = _tenant_id.set(TENANT_UUID)
    yield
    _task_id.reset(task)
    _s2s_token.reset(token)
    _tenant_id.reset(tenant)


Handler = Callable[[httpx.Request], httpx.Response]


def _provider(
    handler: Handler, **kwargs: Any
) -> tuple[AtlasProvider, list[httpx.Request]]:
    """把一个假 Atlas 挂进传输层，于是断言看到的是真实发出去的字节。"""
    seen: list[httpx.Request] = []

    def _record(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return handler(request)

    provider = AtlasProvider(
        "http://atlas.internal",
        max_retries=1,
        transport=httpx.MockTransport(_record),
        **kwargs,
    )
    return provider, seen


def _run(provider: AtlasProvider, operation: str = "chapter_drafting") -> Any:
    return asyncio.run(provider.run(operation, {"a": 1}, "user-1", _Answer))


def _answers(body: dict[str, Any], status: int = 200) -> Handler:
    return lambda request: httpx.Response(status, json=body)


def _completion(content: str, **extra: Any) -> dict[str, Any]:
    body: dict[str, Any] = {
        "id": "atlas-req-1",
        "modelCode": "deepseek-v4-flash",
        "message": {"role": "assistant", "content": content},
        "usage": {"promptTokens": 120, "completionTokens": 40, "totalTokens": 160},
        "latencyMs": 900,
    }
    body.update(extra)
    return body


# ── endpoint 映射 ──────────────────────────────────────────────────────────


def test_every_operation_has_a_declared_endpoint_requirement() -> None:
    """八个 operation 全部登记在案。

    漏一个的表现是它悄悄落回 chat/default，于是那一个环节用的是别人的生成参数
    ——不会报错，只会让那一段产出变差。
    """
    assert set(ENDPOINT_REQUIREMENTS) == {
        "project_overview_source_selection",
        "project_overview_extraction",
        "technical_scoring_extraction",
        "outline_skeleton_planning",
        "outline_branch_expansion",
        "chapter_drafting",
        "section_revision",
        "consistency_review",
    }


def test_falls_back_to_the_shared_endpoint_until_grants_exist() -> None:
    """专属 endpoint 未授权的那段时间必须能用兜底跑。

    那段时间一定存在（授权是运营侧的一次动作），而在它期间让每次调用都 403
    等于把整个产品停掉。
    """
    assert (
        endpoint_for("chapter_drafting", use_dedicated_endpoints=False)
        == DEFAULT_ENDPOINT_CODE
    )
    assert (
        endpoint_for("chapter_drafting", use_dedicated_endpoints=True)
        == "chat/tenderforge-fast-drafting"
    )


def test_unknown_operation_routes_somewhere_rather_than_failing() -> None:
    assert (
        endpoint_for("a_new_operation", use_dedicated_endpoints=True)
        == DEFAULT_ENDPOINT_CODE
    )


def test_lists_the_endpoints_that_need_granting() -> None:
    """给接入信用的清单：少授权一个，对应 operation 全部 403。"""
    codes = required_endpoint_codes()
    assert codes == sorted(set(codes)), "去重且有序，否则这份清单会被抄错"
    assert "chat/tenderforge-quality-review" in codes


def test_separates_the_review_endpoint_from_the_drafting_one() -> None:
    """审查开 thinking 且温度为 0，正文温度 0.4 且不开 thinking。

    并到一个 endpoint 上，要么审查失去推理深度，要么正文变得刻板重复。
    这条断言保护的是「分档」这件事本身。
    """
    review = ENDPOINT_REQUIREMENTS["consistency_review"]
    drafting = ENDPOINT_REQUIREMENTS["chapter_drafting"]

    assert review.endpoint_code != drafting.endpoint_code
    assert review.thinking is True
    assert drafting.thinking is False
    assert review.temperature == 0.0
    assert drafting.temperature == 0.4


# ── 请求形状 ───────────────────────────────────────────────────────────────


def test_sends_the_fields_atlas_actually_reads(caller: None) -> None:
    provider, seen = _provider(_answers(_completion('{"value":"ok"}')))

    _run(provider)

    body = json.loads(seen[0].content)
    assert body["endpointCode"] == DEFAULT_ENDPOINT_CODE
    assert body["taskId"] == "task-1"
    assert body["requestId"], "requestId 在客户端生成——它是 Atlas 请求日志里唯一的定位手段"
    assert len(body["messages"]) == 2
    assert seen[0].headers["authorization"] == "Bearer minted.jwt.value"


def test_sends_the_tenant_uuid_from_the_token_not_the_product_code(
    caller: None,
) -> None:
    """tenantId 取自票里的 claim。

    送产品码看起来能跑：Atlas 只校验它非空，而产品授权那条路径在租户断言之前
    就返回了。一旦授权缺失或 endpoint 被改指，控制流落到 UUID 断言上，
    失败表现为 400 INVALID_TENANT_ID——读起来像请求体写错了。非 UUID 还会让
    Atlas 的请求日志写进 NULL，本产品的流量就从每一张租户汇总表里消失。
    """
    provider, seen = _provider(_answers(_completion('{"value":"ok"}')))

    _run(provider)

    body = json.loads(seen[0].content)
    assert body["tenantId"] == TENANT_UUID
    assert body["tenantId"] != "tenderforge"


def test_refuses_to_call_without_a_task_id() -> None:
    """Atlas 从 v0.15.0 起强制要求 taskId，缺失即 400。

    在这里提前拒绝，是因为 Atlas 的报错落在网络那一端，而这里能说清楚断的是
    哪一段——task_id 由 Java 入站带入，缺失说明中间某一跳把它丢了。
    """
    token = _s2s_token.set("t")
    try:
        provider, seen = _provider(_answers(_completion('{"value":"ok"}')))
        with pytest.raises(AtlasTaskIdMissingError):
            _run(provider)
        assert not seen, "没有 task_id 就不该把请求送出去"
    finally:
        _s2s_token.reset(token)


def test_refuses_to_call_without_a_minted_token() -> None:
    """票由 Java 侧现铸并转呈；没带票就明确拒绝，不要送出去让 Atlas 回 401。

    401 看起来像凭据配错了，而真相是根本没带——两者要查的地方完全不同。
    """
    task = _task_id.set("task-1")
    try:
        provider, seen = _provider(_answers(_completion('{"value":"ok"}')))
        with pytest.raises(AiProviderNotConfiguredError):
            _run(provider)
        assert not seen
    finally:
        _task_id.reset(task)


def test_refuses_when_atlas_is_not_configured() -> None:
    with pytest.raises(AiProviderNotConfiguredError):
        asyncio.run(AtlasProvider("").run("chapter_drafting", {}, "u", _Answer))


# ── 回答的读法 ─────────────────────────────────────────────────────────────


def test_decodes_structured_output_and_usage(caller: None) -> None:
    provider, _ = _provider(_answers(_completion('{"value":"ok"}')))

    result = _run(provider)

    assert result.data == {"value": "ok"}
    assert result.diagnostics.input_tokens == 120
    assert result.diagnostics.output_tokens == 40


def test_reports_no_usage_rather_than_zero_when_upstream_reported_none(
    caller: None,
) -> None:
    """上游没报用量时 Atlas 返回三个 0，而它自己内部记的是 NULL。

    把这些 0 当成真实消耗累加会让消耗被低估，且看起来一切正常——
    「这次没花 token」和「花了多少没人知道」是两件不同的事。
    """
    body = _completion('{"value":"ok"}')
    body["usage"] = {"promptTokens": 0, "completionTokens": 0, "totalTokens": 0}
    provider, _ = _provider(_answers(body))

    result = _run(provider)

    assert result.diagnostics.input_tokens is None
    assert result.diagnostics.output_tokens is None


def test_extracts_json_from_a_fenced_answer(caller: None) -> None:
    """Atlas 的请求体里没有 response_format，模型可能加代码围栏。

    直连时这件事由 ``response_format=json_object`` 挡掉；迁到 Atlas 之后，
    产品原有的宽容解码器就是<b>唯一</b>的那道防线。
    """
    provider, _ = _provider(_answers(_completion('```json\n{"value":"ok"}\n```')))

    assert _run(provider).data == {"value": "ok"}


def test_reports_truncation_as_such(caller: None) -> None:
    provider, _ = _provider(
        _answers(_completion('{"value":"o', finishReason="length"))
    )

    with pytest.raises(AiProviderOutputError) as failure:
        _run(provider)

    assert "截断" in str(failure.value)


# ── 错误与重试 ─────────────────────────────────────────────────────────────


def test_surfaces_a_rejected_token_without_retrying_it(caller: None) -> None:
    """这个服务不铸票，原样再送一遍同一张必然再被拒。

    重铸由 Java 侧完成——在这里退避重试是纯粹的浪费，而且会把一次凭据问题的
    等待时间乘以重试次数。
    """
    provider, seen = _provider(
        _answers({"code": "S2S_TOKEN_INVALID", "message": "expired"}, status=401)
    )

    with pytest.raises(AtlasTokenRejectedError):
        _run(provider)

    assert len(seen) == 1, "拒票不重试"


def test_names_a_missing_grant_as_such_and_never_retries_it(caller: None) -> None:
    """授权缺失是运营侧的一次动作，退避多久都不会自己变好。

    而且它<b>不是</b>通则那个 NOT_ENTITLED 的含义——那个码说的是「这个用户没买」，
    会被渲染成一句「请先订阅」，把所有人朝错误的方向带。
    """
    provider, seen = _provider(
        _answers({"code": "NOT_ENTITLED", "message": "no grant"}, status=403)
    )

    with pytest.raises(AtlasNotEntitledError) as failure:
        _run(provider)

    assert len(seen) == 1
    assert failure.value.code == "AI_ATLAS_NOT_ENTITLED"
    assert "授权" in str(failure.value)


def test_does_not_retry_a_commercial_ceiling_that_arrives_as_429(caller: None) -> None:
    """QUOTA_EXCEEDED 以 429 到达，长得和会自己恢复的容量闸门一模一样。

    按状态码判会一路重试到耗尽——而只有运营抬高上限才可能成功。
    """
    provider, seen = _provider(
        _answers({"code": "QUOTA_EXCEEDED", "message": "pool exhausted"}, status=429)
    )

    with pytest.raises(AiProviderError):
        _run(provider)

    assert len(seen) == 1, "商业上限不重试"


def test_does_not_retry_a_capability_gap_that_arrives_as_501(caller: None) -> None:
    """MODEL_NOT_IMPLEMENTED 是 501，落在 5xx 里却绝不该重试——
    上游根本没有这个能力，重试只是把超时再付一遍。
    """
    provider, seen = _provider(
        _answers({"code": "MODEL_NOT_IMPLEMENTED", "message": "nope"}, status=501)
    )

    with pytest.raises(AiProviderError):
        _run(provider)

    assert len(seen) == 1


def test_retries_a_transient_upstream_failure(caller: None) -> None:
    calls = {"n": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        calls["n"] += 1
        if calls["n"] == 1:
            return httpx.Response(
                503, json={"code": "PROVIDER_UNAVAILABLE", "message": "down"}
            )
        return httpx.Response(200, json=_completion('{"value":"ok"}'))

    provider, seen = _provider(handler)

    assert _run(provider).data == {"value": "ok"}
    assert len(seen) == 2


def test_lets_the_callee_override_the_local_retry_table(caller: None) -> None:
    """被调方自己说了不能重试，就不重试——即使本地码表没见过这个码。

    反过来让本地硬编的表说了算，等于 Atlas 每加一个新错误码，
    我们就按猜的方式处理它一次。
    """
    provider, seen = _provider(
        _answers(
            {"code": "A_BRAND_NEW_CODE", "message": "x", "retryable": False},
            status=503,
        )
    )

    with pytest.raises(AiProviderError):
        _run(provider)

    assert len(seen) == 1


def test_survives_an_error_body_that_is_not_json(caller: None) -> None:
    """代理页面、被截断的响应——不能因为读不懂错误体就抛一个解析异常。"""
    provider, _ = _provider(
        lambda request: httpx.Response(502, text="<html>bad gateway</html>")
    )

    with pytest.raises(AiProviderError) as failure:
        _run(provider)

    assert "UNKNOWN" in str(failure.value)
