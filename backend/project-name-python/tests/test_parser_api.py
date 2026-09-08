# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-02
import io

from docx import Document
from fastapi.testclient import TestClient

from czghagent_ai.app import app
from czghagent_ai.config import settings
from czghagent_ai.services.document_parser import DocumentParserService

client = TestClient(app)


def test_health_identifies_tenderagent_parser() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "tenderagent-parser"}


def test_parse_requires_internal_token() -> None:
    response = client.post(
        "/internal/parse",
        files={"file": ("招标文件.txt", "评分办法：技术方案满分30分。".encode(), "text/plain")},
    )
    assert response.status_code == 401


def test_parse_text_returns_source_located_evidence() -> None:
    response = client.post(
        "/internal/parse",
        headers={"X-Internal-Token": settings.internal_token},
        files={
            "file": (
                "招标文件.txt",
                "评分办法：技术方案满分30分。\n废标条款：未盖章作无效投标处理。".encode(),
                "text/plain",
            )
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["category"] == "TENDER_DOCUMENT"
    assert len(body["evidence"]) == 2
    assert body["evidence"][0]["locator"] == "段落 1"
    assert body["facts"] == []


def test_parse_docx_preserves_heading_table_and_paragraph_order() -> None:
    document = Document()
    document.add_heading("技术方案响应要求", level=1)
    table = document.add_table(rows=1, cols=2)
    table.cell(0, 0).text = "评分项"
    table.cell(0, 1).text = "20分"
    document.add_paragraph("表后说明")
    buffer = io.BytesIO()
    document.save(buffer)

    parsed = DocumentParserService().parse("招标文件.docx", buffer.getvalue())

    assert len(parsed.evidence) == 3
    assert parsed.evidence[0].locator_type == "HEADING"
    assert parsed.evidence[1].locator_type == "SHEET_ROW"
    assert parsed.evidence[2].locator_type == "PARAGRAPH"
    assert [item.excerpt for item in parsed.evidence] == [
        "技术方案响应要求",
        "评分项 | 20分",
        "表后说明",
    ]
