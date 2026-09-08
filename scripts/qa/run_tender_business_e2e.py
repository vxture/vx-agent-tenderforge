# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-14
import json
import os
import time
import zipfile
from io import BytesIO
from pathlib import Path
from typing import Any, cast

import httpx

BASE_URL = os.getenv("TENDER_WEB_BASE_URL", "http://host.docker.internal:5174")
DOCUMENT = Path(os.getenv("TENDER_REFERENCE_DOCUMENT", "/workspace/test/tender-reference.doc"))
OUTPUT_DIR = Path(os.getenv("TENDER_E2E_OUTPUT_DIR", "/workspace/test/e2e-output"))
PLANNER_PASSWORD = os.environ["BOOTSTRAP_PLANNER_PASSWORD"]


def api_request(
    client: httpx.Client,
    method: str,
    path: str,
    **kwargs: Any,
) -> Any:
    """Call a TenderAgent API and return its data envelope."""
    response = client.request(method, path, **kwargs)
    try:
        payload = response.json()
    except ValueError as exception:
        raise RuntimeError(
            f"{method} {path} returned HTTP {response.status_code}"
        ) from exception
    if response.is_error or not payload.get("success", False):
        code = payload.get("errorCode", f"HTTP_{response.status_code}")
        raise RuntimeError(
            f"{method} {path} failed: {code}: {payload.get('message', '')}"
        )
    return payload.get("data")


def await_generation(client: httpx.Client, bid_id: str) -> dict[str, Any]:
    """Poll Temporal-backed chapter generation until it reaches a terminal state."""
    for _ in range(240):
        workspace = cast(
            dict[str, Any], api_request(client, "GET", f"/api/bids/{bid_id}")
        )
        task = workspace.get("generationTask") or {}
        if task.get("status") in {"SUCCEEDED", "FAILED"}:
            return workspace
        time.sleep(5)
    raise RuntimeError("content generation did not finish within 20 minutes")


def await_interpretation(client: httpx.Client, bid_id: str) -> dict[str, Any]:
    """Poll asynchronous source interpretation until it reaches a terminal state."""
    for _ in range(360):
        workspace = cast(
            dict[str, Any], api_request(client, "GET", f"/api/bids/{bid_id}")
        )
        source_file = workspace.get("sourceFile") or {}
        if source_file.get("parseStatus") == "SUCCEEDED":
            return workspace
        if source_file.get("parseStatus") == "FAILED":
            raise RuntimeError(
                f"interpretation failed: {source_file.get('errorMessage', '')}"
            )
        time.sleep(5)
    raise RuntimeError("interpretation did not finish within 30 minutes")


def await_outline(client: httpx.Client, bid_id: str) -> dict[str, Any]:
    """Poll Temporal-backed outline generation until it reaches a terminal state."""
    for _ in range(120):
        workspace = cast(
            dict[str, Any], api_request(client, "GET", f"/api/bids/{bid_id}")
        )
        task = workspace.get("outlineTask") or {}
        if task.get("status") == "SUCCEEDED":
            return workspace
        if task.get("status") == "FAILED":
            raise RuntimeError(
                f"outline generation failed: {task.get('errorMessage', '')}"
            )
        time.sleep(5)
    raise RuntimeError("outline generation did not finish within 10 minutes")


def await_layout(client: httpx.Client, bid_id: str) -> dict[str, Any]:
    """Poll Temporal-backed document production until it reaches a terminal state."""
    for _ in range(240):
        workspace = cast(
            dict[str, Any], api_request(client, "GET", f"/api/bids/{bid_id}")
        )
        job = (workspace.get("production") or {}).get("layoutJob") or {}
        if job.get("status") in {"SUCCEEDED", "FAILED"}:
            return workspace
        time.sleep(5)
    raise RuntimeError("layout did not finish within 20 minutes")


def criterion_payload(criteria: list[dict[str, Any]]) -> list[dict[str, Any]]:
    fields = (
        "id",
        "type",
        "title",
        "description",
        "score",
        "sourceExcerpt",
        "sourceLocator",
        "scope",
        "confidence",
    )
    return [{field: item.get(field) for field in fields} for item in criteria]


