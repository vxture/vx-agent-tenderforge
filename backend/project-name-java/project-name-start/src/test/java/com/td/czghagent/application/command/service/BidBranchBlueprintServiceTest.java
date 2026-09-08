// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidBranchBlueprintServiceTest {
    @Test
    void generatesOneBlueprintAndReusesItForTheSameBranch() {
        BidProductionRepository repository = mock(BidProductionRepository.class);
        BidAiExecutionService ai = mock(BidAiExecutionService.class);
        TenderAiGateway.BranchBlueprint blueprint = blueprint();
        when(ai.inputHash(any(TenderAiGateway.BranchBlueprintRequest.class),
                eq(BidBranchBlueprintService.PROMPT_VERSION))).thenReturn("input-hash");
        when(ai.inputHash(eq(blueprint), eq(BidBranchBlueprintService.PROMPT_VERSION)))
                .thenReturn("output-hash");
        when(repository.findBranchBlueprint("snapshot", "branch", "input-hash"))
                .thenReturn(Optional.empty(), Optional.of(blueprint), Optional.of(blueprint));
        when(ai.planBranchBlueprint(any(), any(), any(), any(), any())).thenReturn(blueprint);
        BidBranchBlueprintService service = new BidBranchBlueprintService(
                repository, new BidRelevantContextSelector(), ai);

        TenderAiGateway.BranchBlueprint first = service.resolve(snapshot(), unit());
        TenderAiGateway.BranchBlueprint second = service.resolve(snapshot(), unit());

        assertThat(first).isSameAs(blueprint);
        assertThat(second).isSameAs(blueprint);
        verify(ai, times(1)).planBranchBlueprint(any(), any(), any(), any(), any());
        verify(repository).saveBranchBlueprint(
                "snapshot", "branch", "input-hash", blueprint,
                "output-hash", BidBranchBlueprintService.PROMPT_VERSION);
    }

    private BidGenerationSnapshot snapshot() {
        return new BidGenerationSnapshot(
                "snapshot", "task", "bid", "owner", "hash", "测试技术标", "BLIND",
                80, 1, 1, "解决方案契约", "写作总纲", "术语台账", "承诺台账",
                "chapter-draft-v8", LocalDateTime.now(), List.of(),
                List.of(
                        outline("branch", null, "root", 2, 0),
                        outline("leaf", "chapter", "branch", 3, 1)),
                List.of(), List.of());
    }

    private BidGenerationSnapshot.Outline outline(
            String id, String chapterId, String parentId, int level, int order
    ) {
        return new BidGenerationSnapshot.Outline(
                id, chapterId, "READY", parentId, level, id, 0, order,
                id + "任务", List.of(id), List.of("SP-001"));
    }

    private BidGenerationUnit unit() {
        return new BidGenerationUnit(
                "unit", "task", "bid", "chapter", 0, "写作单元", "unit-key",
                "RUNNING", 1, 800, "", "", "", "", "", LocalDateTime.now());
    }

    private TenderAiGateway.BranchBlueprint blueprint() {
        return new TenderAiGateway.BranchBlueprint(
                "方案定位", List.of("统一架构"), List.of("统一约束"),
                List.of(new TenderAiGateway.LeafBlueprint(
                        "chapter", "章节目标", List.of("技术决策"),
                        List.of("实施动作一", "实施动作二"), List.of("交付物"),
                        List.of("验证方法"), "表格与正文")),
                List.of(), List.of("禁止声明"));
    }
}
