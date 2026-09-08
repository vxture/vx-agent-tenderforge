// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.BidDocumentExporter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidContentBudgetServiceTest {

    @Test
    void unusedSiblingBudgetOffsetsAnOverlongChapter() {
        BidContentBudgetService service = new BidContentBudgetService(null, null);
        List<BidGenerationUnit> units = List.of(
                unit("unit-a", "chapter-a", 1_000),
                unit("unit-b", "chapter-b", 1_000));
        BidWorkspace workspace = workspace(List.of(
                chapter("chapter-a", "a".repeat(1_400)),
                chapter("chapter-b", "b".repeat(500))));

        BidContentBudgetService.BudgetAssessment assessment = service.assess(units, workspace);

        assertThat(assessment.totalBudget()).isEqualTo(2_000);
        assertThat(assessment.totalVisibleCharacters()).isEqualTo(1_900);
        assertThat(service.needsCompaction(assessment, rendered(96), 100)).isFalse();
    }

    @Test
    void materiallyExceededAggregateBudgetRequestsSettlement() {
        BidContentBudgetService service = new BidContentBudgetService(null, null);
        BidContentBudgetService.BudgetAssessment assessment =
                new BidContentBudgetService.BudgetAssessment(1_000, 1_201, List.of());

        assertThat(service.needsCompaction(assessment, rendered(100), 100)).isTrue();
    }

    @Test
    void denseLayoutStillOverToleranceRequestsSettlement() {
        BidContentBudgetService service = new BidContentBudgetService(null, null);
        BidContentBudgetService.BudgetAssessment assessment =
                new BidContentBudgetService.BudgetAssessment(2_000, 2_000, List.of());

        assertThat(BidContentBudgetService.maximumAllowedPages(100)).isEqualTo(120);
        assertThat(service.needsCompaction(assessment, rendered(121), 100)).isTrue();
    }

    private BidGenerationUnit unit(String id, String chapterId, int budget) {
        return new BidGenerationUnit(
                id, "task", "bid", chapterId, 0, id, "key-" + id,
                "SUCCEEDED", 1, budget, "<p>正文</p>", "hash", "摘要",
                "", "", LocalDateTime.now());
    }

    private BidWorkspace.Chapter chapter(String id, String text) {
        return new BidWorkspace.Chapter(
                id, "outline-" + id, id, "<p>" + text + "</p>",
                "READY", LocalDateTime.now(), 1);
    }

    private BidWorkspace workspace(List<BidWorkspace.Chapter> chapters) {
        return new BidWorkspace(
                null, null, List.of(), List.of(), chapters, null, null,
                List.of(), List.of(), null);
    }

    private BidDocumentExporter.RenderedDocument rendered(int pages) {
        return new BidDocumentExporter.RenderedDocument(
                new byte[]{1}, pages, "PASSED", "测试");
    }
}
