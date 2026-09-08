package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidWorkspace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidCommandSupportTest {
    @Test
    void normalizesModelSubpointSuffixesAndDropsUnknownReferences() {
        String overviewId = "9ce8112d-eab7-3fbb-a3b1-d2f7d624b4b3";
        String scoringId = "f1b7559d-645d-3d18-8776-139bd8e20a93";
        List<BidWorkspace.Criterion> criteria = List.of(
                criterion(overviewId, "PROJECT_OVERVIEW"),
                criterion(scoringId, "TECHNICAL_SCORING"));
        BidCommandSupport support = new BidCommandSupport(null, null);

        List<String> result = support.validScoringPointIds(
                List.of(scoringId + "-9", scoringId, "model-invented-reference"), criteria);

        assertThat(result).containsExactly(scoringId);
    }

    private BidWorkspace.Criterion criterion(String id, String type) {
        return new BidWorkspace.Criterion(
                id, type, type, "content", null, "", "", "TECHNICAL", "HIGH", 0, false);
    }
}
