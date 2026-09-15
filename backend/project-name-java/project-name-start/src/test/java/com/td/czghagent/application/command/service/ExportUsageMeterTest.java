// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.model.UsageEvent;
import com.td.czghagent.domain.model.UsageMetric;
import com.td.czghagent.domain.repository.BidRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导出计量的规则本身：一份文档一笔，字数按高水位只报增量。
 *
 * <p>水位由一个内存替身扮演（真实库上的行锁与事务由
 * {@link ExportCharacterMeteringIntegrationTest} 验）。这一组守的是算术：
 * 报出的字数累计必须恰好等于导出过的最大全文字数。算错的两个方向都不报错——
 * 多报是反复导出把统计撑大，少报是改长的部分没进账。
 */
class ExportUsageMeterTest {

    private static final TenantScope TENANT = new TenantScope("org-1", "ws-1");

    private final BidRepository bidRepository = mock(BidRepository.class);
    private final AtomicLong highWater = new AtomicLong();
    private final List<UsageEvent> metered = new ArrayList<>();
    private final ExportUsageMeter meter = new ExportUsageMeter(bidRepository, metered::add);

    ExportUsageMeterTest() {
        when(bidRepository.raiseMeteredCharacters(anyString(), anyLong())).thenAnswer(invocation -> {
            long characters = invocation.getArgument(1);
            long previous = highWater.get();
            if (previous >= characters) {
                return OptionalLong.empty();
            }
            highWater.set(characters);
            return OptionalLong.of(previous);
        });
    }

    @Test
    void theFirstExportReportsOneDocumentAndTheWholeText() {
        export("bid-1", "export-1", List.of(
                chapter("c1", "总体方案", "<p>总体方案设计</p>"),   // 4 + 6
                chapter("c2", "实施计划", "<p>实施  计划</p>"),     // 4 + 4
                chapter("c3", "附 件", "  <p> </p> ")));           // 2 + 0

        assertThat(metered).filteredOn(event -> event.metric() == UsageMetric.DOCUMENT_EXPORTS)
                .singleElement().satisfies(event -> {
                    assertThat(event.idempotencyKey()).isEqualTo("tenderforge.document.exports:export-1");
                    assertThat(event.amount()).isEqualTo(1);
                });
        assertThat(characters()).singleElement().satisfies(event -> {
            assertThat(event.amount()).as("章节标题 + 正文，去标签去空白").isEqualTo(20);
            assertThat(event.idempotencyKey()).isEqualTo("tenderforge.document.characters:bid-1:20");
            assertThat(event.workspaceId()).isEqualTo("ws-1");
            assertThat(event.endUserId()).isEqualTo("owner-1");
        });
    }

    @Test
    void reExportingTheSameDocumentReportsNoMoreCharacters() {
        List<BidWorkspace.Chapter> chapters = List.of(chapter("c1", "总体方案", "<p>总体方案设计</p>"));

        export("bid-1", "export-1", chapters);
        export("bid-1", "export-2", chapters);
        export("bid-1", "export-3", chapters);

        assertThat(metered).filteredOn(event -> event.metric() == UsageMetric.DOCUMENT_EXPORTS)
                .as("每次导出仍是一份成果文档").hasSize(3);
        assertThat(characters()).extracting(UsageEvent::amount).containsExactly(10L);
    }

    @Test
    void shrinkingReportsNothingAndRegrowthOnlyCountsWhatExceedsTheOldMark() {
        export("bid-1", "export-1", List.of(chapter("c1", "方案", "<p>" + "字".repeat(98) + "</p>")));  // 100
        export("bid-1", "export-2", List.of(chapter("c1", "方案", "<p>" + "字".repeat(58) + "</p>")));  // 60
        export("bid-1", "export-3", List.of(chapter("c1", "方案", "<p>" + "字".repeat(88) + "</p>")));  // 90
        export("bid-1", "export-4", List.of(chapter("c1", "方案", "<p>" + "字".repeat(148) + "</p>"))); // 150

        assertThat(characters()).extracting(UsageEvent::amount)
                .as("改短不回退；长回 90 仍未过 100，不报；长到 150 只补 50")
                .containsExactly(100L, 50L);
        assertThat(characters().stream().mapToLong(UsageEvent::amount).sum())
                .as("累计 = 导出过的最大全文字数").isEqualTo(150);
    }

    @Test
    void formattingOnlyChangesAreNotGrowth() {
        export("bid-1", "export-1", List.of(chapter("c1", "总体方案", "<p>总体方案设计</p>")));
        export("bid-1", "export-2", List.of(chapter("c1", " 总体方案 ",
                "<p><strong>总体方案</strong>&nbsp;设计</p>\n")));

        assertThat(characters()).extracting(UsageEvent::amount).containsExactly(10L);
    }

    @Test
    void anEmptyDocumentReportsNoCharactersAndLeavesTheMarkAlone() {
        export("bid-1", "export-1", List.of(chapter("c1", " ", "<p> </p>"), chapter("c2", null, null)));

        assertThat(characters()).isEmpty();
        verify(bidRepository, never()).raiseMeteredCharacters(anyString(), anyLong());
    }

    @Test
    void keysFitThePlatformIdempotencyColumnEvenForAVeryLongBid() {
        String bidId = UUID.randomUUID().toString();
        export(bidId, "export-1", List.of(chapter("c1", "", "<p>" + "技".repeat(4_000_000) + "</p>")));

        assertThat(characters()).singleElement().satisfies(event -> {
            assertThat(event.amount()).isEqualTo(4_000_000);
            assertThat(event.idempotencyKey()).hasSizeLessThanOrEqualTo(128);
        });
    }

    private void export(String bidId, String exportId, List<BidWorkspace.Chapter> chapters) {
        meter.record(bid(bidId), chapters, exportId, "owner-1");
    }

    private List<UsageEvent> characters() {
        return metered.stream().filter(event -> event.metric() == UsageMetric.DOCUMENT_CHARACTERS).toList();
    }

    private static BidWorkspace.Chapter chapter(String id, String title, String html) {
        return new BidWorkspace.Chapter(id, "node-" + id, title, html, "SUCCEEDED",
                LocalDateTime.now(), 1);
    }

    private static BidDocument bid(String id) {
        return new BidDocument(id, "owner-1", TENANT, "TA-1", "SCORING_CRITERIA", "测试标书", 60,
                "OPEN", "CONTENT", "COMPLETED", false, null,
                LocalDateTime.now(), LocalDateTime.now(), 1);
    }
}
