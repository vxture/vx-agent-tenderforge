# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-03
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

import fitz  # type: ignore[import-untyped]

from czghagent_ai.document_models import DocumentOutlineNode, DocumentQaResult
from czghagent_ai.services.outline_numbering import build_outline_labels, order_outline

PAGE_TOLERANCE_PERCENT = 50


class DocumentQaError(RuntimeError):
    pass


class DocumentQualityAssurance:
    def __init__(self, soffice_command: str = "libreoffice", timeout_seconds: int = 1800) -> None:
        self._soffice_command = soffice_command
        self._timeout_seconds = timeout_seconds

    def inspect(
        self, docx: bytes, target_pages: int, forbidden_terms: list[str]
    ) -> DocumentQaResult:
        with self._render_pdf(docx) as pdf_path:
            return self._inspect_pdf(pdf_path, target_pages, forbidden_terms)

    def resolve_outline_pages(
        self, docx: bytes, outline: list[DocumentOutlineNode]
    ) -> list[int]:
        """Resolve Heading 1-3 page numbers from LibreOffice PDF bookmarks."""
        with self._render_pdf(docx) as pdf_path:
            document = fitz.open(pdf_path)
            try:
                bookmarks = document.get_toc(simple=True)
            finally:
                document.close()
        return _match_outline_pages(outline, bookmarks)

    def _render_pdf(self, docx: bytes) -> "RenderedPdf":
        if shutil.which(self._soffice_command) is None:
            raise DocumentQaError("LibreOffice未安装，无法执行正式页数质量检查")
        return RenderedPdf(docx, self._soffice_command, self._timeout_seconds)

    def _inspect_pdf(
        self, pdf_path: Path, target_pages: int, forbidden_terms: list[str]
    ) -> DocumentQaResult:
        document = fitz.open(pdf_path)
        try:
            actual_pages = document.page_count
            page_texts = [document.load_page(index).get_text("text").strip() for index in range(actual_pages)]
        finally:
            document.close()
        blank_pages = [index + 1 for index, text in enumerate(page_texts) if len(text) < 4]
        full_text = "\n".join(page_texts).lower().replace(" ", "")
        forbidden_hits = sorted({
            term.strip() for term in forbidden_terms
            if len(term.strip()) >= 2 and term.strip().lower().replace(" ", "") in full_text
        })
        tolerance = target_pages * PAGE_TOLERANCE_PERCENT // 100
        page_difference = actual_pages - target_pages
        passed = abs(page_difference) <= tolerance and not blank_pages and not forbidden_hits
        details = [
            f"目标{target_pages}页，实测{actual_pages}页，偏差{page_difference:+d}页",
            f"允许范围{target_pages - tolerance}至{target_pages + tolerance}页"
            f"（±{PAGE_TOLERANCE_PERCENT}%）",
            f"空白页{len(blank_pages)}页",
            f"禁用口径命中{len(forbidden_hits)}项",
        ]
        return DocumentQaResult(
            status="PASSED" if passed else "FAILED",
            actual_pages=actual_pages,
            summary="；".join(details),
            blank_pages=blank_pages,
            forbidden_hits=forbidden_hits,
        )


class RenderedPdf:
    def __init__(self, docx: bytes, soffice_command: str, timeout_seconds: int) -> None:
        self._docx = docx
        self._soffice_command = soffice_command
        self._timeout_seconds = timeout_seconds
        self._temporary_directory: tempfile.TemporaryDirectory[str] | None = None

    def __enter__(self) -> Path:
        self._temporary_directory = tempfile.TemporaryDirectory(prefix="tenderagent-layout-")
        root = Path(self._temporary_directory.name)
        source = root / "tender.docx"
        source.write_bytes(self._docx)
        profile = root / "libreoffice-profile"
        profile.mkdir()
        environment = os.environ.copy()
        environment["HOME"] = str(root)
        result = subprocess.run(
            [self._soffice_command, "--headless",
             f"-env:UserInstallation=file:///{profile.as_posix().lstrip('/')}",
             "--convert-to", "pdf", "--outdir", str(root), str(source)],
            check=False, capture_output=True, text=True,
            timeout=self._timeout_seconds, env=environment,
        )
        pdf_path = root / "tender.pdf"
        if result.returncode != 0 or not pdf_path.exists() or pdf_path.stat().st_size == 0:
            detail = (result.stderr or result.stdout or "未知错误").strip()
            self.__exit__(None, None, None)
            raise DocumentQaError(f"LibreOffice渲染失败：{detail[:500]}")
        return pdf_path

    def __exit__(self, exc_type: object, exc_value: object, traceback: object) -> None:
        if self._temporary_directory is not None:
            self._temporary_directory.cleanup()
            self._temporary_directory = None


def _match_outline_pages(
    outline: list[DocumentOutlineNode], bookmarks: list[list[object]]
) -> list[int]:
    candidates = [entry for entry in bookmarks if 1 <= _as_int(entry[0]) <= 3]
    labels = build_outline_labels(outline)
    cursor = 0
    pages: list[int] = []
    for node in order_outline(outline):
        expected = _normalize_heading(labels[node.id])
        while cursor < len(candidates):
            level, title, page = candidates[cursor][:3]
            cursor += 1
            if _as_int(level) == node.level and _normalize_heading(str(title)) == expected:
                pages.append(_as_int(page))
                break
        else:
            raise DocumentQaError(f"无法定位目录节点页码：{node.title}")
    return pages


def _normalize_heading(value: str) -> str:
    return "".join(value.split()).casefold()


def _as_int(value: object) -> int:
    if isinstance(value, int | str):
        return int(value)
    raise DocumentQaError(f"PDF书签数字格式无效：{value!r}")
