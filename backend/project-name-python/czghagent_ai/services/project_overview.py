# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-11
import hashlib
import re
from collections.abc import Callable
from typing import Any

from czghagent_ai.services.ai_provider import AiProviderDiagnostics
from czghagent_ai.services.structured_output import (
    AiStructuredExecutor,
    AiStructuredResult,
)
from czghagent_ai.tender_models import (
    InterpretationRequest,
    ProjectOverviewDraft,
    ProjectOverviewResponse,
    ProjectOverviewSourceSelection,
    SourceSegment,
)

_MAX_SELECTED_SOURCE_CHARACTERS = 48_000
_MAX_SELECTED_SEGMENTS = 160
_MAX_OVERVIEW_MARKDOWN_CHARACTERS = 6_400


class ProjectOverviewExtractor:
    def __init__(self, executor: AiStructuredExecutor) -> None:
        self._executor = executor

    async def extract(
        self, request: InterpretationRequest
    ) -> AiStructuredResult[ProjectOverviewResponse]:
        """
        Select relevant evidence before composing a bounded project overview.

        Preconditions:
            - request.segments contains the ordered full-document extraction.
        Side Effects:
            - Calls the model for source selection and bounded composition.
        Error Semantics:
            - Structured output failures identify the failing bounded object.
        """
        indexed = _index_segments(request.segments)
        selection = await self._executor.execute_result(
            "project_overview_source_selection",
            _selection_payload(request, indexed),
            f"{request.request_id}-overview-selection",
            ProjectOverviewSourceSelection,
            object_name="项目概述源片段选择",
            schema_version="project-overview-source-selection-v1",
            normalizer=_selection_normalizer(indexed),
        )
        selected = _selected_segments(indexed, selection.data.ordered_segment_ids)
        draft = await self._executor.execute_result(
            "project_overview_extraction",
            _composition_payload(request, selected),
            f"{request.request_id}-overview-composition",
            ProjectOverviewDraft,
            object_name="项目概述",
            schema_version="interpretation-project-overview-v4",
            normalizer=_normalize_draft,
        )
        response = ProjectOverviewResponse(project_overview=_render_markdown(draft.data))
        return AiStructuredResult(
            data=response,
            diagnostics=_merge_diagnostics(selection.diagnostics, draft.diagnostics),
            attempts=selection.attempts + draft.attempts,
        )


def _index_segments(segments: list[SourceSegment]) -> list[tuple[str, SourceSegment]]:
    return [(f"segment-{index:04d}", segment) for index, segment in enumerate(segments, 1)]


def _selection_payload(
    request: InterpretationRequest,
    indexed: list[tuple[str, SourceSegment]],
) -> dict[str, object]:
    return {
        "documentId": request.document_id,
        "title": request.title,
        "biddingMode": request.bidding_mode,
        "segments": [
            {"id": segment_id, **segment.model_dump(by_alias=True)}
            for segment_id, segment in indexed
        ],
    }


def _selection_normalizer(
    indexed: list[tuple[str, SourceSegment]],
) -> Callable[[dict[str, Any]], None]:
    positions = {segment_id: index for index, (segment_id, _) in enumerate(indexed)}

    def normalize(raw: dict[str, Any]) -> None:
        values = raw.get("orderedSegmentIds")
        if not isinstance(values, list):
            return
        valid = list(dict.fromkeys(
            value for value in values
            if isinstance(value, str) and value in positions
        ))
        bounded = _evenly_cap(valid, _MAX_SELECTED_SEGMENTS)
        raw["orderedSegmentIds"] = sorted(bounded, key=positions.__getitem__)

    return normalize


def _evenly_cap(values: list[str], limit: int) -> list[str]:
    if len(values) <= limit:
        return values
    if limit == 1:
        return values[:1]
    indexes = {
        round(index * (len(values) - 1) / (limit - 1))
        for index in range(limit)
    }
    return [values[index] for index in sorted(indexes)]


