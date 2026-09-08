package com.td.czghagent.application.command.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidSemanticUnitPlannerTest {

    @Test
    void keepsNormalLeafChapterAsOneSemanticUnit() {
        List<BidSemanticUnitPlanner.SemanticUnit> units = BidSemanticUnitPlanner.plan(3_200);

        assertThat(units).singleElement().satisfies(unit -> {
            assertThat(unit.role()).isEqualTo("完整技术响应");
            assertThat(unit.characterBudget()).isEqualTo(3_200);
        });
    }

    @Test
    void splitsLongChapterByWritingResponsibilityAndPreservesBudget() {
        List<BidSemanticUnitPlanner.SemanticUnit> units = BidSemanticUnitPlanner.plan(10_000);

        assertThat(units).extracting(BidSemanticUnitPlanner.SemanticUnit::role)
                .containsExactly("需求判断与响应边界", "技术设计与实施方法", "管控交付与验收");
        assertThat(units).extracting(BidSemanticUnitPlanner.SemanticUnit::characterBudget)
                .allMatch(value -> value > 0);
        assertThat(units.stream().mapToInt(
                BidSemanticUnitPlanner.SemanticUnit::characterBudget).sum()).isEqualTo(10_000);
    }
}
