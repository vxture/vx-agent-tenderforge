# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-09
import hashlib
import math
import re
from collections.abc import Iterable, Sequence
from dataclasses import dataclass, replace
from typing import Any

from czghagent_ai.services.ai_provider import AiProviderDiagnostics
from czghagent_ai.services.structured_output import AiStructuredResult
from czghagent_ai.tender_models import (
    CoverageItem,
    OutlineExpansionNode,
    OutlineExpansionResponse,
    OutlineNode,
    OutlineResponse,
    OutlineSkeletonNode,
    OutlineSkeletonResponse,
)


@dataclass(frozen=True)
class OutlineScale:
    min_level_two: int
    min_level_three: int
    preferred_level_two_min: int
    preferred_level_two_max: int
    preferred_level_three_min: int
    preferred_level_three_max: int
    target_leaf_min: int
    target_leaf_ideal: int
    target_leaf_max: int
    max_level_two: int
    max_leaf: int

    @property
    def phased(self) -> bool:
        return True

    @property
    def target_level_two_min(self) -> int:
        return math.ceil(
            self.target_leaf_ideal / self.preferred_level_three_max
        )

    @property
    def target_level_two_max(self) -> int:
        return max(
            self.target_level_two_min,
            math.floor(
                self.target_leaf_ideal / self.preferred_level_three_min
            ),
        )

    @property
    def absolute_level_two_max(self) -> int:
        return max(
            self.target_level_two_max,
            math.floor(self.target_leaf_ideal / self.min_level_three),
        )

    def payload(self) -> dict[str, object]:
        return {
            "minLevelTwoChildrenPerLevelOne": self.min_level_two,
            "minLevelThreeChildrenPerLevelTwo": self.min_level_three,
            "preferredLevelTwoChildrenPerLevelOne": {
                "min": self.preferred_level_two_min,
                "max": self.preferred_level_two_max,
            },
            "preferredLevelThreeChildrenPerLevelTwo": {
                "min": self.preferred_level_three_min,
                "max": self.preferred_level_three_max,
            },
            "targetLeafChapters": {
                "min": self.target_leaf_min,
                "ideal": self.target_leaf_ideal,
                "max": self.target_leaf_max,
            },
            "targetLevelTwoChapters": {
                "min": self.target_level_two_min,
                "max": self.target_level_two_max,
            },
            "maxLevelTwoChapters": self.max_level_two,
            "maxLeafChapters": self.max_leaf,
            "generationMode": "PHASED_QUOTA",
        }


@dataclass(frozen=True)
class OutlineBranchTarget:
    node: OutlineSkeletonNode
    root_title: str
    leaf_count: int


def normalize_skeleton_pages(value: dict[str, Any]) -> None:
    """Keep page allocation system-owned while preserving model-generated structure."""
    nodes = value.get("nodes")
    if not isinstance(nodes, list):
        return
    normalized = 0
    for node in nodes:
        if not isinstance(node, dict):
            continue
        level = node.get("level")
        expected = 1 if level == 1 else 0
        current = node.get("plannedPages", node.get("planned_pages"))
        if not isinstance(current, int) or (level == 1 and current < 1) or (
            level == 2 and current != 0
        ):
            node["plannedPages"] = expected
            node.pop("planned_pages", None)
            normalized += 1
    if normalized:
        warnings = value.setdefault("warnings", [])
        if isinstance(warnings, list):
            warnings.append(f"系统已归一化{normalized}个骨架章节的页数占位。")


