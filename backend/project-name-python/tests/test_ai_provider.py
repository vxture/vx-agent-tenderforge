# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-05
import asyncio
import json

import httpx
import pytest

from czghagent_ai.config import Settings
from czghagent_ai.model_config import AiModelRequestDialect
from czghagent_ai.services.ai_provider import (
    AiProviderNotConfiguredError,
    AiProviderOutputError,
    AiProviderTimeoutError,
    OpenAiCompatibleProvider,
    _decode_json_object,
)
from czghagent_ai.tender_models import (
    ProjectOverviewDraft,
    ProjectOverviewSourceSelection,
    ReviewResponse,
)


def test_default_settings_use_deepseek_flash(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in (
        "AI_MODEL_API_KEY",
        "AI_MODEL_BASE_URL",
        "AI_MODEL_REQUEST_DIALECT",
        "AI_MODEL_NAME",
        "AI_MODEL_FAST_NAME",
        "AI_MODEL_QUALITY_NAME",
        "AI_MODEL_TIMEOUT_SECONDS",
        "AI_MODEL_STAGE_TIMEOUT_SECONDS",
        "AI_MODEL_MAX_RETRIES",
        "AI_MODEL_THINKING_BUDGET_TOKENS",
        "AI_MODEL_THINKING_DISABLED_OPERATIONS",
        "AI_MODEL_THINKING_ENABLED_OPERATIONS",
    ):
        monkeypatch.delenv(name, raising=False)

    configured = Settings.from_environment()

    assert configured.ai_model_api_key == ""
    assert configured.ai_model_base_url == "https://api.deepseek.com"
    assert configured.ai_model_request_dialect is AiModelRequestDialect.DEEPSEEK
    assert configured.ai_model_name == "deepseek-v4-flash"
    assert configured.ai_model_quality_name == "deepseek-v4-pro"
    assert configured.ai_model_timeout_seconds == 240
    assert configured.ai_model_stage_timeout_seconds == 540
    assert configured.ai_model_max_retries == 1
    assert configured.ai_model_thinking_budget_tokens == 4096
    assert configured.ai_model_thinking_disabled_operations == frozenset(
        {
            "project_overview_source_selection",
            "project_overview_extraction",
            "technical_scoring_extraction",
            "outline_skeleton_planning",
            "outline_branch_expansion",
            "chapter_drafting",
        }
    )
    assert configured.ai_model_thinking_enabled_operations == frozenset(
        {
            "bid_strategy_planning",
            "branch_blueprint_planning",
            "section_revision",
            "consistency_review",
        }
    )


def test_provider_reads_environment_key_and_builds_structured_request() -> None:
    provider = OpenAiCompatibleProvider(
        "sk-test-value", "https://api.deepseek.com/", "deepseek-v4-flash", 30, 0
    )

    request = provider._request("consistency_review", {}, ReviewResponse)

    provider.validate_configuration()
    assert request["model"] == "deepseek-v4-flash"
    assert request["response_format"] == {"type": "json_object"}
    assert request["temperature"] == 0
    assert request["thinking"] == {"type": "enabled"}


def test_provider_routes_quality_operations_and_applies_thinking_policy() -> None:
    provider = OpenAiCompatibleProvider(
        "sk-test-value", "https://api.deepseek.com/", "deepseek-v4-flash", 30, 0,
        quality_model="deepseek-v4-pro",
    )

    draft = provider._request("chapter_drafting", {}, ReviewResponse)
    strategy = provider._request("bid_strategy_planning", {}, ReviewResponse)
    revision = provider._request("section_revision", {}, ReviewResponse)
    skeleton = provider._request("outline_skeleton_planning", {}, ReviewResponse)
    expansion = provider._request("outline_branch_expansion", {}, ReviewResponse)
    blueprint = provider._request("branch_blueprint_planning", {}, ReviewResponse)
    review = provider._request("consistency_review", {}, ReviewResponse)

    assert draft["thinking"] == {"type": "disabled"}
    assert draft["model"] == "deepseek-v4-flash"
    assert draft["temperature"] == 0.4
    assert revision["thinking"] == {"type": "disabled"}
    assert strategy["thinking"] == {"type": "enabled"}
    assert strategy["max_tokens"] == 12288
    assert skeleton["thinking"] == {"type": "disabled"}
    assert expansion["thinking"] == {"type": "disabled"}
    assert expansion["model"] == "deepseek-v4-flash"
    assert skeleton["max_tokens"] == 8192
    assert expansion["max_tokens"] == 6144
    assert blueprint["model"] == "deepseek-v4-pro"
    assert blueprint["max_tokens"] == 12288
    assert review["thinking"] == {"type": "enabled"}


def test_dashscope_dialect_uses_boolean_thinking_field() -> None:
    provider = OpenAiCompatibleProvider(
        "sk-test-value",
        "https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
        "deepseek-v4-flash",
        30,
        0,
        quality_model="deepseek-v4-pro",
        request_dialect=AiModelRequestDialect.DASHSCOPE,
    )

    enabled = provider._request("bid_strategy_planning", {}, ReviewResponse)
    disabled = provider._request("chapter_drafting", {}, ReviewResponse)

    assert enabled["enable_thinking"] is True
    assert enabled["thinking_budget"] == 3072
    assert disabled["enable_thinking"] is False
    assert "thinking" not in enabled
    assert "thinking" not in disabled


def test_openai_dialect_omits_provider_specific_thinking_fields() -> None:
    provider = OpenAiCompatibleProvider(
        "sk-test-value",
        "https://example.com/v1",
        "model",
        30,
        0,
        request_dialect=AiModelRequestDialect.OPENAI,
    )

    request = provider._request("consistency_review", {}, ReviewResponse)

    assert "thinking" not in request
    assert "enable_thinking" not in request


def test_settings_reject_unknown_request_dialect(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AI_MODEL_REQUEST_DIALECT", "unknown")

    with pytest.raises(ValueError, match="AI_MODEL_REQUEST_DIALECT"):
        Settings.from_environment()


def test_provider_rejects_empty_api_key_environment_value() -> None:
    provider = OpenAiCompatibleProvider(
        "  ", "https://example.com/v1", "model", 30, 0
    )

    with pytest.raises(AiProviderNotConfiguredError, match="environment variable"):
        provider.validate_configuration()


def test_provider_reports_model_timeout_with_stage_and_elapsed(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class TimeoutClient:
        async def __aenter__(self) -> "TimeoutClient":
            return self

        async def __aexit__(self, *args: object) -> None:
            return None

        async def post(self, *args: object, **kwargs: object) -> httpx.Response:
            raise httpx.ReadTimeout("model is slow")

    monkeypatch.setattr(
        "czghagent_ai.services.ai_provider.httpx.AsyncClient",
        lambda **kwargs: TimeoutClient(),
    )
    provider = OpenAiCompatibleProvider(
        "sk-test", "https://example.com", "model", 0.1, 0
    )

    with pytest.raises(AiProviderTimeoutError) as raised:
        asyncio.run(provider.run("outline_skeleton_planning", {}, "test", ReviewResponse))

    assert raised.value.code == "AI_MODEL_TIMEOUT"
    assert raised.value.stage == "outline_skeleton_planning"
    assert raised.value.attempts == 1
    assert raised.value.elapsed_millis is not None


def test_thinking_policy_can_be_configured(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv(
        "AI_MODEL_THINKING_DISABLED_OPERATIONS", "chapter_drafting, custom_operation, "
    )

    configured = Settings.from_environment()

    assert configured.ai_model_thinking_disabled_operations == frozenset(
        {"chapter_drafting", "custom_operation"}
    )


def test_project_overview_requests_have_bounded_output_budgets() -> None:
    provider = OpenAiCompatibleProvider(
        "sk-test-value", "https://api.deepseek.com/", "deepseek-v4-flash", 30, 0
    )

    selection = provider._request(
        "project_overview_source_selection", {}, ProjectOverviewSourceSelection
    )
    composition = provider._request(
        "project_overview_extraction", {}, ProjectOverviewDraft
    )

    assert selection["max_tokens"] == 4096
    assert composition["max_tokens"] == 8192


def test_provider_decodes_json_object_response() -> None:
    provider = OpenAiCompatibleProvider("sk-test", "https://example.com", "model", 30, 0)
    response = httpx.Response(
        200,
        json={"choices": [{"message": {"content": json.dumps({"issues": []})}}]},
    )

    result = provider._decode_response(response)

    assert result.data == {"issues": []}
    assert result.diagnostics.response_length == len(json.dumps({"issues": []}))
    assert len(result.diagnostics.response_hash) == 64


def test_provider_reports_length_truncation_separately() -> None:
    provider = OpenAiCompatibleProvider("sk-test", "https://example.com", "model", 30, 0)
    content = '{"projectOverview":"unfinished'
    response = httpx.Response(
        200,
        json={
            "choices": [
                {"message": {"content": content}, "finish_reason": "length"}
            ],
            "usage": {
                "prompt_tokens": 120,
                "completion_tokens": 80,
                "prompt_tokens_details": {"cached_tokens": 40},
                "completion_tokens_details": {"reasoning_tokens": 30},
            },
        },
    )

    with pytest.raises(AiProviderOutputError) as raised:
        provider._decode_response(response)

    assert "truncated" in str(raised.value)
    assert raised.value.finish_reason == "length"
    assert raised.value.response_length == len(content)
    assert raised.value.details()["inputTokens"] == 120
    assert raised.value.details()["outputTokens"] == 80
    assert raised.value.details()["reasoningTokens"] == 30
    assert raised.value.details()["cachedInputTokens"] == 40


def test_provider_repairs_fence_prose_and_trailing_comma() -> None:
    decoded = _decode_json_object(
        '以下是结果：\n```json\n{"projectOverview":"项目内容",}\n```\n请查收'
    )

    assert decoded == {"projectOverview": "项目内容"}


def test_provider_does_not_repair_comma_inside_text() -> None:
    decoded = _decode_json_object('{"projectOverview":"保留合法文本,}"}')

    assert decoded == {"projectOverview": "保留合法文本,}"}
