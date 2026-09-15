# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-15
"""成稿质量检查：空白页、禁用口径、LibreOffice 渲染失败、目录页码回填。

`test_document_qa.py` 已经钉住页数容差与一条禁用口径；这里补它没走到的分支——
其中渲染失败与目录页码定位都是「排版任务失败」的直接原因，而它们此前没有一条测试。
LibreOffice 用 subprocess 替身，不依赖运行环境安装；PDF 用 PyMuPDF 当场生成。
"""

import shutil
import subprocess
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

import fitz  # type: ignore[import-untyped]
import pytest

from czghagent_ai.document_models import DocumentOutlineNode
from czghagent_ai.services.document_qa import (
    DocumentQaError,
    DocumentQualityAssurance,
    RenderedPdf,
    _match_outline_pages,
)


def _pdf(path: Path, page_texts: list[str], toc: list[list[object]] | None = None) -> Path:
    document = fitz.open()
    try:
        for text in page_texts:
            page = document.new_page()
            if text:
                page.insert_text((72, 72), text)
        if toc:
            document.set_toc(toc)
        document.save(path)
    finally:
        document.close()
    return path


def _node(node_id: str, parent_id: str | None, level: int, sort_order: int, title: str) -> DocumentOutlineNode:
    return DocumentOutlineNode(id=node_id, parent_id=parent_id, level=level, title=title, sort_order=sort_order)


# ── 空白页与禁用口径 ─────────────────────────────────────────────────────


def test_a_blank_page_fails_the_check_and_is_reported_by_page_number(tmp_path: Path) -> None:
    pdf = _pdf(tmp_path / "blank.pdf", ["Chapter one body text", "", "Chapter two body text"])

    result = DocumentQualityAssurance()._inspect_pdf(pdf, 3, [])

    assert result.status == "FAILED"
    assert result.blank_pages == [2]
    assert "空白页1页" in result.summary


def test_forbidden_terms_match_ignoring_spaces_and_case(tmp_path: Path) -> None:
    pdf = _pdf(tmp_path / "spaced.pdf", ["Memory 16 gb per node"])

    result = DocumentQualityAssurance()._inspect_pdf(pdf, 1, ["16GB"])

    assert result.status == "FAILED"
    assert result.forbidden_hits == ["16GB"]


def test_a_single_character_forbidden_term_is_ignored_rather_than_matching_everything(tmp_path: Path) -> None:
    pdf = _pdf(tmp_path / "short.pdf", ["Gateway configuration"])

    result = DocumentQualityAssurance()._inspect_pdf(pdf, 1, ["G", "  "])

    assert result.status == "PASSED"
    assert result.forbidden_hits == []


# ── LibreOffice 渲染 ─────────────────────────────────────────────────────


def test_missing_libreoffice_is_a_named_error(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(shutil, "which", lambda name: None)

    with pytest.raises(DocumentQaError, match="LibreOffice未安装"):
        DocumentQualityAssurance().inspect(b"docx", 20, [])


def _outdir(command: list[str]) -> Path:
    return Path(command[command.index("--outdir") + 1])


def test_a_failed_render_reports_the_converter_output_and_cleans_up(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(shutil, "which", lambda name: "/usr/bin/libreoffice")
    seen: list[Path] = []

    def fake_run(command: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
        seen.append(_outdir(command))
        return subprocess.CompletedProcess(command, 1, stdout="", stderr="source file could not be loaded " + "x" * 600)

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(DocumentQaError) as failure:
        DocumentQualityAssurance().inspect(b"docx", 20, [])

    assert str(failure.value).startswith("LibreOffice渲染失败：source file could not be loaded")
    assert len(str(failure.value)) <= len("LibreOffice渲染失败：") + 500, "转换器输出要截断，别把整段日志塞进任务错误"
    assert not seen[0].exists(), "失败时也要清掉临时目录"


def test_a_zero_exit_without_a_pdf_is_still_a_failure(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(shutil, "which", lambda name: "/usr/bin/libreoffice")

    def fake_run(command: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
        (_outdir(command) / "tender.pdf").write_bytes(b"")
        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(DocumentQaError, match="LibreOffice渲染失败"):
        DocumentQualityAssurance().inspect(b"docx", 20, [])


def test_a_successful_render_is_inspected_and_the_temporary_directory_removed(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(shutil, "which", lambda name: "/usr/bin/libreoffice")
    seen: list[Path] = []

    def fake_run(command: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
        outdir = _outdir(command)
        seen.append(outdir)
        _pdf(outdir / "tender.pdf", [f"Technical proposal page {n}" for n in range(1, 21)])
        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)

    result = DocumentQualityAssurance().inspect(b"docx", 20, [])

    assert result.status == "PASSED"
    assert result.actual_pages == 20
    assert not seen[0].exists()


def test_rendered_pdf_context_can_be_exited_twice_safely(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    def fake_run(command: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
        _pdf(_outdir(command) / "tender.pdf", ["Body text for the page"])
        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)
    rendered = RenderedPdf(b"docx", "libreoffice", 60)

    with rendered as pdf_path:
        assert pdf_path.exists()
    rendered.__exit__(None, None, None)


# ── 目录页码回填 ─────────────────────────────────────────────────────────


OUTLINE = [
    _node("r1", None, 1, 0, "技术方案"),
    _node("s1", "r1", 2, 0, "总体设计"),
    _node("l1", "s1", 3, 0, "架构说明"),
    _node("r2", None, 1, 1, "实施计划"),
]


@contextmanager
def _rendered(path: Path) -> Iterator[Path]:
    yield path


def test_outline_pages_come_from_matching_bookmarks_in_outline_order(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    pdf = _pdf(
        tmp_path / "toc.pdf",
        [f"page {n}" for n in range(1, 9)],
        toc=[
            [1, "第一章 技术方案", 1],
            [2, "一、总体设计", 2],
            [3, "（一） 架构说明", 3],
            [1, "第二章 实施计划", 7],
        ],
    )
    monkeypatch.setattr(DocumentQualityAssurance, "_render_pdf", lambda self, docx: _rendered(pdf))

    assert DocumentQualityAssurance().resolve_outline_pages(b"docx", OUTLINE) == [1, 2, 3, 7]


def test_bookmark_matching_skips_unrelated_entries_and_normalizes_spacing_and_case() -> None:
    bookmarks: list[list[object]] = [
        [1, "封面", 1],
        [1, "第一章  技术方案", 2],
        [4, "一、总体设计", 3],
        [2, "一、 总体设计", 4],
        [3, "（一）架构说明", 5],
        [1, "第二章 实施计划", 9],
    ]

    assert _match_outline_pages(OUTLINE, bookmarks) == [2, 4, 5, 9]


def test_an_outline_node_without_a_bookmark_names_the_node() -> None:
    bookmarks: list[list[object]] = [[1, "第一章 技术方案", 1], [2, "一、总体设计", 2]]

    with pytest.raises(DocumentQaError, match="无法定位目录节点页码：架构说明"):
        _match_outline_pages(OUTLINE, bookmarks)


def test_a_bookmark_with_a_non_integer_page_is_rejected() -> None:
    bookmarks: list[list[object]] = [[1, "第一章 技术方案", 1.5]]

    with pytest.raises(DocumentQaError, match="PDF书签数字格式无效"):
        _match_outline_pages(OUTLINE[:1], bookmarks)