def normalize_skeleton_capacity(
    value: dict[str, Any], scale: OutlineScale
) -> None:
    """Cap model-owned branches fairly without dropping any top-level business domain."""
    nodes = value.get("nodes")
    if not isinstance(nodes, list):
        return
    roots = [node for node in nodes if isinstance(node, dict) and node.get("level") == 1]
    root_keys = [
        node.get("nodeKey", node.get("node_key"))
        for node in roots
        if node.get("nodeKey", node.get("node_key"))
    ]
    branches_by_root: dict[str, list[dict[str, Any]]] = {
        str(root_key): [] for root_key in root_keys
    }
    for node in nodes:
        if not isinstance(node, dict) or node.get("level") != 2:
            continue
        parent_key = node.get("parentKey", node.get("parent_key"))
        if parent_key in branches_by_root:
            branches_by_root[str(parent_key)].append(node)
    branches = [branch for items in branches_by_root.values() for branch in items]
    capacity = max(
        scale.target_level_two_max,
        len(root_keys) * scale.min_level_two,
    )
    capacity = min(capacity, scale.absolute_level_two_max, scale.max_level_two)
    if len(branches) <= capacity or any(
        len(branches_by_root[str(root_key)]) < scale.min_level_two
        for root_key in root_keys
    ):
        return
    selected: list[dict[str, Any]] = []
    queues: dict[str, list[dict[str, Any]]] = {}
    for root_key in root_keys:
        key = str(root_key)
        items = branches_by_root[key]
        selected.extend(items[:scale.min_level_two])
        queues[key] = items[scale.min_level_two:]
    if len(selected) > capacity:
        return
    while len(selected) < capacity:
        progressed = False
        for root_key in root_keys:
            queue = queues[str(root_key)]
            if not queue:
                continue
            selected.append(queue.pop(0))
            progressed = True
            if len(selected) == capacity:
                break
        if not progressed:
            break
    selected_ids = {id(node) for node in selected}
    retained = [
        node for node in nodes
        if not isinstance(node, dict)
        or node.get("level") != 2
        or id(node) in selected_ids
        or node.get("parentKey", node.get("parent_key")) not in branches_by_root
    ]
    trimmed = len(branches) - len(selected)
    value["nodes"] = retained
    if trimmed:
        warnings = value.setdefault("warnings", [])
        if isinstance(warnings, list):
            warnings.append(f"模型超额返回的{trimmed}个二级目录已按系统容量公平裁剪。")


def normalize_skeleton(value: dict[str, Any], scale: OutlineScale) -> None:
    normalize_skeleton_pages(value)
    normalize_skeleton_capacity(value, scale)


def build_outline_scale(
    target_pages: int, project_overview: str, technical_scoring: str
) -> OutlineScale:
    # 每个三级目录承担多少页。
    #
    # 短标书的每节略薄（2.6 页），长标书略厚（2.8 页）——长文里章节多了之后，
    # 继续切碎只会制造同义重复的标题，而不是更细的技术分解。
    #
    # 这两个数<b>不是可以随手调的</b>：整条目录链路的配额、二级容量、正文字数预算
    # 都从它们派生。曾经它们被改成 1.5/2.5/2.75，于是 80 页的标书要写 53 个三级节点
    # ——每节 1.5 页。那不会报错，只会让模型被迫把同一件事拆成三个标题，
    # 而评审看到的是一份注水的目录。
    divisor = 2.6 if target_pages <= 200 else 2.8
    base = round(target_pages / divisor)
    base = max(12, min(300, base))
    complexity = _source_complexity(project_overview, technical_scoring)
    bonus = min(round(base * 0.1), max(0, complexity - 6))
    ideal = max(12, min(300, base + bonus))
    # 目标下限保留约 18% 的模型结构容差，避免临界一两个节点导致整单失败。
    minimum = max(12, round(base * 0.85), math.floor(ideal * 0.82))
    maximum_factor = 1.13 if target_pages <= 200 else 1.1
    maximum = min(300, max(minimum, round(ideal * maximum_factor)))
    hard_maximum = min(300, max(maximum, round(ideal * 1.35)))
    max_level_two = max(6, min(120, math.ceil(hard_maximum / 2)))
    return OutlineScale(2, 2, 3, 6, 3, 5, minimum, ideal, maximum,
                        max_level_two, hard_maximum)


def outline_density_errors(
    response: OutlineResponse, scale: OutlineScale
) -> list[str]:
    level_two = [node for node in response.nodes if node.level == 2]
    leaves = [node for node in response.nodes if node.level == 3]
    errors: list[str] = []
    if len(level_two) > scale.max_level_two:
        errors.append(
            f"二级目录共{len(level_two)}个，超过上限{scale.max_level_two}个"
        )
    if len(leaves) < scale.target_leaf_min:
        errors.append(
            f"三级目录仅{len(leaves)}个，目标区间为"
            f"{scale.target_leaf_min}至{scale.target_leaf_max}个；请按独立业务主题继续展开"
        )
    if len(leaves) > scale.max_leaf:
        errors.append(
            f"三级目录共{len(leaves)}个，超过全局安全上限{scale.max_leaf}个"
        )
    exactly_two = _parents_with_exactly_two_children(response.nodes, 2)
    if level_two and exactly_two / len(level_two) >= 0.6 and len(leaves) < scale.target_leaf_ideal:
        errors.append(
            f"{exactly_two}/{len(level_two)}个二级目录恰好只有2个三级目录；"
            "2个只是兜底下限，请优先展开为3至5个具体主题"
        )
    return errors


