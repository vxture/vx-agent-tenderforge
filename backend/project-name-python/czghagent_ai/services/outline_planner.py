# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-09
import asyncio
from typing import Any

from czghagent_ai.services.ai_provider import AiProviderOutputError
from czghagent_ai.services.outline_strategy import (
    OutlineBranchTarget,
    OutlineScale,
    adapt_scale_to_skeleton,
    aggregate_outline_result,
    allocate_branch_targets,
    build_outline_scale,
    expansion_density_errors,
    merge_outline,
    merged_outline_errors,
    normalize_expansion_quota,
    normalize_skeleton,
    skeleton_density_errors,
)
from czghagent_ai.services.structured_output import AiStructuredExecutor, AiStructuredResult
from czghagent_ai.tender_models import (
    OutlineExpansionResponse,
    OutlineRequest,
    OutlineResponse,
    OutlineSkeletonResponse,
)


class AdaptiveOutlinePlanner:
    def __init__(self, executor: AiStructuredExecutor) -> None:
        self._executor = executor

    async def plan_result(
        self, request: OutlineRequest
    ) -> AiStructuredResult[OutlineResponse]:
        payload, scale = self._payload(request)
        result = await self._phased_result(request, payload, scale)
        self._balance_top_level_pages(result.data, request.target_pages)
        return result

    async def _phased_result(
        self, request: OutlineRequest, payload: dict[str, Any], scale: OutlineScale
    ) -> AiStructuredResult[OutlineResponse]:
        skeleton = await self._executor.execute_result(
            "outline_skeleton_planning",
            payload,
            f"{request.request_id}-outline-skeleton",
            OutlineSkeletonResponse,
            object_name="技术标一二级目录骨架",
            schema_version="outline-skeleton-v2",
            normalizer=lambda response: normalize_skeleton(response, scale),
            semantic_validator=lambda response: skeleton_density_errors(response, scale),
        )
        effective_scale = adapt_scale_to_skeleton(skeleton.data, scale)
        targets = allocate_branch_targets(skeleton.data, effective_scale)
        expansions = await self._expand_batches(request, payload, targets)
        merged = merge_outline(skeleton.data, [result.data for result in expansions])
        errors = merged_outline_errors(merged, effective_scale)
        if errors:
            raise AiProviderOutputError("目录确定性合并失败：" + "；".join(errors))
        return aggregate_outline_result(merged, [skeleton, *expansions])

    async def _expand_batches(
        self,
        request: OutlineRequest,
        payload: dict[str, Any],
        targets: list[OutlineBranchTarget],
    ) -> list[AiStructuredResult[OutlineExpansionResponse]]:
        batches = [targets[index:index + 6] for index in range(0, len(targets), 6)]
        semaphore = asyncio.Semaphore(4)

        async def expand(
            index: int, batch: list[OutlineBranchTarget]
        ) -> AiStructuredResult[OutlineExpansionResponse]:
            async with semaphore:
                return await self._executor.execute_result(
                    "outline_branch_expansion",
                    self._expansion_payload(payload, batch),
                    f"{request.request_id}-outline-expansion-{index}",
                    OutlineExpansionResponse,
                    object_name=f"技术标三级目录第{index}批",
                    schema_version="outline-expansion-v2",
                    normalizer=lambda response: normalize_expansion_quota(
                        response, batch
                    ),
                    semantic_validator=lambda response: expansion_density_errors(
                        response, batch
                    ),
                )

        return list(await asyncio.gather(*(
            expand(index, batch) for index, batch in enumerate(batches, 1)
        )))

    def _payload(self, request: OutlineRequest) -> tuple[dict[str, Any], OutlineScale]:
        overview = next(
            (item.description for item in request.criteria if item.type == "PROJECT_OVERVIEW"),
            "",
        )
        scoring = next(
            (item.description for item in request.criteria if item.type == "TECHNICAL_SCORING"),
            "",
        )
        scale = build_outline_scale(request.target_pages, overview, scoring)
        return {
            "title": request.title,
            "targetPages": request.target_pages,
            "biddingMode": request.bidding_mode,
            "projectOverview": overview,
            "technicalScoringRequirements": scoring,
            "outlineScale": scale.payload(),
            "outlineReferences": [
                {"name": item.name, "content": item.summary}
                for item in request.references if item.category == "OUTLINE"
            ],
        }, scale

    def _expansion_payload(
        self, payload: dict[str, Any], targets: list[OutlineBranchTarget]
    ) -> dict[str, Any]:
        return {
            "title": payload["title"],
            "biddingMode": payload["biddingMode"],
            "projectOverview": payload["projectOverview"],
            "technicalScoringRequirements": payload["technicalScoringRequirements"],
            "branches": [
                {
                    "parentKey": target.node.node_key,
                    "rootTitle": target.root_title,
                    "title": target.node.title,
                    "taskBrief": target.node.task_brief,
                    "mustKeywords": target.node.must_keywords,
                    "preferredLeafCount": target.leaf_count,
                }
                for target in targets
            ],
        }

    def _balance_top_level_pages(self, result: OutlineResponse, target_pages: int) -> None:
        roots = [node for node in result.nodes if node.level == 1]
        if not roots:
            raise AiProviderOutputError("Outline does not contain a level-one chapter")
        for node in result.nodes:
            if node.level > 1:
                node.planned_pages = 0
        current = sum(max(1, node.planned_pages) for node in roots)
        for node in roots:
            node.planned_pages = max(1, node.planned_pages)
        difference = target_pages - current
        cursor = 0
        while difference and cursor <= (target_pages + current + len(roots)) * 2:
            root = roots[cursor % len(roots)]
            if difference > 0:
                root.planned_pages += 1
                difference -= 1
            elif root.planned_pages > 1:
                root.planned_pages -= 1
                difference += 1
            cursor += 1
        if difference:
            raise AiProviderOutputError("Target page budget cannot be balanced")
        if current != target_pages:
            result.warnings.append("AI page allocation was normalized to the requested total.")
