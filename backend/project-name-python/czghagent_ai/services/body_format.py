import html
import re
from dataclasses import dataclass

from czghagent_ai.services.table_format import normalize_editor_tables

_CHINESE_NUMBER = r"[零〇一二两三四五六七八九十百]+"
_BODY_NUMBERING = re.compile(
    rf"^\s*(?:"
    rf"第(?P<section>{_CHINESE_NUMBER})[章节部分篇]\s*"
    rf"|(?P<primary>{_CHINESE_NUMBER})[、.．]\s*"
    rf"|[（(](?P<secondary>{_CHINESE_NUMBER})[）)]\s*"
    rf")"
)
_BODY_BLOCK_TEXT = re.compile(
    r"(<(?:p|li)\b[^>]*>\s*)(?:#{1,6}\s*)?([^<]*)",
    flags=re.IGNORECASE,
)
_PARAGRAPH = re.compile(r"(<p\b[^>]*>)([\s\S]*?)</p>", flags=re.IGNORECASE)
_TABLE_CAPTION = re.compile(r"^表\s*[0-9一二三四五六七八九十]", flags=re.IGNORECASE)
_INLINE_NUMBERING = re.compile(
    r"(^|\s+|[。！？；])(?:(?P<primary>\d{1,3})[.．]\s+"
    r"|[（(](?P<secondary>\d{1,3})[）)]\s*"
    r"|(?P<legacy>\d{1,3})[）)]\s*"
    r"|(?P<ordinal>第[一二三四五六七八九十百]+)[，、]\s*)(?=\S)"
)


@dataclass(frozen=True)
class MarkdownTable:
    caption: str
    header: list[str]
    rows: list[list[str]]


def normalize_body_html(value: str, fallback_table_context: str = "") -> str:
    content = re.sub(r"<h[1-6]\b[^>]*>", "<p>", value, flags=re.IGNORECASE)
    content = re.sub(r"</h[1-6]\s*>", "</p>", content, flags=re.IGNORECASE)
    content = _PARAGRAPH.sub(_replace_markdown_table_paragraph, content)
    content = normalize_editor_tables(content, fallback_table_context)
    content = _PARAGRAPH.sub(_split_numbered_paragraph, content)
    return _BODY_BLOCK_TEXT.sub(
        lambda match: f"{match.group(1)}{normalize_body_numbering_text(match.group(2))}",
        content,
    )


def parse_markdown_table_text(value: str) -> MarkdownTable | None:
    text = html.unescape(re.sub(r"<[^>]+>", "", value)).strip()
    if not text or "|" not in text:
        return None
    multiline = _parse_multiline_markdown_table(text)
    return multiline or _parse_inline_markdown_table(text)


def _replace_markdown_table_paragraph(match: re.Match[str]) -> str:
    table = parse_markdown_table_text(match.group(2))
    if table is None:
        return match.group(0)
    title = (
        f'<p data-table-title="true">{html.escape(table.caption)}</p>'
        if table.caption
        else ""
    )
    header = "".join(f"<th><p>{html.escape(cell)}</p></th>" for cell in table.header)
    rows = "".join(
        "<tr>" + "".join(f"<td><p>{html.escape(cell)}</p></td>" for cell in row) + "</tr>"
        for row in table.rows
    )
    return (
        f"{title}<table><thead><tr>{header}</tr></thead><tbody>{rows}</tbody></table>"
        '<p data-table-note="true"></p>'
    )


def split_numbered_body_text(value: str) -> list[str]:
    matches = list(_INLINE_NUMBERING.finditer(value))
    if not matches:
        return [value]
    starts = [match.start() + len(match.group(1)) for match in matches]
    if len(starts) == 1 and starts[0] > 0 and not matches[0].group("ordinal"):
        return [value]
    output: list[str] = []
    if starts[0] > 0 and (prefix := value[:starts[0]].strip()):
        output.append(prefix)
    for index, match in enumerate(matches):
        end = starts[index + 1] if index + 1 < len(starts) else len(value)
        marker_end = match.end()
        number = match.group("primary") or match.group("secondary") or match.group("legacy")
        if match.group("ordinal"):
            marker = f"{match.group('ordinal')}，"
        else:
            marker = f"{number}. " if match.group("primary") else f"（{number}）"
        body = value[marker_end:end].strip()
        output.append(f"{marker}{body}")
    return [item for item in output if item]


