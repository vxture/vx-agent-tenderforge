# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-06
import asyncio
from typing import Any

import pytest

from czghagent_ai.services.ai_provider import (
    AiProviderDiagnostics,
    AiProviderOutputError,
    AiProviderResult,
)
from czghagent_ai.services.structured_output import (
    AiStructuredExecutor,
    AiStructuredOutputError,
    correction_payload,
)
from czghagent_ai.tender_models import ProjectOverviewResponse


class SequenceProvider:
    def __init__(self, responses: list[dict[str, Any]]) -> None:
        self.responses = responses
        self.calls: list[dict[str, Any]] = []

    async def run(
        self,
        operation: str,
        payload: dict[str, Any],
        user: str,
        response_model: type[object],
    ) -> dict[str, Any]:
        del operation, user, response_model
        self.calls.append(payload)
        return self.responses[min(len(self.calls) - 1, len(self.responses) - 1)]


def test_executor_normalizes_known_field_alias() -> None:
    provider = SequenceProvider([{"overview": "## 项目概述\n建设统一平台"}])
    executor = AiStructuredExecutor(provider)

    result = asyncio.run(executor.execute(
        "project_overview_extraction",
        {"segments": []},
        "test",
        ProjectOverviewResponse,
        object_name="项目概述",
        schema_version="overview-v2",
    ))

    assert result.project_overview.startswith("## 项目概述")
    assert len(provider.calls) == 1


def test_executor_corrects_only_the_invalid_object() -> None:
    provider = SequenceProvider([
        {"wrong": {"value": "invalid"}},
        {"projectOverview": "纠正后的项目概述"},
    ])
    executor = AiStructuredExecutor(provider)

    result = asyncio.run(executor.execute(
        "project_overview_extraction",
        {"documentId": "doc-1"},
        "test",
        ProjectOverviewResponse,
        object_name="项目概述",
        schema_version="overview-v2",
    ))

    assert result.project_overview == "纠正后的项目概述"
    assert provider.calls[1]["originalInput"] == {"documentId": "doc-1"}
    assert provider.calls[1]["validationErrors"]


def test_executor_accumulates_usage_across_structure_repair() -> None:
    class DiagnosticProvider:
        def __init__(self) -> None:
            self.calls = 0

        async def run(
            self,
            operation: str,
            payload: dict[str, Any],
            user: str,
            response_model: type[object],
        ) -> AiProviderResult:
            del operation, payload, user, response_model
            self.calls += 1
            data: dict[str, Any] = (
                {"wrong": {"value": "invalid"}}
                if self.calls == 1
                else {"projectOverview": "纠正后的项目概述"}
            )
            return AiProviderResult(data, AiProviderDiagnostics(
                finish_reason="stop",
                response_length=10 * self.calls,
                response_hash=str(self.calls) * 64,
                input_tokens=100 * self.calls,
                output_tokens=20 * self.calls,
                reasoning_tokens=5 * self.calls,
                cached_input_tokens=40 * self.calls,
            ))

    result = asyncio.run(AiStructuredExecutor(DiagnosticProvider()).execute_result(
        "project_overview_extraction",
        {"documentId": "doc-1"},
        "test",
        ProjectOverviewResponse,
        object_name="项目概述",
        schema_version="overview-v2",
    ))

    assert result.attempts == 2
    assert result.diagnostics.input_tokens == 300
    assert result.diagnostics.output_tokens == 60
    assert result.diagnostics.reasoning_tokens == 15
    assert result.diagnostics.cached_input_tokens == 120
    assert result.diagnostics.response_length == 30


def test_executor_reports_field_level_diagnostics_after_correction() -> None:
    provider = SequenceProvider([{}, {}])
    executor = AiStructuredExecutor(provider)

    with pytest.raises(AiStructuredOutputError) as raised:
        asyncio.run(executor.execute(
            "project_overview_extraction",
            {"documentId": "doc-1"},
            "test",
            ProjectOverviewResponse,
            object_name="项目概述",
            schema_version="overview-v2",
        ))

    details = raised.value.details()
    assert details["objectName"] == "项目概述"
    assert details["schemaVersion"] == "overview-v2"
    assert details["attempts"] == 2
    validation_errors = details["validationErrors"]
    assert isinstance(validation_errors, list)
    assert any(
        isinstance(item, str) and "projectOverview" in item
        for item in validation_errors
    )


def test_executor_accumulates_usage_when_structure_repair_fails() -> None:
    class InvalidOutputProvider:
        def __init__(self) -> None:
            self.calls = 0

        async def run(
            self,
            operation: str,
            payload: dict[str, Any],
            user: str,
            response_model: type[object],
        ) -> dict[str, Any]:
            del operation, payload, user, response_model
            self.calls += 1
            raise AiProviderOutputError(
                "invalid JSON",
                raw_output="{",
                response_length=1,
                response_hash=str(self.calls) * 64,
                input_tokens=100 * self.calls,
                output_tokens=20 * self.calls,
                reasoning_tokens=5 * self.calls,
                cached_input_tokens=40 * self.calls,
                attempts=1,
            )

    with pytest.raises(AiStructuredOutputError) as raised:
        asyncio.run(AiStructuredExecutor(InvalidOutputProvider()).execute(
            "project_overview_extraction",
            {"documentId": "doc-1"},
            "test",
            ProjectOverviewResponse,
            object_name="项目概述",
            schema_version="overview-v2",
        ))

    details = raised.value.details()
    assert details["attempts"] == 2
    assert details["inputTokens"] == 300
    assert details["outputTokens"] == 60
    assert details["reasoningTokens"] == 15
    assert details["cachedInputTokens"] == 120


def test_executor_retries_semantically_incomplete_object() -> None:
    provider = SequenceProvider([
        {"projectOverview": "不完整内容"},
        {"projectOverview": "完整内容，包含验收要求"},
    ])
    executor = AiStructuredExecutor(provider)

    result = asyncio.run(executor.execute(
        "project_overview_extraction",
        {"documentId": "doc-1"},
        "test",
        ProjectOverviewResponse,
        object_name="项目概述",
        schema_version="overview-v2",
        semantic_validator=lambda response: (
            [] if "验收要求" in response.project_overview else ["缺少验收要求"]
        ),
    ))

    assert result.project_overview == "完整内容，包含验收要求"
    assert provider.calls[1]["validationErrors"] == ["缺少验收要求"]


def test_executor_rejects_object_that_remains_semantically_incomplete() -> None:
    provider = SequenceProvider([
        {"projectOverview": "仍不完整"},
        {"projectOverview": "仍不完整"},
    ])
    executor = AiStructuredExecutor(provider)

    with pytest.raises(AiStructuredOutputError) as raised:
        asyncio.run(executor.execute(
            "project_overview_extraction",
            {"documentId": "doc-1"},
            "test",
            ProjectOverviewResponse,
            object_name="项目概述",
            schema_version="overview-v2",
            semantic_validator=lambda response: ["缺少验收要求"],
        ))

    assert raised.value.details()["validationErrors"] == ["缺少验收要求"]


def test_project_overview_schema_enforces_prompt_length_limit() -> None:
    with pytest.raises(ValueError):
        ProjectOverviewResponse(project_overview="x" * 6501)


def test_truncation_correction_does_not_echo_the_entire_oversized_draft() -> None:
    payload = correction_payload(
        {"documentId": "doc-1"},
        "x" * 20_000,
        ["AI model output was truncated before completing JSON"],
        "项目概述",
    )

    assert payload["invalidOutput"] == ""
    assert "maxLength" in payload["correctionInstruction"]