def skeleton_density_errors(
    response: OutlineSkeletonResponse, scale: OutlineScale
) -> list[str]:
    branches = [node for node in response.nodes if node.level == 2]
    roots = [node for node in response.nodes if node.level == 1]
    maximum_branches = min(
        scale.max_level_two,
        scale.absolute_level_two_max,
        max(scale.target_level_two_max, len(roots) * scale.min_level_two),
    )
    errors: list[str] = []
    if len(branches) > maximum_branches:
        errors.append(f"二级骨架共{len(branches)}个，超过上限{maximum_branches}个")
    missing_briefs = sum(not node.task_brief.strip() for node in branches)
    if branches and missing_briefs / len(branches) > 0.2:
        errors.append(
            f"{missing_briefs}个二级目录缺少任务简述，无法可靠展开具体三级主题"
        )
    return errors


def adapt_scale_to_skeleton(
    skeleton: OutlineSkeletonResponse, scale: OutlineScale
) -> OutlineScale:
    """Adapt the leaf target to the model's valid business skeleton.

    Preconditions:
        - The skeleton already passed structural validation.
        - Each branch can carry between ``min_level_three`` and
          ``preferred_level_three_max`` leaves.
    Side Effects:
        - Appends a diagnostic warning when the requested target is adjusted.
    Error Semantics:
        - An empty skeleton is returned unchanged and remains invalid upstream.
    """
    branch_count = sum(node.level == 2 for node in skeleton.nodes)
    if branch_count == 0:
        return scale
    minimum_capacity = branch_count * scale.min_level_three
    maximum_capacity = branch_count * scale.preferred_level_three_max
    target = min(max(scale.target_leaf_ideal, minimum_capacity), maximum_capacity)
    if target == scale.target_leaf_ideal:
        return scale
    skeleton.warnings.append(
        f"根据{branch_count}个有效二级目录的内容容量，三级目录计划已由"
        f"{scale.target_leaf_ideal}个调整为{target}个，不使用空泛章节凑数。"
    )
    return replace(
        scale,
        target_leaf_min=min(scale.target_leaf_min, target),
        target_leaf_ideal=target,
        target_leaf_max=max(target, min(scale.target_leaf_max, maximum_capacity)),
    )


def allocate_branch_targets(
    skeleton: OutlineSkeletonResponse, scale: OutlineScale
) -> list[OutlineBranchTarget]:
    roots = {node.node_key: node.title for node in skeleton.nodes if node.level == 1}
    branches = [node for node in skeleton.nodes if node.level == 2]
    minimum_capacity = len(branches) * scale.min_level_three
    maximum_capacity = len(branches) * scale.preferred_level_three_max
    if not minimum_capacity <= scale.target_leaf_ideal <= maximum_capacity:
        raise ValueError("outline skeleton cannot carry the deterministic leaf quota")
    desired = scale.target_leaf_ideal
    counts = [scale.min_level_three] * len(branches)
    remaining = desired - sum(counts)
    priority = sorted(
        range(len(branches)),
        key=lambda index: (
            len(branches[index].task_brief) + 30 * len(branches[index].must_keywords),
            -index,
        ),
        reverse=True,
    )
    while remaining > 0:
        progressed = False
        for index in priority:
            if counts[index] >= scale.preferred_level_three_max:
                continue
            counts[index] += 1
            remaining -= 1
            progressed = True
            if remaining == 0:
                break
        if not progressed:
            break
    return [
        OutlineBranchTarget(node, roots.get(node.parent_key or "", ""), counts[index])
        for index, node in enumerate(branches)
    ]


