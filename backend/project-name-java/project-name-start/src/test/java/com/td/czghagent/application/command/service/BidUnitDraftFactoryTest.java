package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.port.ReferenceRetriever;
import com.td.czghagent.domain.port.TenderAiGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BidUnitDraftFactoryTest {
    @Test
    void sendsOnlyTemplateChunksToChapterReferenceRetrieval() {
        List<List<BidGenerationSnapshot.ReferenceChunk>> observed = new ArrayList<>();
        ReferenceRetriever retriever = (query, candidates, maxChunks, maxCharacters) -> {
            observed.add(candidates);
            return candidates;
        };
        BidUnitDraftFactory factory = new BidUnitDraftFactory(
                retriever, new BidRelevantContextSelector());
        BidGenerationSnapshot.ReferenceChunk template = reference("template", "TEMPLATE", 0);
        BidGenerationSnapshot.ReferenceChunk outline = reference("outline", "OUTLINE", 1);
        BidGenerationSnapshot snapshot = snapshot(List.of(template, outline));
        BidGenerationUnit current = unit("unit-a", "chapter-a", "PENDING", "");

        TenderAiGateway.ChapterDraftRequest request = factory.create(
                snapshot, current, List.of(current), blueprint());

        assertThat(observed).hasSize(1);
        assertThat(observed.getFirst()).containsExactly(template);
        assertThat(request.evidence()).extracting(TenderAiGateway.SourceSegment::locatorType)
                .containsExactly("TEMPLATE_REFERENCE");
        assertThat(request.styleProfile()).contains("范文风格统计");
        assertThat(request.writingPlan()).contains("当前语义职责", "评委关注点");
    }

    @Test
    void previousSummaryStaysInsideCurrentTopLevelLane() {
        ReferenceRetriever retriever = mock(ReferenceRetriever.class);
        when(retriever.retrieve(anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(List.of());
        BidUnitDraftFactory factory = new BidUnitDraftFactory(
                retriever, new BidRelevantContextSelector());
        BidGenerationSnapshot snapshot = snapshot(List.of());
        BidGenerationUnit firstRootUnit = unit(
                "unit-a", "chapter-a", "SUCCEEDED", "同一一级章节的前序摘要");
        BidGenerationUnit secondRootCurrent = unit(
                "unit-b", "chapter-b", "PENDING", "");
        BidGenerationUnit otherRootCurrent = unit(
                "unit-c", "chapter-c", "PENDING", "");
        List<BidGenerationUnit> units = List.of(
                firstRootUnit, secondRootCurrent, otherRootCurrent);

        TenderAiGateway.ChapterDraftRequest sameLane = factory.create(
                snapshot, secondRootCurrent, units, blueprint());
        TenderAiGateway.ChapterDraftRequest otherLane = factory.create(
                snapshot, otherRootCurrent, units, blueprint());

        assertThat(sameLane.previousSummary()).contains("同一一级章节的前序摘要");
        assertThat(otherLane.previousSummary()).isEmpty();
        assertThat(sameLane.termRegistry()).isEqualTo("术语重复副本");
        assertThat(sameLane.evidence()).isEmpty();
    }

    @Test
    void passesActualProseAndChapterOpeningForContinuity() {
        ReferenceRetriever retriever = mock(ReferenceRetriever.class);
        when(retriever.retrieve(anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(List.of());
        BidUnitDraftFactory factory = new BidUnitDraftFactory(
                retriever, new BidRelevantContextSelector());
        BidGenerationSnapshot snapshot = snapshot(List.of());
        BidGenerationUnit previous = new BidGenerationUnit(
                "unit-previous", "task", "bid", "chapter-a", 0, "需求判断与响应边界",
                "key-previous", "SUCCEEDED", 1, 900,
                "<p>前序真实段落说明输入、处理动作与输出成果。</p>", "hash", "前序摘要",
                "", "", LocalDateTime.now());
        BidGenerationUnit current = new BidGenerationUnit(
                "unit-current", "task", "bid", "chapter-a", 1, "技术设计与实施方法",
                "key-current", "PENDING", 0, 1_200, "", "", "", "", "",
                LocalDateTime.now());

        TenderAiGateway.ChapterDraftRequest request = factory.create(
                snapshot, current, List.of(previous, current), blueprint());

        assertThat(request.previousProse()).contains("前序真实段落");
        assertThat(request.chapterOpening()).contains("前序真实段落");
        assertThat(request.repetitionAvoidance()).anyMatch(item -> item.startsWith("前序真实段落"));
        assertThat(request.writingPlan()).contains("技术设计与实施方法", "输入", "输出");
    }

    private BidGenerationSnapshot snapshot(List<BidGenerationSnapshot.ReferenceChunk> references) {
        return new BidGenerationSnapshot(
                "snapshot", "task", "bid", "owner", "hash", "测试标书", "BLIND",
                80, 1, 1, "解决方案契约", "写作规则", "术语重复副本", "全局承诺",
                "chapter-unit-v4",
                LocalDateTime.now(),
                List.of(new BidGenerationSnapshot.Requirement(
                        "overview", "PROJECT_OVERVIEW", "项目概述", "技术建设内容",
                        null, "技术建设内容", "招标文件", 0)),
                List.of(
                        outline("root-a", null, null, 1, 0),
                        outline("section-a", null, "root-a", 2, 1),
                        outline("leaf-a", "chapter-a", "section-a", 3, 2),
                        outline("leaf-b", "chapter-b", "section-a", 3, 3),
                        outline("root-b", null, null, 1, 4),
                        outline("section-b", null, "root-b", 2, 5),
                        outline("leaf-c", "chapter-c", "section-b", 3, 6)
                ), List.of(), references);
    }

    private TenderAiGateway.BranchBlueprint blueprint() {
        return new TenderAiGateway.BranchBlueprint(
                "形成统一、可实施且可验证的二级技术域方案。",
                List.of("统一模块边界和接口契约"), List.of("保持冻结事实一致"),
                List.of(), List.of(), List.of());
    }

    private BidGenerationSnapshot.ReferenceChunk reference(
            String id, String category, int index
    ) {
        return new BidGenerationSnapshot.ReferenceChunk(
                id, "asset-" + id, category, id, "实施方案", "第1页",
                id + "内容", "hash-" + id, index);
    }

    private BidGenerationSnapshot.Outline outline(
            String sourceId, String chapterId, String parentId, int level, int sortOrder
    ) {
        return new BidGenerationSnapshot.Outline(
                sourceId, chapterId, "PENDING", parentId, level, sourceId,
                level == 1 ? 40 : 0, sortOrder, sourceId + "任务",
                List.of(sourceId), List.of());
    }

    private BidGenerationUnit unit(
            String id, String chapterId, String status, String summary
    ) {
        return new BidGenerationUnit(
                id, "task", "bid", chapterId, 0, id, "key-" + id, status, 0,
                800, "", "", summary, "", "", LocalDateTime.now());
    }
}
