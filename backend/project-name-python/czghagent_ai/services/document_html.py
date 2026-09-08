# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-03
import re
from dataclasses import dataclass, field
from html.parser import HTMLParser

from czghagent_ai.services.body_format import (
    normalize_body_numbering_text,
    parse_markdown_table_text,
    split_numbered_body_text,
)


@dataclass
class HtmlBlock:
    kind: str
    text: str = ""
    level: int = 0
    items: list[str] = field(default_factory=list)
    caption: str = ""
    note: str = ""
    header: list[str] = field(default_factory=list)
    rows: list[list[str]] = field(default_factory=list)


class TenderHtmlParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.blocks: list[HtmlBlock] = []
        self._text: list[str] = []
        self._tag = ""
        self._paragraph_role = ""
        self._list_tag = ""
        self._list_items: list[str] = []
        self._table_header: list[str] = []
        self._table_rows: list[list[str]] = []
        self._current_row: list[str] | None = None
        self._cell_text: list[str] | None = None
        self._cell_is_header = False
        self._caption_text: list[str] | None = None
        self._pending_table_title = ""
        self._suppressed_div_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if self._suppressed_div_depth:
            self._suppressed_div_depth += 1
            return
        if tag == "div" and attributes.get("data-flow-diagram"):
            self._suppressed_div_depth = 1
        elif tag in {"p", "h1", "h2", "h3", "h4", "h5", "h6"}:
            if self._cell_text is None and not self._list_tag:
                self._tag = tag
                self._paragraph_role = (
                    "table_title" if "data-table-title" in attributes
                    else "table_note" if "data-table-note" in attributes else ""
                )
                self._text = []
        elif tag in {"ul", "ol"}:
            self._list_tag = tag
            self._list_items = []
        elif tag == "li":
            self._text = []
        elif tag == "table":
            self._table_header = []
            self._table_rows = []
        elif tag == "caption":
            self._caption_text = []
        elif tag == "tr":
            self._current_row = []
        elif tag in {"th", "td"}:
            self._cell_text = []
            self._cell_is_header = tag == "th"
        elif tag == "br":
            self._append_text("\n")

    def handle_endtag(self, tag: str) -> None:
        if self._suppressed_div_depth:
            self._suppressed_div_depth -= 1
            return
        if tag in {"p", "h1", "h2", "h3", "h4", "h5", "h6"} and self._tag == tag:
            raw_text = "".join(self._text)
            text = self._normalized(self._text)
            if self._paragraph_role == "table_title":
                if not self._pending_table_title:
                    self._pending_table_title = text
            elif self._paragraph_role == "table_note":
                if self.blocks and self.blocks[-1].kind == "table":
                    self.blocks[-1].note = text
            elif tag == "p" and (table := _parse_markdown_table(raw_text)) is not None:
                if self.blocks and _is_table_caption(self.blocks[-1]):
                    table.caption = self.blocks.pop().text
                self.blocks.append(table)
            elif tag == "p" and self.blocks and self.blocks[-1].kind == "table" and _is_table_note(text):
                self.blocks[-1].note = text
            elif text:
                for paragraph in split_numbered_body_text(text):
                    self.blocks.append(HtmlBlock(
                        kind="paragraph",
                        text=normalize_body_numbering_text(paragraph),
                    ))
            self._tag = ""
            self._paragraph_role = ""
            self._text = []
        elif tag == "li":
            text = normalize_body_numbering_text(self._normalized(self._text))
            if text:
                self._list_items.append(text)
            self._text = []
        elif tag in {"ul", "ol"} and self._list_tag == tag:
            if self._list_items:
                self.blocks.append(HtmlBlock(
                    kind="bullet_list" if tag == "ul" else "ordered_list",
                    items=list(self._list_items),
                ))
            self._list_tag = ""
            self._list_items = []
        elif tag in {"th", "td"} and self._cell_text is not None:
            value = self._normalized(self._cell_text)
            if self._current_row is not None:
                self._current_row.append(value)
            self._cell_text = None
        elif tag == "tr" and self._current_row is not None:
            if self._cell_is_header and not self._table_header:
                self._table_header = list(self._current_row)
            elif any(self._current_row):
                self._table_rows.append(list(self._current_row))
            self._current_row = None
            self._cell_is_header = False
        elif tag == "caption" and self._caption_text is not None:
            self._caption_text = [self._normalized(self._caption_text)]
        elif tag == "table":
            caption = self._pending_table_title or (
                self._caption_text[0] if self._caption_text else ""
            )
            self.blocks.append(HtmlBlock(
                kind="table", caption=caption, header=list(self._table_header),
                rows=list(self._table_rows),
            ))
            self._pending_table_title = ""
            self._caption_text = None
            self._table_header = []
            self._table_rows = []

    def handle_data(self, data: str) -> None:
        if not self._suppressed_div_depth:
            self._append_text(data)

    def _append_text(self, value: str) -> None:
        if self._cell_text is not None:
            self._cell_text.append(value)
        elif self._caption_text is not None:
            self._caption_text.append(value)
        elif self._tag or self._list_tag:
            self._text.append(value)

    def _normalized(self, values: list[str]) -> str:
        return " ".join("".join(values).split())

def parse_tender_html(value: str) -> list[HtmlBlock]:
    parser = TenderHtmlParser()
    parser.feed(value or "")
    parser.close()
    return parser.blocks


def _parse_markdown_table(value: str) -> HtmlBlock | None:
    table = parse_markdown_table_text(value)
    if table is None:
        return None
    return HtmlBlock(
        kind="table", caption=table.caption, header=table.header, rows=table.rows
    )


def _is_table_caption(block: HtmlBlock) -> bool:
    return block.kind == "paragraph" and re.match(r"^表\s*[0-9一二三四五六七八九十]", block.text) is not None


def _is_table_note(value: str) -> bool:
    return re.match(r"^(?:表注|注)[：:]", value) is not None
