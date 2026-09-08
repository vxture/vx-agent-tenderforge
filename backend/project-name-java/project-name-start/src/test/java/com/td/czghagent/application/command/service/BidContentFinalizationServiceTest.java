package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidProductionState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BidContentFinalizationServiceTest {

    @Test
    void reusesOnlySuccessfulLayoutForCurrentFrozenContentHash() {
        BidProductionState.LayoutJob current = layout("SUCCEEDED", "current-hash");
        BidProductionState.LayoutJob stale = layout("SUCCEEDED", "old-hash");

        assertThat(BidContentFinalizationService.layoutMatchesContent(
                current, "FROZEN", "current-hash")).isTrue();
        assertThat(BidContentFinalizationService.layoutMatchesContent(
                stale, "FROZEN", "current-hash")).isFalse();
        assertThat(BidContentFinalizationService.layoutMatchesContent(
                current, "GENERATING", "current-hash")).isFalse();
        assertThat(BidContentFinalizationService.layoutMatchesContent(
                current, "FROZEN", null)).isFalse();
    }

    private BidProductionState.LayoutJob layout(String status, String inputHash) {
        return new BidProductionState.LayoutJob(
                "layout-1", status, null, inputHash, 80, 77,
                "PASSED", "质量检查通过", null, LocalDateTime.now(), LocalDateTime.now());
    }
}
