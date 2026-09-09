# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-09
"""分阶段目录接口：五个路由确实活着，且与一次性路径给出同一棵树。

这一组的存在理由是它保护的那个失败：这五个路由曾经全部调用服务上不存在的
方法，每一次调用都是 AttributeError 变成的 500。路由注册着、文档写着、
Java 客户端也备着，唯独实现没了——而单元测试测不到这种断裂，
因为断的正是「路由」和「实现」之间那一层。
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).parent))

import czghagent_ai.api.internal as internal  # noqa: E402
from czghagent_ai.app import app  # noqa: E402
from czghagent_ai.services.tender_ai import TenderAiService  # noqa: E402
from test_tender_ai import (  # noqa: E402
    AdaptiveOutlineProvider,
    branch_blueprint_response,
)

HEADERS = {"X-Internal-Token": "local-development-token"}
OUTLINE: dict[str, Any] = {
    "requestId": "stages",
    "title": "分阶段技术标",
    "targetPages": 20,
    "biddingMode": "BLIND",
    "criteria": [{
        "id": "c1", "type": "PROJECT_OVERVIEW", "title": "概述",
        "description": "建设统一技术平台",
    }],
}


class _Provider(AdaptiveOutlineProvider):
    """比 AdaptiveOutlineProvider 多认一个技术域蓝图操作。"""

    async def run(
        self, operation: str, payload: dict[str, Any], user: str, response_model: type
    ) -> dict[str, Any]:
        if operation == "branch_blueprint_planning":
            self.calls.append((operation, payload, user))
            return branch_blueprint_response("leaf-1")
        return await super().run(operation, payload, user, response_model)


@pytest.fixture
def client() -> Any:
    previous = internal.tender_ai_service
    internal.tender_ai_service = TenderAiService(_Provider())
    yield TestClient(app)
    internal.tender_ai_service = previous


def _stage(client: Any, path: str, body: dict[str, Any]) -> dict[str, Any]:
    response = client.post(f"/internal/tender{path}", headers=HEADERS, json=body)
    assert response.status_code == 200, f"{path} -> {response.status_code} {response.text[:200]}"
    return response.json()


# ── 四个阶段串起来 ─────────────────────────────────────────────────────────


def test_the_four_outline_stages_compose_into_one_tree(client: Any) -> None:
    strategy = _stage(client, "/outline/strategy", OUTLINE)["data"]
    skeleton = _stage(
        client, "/outline/skeleton", {"outline": OUTLINE, "strategy": strategy}
    )["data"]

    expansions = [
        _stage(client, "/outline/expansion", {
            "outline": OUTLINE, "strategy": strategy,
            "batchIndex": index, "branches": batch,
        })["data"]
        for index, batch in enumerate(skeleton["batches"], 1)
    ]
    assembled = _stage(client, "/outline/assemble", {
        "outline": OUTLINE, "skeleton": skeleton, "expansions": expansions,
    })

    leaves = [node for node in assembled["nodes"] if node["level"] == 3]
    assert leaves, "装配出来必须有三级节点"
    assert all(node["level"] == 3 for node in leaves)
    assert sum(
        node["plannedPages"] for node in assembled["nodes"] if node["level"] == 1
    ) == OUTLINE["targetPages"]


def test_the_skeleton_stage_hands_back_the_batches_it_decided(client: Any) -> None:
    """分批由骨架阶段定下并发出，不留给调用方自己切。

    调用方各切各的，重跑一批时的分组就可能和上一次不同，
    于是「只重跑第 3 批」重跑的其实是另外一批分支——而那不会报错，
    只会让恢复出来的目录和原来的不是同一份。
    """
    strategy = _stage(client, "/outline/strategy", OUTLINE)["data"]
    skeleton = _stage(
        client, "/outline/skeleton", {"outline": OUTLINE, "strategy": strategy}
    )["data"]

    assert skeleton["batches"], "骨架必须给出批次划分"
    keys = [branch["nodeKey"] for batch in skeleton["batches"] for branch in batch]
    assert len(keys) == len(set(keys)), "同一个分支不能出现在两个批次里"
    assert all(len(batch) <= 6 for batch in skeleton["batches"])


def test_the_strategy_stage_supplies_the_scoring_point_ids(client: Any) -> None:
    """评分点由策略阶段编号，一路传到三级节点。

    没有它，目录只是一棵结构树，和评分表之间没有任何可追溯的连接——
    正文阶段也就无从召回本章该响应的评分原文。
    """
    strategy = _stage(client, "/outline/strategy", OUTLINE)["data"]
    skeleton = _stage(
        client, "/outline/skeleton", {"outline": OUTLINE, "strategy": strategy}
    )["data"]

    assert skeleton["scoringPointIds"], "骨架要把策略编好的评分点带出来"
    assert all(item.startswith("SP-") for item in skeleton["scoringPointIds"])


# ── 分阶段与一次性必须一致 ─────────────────────────────────────────────────


def test_staged_and_single_shot_paths_produce_the_same_tree(client: Any) -> None:
    """两条路径共用同一批实现，所以同一份输入必须装出同一棵树。

    分成两套实现的表现是：分阶段恢复出来的目录和原来那份不一样，
    而两边各自看都合理——没有任何一侧会报错。
    """
    strategy = _stage(client, "/outline/strategy", OUTLINE)["data"]
    skeleton = _stage(
        client, "/outline/skeleton", {"outline": OUTLINE, "strategy": strategy}
    )["data"]
    expansions = [
        _stage(client, "/outline/expansion", {
            "outline": OUTLINE, "strategy": strategy,
            "batchIndex": index, "branches": batch,
        })["data"]
        for index, batch in enumerate(skeleton["batches"], 1)
    ]
    staged = _stage(client, "/outline/assemble", {
        "outline": OUTLINE, "skeleton": skeleton, "expansions": expansions,
    })

    one_shot = _stage(client, "/outline", OUTLINE)["data"]

    def shape(tree: dict[str, Any]) -> list[tuple[int, str]]:
        return [(node["level"], node["title"]) for node in tree["nodes"]]

    assert shape(staged) == shape(one_shot)


# ── 装配是纯函数 ───────────────────────────────────────────────────────────


def test_assembly_never_calls_the_model(client: Any) -> None:
    """装配只做合并、编号和页数归一。

    让模型参与装配意味着同样的输入可能装出不同的树，
    于是重跑一批就会改变整份目录——恢复也就失去了意义。
    """
    strategy = _stage(client, "/outline/strategy", OUTLINE)["data"]
    skeleton = _stage(
        client, "/outline/skeleton", {"outline": OUTLINE, "strategy": strategy}
    )["data"]
    expansions = [
        _stage(client, "/outline/expansion", {
            "outline": OUTLINE, "strategy": strategy,
            "batchIndex": index, "branches": batch,
        })["data"]
        for index, batch in enumerate(skeleton["batches"], 1)
    ]
    before = len(internal.tender_ai_service._client.calls)

    _stage(client, "/outline/assemble", {
        "outline": OUTLINE, "skeleton": skeleton, "expansions": expansions,
    })

    assert len(internal.tender_ai_service._client.calls) == before


# ── 技术域蓝图 ─────────────────────────────────────────────────────────────


def test_the_branch_blueprint_route_answers(client: Any) -> None:
    body = _stage(client, "/chapter/blueprint", {
        "requestId": "stages",
        "bidTitle": "分阶段技术标",
        "biddingMode": "BLIND",
        "branch": {"id": "b1", "title": "建设任务"},
        "chapters": [{"id": "leaf-1", "title": "实施方案", "plannedPages": 0}],
        "criteria": [{
            "id": "c1", "type": "TECHNICAL_SCORING", "title": "技术评分",
            "description": "方案完整得20分",
        }],
    })

    assert body["data"]["chapters"][0]["chapterId"] == "leaf-1"
    assert body["data"]["solutionPositioning"]
