# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-02
import json
import os
import urllib.request


BASE_URL = os.getenv("TENDER_AI_BASE_URL", "http://127.0.0.1:8000")
TOKEN = os.environ["AI_SERVICE_INTERNAL_TOKEN"]


def post(path: str, body: dict[str, object]) -> dict[str, object]:
    request = urllib.request.Request(
        BASE_URL + path,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "X-Internal-Token": TOKEN},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=600) as response:
        return json.load(response)


interpretation = post(
    "/internal/tender/interpretation",
    {
        "requestId": "smoke-interpretation",
        "documentId": "smoke-document",
        "title": "\u6280\u672f\u6807\u8054\u8c03",
        "biddingMode": "BLIND",
        "segments": [
            {
                "locatorType": "PAGE",
                "locator": "\u7b2c 8 \u9875",
                "text": (
                    "\u6280\u672f\u5b9e\u65bd\u65b9\u6848\u6ee1\u520620\u5206\u3002"
                    "\u65b9\u6848\u5e94\u5305\u542b\u5b9e\u65bd\u65b9\u6cd5\u3001"
                    "\u8fdb\u5ea6\u8ba1\u5212\u3001\u8d28\u91cf\u4fdd\u8bc1\u548c"
                    "\u9a8c\u6536\u6d41\u7a0b\u3002\u6697\u6807\u6b63\u6587"
                    "\u4e0d\u5f97\u51fa\u73b0\u6295\u6807\u4eba\u540d\u79f0\u3002"
                ),
            }
        ],
    },
)
project_overview = interpretation["projectOverview"]
technical_scoring = interpretation["technicalScoringRequirements"]
assert isinstance(project_overview, str) and project_overview.strip()
assert isinstance(technical_scoring, str) and technical_scoring.strip()
assert "实施方案" in technical_scoring
assert "20分" in technical_scoring
assert "方案完整" in technical_scoring

criteria = [
    {
        "id": "project-overview",
        "type": "PROJECT_OVERVIEW",
        "title": "项目概述",
        "description": project_overview,
    },
    {
        "id": "technical-scoring",
        "type": "TECHNICAL_SCORING",
        "title": "技术部分评分要求",
        "description": technical_scoring,
    },
]

outline = post(
    "/internal/tender/outline",
    {
        "requestId": "smoke-outline",
        "title": "\u6280\u672f\u6807\u8054\u8c03",
        "targetPages": 20,
        "biddingMode": "BLIND",
        "criteria": criteria,
        "references": [],
    },
)
nodes = outline["nodes"]
assert isinstance(nodes, list) and nodes
parent_keys = {node["parentKey"] for node in nodes if node.get("parentKey")}
leaves = [node for node in nodes if node["nodeKey"] not in parent_keys]
assert sum(node["plannedPages"] for node in leaves) == 20

leaf = leaves[0]
leaf["taskBrief"] = (
    leaf.get("taskBrief", "")
    + "\u3002\u8f93\u51fa\u81f3\u5c11\u4e00\u4e2a\u804c\u8d23"
    "\u5206\u5de5\u8868\u683c\u548c\u4e00\u4e2a\u5b9e\u65bd\u6d41\u7a0b\u56fe\u3002"
)
draft = post(
    "/internal/tender/chapter",
    {
        "requestId": "smoke-draft",
        "bidTitle": "\u6280\u672f\u6807\u8054\u8c03",
        "biddingMode": "BLIND",
        "chapter": {
            "id": "chapter-1",
            "title": leaf["title"],
            "plannedPages": max(1, leaf["plannedPages"]),
            "taskBrief": leaf["taskBrief"],
            "mustKeywords": leaf.get("mustKeywords", []),
            "scoringPointIds": leaf.get("scoringPointIds", []),
        },
        "criteria": criteria,
        "dictionary": outline.get("dictionary", {}),
        "evidence": [
            {
                "locatorType": "PAGE",
                "locator": "第 8 页",
                "text": technical_scoring,
            }
        ],
        "previousSummary": "",
        "nextBrief": "",
        "wordBudget": 1200,
    },
)
block_types = {block["type"] for block in draft["blocks"]}
assert "table" in block_types
assert "flowchart" not in block_types
assert "<table" in draft["html"]
assert "data-table-title" in draft["html"] and "data-table-note" in draft["html"]
assert "data-flow-diagram" not in draft["html"]

review = post(
    "/internal/tender/review",
    {
        "requestId": "smoke-review",
        "payload": {
            "biddingMode": "BLIND",
            "dictionary": outline.get("dictionary", {}),
            "coverage": outline.get("coverage", []),
            "chapters": [
                {
                    "chapterId": "chapter-1",
                    "title": leaf["title"],
                    "summary": draft["summary"],
                    "terms": draft.get("terms", []),
                    "commitments": draft.get("commitments", []),
                }
            ],
        },
    },
)
assert isinstance(review["passed"], bool)
assert isinstance(review["issues"], list)

revision = post(
    "/internal/tender/revision",
    {
        "requestId": "smoke-revision",
        "mode": "POLISH",
        "selectedHtml": draft["html"][:4000],
        "beforeContext": "",
        "afterContext": "",
        "instruction": "",
        "protectedFacts": ["20\u5206"],
        "dictionary": outline.get("dictionary", {}),
    },
)
assert revision["html"] and revision["changeSummary"]

print(
    json.dumps(
        {
            "projectOverviewCharacters": len(project_overview),
            "technicalScoringCharacters": len(technical_scoring),
            "outlineNodes": len(nodes),
            "leafPages": sum(node["plannedPages"] for node in leaves),
            "draftBlocks": sorted(block_types),
            "reviewPassed": review["passed"],
            "reviewIssues": len(review["issues"]),
            "revisionBlocks": len(revision["blocks"]),
        },
        ensure_ascii=True,
    )
)
