package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationPlan;
import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BidGenerationExecutionPlannerTest {
    @Test
    void groupsUnitsByTopLevelChapterAndPreservesOutlineOrder() {
        BidGenerationSnapshot snapshot = snapshot(List.of(
                outline("root-a", null, null, 1, 0),
                outline("section-a", null, "root-a", 2, 1),
                outline("leaf-a1", "chapter-a1", "section-a", 3, 2),
                outline("leaf-a2", "chapter-a2", "section-a", 3, 3),
                outline("root-b", null, null, 1, 4),
                outline("section-b", null, "root-b", 2, 5),
                outline("leaf-b1", "chapter-b1", "section-b", 3, 6)
        ));
        List<BidGenerationUnit> units = List.of(
                unit("b-1", "chapter-b1", 0, 1_000),
                unit("a-2-1", "chapter-a2", 0, 1_200),
                unit("a-1-2", "chapter-a1", 1, 900),
                unit("a-1-1", "chapter-a1", 0, 900)
        );

        BidGenerationPlan plan = new BidGenerationExecutionPlanner(3).plan(snapshot, units);

        assertThat(plan.maxConcurrency()).isEqualTo(3);
        assertThat(plan.lanes()).extracting(BidGenerationPlan.Lane::rootOutlineId)
                .containsExactly("root-a", "root-b");
        assertThat(plan.lanes().getFirst().unitIds())
                .containsExactly("a-1-1", "a-1-2", "a-2-1");
        assertThat(plan.lanes().getFirst().estimatedCharacters()).isEqualTo(3_000);
        assertThat(plan.lanes().get(1).unitIds()).containsExactly("b-1");
    }

    @Test
    void rejectsConcurrencyOutsideDeploymentBoundary() {
        assertThatThrownBy(() -> new BidGenerationExecutionPlanner(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1至5");
        assertThatThrownBy(() -> new BidGenerationExecutionPlanner(6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1至5");
    }

    private BidGenerationSnapshot snapshot(List<BidGenerationSnapshot.Outline> outline) {
        return new BidGenerationSnapshot(
                "snapshot", "task", "bid", "owner", "hash", "测试标书", "BLIND",
                80, 1, 1, "解决方案契约", "", "", "", "chapter-unit-v4",
                LocalDateTime.now(),
                List.of(), outline, List.of(), List.of());
    }

    private BidGenerationSnapshot.Outline outline(
            String sourceId, String chapterId, String parentId, int level, int sortOrder
    ) {
        return new BidGenerationSnapshot.Outline(
                sourceId, chapterId, "PENDING", parentId, level, sourceId,
                level == 1 ? 40 : 0, sortOrder, "", List.of(), List.of());
    }

    private BidGenerationUnit unit(
            String id, String chapterId, int unitIndex, int budget
    ) {
        return new BidGenerationUnit(
                id, "task", "bid", chapterId, unitIndex, id, "key-" + id,
                "PENDING", 0, budget, "", "", "", "", "", LocalDateTime.now());
    }
}
