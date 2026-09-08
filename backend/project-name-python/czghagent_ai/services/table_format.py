# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-11
import html
import re
from collections import Counter
from dataclasses import dataclass

_TABLE = re.compile(r"<table\b(?P<attributes>[^>]*)>(?P<body>[\s\S]*?)</table>", re.IGNORECASE)
_CAPTION = re.compile(r"<caption\b[^>]*>(?P<text>[\s\S]*?)</caption>", re.IGNORECASE)
_ROW = re.compile(r"<tr\b[^>]*>(?P<body>[\s\S]*?)</tr>", re.IGNORECASE)
_CELL = re.compile(
    r"<(?P<tag>th|td)\b(?P<attributes>[^>]*)>(?P<body>[\s\S]*?)</(?P=tag)>",
    re.IGNORECASE,
)
_PARAGRAPH = re.compile(
    r"<p\b(?P<attributes>[^>]*)>(?P<body>(?:(?!<p\b)[\s\S])*?)</p>",
    re.IGNORECASE,
)
_FOLLOWING_PARAGRAPH = re.compile(
    r"\s*(?P<paragraph><p\b(?P<attributes>[^>]*)>(?P<body>[\s\S]*?)</p>)",
    re.IGNORECASE,
)
_TABLE_TITLE = re.compile(
    r"^(?:表(?:格)?\s*[0-9一二三四五六七八九十百]+(?:[-—.．][0-9一二三四五六七八九十百]+)?"
    r"|表题\s*[：:])",
    re.IGNORECASE,
)
_TABLE_NOTE = re.compile(r"^(?:表注|注)\s*[：:]", re.IGNORECASE)
_TITLE_SUFFIXES = ("对照表", "明细表", "一览表", "汇总表", "配置表", "矩阵", "清单")


@dataclass(frozen=True)
class _TableMarkup:
    html: str
    caption: str
    headers: list[str]


def normalize_editor_tables(value: str, fallback_context: str = "") -> str:
    """
    将受限正文 HTML 中的表格统一为表题、表格、表注三段结构。

    Preconditions:
        - value 已经过危险脚本和事件属性清理。
    Side Effects:
        - 无；返回新的 HTML 字符串。
    Error Semantics:
        - 不抛出业务异常；无法识别的表格使用确定性表题兜底。
    """
    matches = list(_TABLE.finditer(value))
    if not matches:
        return value
    output: list[str] = []
    cursor = 0
    table_index = 0
    for match in matches:
        if match.start() < cursor:
            continue
        table_index += 1
        prefix, preceding_title = _take_preceding_title(value[cursor:match.start()])
        table = _normalize_table_markup(match)
        title = preceding_title or table.caption
        if not title:
            title = build_fallback_table_title(table.headers, fallback_context, table_index)
        note, next_cursor = _take_following_note(value, match.end())
        output.extend((prefix, _title_paragraph(title), table.html, _note_paragraph(note)))
        cursor = next_cursor
    output.append(value[cursor:])
    return "".join(output)


def build_fallback_table_title(
    headers: list[str], fallback_context: str = "", table_index: int = 1
) -> str:
    """根据章节上下文或表头构造非空、可人工修改的兜底表题。"""
    context = _plain_text(fallback_context)
    if context:
        suffix = f"（{table_index}）" if table_index > 1 else ""
        return f"{context}明细表{suffix}"
    usable = [_plain_text(item) for item in headers if _plain_text(item)]
    if len(usable) >= 2:
        return f"{usable[0]}与{usable[1]}对照表"
    if usable:
        return f"{usable[0]}明细表"
    return f"技术内容明细表{'（' + str(table_index) + '）' if table_index > 1 else ''}"


def build_numbered_table_title(
    value: str, chapter_number: int, table_number: int, fallback_title: str
) -> str:
    """清理模型或历史表号，并按一级章节生成确定性表题。"""
    semantic_title = _clean_title(value) or _clean_title(fallback_title)
    return f"表 {chapter_number}-{table_number} {semantic_title or '技术内容明细表'}"


def _take_preceding_title(prefix: str) -> tuple[str, str]:
    remaining = prefix
    titles: list[str] = []
    while paragraph := _last_paragraph(remaining):
        attributes = paragraph.group("attributes")
        text = _plain_text(paragraph.group("body"))
        tagged = "data-table-title" in attributes.lower()
        if not tagged and not _looks_like_table_title(text):
            break
        titles.append(_clean_title(text))
        remaining = remaining[:paragraph.start()]
    selected = next((title for title in reversed(titles) if title), "")
    return (remaining, selected) if titles else (prefix, "")


