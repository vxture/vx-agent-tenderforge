# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-02
import json
import os

import httpx


BASE_URL = os.getenv("TENDER_AI_BASE_URL", "http://127.0.0.1:8000")
TOKEN = os.environ["AI_SERVICE_INTERNAL_TOKEN"]
DOCUMENT = os.getenv("TENDER_REFERENCE_DOCUMENT", "/tmp/tender-test.doc")


def post(path: str, body: dict[str, object], timeout: float = 900) -> dict[str, object]:
    response = httpx.post(
        BASE_URL + path,
        headers={"X-Internal-Token": TOKEN},
        json=body,
        timeout=timeout,
    )
    response.raise_for_status()
    return response.json()


with open(DOCUMENT, "rb") as source:
    parsed_response = httpx.post(
        BASE_URL + "/internal/parse",
        headers={"X-Internal-Token": TOKEN},
        files={"file": ("reference.doc", source, "application/msword")},
        timeout=240,
    )
parsed_response.raise_for_status()
parsed = parsed_response.json()
segments = [
    {
        "locatorType": item["locatorType"],
        "locator": item["locator"],
        "text": item["excerpt"],
    }
    for item in parsed["evidence"]
]

interpretation = post(
    "/internal/tender/interpretation",
    {
        "requestId": "reference-interpretation",
        "documentId": "reference-document",
        "title": "ZG-\u57fa\u4e8e\u5927\u6a21\u578b\u7684\u91d1\u94bc\u751f\u4ea7\u8fd0\u8425\u8f85\u52a9\u667a\u80fd\u51b3\u7b56\u9879\u76ee",
        "biddingMode": "OPEN",
        "segments": segments,
    },
)
project_overview = interpretation["projectOverview"]
technical_scoring = interpretation["technicalScoringRequirements"]
assert isinstance(project_overview, str) and project_overview.strip()
assert isinstance(technical_scoring, str) and technical_scoring.strip()

for required_text in ("项目名称", "建设", "智能体", "数据", "运维"):
    assert required_text in project_overview
for required_text in (
    "实施方案",
    "算力基础设施租赁方案",
    "平台底座建设方案",
    "智能体应用建设方案",
    "数据服务与系统集成方案",
    "人员配置",
    "保障措施",
    "培训方案",
    "售后服务",
    "项目实施与运维保障方案",
    "五年AI大模型建设全周期规划方案",
    "5年运维",
):
    assert required_text in technical_scoring

print(
    json.dumps(
        {
            "parsedSegments": len(segments),
            "projectOverviewCharacters": len(project_overview),
            "technicalScoringCharacters": len(technical_scoring),
            "businessObjects": ["PROJECT_OVERVIEW", "TECHNICAL_SCORING"],
        },
        ensure_ascii=True,
    )
)
