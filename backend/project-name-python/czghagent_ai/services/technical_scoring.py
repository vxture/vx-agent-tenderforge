# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-10
import hashlib
import re
from dataclasses import dataclass

from czghagent_ai.tender_models import SourceSegment

_SCORE = re.compile(
    r"[（(\[]?\s*\d+(?:\.\d+)?\s*"
    r"(?:[-–—~至]\s*\d+(?:\.\d+)?)?\s*分\s*[)）\]]?"
)
_SCORING_SIGNAL = re.compile(
    r"(?:得\s*[（(]?\s*\d|得分|赋分|不计分|不得分|满分\s*\d|"
    r"最高(?:得)?\s*\d|自主赋分|评分\s*[1-9]\d*\s*分)"
)
_ENUMERATION = re.compile(
    r"\s+(?=(?:[一二三四五六七八九十]+、|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]|"
    r"\d{1,2}[.、](?=[^\d])))"
)
_NON_TECHNICAL = re.compile(r"(?:商务|报价|价格).{0,12}(?:评分|评审|标准)")


@dataclass(frozen=True)
class TechnicalScoringClause:
    clause_id: str
    order: int
    group_title: str
    group_score: str
    text: str
    locator: str

    def to_prompt(self) -> dict[str, object]:
        return {
            "id": self.clause_id,
            "order": self.order,
            "groupTitle": self.group_title,
            "groupScore": self.group_score,
            "text": self.text,
            "locator": self.locator,
        }


@dataclass(frozen=True)
class TechnicalScoringCatalog:
    clauses: tuple[TechnicalScoringClause, ...]
    total_score: str

    @property
    def ordered_ids(self) -> list[str]:
        return [clause.clause_id for clause in self.clauses]

    def to_prompt(self) -> list[dict[str, object]]:
        return [clause.to_prompt() for clause in self.clauses]


def build_technical_scoring_catalog(
    segments: list[SourceSegment],
) -> TechnicalScoringCatalog:
    """
    Build source-owned scoring clauses from the already located technical window.

    Preconditions:
        - segments preserve source-document order.
    Side Effects:
        - None.
    Error Semantics:
        - Returns an empty catalog when no scoring clause can be identified.
    """
    clauses: list[TechnicalScoringClause] = []
    seen: set[str] = set()
    current_title = "技术评分要求"
    current_score = ""
    total_score = ""
    for index, segment in enumerate(segments):
        table = _table_row(segment.text)
        if table is not None:
            marker_score, title, score, body = table
            total_score = total_score or marker_score
            current_title, current_score = title, score
            _append_clause(clauses, seen, segment, index, title, score, body)
            continue
        if _is_non_technical(segment.text):
            continue
        heading = _plain_heading(segment.text)
        if heading is not None:
            current_title, current_score = heading
            continue
        _append_clause(
            clauses, seen, segment, index, current_title, current_score, segment.text
        )
    return TechnicalScoringCatalog(tuple(clauses), total_score)


def selection_errors(
    ordered_clause_ids: list[str], catalog: TechnicalScoringCatalog
) -> list[str]:
    expected = catalog.ordered_ids
    expected_set = set(expected)
    unknown = [value for value in ordered_clause_ids if value not in expected_set]
    duplicates = _duplicates(ordered_clause_ids)
    missing = [value for value in expected if value not in ordered_clause_ids]
    errors: list[str] = []
    if unknown:
        errors.append(f"包含未知条款ID：{', '.join(unknown[:12])}")
    if duplicates:
        errors.append(f"条款ID重复：{', '.join(duplicates[:12])}")
    if missing:
        errors.append(f"缺少源条款ID：{', '.join(missing[:12])}")
    if not unknown and not duplicates and not missing and ordered_clause_ids != expected:
        errors.append("条款ID顺序与招标文件原始顺序不一致")
    return errors


