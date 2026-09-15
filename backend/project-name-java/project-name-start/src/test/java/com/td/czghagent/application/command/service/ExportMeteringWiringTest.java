// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.model.UsageEvent;
import com.td.czghagent.domain.model.UsageMetric;
import com.td.czghagent.domain.port.BidDocumentExporter;
import com.td.czghagent.domain.port.BidGenerationOrchestrator;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 两条导出路径都真的接上了导出计量。
 *
 * <p>规则由 {@link ExportUsageMeterTest} 验，事务与行锁由
 * {@link ExportCharacterMeteringIntegrationTest} 在真实库上验；这里只验「接没接上」。
 * 漏接一条路径不会报错——正式排版那条此前就是这样，一份份成果文档产出来，账上一笔都没有。
 */
class ExportMeteringWiringTest {

    private static final TenantScope TENANT = new TenantScope("org-1", "ws-1");

    private final BidRepository bidRepository = mock(BidRepository.class);
    private final BidProductionRepository productionRepository = mock(BidProductionRepository.class);
    private final BidDocumentExporter exporter = mock(BidDocumentExporter.class);
    private final FileStorage fileStorage = mock(FileStorage.class);
    private final AuditRepository auditRepository = mock(AuditRepository.class);
    private final List<UsageEvent> metered = new ArrayList<>();
    private final ExportUsageMeter meter = new ExportUsageMeter(bidRepository, metered::add);

    private final BidDocument bid = new BidDocument(
            "bid-1", "owner-1", TENANT, "TA-1", "SCORING_CRITERIA", "测试标书", 60,
            "OPEN", "CONTENT", "COMPLETED", false, null,
            LocalDateTime.now(), LocalDateTime.now(), 1);
    // 标题「章节cN」各 4 字；正文 6 + 4 + 0。全文 22 字。
    private final BidWorkspace workspace = new BidWorkspace(
            bid, null, List.of(), List.of(),
            List.of(chapter("c1", "<p>总体方案设计</p>"), chapter("c2", "<p>实施计划</p>"),
                    chapter("c3", "")),
            null, null, List.of(), List.of(),
            new BidProductionState(
                    "FROZEN", 1, "interpretation-hash", "FROZEN", 1, "outline-hash",
                    "FROZEN", 1, null, null, List.of(), List.of(), List.of(), List.of(), null));

    ExportMeteringWiringTest() {
        when(bidRepository.loadWorkspace(bid)).thenReturn(workspace);
        when(bidRepository.nextExportVersion("bid-1")).thenReturn(1);
        when(bidRepository.raiseMeteredCharacters(eq("bid-1"), anyLong())).thenReturn(OptionalLong.of(0));
        when(fileStorage.store(anyString(), anyString(), anyString(), any(byte[].class)))
                .thenReturn(new StoredFile("bids/bid-1/exports/x.docx", "x.docx",
                        "application/octet-stream", 3, "hash", null));
    }

    @Test
    void theProductionLayoutPathMetersTheExportAndItsCharacters() {
        when(bidRepository.findBidForTask("bid-1", "owner-1")).thenReturn(Optional.of(bid));
        when(exporter.render(any(), any(), any(), any())).thenReturn(
                new BidDocumentExporter.RenderedDocument(new byte[]{1, 2, 3}, 12, "PASSED", "ok"));

        layoutProcessor().process("layout-1", "bid-1", "owner-1");

        assertMetersOneExportAndTheWholeDocument();
    }

    @Test
    void aLayoutJobThatFailsQaMetersNothing() {
        when(bidRepository.findBidForTask("bid-1", "owner-1")).thenReturn(Optional.of(bid));
        when(exporter.render(any(), any(), any(), any())).thenReturn(
                new BidDocumentExporter.RenderedDocument(new byte[]{1}, null, "FAILED", "表格溢出"));

        assertThatThrownBy(() -> layoutProcessor().process("layout-1", "bid-1", "owner-1"))
                .isInstanceOf(BusinessException.class);
        assertThat(metered).as("没有产出成果文档就没有可计量的事").isEmpty();
    }

    @Test
    void theSynchronousExportPathMetersTheExportAndItsCharacters() {
        when(bidRepository.findBid("bid-1", "owner-1", TENANT)).thenReturn(Optional.of(bid));
        when(exporter.renderDocx(any(), any(), any())).thenReturn(new byte[]{1, 2, 3});
        // export 行的 id 在服务内部随机生成；读回时从计量事件里取——计量没接上，这里也会断。
        when(bidRepository.findExportSummary(eq("bid-1"), anyString())).thenAnswer(invocation ->
                Optional.of(new BidExport(
                        exportIdFromMeteredEvents(), "bid-1", 1, "x.docx", 3, null, "NOT_CHECKED",
                        LocalDateTime.now())));
        BidContentCommandService service = new BidContentCommandService(
                bidRepository, fileStorage, mock(BidAiExecutionService.class), exporter,
                productionRepository, mock(BidGenerationOrchestrator.class),
                mock(BidGenerationService.class), new BidCommandSupport(bidRepository, auditRepository),
                metered::add, meter);

        service.createExport("bid-1", new OperationContext(
                new CurrentUser("owner-1", "owner", "Owner", "PLANNER", null, TENANT),
                "trace-1", "127.0.0.1"));

        assertMetersOneExportAndTheWholeDocument();
    }

    private BidLayoutProcessor layoutProcessor() {
        return new BidLayoutProcessor(
                bidRepository, productionRepository, exporter, fileStorage, auditRepository, meter);
    }

    private void assertMetersOneExportAndTheWholeDocument() {
        assertThat(metered).filteredOn(event -> event.metric() == UsageMetric.DOCUMENT_EXPORTS)
                .singleElement()
                .satisfies(event -> assertThat(event.workspaceId()).isEqualTo("ws-1"));
        assertThat(metered).filteredOn(event -> event.metric() == UsageMetric.DOCUMENT_CHARACTERS)
                .extracting(UsageEvent::amount).containsExactly(22L);
    }

    private String exportIdFromMeteredEvents() {
        return metered.stream().filter(event -> event.metric() == UsageMetric.DOCUMENT_EXPORTS)
                .map(event -> event.idempotencyKey().substring("tenderforge.document.exports:".length()))
                .findFirst().orElseThrow(() -> new AssertionError("同步导出没有记导出那一笔"));
    }

    private static BidWorkspace.Chapter chapter(String id, String html) {
        return new BidWorkspace.Chapter(id, "node-" + id, "章节" + id, html, "SUCCEEDED",
                LocalDateTime.now(), 1);
    }
}