def _split_numbered_paragraph(match: re.Match[str]) -> str:
    opening, body = match.group(1), match.group(2)
    if "<" in body or ">" in body:
        return match.group(0)
    parts = split_numbered_body_text(html.unescape(body))
    if len(parts) == 1 and parts[0] == html.unescape(body):
        return match.group(0)
    return "".join(
        f"{opening if index == 0 else '<p>'}{html.escape(part)}</p>"
        for index, part in enumerate(parts)
    )


def _parse_multiline_markdown_table(text: str) -> MarkdownTable | None:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    parsed = [_split_pipe_row(line) for line in lines]
    for separator_index, row in enumerate(parsed):
        if separator_index == 0 or row is None or not _is_separator_row(row):
            continue
        header = parsed[separator_index - 1]
        if header is None or len(header) != len(row):
            continue
        data = [
            candidate for candidate in parsed[separator_index + 1:]
            if candidate is not None and len(candidate) == len(header)
        ]
        if not data:
            continue
        caption_lines = lines[:separator_index - 1]
        caption = " ".join(caption_lines) if caption_lines else ""
        return MarkdownTable(caption=caption, header=header, rows=data)
    return None


def _parse_inline_markdown_table(text: str) -> MarkdownTable | None:
    chunks = [chunk.strip() for chunk in re.split(r"\|\s*\|", text) if chunk.strip(" |")]
    rows = [_split_pipe_row(chunk) for chunk in chunks]
    for separator_index, separator in enumerate(rows):
        if separator_index == 0 or separator is None or not _is_separator_row(separator):
            continue
        header = rows[separator_index - 1]
        if header is None:
            continue
        caption = ""
        if len(header) == len(separator) + 1 and _TABLE_CAPTION.match(header[0]):
            caption, header = header[0], header[1:]
        elif len(header) == len(separator) + 1 and _TABLE_CAPTION.match(header[-1]):
            caption, header = header[-1], header[:-1]
        if len(header) != len(separator):
            continue
        data = [
            candidate for candidate in rows[separator_index + 1:]
            if candidate is not None and len(candidate) == len(header)
        ]
        if data:
            return MarkdownTable(caption=caption, header=header, rows=data)
    return None


def _split_pipe_row(value: str) -> list[str] | None:
    normalized = value.strip().strip("|").strip()
    if "|" not in normalized:
        return None
    cells = [cell.strip() for cell in normalized.split("|")]
    return cells if len(cells) >= 2 else None


def _is_separator_row(row: list[str]) -> bool:
    return len(row) >= 2 and all(re.fullmatch(r":?-{3,}:?", cell) for cell in row)


def normalize_body_numbering_text(value: str) -> str:
    match = _BODY_NUMBERING.match(value)
    if match is None:
        return value
    numeral = match.group("section") or match.group("primary") or match.group("secondary")
    number = _chinese_number_to_int(numeral)
    if number is None:
        return value
    marker = f"（{number}）" if match.group("secondary") else f"{number}. "
    return f"{marker}{value[match.end():]}"


def _chinese_number_to_int(value: str) -> int | None:
    digits = {
        "零": 0, "〇": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4,
        "五": 5, "六": 6, "七": 7, "八": 8, "九": 9,
    }
    if value in digits:
        return digits[value]
    if "百" in value:
        hundreds, remainder = value.split("百", 1)
        hundreds_value = digits.get(hundreds, 1 if not hundreds else -1)
        if hundreds_value < 0:
            return None
        remainder_value = _chinese_number_to_int(remainder) if remainder else 0
        return None if remainder_value is None else hundreds_value * 100 + remainder_value
    if "十" in value:
        tens, ones = value.split("十", 1)
        tens_value = digits.get(tens, 1 if not tens else -1)
        ones_value = digits.get(ones, 0 if not ones else -1)
        if tens_value < 0 or ones_value < 0:
            return None
        return tens_value * 10 + ones_value
    number = 0
    for character in value:
        digit = digits.get(character)
        if digit is None:
            return None
        number = number * 10 + digit
    return number
