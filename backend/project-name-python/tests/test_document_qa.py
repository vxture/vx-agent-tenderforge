# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-03
from pathlib import Path

import fitz  # type: ignore[import-untyped]

from czghagent_ai.services.document_qa import DocumentQualityAssurance


def make_pdf(path: Path, pages: int, text: str = "技术投标文件正文") -> None:
    document = fitz.open()
    try:
        for page_number in range(1, pages + 1):
            page = document.new_page()
            page.insert_text((72, 72), f"{text} {page_number}")
        document.save(path)
    finally:
        document.close()


def test_page_variance_at_fifty_percent_boundaries_passes(tmp_path: Path) -> None:
    for pages in (40, 120):
        pdf_path = tmp_path / f"within-tolerance-{pages}.pdf"
        make_pdf(pdf_path, pages)

        result = DocumentQualityAssurance()._inspect_pdf(pdf_path, 80, ["16GB"])

        assert result.status == "PASSED"
        assert result.actual_pages == pages
        assert "允许范围40至120页（±50%）" in result.summary
        assert not result.blank_pages
        assert not result.forbidden_hits


def test_page_variance_beyond_fifty_percent_fails(tmp_path: Path) -> None:
    for pages in (39, 121):
        pdf_path = tmp_path / f"outside-tolerance-{pages}.pdf"
        make_pdf(pdf_path, pages)

        result = DocumentQualityAssurance()._inspect_pdf(pdf_path, 80, [])

        assert result.status == "FAILED"


def test_500_page_document_with_432_pages_passes(tmp_path: Path) -> None:
    pdf_path = tmp_path / "500-page-target.pdf"
    make_pdf(pdf_path, 432)

    result = DocumentQualityAssurance()._inspect_pdf(pdf_path, 500, [])

    assert result.status == "PASSED"
    assert "允许范围250至750页（±50%）" in result.summary


def test_500_page_document_with_337_pages_passes(tmp_path: Path) -> None:
    pdf_path = tmp_path / "500-page-target-underfilled.pdf"
    make_pdf(pdf_path, 337)

    result = DocumentQualityAssurance()._inspect_pdf(pdf_path, 500, [])

    assert result.status == "PASSED"
    assert result.actual_pages == 337
    assert "允许范围250至750页（±50%）" in result.summary


def test_forbidden_term_always_fails_even_within_page_tolerance(tmp_path: Path) -> None:
    pdf_path = tmp_path / "forbidden.pdf"
    make_pdf(pdf_path, 20, "前置代理服务器 16GB")

    result = DocumentQualityAssurance()._inspect_pdf(pdf_path, 20, ["16GB"])

    assert result.status == "FAILED"
    assert result.forbidden_hits == ["16GB"]
