# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-08
"""《产品接入通则》在 Python 面上的条款。

这一组用例保护的是<b>契约</b>而不是实现：错误封套的必备字段、被调方与调用方
共用同一种信封、task_id 的原样传递。它们的共同点是——违反了不会崩，
只会安静地给出一个形状不同但看起来正常的响应，而那正是新接入方最先撞上的东西。
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from czghagent_ai import brand
from czghagent_ai.app import app
from czghagent_ai.errors import RETRYABLE_CODES, ServiceError, envelope
from czghagent_ai.task_context import HEADER, MAX_LENGTH, current_task_id


@pytest.fixture
def client() -> TestClient:
    # raise_server_exceptions=False 才能拿到兜底处理器真正返回的 500 响应；
    # 默认值会把异常直接抛给测试，于是永远测不到那条分支——而它恰好是
    # 唯一一条「出问题时调用方看到什么」的路径。
    return TestClient(app, raise_server_exceptions=False)


# ── X-1 错误封套 ────────────────────────────────────────────────────────────


def test_envelope_carries_the_three_mandatory_fields() -> None:
    body = envelope("AI_MODEL_TIMEOUT", "超时", True)

    assert set(body) == {"code", "message", "retryable"}


def test_envelope_omits_the_field_slot_instead_of_sending_null() -> None:
    """一个恒为 null 的键会让调用方以为它有时候有值，然后写一条永远不进的分支。"""
    assert "field" not in envelope("X", "y", False)
    assert envelope("X", "y", False, "title")["field"] == "title"


def test_retryable_is_derived_from_whether_waiting_can_help() -> None:
    assert ServiceError("AI_MODEL_TIMEOUT", "超时", 504).retryable is True
    assert ServiceError("AI_PROVIDER_NOT_CONFIGURED", "未配置", 503).retryable is False
    assert ServiceError("AI_OUTPUT_INVALID", "结构不合法", 502).retryable is False
    assert "AI_PROVIDER_NOT_CONFIGURED" not in RETRYABLE_CODES


def test_explicit_retryable_overrides_the_code_derived_default() -> None:
    assert ServiceError("AI_MODEL_TIMEOUT", "x", 504, retryable=False).retryable is False


# ── 每一条离开本服务的失败都用同一个封套 ────────────────────────────────────


def test_missing_internal_token_answers_the_platform_envelope(client: TestClient) -> None:
    response = client.post("/internal/parse")

    assert response.status_code == 401
    assert response.json() == {
        "code": "AUTH_INTERNAL_TOKEN_INVALID",
        "message": "内部服务令牌无效",
        "retryable": False,
    }


def test_unknown_route_does_not_leak_the_frameworks_own_envelope(client: TestClient) -> None:
    """路由未命中的 404 由 Starlette 自己抛，抛的是基类。

    只把处理器注册在 FastAPI 子类上时，这一面最常被撞到的响应恰好是唯一漏网的
    ——实测它会以 ``{"detail": "Not Found"}`` 的形状溜出去，也就是这个服务的第二种信封。
    """
    response = client.get("/internal/does-not-exist")

    assert response.status_code == 404
    assert response.json()["code"] == "REQUEST_ROUTE_NOT_FOUND"
    assert "detail" not in response.json()


def test_method_not_allowed_also_uses_the_envelope(client: TestClient) -> None:
    response = client.get("/internal/parse")

    assert response.status_code == 405
    assert response.json()["code"] == "REQUEST_METHOD_NOT_ALLOWED"


def test_validation_failure_names_the_offending_field(client: TestClient) -> None:
    response = client.post(
        "/internal/tender/chapter",
        headers={"X-Internal-Token": "local-development-token"},
        json={},
    )

    body = response.json()
    assert response.status_code == 422
    assert body["code"] == "REQUEST_VALIDATION_FAILED"
    assert body["retryable"] is False
    assert body["field"], "字段级错误必须指出是哪个字段，否则调用方只能逐个试"


# ── X-2 task_id ─────────────────────────────────────────────────────────────


def test_task_id_is_absent_by_default_rather_than_invented() -> None:
    """为空是合法状态。

    在这里兜底成一个新 UUID 会造出一个对方库里查不到的假聚合键，
    而假键比空值更难排查：它让「查得到调用、查不到它属于哪个任务」
    看起来像是对方丢了数据。
    """
    assert current_task_id() is None


def test_task_id_middleware_normalizes_what_the_caller_sent() -> None:
    captured: list[str | None] = []

    @app.get("/__task_probe")  # type: ignore[misc]
    async def probe() -> dict[str, str]:
        captured.append(current_task_id())
        return {"ok": "1"}

    with TestClient(app) as probe_client:
        probe_client.get("/__task_probe", headers={HEADER: "  task-1  "})
        probe_client.get("/__task_probe", headers={HEADER: "   "})
        probe_client.get("/__task_probe", headers={HEADER: "x" * (MAX_LENGTH + 1)})
        probe_client.get("/__task_probe")

    assert captured == [
        "task-1",  # 两侧空白被裁掉
        None,  # 空白等于没送
        None,  # 超长视为不合法而不是截断——截断会让两侧的键对不上
        None,  # 没送
    ]


def test_task_id_does_not_leak_between_requests() -> None:
    """请求作用域必须干净。

    ContextVar 漏出去的表现是「下一个请求带着上一个任务的 id」，
    而那在审计里表现为两个任务的消耗被合并，且没有任何报错。
    """
    with TestClient(app) as probe_client:
        probe_client.get("/health", headers={HEADER: "task-leak-check"})

    assert current_task_id() is None


# ── 运行时面 ────────────────────────────────────────────────────────────────


def test_liveness_probe_has_no_dependencies(client: TestClient) -> None:
    """存活探针按契约零依赖：在这里检查模型配置会让一次上游抖动重启掉整个容器。"""
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


# ── 产品身份 ────────────────────────────────────────────────────────────────


def test_product_code_matches_the_platform_registration_format() -> None:
    import re

    assert re.fullmatch(r"[a-z][a-z0-9_-]{0,31}", brand.PRODUCT_CODE)
    assert brand.PRODUCT_CODE == "tenderforge"
