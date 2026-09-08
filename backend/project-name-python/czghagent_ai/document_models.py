# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-03
from typing import Literal

from pydantic import Field, model_validator

from czghagent_ai.models import ApiModel


class DocumentOutlineNode(ApiModel):
    id: str
    parent_id: str | None = None
    level: int = Field(ge=1, le=3)
    title: str = Field(min_length=1, max_length=200)
    sort_order: int = Field(ge=0)


class DocumentChapter(ApiModel):
    id: str
    outline_node_id: str
    title: str = Field(min_length=1, max_length=200)
    content: str = Field(max_length=2_000_000)


class DocumentRenderRequest(ApiModel):
    bid_id: str
    title: str = Field(min_length=2, max_length=160)
    bidding_mode: Literal["OPEN", "BLIND"]
    target_pages: int = Field(ge=20, le=2000)
    outline: list[DocumentOutlineNode] = Field(min_length=1)
    chapters: list[DocumentChapter] = Field(min_length=1)
    forbidden_terms: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_references(self) -> "DocumentRenderRequest":
        node_ids = {node.id for node in self.outline}
        if len(node_ids) != len(self.outline):
            raise ValueError("outline node ids must be unique")
        if any(chapter.outline_node_id not in node_ids for chapter in self.chapters):
            raise ValueError("chapter must reference an outline node")
        return self


class DocumentQaResult(ApiModel):
    status: Literal["PASSED", "FAILED"]
    actual_pages: int | None = None
    summary: str
    blank_pages: list[int] = Field(default_factory=list)
    forbidden_hits: list[str] = Field(default_factory=list)
