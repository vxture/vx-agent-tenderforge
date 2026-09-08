# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-14
from pydantic import Field

from czghagent_ai.models import ApiModel


class TechnicalTheme(ApiModel):
    title: str = Field(min_length=2, max_length=100)
    objective: str = Field(min_length=10, max_length=300)
    approach: str = Field(min_length=20, max_length=600)
    components: list[str] = Field(min_length=2, max_length=6)
    controls: list[str] = Field(min_length=1, max_length=6)
    verification: list[str] = Field(min_length=1, max_length=5)


class ScoringResponseStrategy(ApiModel):
    requirement: str = Field(min_length=5, max_length=600)
    evaluator_intent: str = Field(min_length=5, max_length=240)
    response_elements: list[str] = Field(min_length=2, max_length=6)
    evidence_plan: list[str] = Field(min_length=1, max_length=5)
    priority: int = Field(ge=1, le=5)


class BidStrategyResponse(ApiModel):
    project_archetype: str = Field(min_length=2, max_length=120)
    solution_positioning: str = Field(min_length=20, max_length=600)
    design_principles: list[str] = Field(min_length=3, max_length=8)
    technical_themes: list[TechnicalTheme] = Field(min_length=2, max_length=10)
    scoring_responses: list[ScoringResponseStrategy] = Field(min_length=1, max_length=50)
    cross_cutting_constraints: list[str] = Field(min_length=1, max_length=10)
    assumptions: list[str] = Field(default_factory=list, max_length=10)
    prohibited_claims: list[str] = Field(default_factory=list, max_length=10)