def outline_payload(outline: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "clientId": node["id"],
            "parentClientId": node.get("parentId"),
            "level": node["level"],
            "title": node["title"],
            "plannedPages": node["plannedPages"],
            "taskBrief": node.get("taskBrief", ""),
            "mustKeywords": node.get("mustKeywords", []),
            "scoringPointIds": node.get("scoringPointIds", []),
        }
        for node in outline
    ]


def docx_text(content: bytes) -> str:
    with zipfile.ZipFile(BytesIO(content)) as archive:
        return "\n".join(
            archive.read(name).decode("utf-8", errors="ignore")
            for name in archive.namelist()
            if name.startswith("word/") and name.endswith(".xml")
        )


def main() -> None:
    """Run the real tender DOC through the complete production workflow."""
    if not DOCUMENT.is_file():
        raise RuntimeError(f"reference document not found: {DOCUMENT}")
    timeout = httpx.Timeout(900.0, connect=30.0)
    with httpx.Client(base_url=BASE_URL, timeout=timeout) as client:
        login = api_request(
            client,
            "POST",
            "/api/auth/login",
            json={"username": "planner", "password": PLANNER_PASSWORD},
        )
        client.headers["Authorization"] = f"Bearer {login['token']}"
        workspace = cast(
            dict[str, Any],
            api_request(
                client,
                "POST",
                "/api/bids",
                json={
                    "writingMethod": "SCORING_CRITERIA",
                    "title": "金钼生产运营辅助智能决策项目技术暗标联调",
                    "targetPages": 20,
                    "biddingMode": "BLIND",
                },
            ),
        )
        bid_id = workspace["bid"]["id"]

        with DOCUMENT.open("rb") as source:
            workspace = cast(
                dict[str, Any],
                api_request(
                    client,
                    "POST",
                    f"/api/bids/{bid_id}/source-file",
                    files={"file": (DOCUMENT.name, source, "application/msword")},
                ),
            )
        api_request(client, "POST", f"/api/bids/{bid_id}/interpretation/parse")
        workspace = await_interpretation(client, bid_id)
        criteria = workspace["criteria"]
        object_types = [item["type"] for item in criteria]
        if object_types != ["PROJECT_OVERVIEW", "TECHNICAL_SCORING"]:
            raise RuntimeError(f"interpretation contract mismatch: {object_types}")
        if any(not item.get("description", "").strip() for item in criteria):
            raise RuntimeError("interpretation returned an empty business object")
        if not all(item.get("sourceLocator") for item in criteria):
            raise RuntimeError("interpretation returned criteria without source locators")

        stable_revision = workspace["bid"]["revision"]
        workspace = cast(
            dict[str, Any],
            api_request(
                client,
                "PUT",
                f"/api/bids/{bid_id}/criteria",
                headers={"Origin": "http://127.0.0.1:5174"},
                json={"items": criterion_payload(criteria), "revision": stable_revision},
            ),
        )
        if workspace["bid"]["revision"] != stable_revision:
            raise RuntimeError("unchanged interpretation save unexpectedly changed revision")
        if workspace["bid"]["contentStale"]:
            raise RuntimeError("unchanged interpretation save marked content stale")

        workspace = cast(
            dict[str, Any],
            api_request(
                client,
                "POST",
                f"/api/bids/{bid_id}/interpretation/freeze",
                json={"revision": workspace["bid"]["revision"]},
            ),
        )
        if workspace["production"]["interpretationStatus"] != "FROZEN":
            raise RuntimeError("interpretation was not frozen")

        api_request(client, "POST", f"/api/bids/{bid_id}/outline/generate")
        workspace = await_outline(client, bid_id)
        generated_outline = workspace["outline"]
        parent_ids = {
            item["parentId"] for item in generated_outline if item.get("parentId")
        }
        leaves = [item for item in generated_outline if item["id"] not in parent_ids]
        if not leaves or any(item["level"] != 3 for item in leaves):
            raise RuntimeError("outline did not contain valid third-level leaf chapters")
        if sum(item["plannedPages"] for item in leaves) != 20:
            raise RuntimeError("outline leaf page budget does not equal the bid target")

        workspace = cast(
            dict[str, Any],
            api_request(
                client,
                "PUT",
                f"/api/bids/{bid_id}/outline",
                json={
                    "confirm": True,
                    "revision": workspace["bid"]["revision"],
                    "nodes": outline_payload(generated_outline),
                },
            ),
        )
        if workspace["production"]["outlineStatus"] != "FROZEN":
            raise RuntimeError("outline was not frozen")
        api_request(client, "POST", f"/api/bids/{bid_id}/content/generate")
        workspace = await_generation(client, bid_id)
        task = workspace["generationTask"]
        if task["status"] != "SUCCEEDED":
            raise RuntimeError(f"content generation failed: {task.get('errorMessage', '')}")
        events = workspace["production"]["generationEvents"]
        if not events or task["completedUnits"] != task["totalUnits"]:
            raise RuntimeError("generation units or events are incomplete")
        if not workspace["chapters"] or any(
            not chapter["content"] for chapter in workspace["chapters"]
        ):
            raise RuntimeError("generated chapters are incomplete")

        chapter = workspace["chapters"][0]
        original_content = chapter["content"]
        candidate = api_request(
            client,
            "POST",
            f"/api/bids/{bid_id}/chapters/{chapter['id']}/ai-revisions",
            json={
                "mode": "POLISH",
                "selectedHtml": original_content,
                "beforeContext": "",
                "afterContext": "",
                "instruction": "保持技术事实、暗标要求和冻结指标不变，提升专业性与连贯性。",
                "revision": chapter["revision"],
            },
        )
        unchanged = cast(
            dict[str, Any], api_request(client, "GET", f"/api/bids/{bid_id}")
        )
        if unchanged["chapters"][0]["content"] != original_content:
            raise RuntimeError("AI revision candidate overwrote content before acceptance")
        replacement = candidate["replacementHtml"]
        workspace = cast(
            dict[str, Any],
            api_request(
                client,
                "PATCH",
                f"/api/bids/{bid_id}/chapters/{chapter['id']}",
                json={"content": replacement, "revision": chapter["revision"]},
            ),
        )

        workspace = cast(
            dict[str, Any],
            api_request(client, "POST", f"/api/bids/{bid_id}/content/review"),
        )
        blocking = [
            issue
            for issue in workspace["production"]["reviewIssues"]
            if issue["status"] == "OPEN" and issue["severity"] == "ERROR"
        ]
        if blocking:
            raise RuntimeError(f"content review returned blocking issues: {blocking}")
        workspace = cast(
            dict[str, Any],
            api_request(
                client,
                "POST",
                f"/api/bids/{bid_id}/content/freeze",
                json={"revision": workspace["bid"]["revision"]},
            ),
        )
        if workspace["production"]["contentStatus"] != "FROZEN":
            raise RuntimeError("reviewed content was not frozen")

        api_request(client, "POST", f"/api/bids/{bid_id}/layout-jobs")
        workspace = await_layout(client, bid_id)
        layout_job = workspace["production"]["layoutJob"]
        if layout_job["status"] != "SUCCEEDED" or layout_job["qaStatus"] != "PASSED":
            raise RuntimeError(
                f"layout failed: {layout_job.get('qaSummary') or layout_job.get('errorMessage')}"
            )
        download = client.get(f"/api/bids/{bid_id}/exports/latest/download")
        download.raise_for_status()
        if not download.content.startswith(b"PK"):
            raise RuntimeError("latest export is not an OOXML document")
        if not docx_text(download.content).strip():
            raise RuntimeError("latest export contains no readable document content")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_path = OUTPUT_DIR / f"{bid_id}.docx"
    output_path.write_bytes(download.content)
    type_counts: dict[str, int] = {}
    for criterion in criteria:
        criterion_type = criterion["type"]
        type_counts[criterion_type] = type_counts.get(criterion_type, 0) + 1
    print(
        json.dumps(
            {
                "bidId": bid_id,
                "interpretationItems": len(criteria),
                "interpretationTypes": type_counts,
                "outlineNodes": len(generated_outline),
                "outlineLeaves": len(leaves),
                "generationUnits": task["totalUnits"],
                "generationEvents": len(events),
                "candidateNotPersisted": True,
                "reviewIssues": len(workspace["production"]["reviewIssues"]),
                "layoutPages": layout_job["actualPages"],
                "layoutQa": layout_job["qaStatus"],
                "exportVersion": workspace["exports"][0]["version"],
                "exportBytes": len(download.content),
                "outputPath": str(output_path),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