def normalize_expansion_quota(
    value: dict[str, Any], targets: list[OutlineBranchTarget]
) -> None:
    """Reconcile model leaves to exact quotas without inventing project facts."""
    nodes = value.get("nodes")
    if not isinstance(nodes, list):
        return
    quotas = {target.node.node_key: target.leaf_count for target in targets}
    counts = dict.fromkeys(quotas, 0)
    retained: list[Any] = []
    trimmed = 0
    for node in nodes:
        if not isinstance(node, dict):
            retained.append(node)
            continue
        parent_key = node.get("parentKey", node.get("parent_key"))
        if parent_key not in quotas:
            retained.append(node)
            continue
        if counts[parent_key] >= quotas[parent_key]:
            trimmed += 1
            continue
        counts[parent_key] += 1
        retained.append(node)
    value["nodes"] = retained
    filled = 0
    dimensions = (
        ("建设内容", "建设范围、功能组成和配置要求"),
        ("技术实现", "技术路线、实现方法和关键控制要点"),
        ("实施与控制", "实施步骤、过程控制和协同机制"),
        ("交付与验收", "交付成果、验收依据和验证方法"),
        ("运行保障", "运行维护、风险处置和持续优化措施"),
    )
    existing_titles: dict[str, set[str]] = {
        parent_key: set() for parent_key in quotas
    }
    for node in retained:
        if not isinstance(node, dict):
            continue
        parent_key = node.get("parentKey", node.get("parent_key"))
        title = node.get("title")
        if parent_key in existing_titles and isinstance(title, str):
            existing_titles[parent_key].add(title.strip())
    for target in targets:
        parent_key = target.node.node_key
        while counts[parent_key] < quotas[parent_key]:
            dimension, focus = dimensions[counts[parent_key] % len(dimensions)]
            title = f"{target.node.title}{dimension}"
            suffix = 2
            while title in existing_titles[parent_key]:
                title = f"{target.node.title}{dimension}{suffix}"
                suffix += 1
            retained.append({
                "parentKey": parent_key,
                "title": title,
                "taskBrief": (
                    f"围绕{target.node.title}，结合二级章节任务要求细化{focus}，"
                    "不得补充项目输入中不存在的参数或承诺"
                ),
                "mustKeywords": target.node.must_keywords[:8],
            })
            existing_titles[parent_key].add(title)
            counts[parent_key] += 1
            filled += 1
    if trimmed:
        warnings = value.setdefault("warnings", [])
        if isinstance(warnings, list):
            warnings.append(f"模型超额返回的{trimmed}个三级目录已按系统配额裁剪。")
    if filled:
        warnings = value.setdefault("warnings", [])
        if isinstance(warnings, list):
            warnings.append(f"模型缺额的{filled}个三级目录已按二级任务语义补齐。")


def expansion_density_errors(
    response: OutlineExpansionResponse, targets: list[OutlineBranchTarget]
) -> list[str]:
    expected = {target.node.node_key: target.leaf_count for target in targets}
    actual = dict.fromkeys(expected, 0)
    errors: list[str] = []
    for node in response.nodes:
        if node.parent_key not in expected:
            errors.append(f"三级目录引用了本批次之外的父节点：{node.parent_key}")
            continue
        actual[node.parent_key] += 1
    for parent_key, expected_count in expected.items():
        if actual[parent_key] != expected_count:
            errors.append(
                f"父节点{parent_key}需要{expected_count}个三级目录，实际返回"
                f"{actual[parent_key]}个"
            )
    return errors


def merge_outline(
    skeleton: OutlineSkeletonResponse,
    expansions: list[OutlineExpansionResponse],
) -> OutlineResponse:
    expanded_by_parent: dict[str, list[OutlineExpansionNode]] = {
        skeleton_node.node_key: []
        for skeleton_node in skeleton.nodes
        if skeleton_node.level == 2
    }
    warnings = [*skeleton.warnings]
    for expansion in expansions:
        warnings.extend(expansion.warnings)
        for expansion_node in expansion.nodes:
            expanded_by_parent.setdefault(expansion_node.parent_key, []).append(expansion_node)
    skeleton_children: dict[str, list[OutlineSkeletonNode]] = {}
    for node in skeleton.nodes:
        if node.parent_key:
            skeleton_children.setdefault(node.parent_key, []).append(node)
    output: list[OutlineNode] = []
    existing = {node.node_key for node in skeleton.nodes}
    for root in (node for node in skeleton.nodes if node.level == 1):
        output.append(_skeleton_to_outline(root))
        for branch in skeleton_children.get(root.node_key, []):
            output.append(_skeleton_to_outline(branch))
            for index, leaf in enumerate(expanded_by_parent.get(branch.node_key, []), 1):
                node_key = _unique_key(f"{branch.node_key}-leaf-{index}", existing)
                output.append(OutlineNode(
                    node_key=node_key,
                    parent_key=branch.node_key,
                    level=3,
                    title=leaf.title.strip(),
                    planned_pages=0,
                    task_brief=leaf.task_brief.strip(),
                    must_keywords=leaf.must_keywords,
                    # 评分点原样带过来。同一个构造里 task_brief 和 must_keywords
                    # 都保留了，唯独这个被清空——而正文阶段正是据它召回本章该
                    # 响应的冻结评分原文（§5.3/§5.4）。丢了不报错，
                    # 只是每一章都召不回自己要响应的评分要求。
                    scoring_point_ids=leaf.scoring_point_ids,
                ))
    warnings.append("目录已按一二级骨架和系统分配的三级章节配额分批生成。")
    return OutlineResponse(
        nodes=output, coverage=_coverage_from_leaves(output), warnings=warnings
    )