def render_technical_scoring_markdown(
    catalog: TechnicalScoringCatalog,
    ordered_clause_ids: list[str] | None = None,
) -> str:
    selected = ordered_clause_ids or catalog.ordered_ids
    clauses = {clause.clause_id: clause for clause in catalog.clauses}
    output = ["### 技术部分"]
    if catalog.total_score:
        output.extend(["", f"* 分值：{catalog.total_score}"])
    active_group: tuple[str, str] | None = None
    for clause_id in selected:
        clause = clauses[clause_id]
        group = (clause.group_title, clause.group_score)
        if group != active_group:
            output.extend(["", f"#### {clause.group_title}"])
            if clause.group_score:
                output.extend(["", f"* 分值：{clause.group_score}"])
            active_group = group
        output.extend(["", _readable_source_text(clause.text)])
    return "\n".join(output).strip()


def _append_clause(
    clauses: list[TechnicalScoringClause],
    seen: set[str],
    segment: SourceSegment,
    source_index: int,
    group_title: str,
    group_score: str,
    body: str,
) -> None:
    text = _source_text(body)
    if not _is_clause(text):
        return
    duplicate_key = _match_text(f"{group_title}\x1e{text}")
    if duplicate_key in seen:
        return
    seen.add(duplicate_key)
    identity = f"{source_index}\x1e{segment.locator}\x1e{text}"
    digest = hashlib.sha256(identity.encode("utf-8")).hexdigest()[:16]
    clauses.append(TechnicalScoringClause(
        clause_id=f"score-{digest}",
        order=len(clauses),
        group_title=group_title or "技术评分要求",
        group_score=group_score,
        text=text,
        locator=segment.locator,
    ))


def _table_row(value: str) -> tuple[str, str, str, str] | None:
    parts = [part.strip() for part in re.split(r"\s*\|\s*", value) if part.strip()]
    if len(parts) < 3:
        return None
    marker_index = next(
        (index for index, part in enumerate(parts) if _is_technical_marker(part)), None
    )
    if marker_index is None or marker_index + 2 >= len(parts):
        return None
    group = parts[marker_index + 1]
    body = " | ".join(parts[marker_index + 2 :]).strip()
    if _is_non_technical(group) or _is_non_technical(body):
        return None
    return (
        _score(parts[marker_index]),
        _title(group),
        _score(group),
        body,
    )


def _plain_heading(value: str) -> tuple[str, str] | None:
    text = _source_text(value)
    score = _score(text)
    if not score or len(text) > 160 or _SCORING_SIGNAL.search(text):
        return None
    title = _title(text)
    return (title, score) if len(title) >= 2 else None


def _is_clause(value: str) -> bool:
    if not value or _is_non_technical(value):
        return False
    if _SCORING_SIGNAL.search(value):
        return True
    scores = _SCORE.findall(value)
    return len(value) >= 24 and len(scores) >= 2


def _is_technical_marker(value: str) -> bool:
    text = _match_text(value)
    return any(marker in text for marker in (
        "技术评分标准", "技术评审标准", "技术评分", "技术评审"
    ))


def _is_non_technical(value: str) -> bool:
    return bool(_NON_TECHNICAL.search(_source_text(value)))


def _score(value: str) -> str:
    matches = _SCORE.findall(value)
    return re.sub(r"^[（(\[]|[)）\]]$", "", matches[-1]).strip() if matches else ""


def _title(value: str) -> str:
    title = _SCORE.sub("", value)
    title = re.sub(r"^[\s（()）一二三四五六七八九十、.\d\-—]+", "", title)
    return re.sub(r"\s+", "", title).strip("：:| ") or "技术评分要求"


def _source_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip(" |")


def _readable_source_text(value: str) -> str:
    return _ENUMERATION.sub("\n\n", _source_text(value))


def _match_text(value: str) -> str:
    return re.sub(r"[^\w\u4e00-\u9fff]+", "", value).lower()


def _duplicates(values: list[str]) -> list[str]:
    seen: set[str] = set()
    duplicates: list[str] = []
    for value in values:
        if value in seen and value not in duplicates:
            duplicates.append(value)
        seen.add(value)
    return duplicates
