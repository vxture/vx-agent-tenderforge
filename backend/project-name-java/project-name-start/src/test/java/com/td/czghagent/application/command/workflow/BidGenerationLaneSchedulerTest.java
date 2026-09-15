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

    @Test
    void anEmptyPlanSchedulesNothing() {
        assertThat(BidGenerationLaneScheduler.schedule(new BidGenerationPlan(List.of(), 3))).isEmpty();
    }

    /** 最大的 lane 先分，每次放进当前负载最小的队列——长章节不会全挤在一个 runner 上。 */
    @Test
    void placesLargestLanesFirstOntoTheLeastLoadedRunner() {
        List<List<BidGenerationPlan.Lane>> queues = BidGenerationLaneScheduler.schedule(new BidGenerationPlan(List.of(
                lane("small", 100, "s1"), lane("huge", 900, "h1"),
                lane("medium", 500, "m1"), lane("tiny", 50, "t1")
        ), 2));

        assertThat(ids(queues.get(0))).containsExactly("huge");
        assertThat(ids(queues.get(1))).containsExactly("medium", "small", "tiny");
    }

    /**
     * 同样大的 lane 按根目录标识决胜，输入顺序不影响结果。
     *
     * <p>分配必须确定：工作流重放时要得到同一组队列，否则重放与原执行对不上，Temporal 判非确定性错误。
     */
    @Test
    void breaksTiesByRootOutlineIdSoInputOrderDoesNotChangeTheQueues() {
        List<List<BidGenerationPlan.Lane>> forward = BidGenerationLaneScheduler.schedule(new BidGenerationPlan(List.of(
                lane("b", 100, "b1"), lane("a", 100, "a1"), lane("c", 100, "c1")), 2));
        List<List<BidGenerationPlan.Lane>> reversed = BidGenerationLaneScheduler.schedule(new BidGenerationPlan(List.of(
                lane("c", 100, "c1"), lane("a", 100, "a1"), lane("b", 100, "b1")), 2));

        assertThat(forward.stream().map(BidGenerationLaneSchedulerTest::ids).toList())
                .isEqualTo(reversed.stream().map(BidGenerationLaneSchedulerTest::ids).toList())
                .isEqualTo(List.of(List.of("a", "c"), List.of("b")));
    }

    @Test
    void returnsImmutableQueues() {
        List<List<BidGenerationPlan.Lane>> queues = BidGenerationLaneScheduler.schedule(
                new BidGenerationPlan(List.of(lane("a", 10, "a1")), 1));

        assertThat(queues).isUnmodifiable();
        assertThat(queues.get(0)).isUnmodifiable();
    }

    private static List<String> ids(List<BidGenerationPlan.Lane> queue) {
        return queue.stream().map(BidGenerationPlan.Lane::rootOutlineId).toList();
    }

    private BidGenerationPlan.Lane lane(
            String id, int characters, String... unitIds
    ) {
        return new BidGenerationPlan.Lane(id, id, characters, List.of(unitIds));
    }
}
