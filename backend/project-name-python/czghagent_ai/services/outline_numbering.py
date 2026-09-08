# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-05
import re
from collections import defaultdict
from collections.abc import Sequence

from czghagent_ai.document_models import DocumentOutlineNode

_NUMBER_PREFIXES = {
    1: re.compile(r"^第[〇零一二三四五六七八九十百千两\d]+章[、.．\s]*"),
    2: re.compile(r"^[〇零一二三四五六七八九十百千两\d]+[、.．]\s*"),
    3: re.compile(r"^[（(][〇零一二三四五六七八九十百千两\d]+[）)]\s*"),
}


def build_outline_labels(
    outline: Sequence[DocumentOutlineNode],
) -> dict[str, str]:
    siblings: dict[str | None, list[DocumentOutlineNode]] = defaultdict(list)
    for node in outline:
        siblings[node.parent_id].append(node)
    indexes: dict[str, int] = {}
    for nodes in siblings.values():
        for index, node in enumerate(
            sorted(nodes, key=lambda item: (item.sort_order, item.id)), start=1
        ):
            indexes[node.id] = index
    return {
        node.id: f"{_prefix(node.level, indexes[node.id])}{_strip_prefix(node.title, node.level)}"
        for node in outline
    }


def order_outline(
    outline: Sequence[DocumentOutlineNode],
) -> list[DocumentOutlineNode]:
    siblings: dict[str | None, list[DocumentOutlineNode]] = defaultdict(list)
    for node in outline:
        siblings[node.parent_id].append(node)
    for nodes in siblings.values():
        nodes.sort(key=lambda item: (item.sort_order, item.id))

    ordered: list[DocumentOutlineNode] = []
    visited: set[str] = set()

    def visit(node: DocumentOutlineNode) -> None:
        if node.id in visited:
            return
        visited.add(node.id)
        ordered.append(node)
        for child in siblings.get(node.id, []):
            visit(child)

    for root in siblings.get(None, []):
        visit(root)
    for node in sorted(outline, key=lambda item: (item.sort_order, item.id)):
        visit(node)
    return ordered


def _prefix(level: int, index: int) -> str:
    number = _chinese_number(index)
    if level == 1:
        return f"第{number}章 "
    if level == 2:
        return f"{number}、"
    return f"（{number}）"


def _strip_prefix(title: str, level: int) -> str:
    value = title.strip()
    return _NUMBER_PREFIXES[level].sub("", value).strip()


def _chinese_number(value: int) -> str:
    if value <= 0:
        raise ValueError("outline sibling index must be positive")
    digits = "零一二三四五六七八九"
    if value < 10:
        return digits[value]
    if value < 20:
        return "十" + (digits[value % 10] if value % 10 else "")
    if value < 100:
        return digits[value // 10] + "十" + (digits[value % 10] if value % 10 else "")
    if value < 1000:
        remainder = value % 100
        suffix = "" if remainder == 0 else ("零" if remainder < 10 else "") + _chinese_number(remainder)
        return digits[value // 100] + "百" + suffix
    thousands = value // 1000
    remainder = value % 1000
    suffix = "" if remainder == 0 else ("零" if remainder < 100 else "") + _chinese_number(remainder)
    return _chinese_number(thousands) + "千" + suffix
