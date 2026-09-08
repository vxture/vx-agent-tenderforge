package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.model.BidGenerationPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidGenerationLaneSchedulerTest {
    @Test
    void capsRunnersAndKeepsEachTopLevelLaneIntact() {
        BidGenerationPlan plan = new BidGenerationPlan(List.of(
                lane("a", 9_000, "a1", "a2"),
                lane("b", 7_000, "b1"),
                lane("c", 5_000, "c1", "c2"),
                lane("d", 2_000, "d1")
        ), 3);

        List<List<BidGenerationPlan.Lane>> queues = BidGenerationLaneScheduler.schedule(plan);

        assertThat(queues).hasSize(3);
        assertThat(queues.stream().flatMap(List::stream)
                .map(BidGenerationPlan.Lane::rootOutlineId).toList())
                .containsExactlyInAnyOrder("a", "b", "c", "d");
        assertThat(queues.stream().flatMap(List::stream)
                .filter(item -> item.rootOutlineId().equals("a"))
                .findFirst().orElseThrow().unitIds()).containsExactly("a1", "a2");
    }

    @Test
    void serialConfigurationUsesOneRunner() {
        BidGenerationPlan plan = new BidGenerationPlan(List.of(
                lane("a", 2_000, "a1"), lane("b", 1_000, "b1")
        ), 1);

        assertThat(BidGenerationLaneScheduler.schedule(plan))
                .singleElement().satisfies(queue -> assertThat(queue).hasSize(2));
    }

    private BidGenerationPlan.Lane lane(
            String id, int characters, String... unitIds
    ) {
        return new BidGenerationPlan.Lane(id, id, characters, List.of(unitIds));
    }
}
