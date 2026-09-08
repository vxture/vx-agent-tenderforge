# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-11
import asyncio
from typing import Any

from czghagent_ai.services.project_overview import (
    ProjectOverviewExtractor,
    _clip_text,
    _evenly_cap,
    _normalize_draft,
    _render_markdown,
)
from czghagent_ai.services.structured_output import AiStructuredExecutor
from czghagent_ai.tender_models import InterpretationRequest, ProjectOverviewDraft


class ProjectOverviewProvider:
    def __init__(self) -> None:
        self.calls: list[tuple[str, dict[str, Any]]] = []

    async def run(
        self,
        operation: str,
        payload: dict[str, Any],
        user: str,
        response_model: type[object],
    ) -> dict[str, Any]:
        del user, response_model
        self.calls.append((operation, payload))
        if operation == "project_overview_source_selection":
            return {
                "orderedSegmentIds": [
                    "segment-0003",
                    "unknown",
                    "segment-0001",
                    "segment-0003",
                ]
            }
        return {
            "sections": [
                {"title": "## 项目名称及目标", "content": "建设统一技术平台。"},
                {"title": "交付与验收", "content": "完成部署并通过验收。"},
            ]
        }


def test_overview_selects_and_orders_evidence_before_composition() -> None:
    provider = ProjectOverviewProvider()
    extractor = ProjectOverviewExtractor(AiStructuredExecutor(provider))
    request = InterpretationRequest.model_validate({
        "requestId": "overview-test",
        "documentId": "doc-1",
        "title": "测试项目",
        "biddingMode": "OPEN",
        "segments": [
            {"locatorType": "PAGE", "locator": "第1页", "text": "项目名称"},
            {"locatorType": "PAGE", "locator": "第2页", "text": "投标程序"},
            {"locatorType": "TABLE", "locator": "第3页", "text": "验收要求"},
        ],
    })

    result = asyncio.run(extractor.extract(request))

    assert result.data.project_overview == (
        "## 项目名称及目标\n\n建设统一技术平台。\n\n"
        "## 交付与验收\n\n完成部署并通过验收。"
    )
    composition = provider.calls[1][1]
    assert [item["id"] for item in composition["selectedSegments"]] == [
        "segment-0001",
        "segment-0003",
    ]
    assert result.attempts == 2


def test_overview_schema_keeps_rendered_markdown_under_api_limit() -> None:
    class MaximumProvider(ProjectOverviewProvider):
        async def run(
            self,
            operation: str,
            payload: dict[str, Any],
            user: str,
            response_model: type[object],
        ) -> dict[str, Any]:
            del payload, user, response_model
            if operation == "project_overview_source_selection":
                return {"orderedSegmentIds": ["segment-0001"]}
            return {
                "sections": [
                    {"title": str(index) * 80, "content": "内" * 700}
                    for index in range(1, 9)
                ]
            }

    request = InterpretationRequest.model_validate({
        "requestId": "overview-max",
        "documentId": "doc-1",
        "title": "测试项目",
        "biddingMode": "OPEN",
        "segments": [
            {"locatorType": "PAGE", "locator": "第1页", "text": "项目内容"},
        ],
    })

    result = asyncio.run(
        ProjectOverviewExtractor(AiStructuredExecutor(MaximumProvider())).extract(request)
    )

    assert len(result.data.project_overview) <= 6500


def test_selected_source_clipping_preserves_both_ends() -> None:
    source = "A" * 500 + "B" * 500

    clipped = _clip_text(source, 220)

    assert clipped.startswith("A" * 90)
    assert clipped.endswith("B" * 90)
    assert "片段中部省略" in clipped
    assert len(clipped) <= 220


def test_oversized_model_selection_is_bounded_across_the_full_document() -> None:
    values = [f"segment-{index:04d}" for index in range(1, 686)]

    bounded = _evenly_cap(values, 160)

    assert len(bounded) == 160
    assert bounded[0] == "segment-0001"
    assert bounded[-1] == "segment-0685"
    assert any("0300" <= value[-4:] <= "0400" for value in bounded)


def test_overview_normalizer_bounds_total_markdown_instead_of_each_section() -> None:
    raw: dict[str, Any] = {
        "sections": [
            {"title": f"内容区块{index}", "content": "详细内容。" * 500}
            for index in range(1, 9)
        ]
    }

    _normalize_draft(raw)
    draft = ProjectOverviewDraft.model_validate(raw)

    assert len(_render_markdown(draft)) <= 6400
    assert len(draft.sections) == 8
