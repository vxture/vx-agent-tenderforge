// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidReferenceChunk;
import com.td.czghagent.domain.model.ParsedDocument;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.DocumentParser;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 参考素材入库：只在没有分块时解析一次、按标题归段切块、失败时留下原因。
 *
 * <p>这里出错的样子都不报错：每次生成都重新解析一遍（慢，且同一素材的分块标识每次都变）；
 * 分块不带重叠，一句跨块的要求在两块里都读不全；解析失败没标记，素材永远停在「处理中」，
 * 目录与正文静默地不再引用它。
 */
class BidAssetIngestionServiceTest {

    private static final StoredFile FILE = new StoredFile(
            "assets/a1.docx", "智慧园区方案.docx", "application/octet-stream", 3, "hash", new byte[]{1, 2, 3});

    private BidRepository repository;
    private FileStorage storage;
    private DocumentParser parser;
    private BidAssetIngestionService service;

    @BeforeEach
    void setUp() {
        repository = mock(BidRepository.class);
        storage = mock(FileStorage.class);
        parser = mock(DocumentParser.class);
        service = new BidAssetIngestionService(repository, storage, parser);
        when(repository.listAssetChunks(anyList())).thenReturn(List.of());
        when(storage.read(anyString(), anyString(), anyString())).thenReturn(FILE);
    }

    /** 图库素材是图片，不参与文本检索：不读文件、不解析、不改状态。 */
    @Test
    void galleryAssetsAreNeverParsed() {
        assertThat(service.ensureIngested(asset("GALLERY"))).isEmpty();

        verifyNoInteractions(repository, storage, parser);
    }

    @Test
    void anAssetThatAlreadyHasChunksIsNotParsedAgain() {
        List<BidReferenceChunk> stored = List.of(new BidReferenceChunk(
                "chunk-1", "a1", "OUTLINE", "方案", 0, "总体设计", "段落 1", "已入库内容", "h", 5));
        when(repository.listAssetChunks(List.of("a1"))).thenReturn(stored);

        assertThat(service.ensureIngested(asset("OUTLINE"))).isSameAs(stored);

        verifyNoInteractions(storage, parser);
        verify(repository, never()).markAssetIngestion(anyString(), anyString(), any());
    }

