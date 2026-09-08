# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-02
from pydantic import BaseModel, ConfigDict, Field


def to_camel(value: str) -> str:
    parts = value.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")


class Evidence(ApiModel):
    locator_type: str
    locator: str
    excerpt: str
    ocr_confidence: float | None = None


class CandidateFact(ApiModel):
    field_code: str
    field_name: str
    value: str
    unit: str = ""
    source_location: str
    confidence: str
    source_excerpt: str
    scope: str = "招标文件"
    standard: str = "来源文件原始口径"


class DetailCandidate(ApiModel):
    field_code: str
    value: str
    source_location: str
    confidence: str
    source_excerpt: str


class ParsedDocument(ApiModel):
    category: str = "TENDER_DOCUMENT"
    summary: str
    confidence: str = "HIGH"
    evidence: list[Evidence]
    facts: list[CandidateFact] = Field(default_factory=list)
    details: list[DetailCandidate] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