def _last_paragraph(value: str) -> re.Match[str] | None:
    paragraphs = list(_PARAGRAPH.finditer(value))
    if not paragraphs:
        return None
    paragraph = paragraphs[-1]
    return paragraph if not value[paragraph.end():].strip() else None


def _take_following_note(value: str, table_end: int) -> tuple[str, int]:
    paragraph = _FOLLOWING_PARAGRAPH.match(value, table_end)
    if paragraph is None:
        return "", table_end
    attributes = paragraph.group("attributes")
    text = _plain_text(paragraph.group("body"))
    tagged = "data-table-note" in attributes.lower()
    if not tagged and not _TABLE_NOTE.match(text):
        return "", table_end
    return text, paragraph.end()


def _normalize_table_markup(match: re.Match[str]) -> _TableMarkup:
    body = match.group("body")
    caption_match = _CAPTION.search(body)
    caption = _clean_title(_plain_text(caption_match.group("text"))) if caption_match else ""
    body = _CAPTION.sub("", body)
    body, row_title = _extract_title_from_rows(body, caption)
    headers = _first_row_values(body)
    title = caption or row_title
    return _TableMarkup(
        html=f'<table{match.group("attributes")}>{body}</table>',
        caption=title,
        headers=headers,
    )


def _extract_title_from_rows(body: str, known_caption: str) -> tuple[str, str]:
    rows = list(_ROW.finditer(body))
    if not rows:
        return body, ""
    first = rows[0]
    cells = list(_CELL.finditer(first.group("body")))
    later_counts = [len(list(_CELL.finditer(row.group("body")))) for row in rows[1:]]
    expected = _most_common_positive(later_counts)
    if len(cells) == 1 and rows[1:] and _is_title_cell(cells[0], known_caption, True):
        return body[:first.start()] + body[first.end():], _cell_text(cells[0])
    if expected and len(cells) == expected + 1:
        candidate = _choose_edge_title_cell(cells, known_caption)
        if candidate is not None:
            repaired_row = first.group(0).replace(candidate.group(0), "", 1)
            repaired = body[:first.start()] + repaired_row + body[first.end():]
            return repaired, _cell_text(candidate)
    return body, ""


def _choose_edge_title_cell(
    cells: list[re.Match[str]], known_caption: str
) -> re.Match[str] | None:
    candidates = [cells[0], cells[-1]]
    scored = [(candidate, _title_cell_score(candidate, known_caption)) for candidate in candidates]
    scored.sort(key=lambda item: item[1], reverse=True)
    if scored[0][1] < 2 or (len(scored) > 1 and scored[0][1] == scored[1][1]):
        return None
    return scored[0][0]


def _title_cell_score(cell: re.Match[str], known_caption: str) -> int:
    text = _cell_text(cell)
    score = 0
    if known_caption and text == known_caption:
        score += 12
    if _TABLE_TITLE.match(text):
        score += 10
    if "colspan" in cell.group("attributes").lower():
        score += 8
    if text.endswith(_TITLE_SUFFIXES):
        score += 4
    if len(text) >= 10:
        score += 2
    return score


def _is_title_cell(cell: re.Match[str], known_caption: str, single_cell: bool) -> bool:
    score = _title_cell_score(cell, known_caption)
    return score >= 3 or (single_cell and "colspan" in cell.group("attributes").lower())


def _first_row_values(body: str) -> list[str]:
    row = _ROW.search(body)
    if row is None:
        return []
    return [_cell_text(cell) for cell in _CELL.finditer(row.group("body"))]


def _most_common_positive(values: list[int]) -> int:
    positive = [value for value in values if value > 0]
    return Counter(positive).most_common(1)[0][0] if positive else 0


def _cell_text(cell: re.Match[str]) -> str:
    return _plain_text(cell.group("body"))


def _plain_text(value: str) -> str:
    return " ".join(html.unescape(re.sub(r"<[^>]+>", " ", value or "")).split())


def _clean_title(value: str) -> str:
    without_label = re.sub(r"^表题\s*[：:]\s*", "", value).strip()
    return _TABLE_TITLE.sub("", without_label, count=1).lstrip("、:：.．-— ")


def _looks_like_table_title(value: str) -> bool:
    return bool(value and (_TABLE_TITLE.match(value) or value.endswith(_TITLE_SUFFIXES)))


def _title_paragraph(value: str) -> str:
    return f'<p data-table-title="true">{html.escape(value)}</p>'


def _note_paragraph(value: str) -> str:
    return f'<p data-table-note="true">{html.escape(value)}</p>'
