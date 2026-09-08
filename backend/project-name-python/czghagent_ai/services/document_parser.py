# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-02
from czghagent_ai.models import Evidence, ParsedDocument
from czghagent_ai.services.file_readers import FileReaders, TextSegment


class DocumentParserService:
    def __init__(self) -> None:
        self._readers = FileReaders()

    def parse(self, file_name: str, content: bytes) -> ParsedDocument:
        """Convert a supported tender document into source-located text segments."""
        segments = self._readers.read(file_name, content)
        evidence_segments = self._select_evidence(segments)
        return ParsedDocument(
            summary=f"已读取 {len(segments)} 个文字片段。",
            evidence=[
                Evidence(
                    locator_type=item.locator_type,
                    locator=item.locator,
                    excerpt=item.text,
                    ocr_confidence=item.ocr_confidence,
                )
                for item in evidence_segments
            ],
        )

    def _select_evidence(self, segments: list[TextSegment]) -> list[TextSegment]:
        return [item for item in segments if item.text.strip()]