    /**
     * 解析一次、按标题归段、逐块带上定位与内容哈希；先标处理中，写完分块才标可用。
     *
     * <p>标题段本身不成块，只决定后面各块归在哪个标题下；标题出现之前归在素材名下。
     */
    @Test
    void parsesOnceGroupsTextUnderHeadingsAndActivatesAfterTheChunksAreWritten() {
        when(parser.parse("a1", FILE)).thenReturn(parsed(null,
                new ParsedDocument.Evidence("PARAGRAPH", "段落 1", "  项目背景说明  "),
                new ParsedDocument.Evidence("HEADING", "标题 1", " 总体设计 "),
                new ParsedDocument.Evidence("PARAGRAPH", "段落 2", "采用分层架构"),
                new ParsedDocument.Evidence("PARAGRAPH", "段落 3", "   ")));

        List<BidReferenceChunk> chunks = service.ensureIngested(asset("OUTLINE"));

        assertThat(chunks).hasSize(2);
        BidReferenceChunk first = chunks.get(0);
        assertThat(first.assetId()).isEqualTo("a1");
        assertThat(first.category()).isEqualTo("OUTLINE");
        assertThat(first.assetName()).isEqualTo("智慧园区方案");
        assertThat(first.chunkIndex()).isZero();
        assertThat(first.heading()).isEqualTo("智慧园区方案");
        assertThat(first.sourceLocator()).isEqualTo("段落 1");
        assertThat(first.content()).isEqualTo("项目背景说明");
        assertThat(first.contentHash()).isEqualTo(sha256("项目背景说明"));
        assertThat(first.characterCount()).isEqualTo("项目背景说明".length());
        BidReferenceChunk second = chunks.get(1);
        assertThat(second.chunkIndex()).isEqualTo(1);
        assertThat(second.heading()).isEqualTo("总体设计");
        assertThat(second.sourceLocator()).isEqualTo("段落 2");
        assertThat(second.id()).isNotEqualTo(first.id());

        verify(storage).read("assets/a1.docx", "智慧园区方案.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        InOrder order = inOrder(repository);
        order.verify(repository).markAssetIngestion("a1", "PROCESSING", null);
        order.verify(repository).replaceAssetChunks("a1", chunks);
        order.verify(repository).markAssetIngestion("a1", "ACTIVE", null);
    }

    /** 长段按 1200 字切、相邻块重叠 150 字：一句跨块的要求至少在一块里是完整的。 */
    @Test
    void longTextIsSplitIntoOverlappingChunks() {
        String text = IntStream.range(0, 3000).mapToObj(index -> String.valueOf((char) ('a' + index % 26)))
                .collect(Collectors.joining());
        when(parser.parse("a1", FILE)).thenReturn(parsed(null, new ParsedDocument.Evidence("PARAGRAPH", "段落 1", text)));

        List<BidReferenceChunk> chunks = service.ensureIngested(asset("OUTLINE"));

        assertThat(chunks).extracting(BidReferenceChunk::content).containsExactly(
                text.substring(0, 1200), text.substring(1050, 2250), text.substring(2100, 3000));
        assertThat(chunks).extracting(BidReferenceChunk::chunkIndex).containsExactly(0, 1, 2);
    }

    /** 扫描件之类只有摘要没有正文证据：摘要本身成块，而不是判为空素材。 */
    @Test
    void aDocumentWithOnlyASummaryIsIngestedFromTheSummary() {
        when(parser.parse("a1", FILE)).thenReturn(parsed("素材摘要：园区网络改造"));

        List<BidReferenceChunk> chunks = service.ensureIngested(asset("OUTLINE"));

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.heading()).isEqualTo("摘要");
            assertThat(chunk.sourceLocator()).isEqualTo("摘要");
            assertThat(chunk.content()).isEqualTo("素材摘要：园区网络改造");
        });
    }

    /** 没有任何可检索文本：标记失败并说出原因，不写空分块、不标可用。 */
    @Test
    void anAssetWithNoSearchableTextIsMarkedFailedWithTheReason() {
        when(parser.parse("a1", FILE)).thenReturn(parsed("  ", new ParsedDocument.Evidence("HEADING", "标题 1", "仅有标题")));

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> service.ensureIngested(asset("OUTLINE")));

        assertThat(failure.getErrorCode()).isEqualTo("BID_ASSET_INGESTION_FAILED");
        assertThat(failure.getHttpStatus()).isEqualTo(502);
        assertThat(failure.getMessage()).isEqualTo("素材中没有可检索文本");
        verify(repository).markAssetIngestion("a1", "FAILED", "素材中没有可检索文本");
        verify(repository, never()).replaceAssetChunks(anyString(), anyList());
        verify(repository, never()).markAssetIngestion("a1", "ACTIVE", null);
    }

    /** 解析器的报错原文可能很长（堆栈、整段文本）：落库与抛出的都截到 1000 字。 */
    @Test
    void aParserFailureIsRecordedTruncatedTo1000Characters() {
        String longMessage = "解".repeat(1500);
        when(parser.parse("a1", FILE)).thenThrow(new IllegalStateException(longMessage));

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> service.ensureIngested(asset("OUTLINE")));

        assertThat(failure.getMessage()).isEqualTo("解".repeat(1000));
        verify(repository).markAssetIngestion("a1", "FAILED", "解".repeat(1000));
    }

    @Test
    void aFailureWithoutAMessageStillSaysWhatFailed() {
        when(storage.read(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException());

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> service.ensureIngested(asset("OUTLINE")));

        assertThat(failure.getMessage()).isEqualTo("素材解析失败");
        verify(repository).markAssetIngestion("a1", "FAILED", "素材解析失败");
    }

    private static BidRepository.AssetRecord asset(String category) {
        return new BidRepository.AssetRecord("a1", "owner-1", new TenantScope("org-1", "ws-1"), category,
                "智慧园区方案", "智慧园区方案.docx", "assets/a1.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 3, "hash", "ACTIVE");
    }

    private static ParsedDocument parsed(String summary, ParsedDocument.Evidence... evidence) {
        return new ParsedDocument("TENDER_DOCUMENT", summary, "HIGH", List.of(evidence), List.of(), List.of(), List.of());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
