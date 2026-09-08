# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-14
from typing import Any

from czghagent_ai.services.structured_output import AiStructuredExecutor, AiStructuredResult
from czghagent_ai.strategy_models import BidStrategyResponse


class BidStrategyPlanner:
    def __init__(self, executor: AiStructuredExecutor) -> None:
        self._executor = executor

    async def plan_result(
        self, request_id: str, payload: dict[str, Any]
    ) -> AiStructuredResult[BidStrategyResponse]:
        """
        Build a hidden technical response strategy before creating the outline.

        Preconditions:
            - payload contains the frozen overview and technical scoring requirements.
        Side Effects:
            - Calls the quality model and may issue one focused structure repair.
        Error Semantics:
            - Structured output failures stop outline generation for a retryable task failure.
        """
        return await self._executor.execute_result(
            "bid_strategy_planning",
            {
                "title": payload["title"],
                "targetPages": payload["targetPages"],
                "biddingMode": payload["biddingMode"],
                "projectOverview": payload["projectOverview"],
                "technicalScoringRequirements": payload["technicalScoringRequirements"],
            },
            f"{request_id}-bid-strategy",
            BidStrategyResponse,
            object_name="技术标投标响应策略",
            schema_version="bid-strategy-v2",
        )


def strategy_prompt_context(strategy: BidStrategyResponse) -> dict[str, object]:
    data = strategy.model_dump(by_alias=True)
    responses = data.get("scoringResponses", [])
    if isinstance(responses, list):
        for index, response in enumerate(responses, 1):
            if isinstance(response, dict):
                response["scoringPointId"] = f"SP-{index:03d}"
    return data


def strategy_scoring_point_ids(strategy: BidStrategyResponse) -> list[str]:
    return [f"SP-{index:03d}" for index in range(1, len(strategy.scoring_responses) + 1)]