def _selected_segments(
    indexed: list[tuple[str, SourceSegment]], selected_ids: list[str]
) -> list[dict[str, object]]:
    by_id = dict(indexed)
    selected = [(segment_id, by_id[segment_id]) for segment_id in selected_ids]
    per_segment_limit = max(200, _MAX_SELECTED_SOURCE_CHARACTERS // len(selected))
    return [
        {
            "id": segment_id,
            "locatorType": segment.locator_type,
            "locator": segment.locator,
            "text": _clip_text(segment.text, per_segment_limit),
        }
        for segment_id, segment in selected
    ]


def _clip_text(value: str, limit: int) -> str:
    if len(value) <= limit:
        return value
    marker = "\n...[片段中部省略]...\n"
    side = max(1, (limit - len(marker)) // 2)
    return value[:side] + marker + value[-side:]


def _composition_payload(
    request: InterpretationRequest, selected: list[dict[str, object]]
) -> dict[str, object]:
    return {
        "documentId": request.document_id,
        "title": request.title,
        "biddingMode": request.bidding_mode,
        "selectedSegments": selected,
    }


def _normalize_draft(raw: dict[str, Any]) -> None:
    sections = raw.get("sections")
    if not isinstance(sections, list):
        return
    normalized = [section for section in sections if isinstance(section, dict)][:8]
    for section in normalized:
        title = section.get("title")
        content = section.get("content")
        if isinstance(title, str):
            section["title"] = re.sub(r"^#{1,6}\s*", "", title.strip())[:80]
        if isinstance(content, str):
            section["content"] = content.strip()
    raw["sections"] = normalized
    _bound_rendered_content(normalized)


def _bound_rendered_content(sections: list[dict[str, Any]]) -> None:
    textual = [section for section in sections if isinstance(section.get("content"), str)]
    if not textual:
        return
    fixed_length = sum(
        5 + len(str(section.get("title", ""))) for section in textual
    ) + 2 * (len(textual) - 1)
    content_budget = max(len(textual), _MAX_OVERVIEW_MARKDOWN_CHARACTERS - fixed_length)
    lengths = [len(str(section["content"])) for section in textual]
    if sum(lengths) <= content_budget:
        return
    limits = _proportional_limits(lengths, content_budget)
    for section, limit in zip(textual, limits, strict=True):
        section["content"] = _truncate_markdown(str(section["content"]), limit)


def _proportional_limits(lengths: list[int], budget: int) -> list[int]:
    total = sum(lengths)
    limits = [max(1, length * budget // total) for length in lengths]
    while sum(limits) > budget:
        index = max(range(len(limits)), key=limits.__getitem__)
        limits[index] -= 1
    return limits


def _truncate_markdown(value: str, limit: int) -> str:
    if len(value) <= limit:
        return value
    candidate = value[:limit].rstrip()
    minimum = limit // 2
    boundaries = [
        candidate.rfind(marker, minimum)
        for marker in ("\n\n", "\n", "。", "；")
    ]
    boundary = max(boundaries)
    if boundary < minimum:
        return candidate
    suffix = 1 if candidate[boundary] in "。；" else 0
    return candidate[: boundary + suffix].rstrip()


def _render_markdown(draft: ProjectOverviewDraft) -> str:
    sections: list[str] = []
    for section in draft.sections:
        title = re.sub(r"^#{1,6}\s*", "", section.title.strip())
        sections.append(f"## {title}\n\n{section.content.strip()}")
    return "\n\n".join(sections)


def _merge_diagnostics(
    selection: AiProviderDiagnostics, composition: AiProviderDiagnostics
) -> AiProviderDiagnostics:
    return AiProviderDiagnostics(
        finish_reason=composition.finish_reason,
        response_length=selection.response_length + composition.response_length,
        response_hash=hashlib.sha256(
            f"{selection.response_hash}:{composition.response_hash}".encode("ascii")
        ).hexdigest(),
        input_tokens=_sum_optional(selection.input_tokens, composition.input_tokens),
        output_tokens=_sum_optional(selection.output_tokens, composition.output_tokens),
        reasoning_tokens=_sum_optional(
            selection.reasoning_tokens, composition.reasoning_tokens
        ),
        cached_input_tokens=_sum_optional(
            selection.cached_input_tokens, composition.cached_input_tokens
        ),
        attempts=selection.attempts + composition.attempts,
    )


def _sum_optional(first: int | None, second: int | None) -> int | None:
    if first is None and second is None:
        return None
    return (first or 0) + (second or 0)