def _coverage_from_leaves(nodes: list[OutlineNode]) -> list[CoverageItem]:
    """按叶子上的评分点归集出「哪条评分由哪些章节响应」。

    分批装配自己拼出整棵树，所以覆盖关系也只能在这里推导——模型每批只看见
    自己那几个分支，谁都给不出全局映射。而从叶子推导出来的映射与树永远一致，
    因为它就是树的一个投影。

    保持首次出现顺序而不是排序：目录的阅读顺序就是评审的阅读顺序。
    """
    grouped: dict[str, list[str]] = {}
    for node in nodes:
        if node.level != 3:
            continue
        for point in node.scoring_point_ids:
            key = point.strip()
            if not key:
                continue
            keys = grouped.setdefault(key, [])
            if node.node_key not in keys:
                keys.append(node.node_key)
    return [
        CoverageItem(scoring_point_id=point, node_keys=keys)
        for point, keys in grouped.items()
    ]


def merged_outline_errors(
    response: OutlineResponse, scale: OutlineScale
) -> list[str]:
    leaves = [node for node in response.nodes if node.level == 3]
    errors = outline_density_errors(response, scale)
    if len(leaves) != scale.target_leaf_ideal:
        errors.insert(
            0,
            f"确定性合并后三级目录应为{scale.target_leaf_ideal}个，实际为{len(leaves)}个",
        )
    return errors


def aggregate_outline_result(
    data: OutlineResponse,
    results: Sequence[
        AiStructuredResult[OutlineSkeletonResponse]
        | AiStructuredResult[OutlineExpansionResponse]
    ],
) -> AiStructuredResult[OutlineResponse]:
    diagnostics = [result.diagnostics for result in results]
    response_hash = hashlib.sha256(
        "|".join(item.response_hash for item in diagnostics).encode("utf-8")
    ).hexdigest()
    combined = AiProviderDiagnostics(
        finish_reason=diagnostics[-1].finish_reason,
        response_length=sum(item.response_length for item in diagnostics),
        response_hash=response_hash,
        input_tokens=_sum_optional(item.input_tokens for item in diagnostics),
        output_tokens=_sum_optional(item.output_tokens for item in diagnostics),
    )
    return AiStructuredResult(data, combined, sum(result.attempts for result in results))


def _source_complexity(project_overview: str, technical_scoring: str) -> int:
    headings = len(re.findall(r"(?m)^\s{0,3}#{1,6}\s+", technical_scoring))
    scored_items = sum(
        bool(re.search(r"(?:得|赋|满|最高|最低|不计|不得)\s*\d*(?:\.\d+)?\s*分", line))
        for line in technical_scoring.splitlines()
    )
    overview_headings = len(re.findall(r"(?m)^\s{0,3}#{1,6}\s+", project_overview))
    return headings + scored_items + min(5, math.ceil(overview_headings / 2))


def _parents_with_exactly_two_children(nodes: list[OutlineNode], level: int) -> int:
    counts: dict[str, int] = {}
    for node in nodes:
        if node.parent_key:
            counts[node.parent_key] = counts.get(node.parent_key, 0) + 1
    return sum(counts.get(node.node_key, 0) == 2 for node in nodes if node.level == level)


def _skeleton_roots_with_two_children(nodes: list[OutlineSkeletonNode]) -> int:
    counts: dict[str, int] = {}
    for node in nodes:
        if node.parent_key:
            counts[node.parent_key] = counts.get(node.parent_key, 0) + 1
    return sum(counts.get(node.node_key, 0) == 2 for node in nodes if node.level == 1)


def _skeleton_to_outline(node: OutlineSkeletonNode) -> OutlineNode:
    return OutlineNode(
        node_key=node.node_key,
        parent_key=node.parent_key,
        level=node.level,
        title=node.title,
        planned_pages=node.planned_pages,
        task_brief=node.task_brief,
        must_keywords=node.must_keywords,
        scoring_point_ids=[],
    )


def _unique_key(candidate: str, existing: set[str]) -> str:
    key = candidate
    counter = 2
    while key in existing:
        key = f"{candidate}-{counter}"
        counter += 1
    existing.add(key)
    return key


def _sum_optional(values: Iterable[int | None]) -> int | None:
    items = [value for value in values if isinstance(value, int)]
    return sum(items) if items else None
