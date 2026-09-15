// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.model.BidGenerationPlan;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 正文生成工作流：按 lane 并行生成、可修复失败只重跑一次、任何失败都落到 {@code fail}。
 *
 * <p>用 Temporal 的时间跳跃测试环境真跑工作流，而不是直接 new 实现类调方法——
 * 工作流代码里的 {@code Async}、{@code Promise}、活动重试与 {@code Workflow.getVersion}
 * 离开 Temporal 运行时根本不成立。活动换成记录调用的替身，按单元配置失败。
 *
 * <p>新启动的工作流取 {@code getVersion} 的最大版本，即「按 lane 并行 + 可修复失败重跑」这条路径。
 * 串行与不带修复的两条旧路径只在重放历史里走到，这里没有录下的历史，不覆盖它们。
 *
 * <p>整类限时：工作流里抛非 {@code ApplicationFailure} 的异常不会让工作流失败，只会让工作流任务
 * 被无限重放——测试会挂住而不是变红（2026-09-15 正是这样发现的）。限时让那种回退表现为红。
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class BidContentGenerationWorkflowTest {

    private static final String QUEUE = "bid-content-generation-test";

    private TestWorkflowEnvironment environment;
    private Worker worker;
    private WorkflowClient client;
    private RecordingActivities activities;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        worker = environment.newWorker(QUEUE);
        worker.registerWorkflowImplementationTypes(BidContentGenerationWorkflowImpl.class);
        client = environment.getWorkflowClient();
        activities = new RecordingActivities();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void generatesEveryUnitOnceAcrossLanesAndThenFinalizes() {
        activities.plan = plan(2, lane("r1", 300, "u1", "u2"), lane("r2", 100, "u3"));

        run();

        assertThat(activities.unitCalls()).containsExactlyInAnyOrder("u1", "u2", "u3");
        assertThat(activities.calls.indexOf("unit:u1"))
                .as("同一个 lane 里的单元按顺序生成").isLessThan(activities.calls.indexOf("unit:u2"));
        assertThat(activities.calls).last().isEqualTo("complete");
        assertThat(activities.failures()).isEmpty();
    }

    /** 可修复的失败（模型输出不合格、服务商抖动、质量拦截）只重跑失败的那个单元一次，修好就照常定稿。 */
    @Test
    void repairsARecoverableUnitFailureOnceAndStillFinalizes() {
        activities.plan = plan(2, lane("r1", 300, "u1", "u2"), lane("r2", 100, "u3"));
        activities.failNext("u2", recoverable("AI_OUTPUT_INVALID", "结构化输出缺字段"));

        run();

        assertThat(activities.unitCalls())
                .as("修复只重跑失败单元，不把整本重来一遍")
                .containsExactlyInAnyOrder("u1", "u2", "u2", "u3");
        assertThat(activities.calls).last().isEqualTo("complete");
        assertThat(activities.failures()).isEmpty();
    }

    @Test
    void failsWithASummaryWhenAUnitIsStillBrokenAfterRepairAndNeverFinalizes() {
        activities.plan = plan(1, lane("r1", 300, "u1", "u2"));
        activities.failNext("u2", recoverable("AI_PROVIDER_ERROR", "上游超时"));
        activities.failNext("u2", recoverable("AI_PROVIDER_ERROR", "上游仍然超时"));

        assertThatThrownBy(this::run).isInstanceOf(WorkflowFailedException.class);

        assertThat(activities.calls).doesNotContain("complete");
        assertThat(activities.failures())
                .as("写回任务的是原因原文，不是 Temporal 格式化的 message='…', type='…'")
                .containsExactly("正文生成仍有1个单元未能自动修复：AI_PROVIDER_ERROR：上游仍然超时");
    }

    /** 不在可修复名单里的失败（数据不存在、权限、快照过期……）重跑也不会变好，立刻失败，不进修复。 */
    @Test
    void failsImmediatelyOnANonRecoverableUnitFailureWithoutARepairPass() {
        activities.plan = plan(1, lane("r1", 300, "u1", "u2", "u3"));
        activities.failNext("u2", recoverable("BID_SNAPSHOT_STALE", "目录快照已被新的冻结替换"));

        assertThatThrownBy(this::run).isInstanceOf(WorkflowFailedException.class);

        assertThat(activities.unitCalls()).as("失败单元之后的单元不再生成，失败单元也不重跑")
                .containsExactly("u1", "u2");
        assertThat(activities.calls).doesNotContain("complete");
        assertThat(activities.failures()).containsExactly("目录快照已被新的冻结替换");
    }

    @Test
    void recordsTheRootCauseWhenThePlanCannotBePrepared() {
        activities.planFailure = recoverable("BID_NOT_READY", "目录尚未冻结");

        assertThatThrownBy(this::run).isInstanceOf(WorkflowFailedException.class);

        assertThat(activities.unitCalls()).isEmpty();
        assertThat(activities.failures()).containsExactly("目录尚未冻结");
    }

    /** 定稿失败同样要落到 fail——否则任务永远停在「生成中」，界面转圈不止。 */
    @Test
    void recordsAFailureWhenFinalizationFails() {
        activities.plan = plan(1, lane("r1", 100, "u1"));
        activities.completeFailure = recoverable("EXPORT_WRITE_FAILED", "成稿写入失败");

        assertThatThrownBy(this::run).isInstanceOf(WorkflowFailedException.class);

        assertThat(activities.failures()).containsExactly("成稿写入失败");
    }

    private void run() {
        worker.registerActivitiesImplementations(activities);
        environment.start();
        BidContentGenerationWorkflow workflow = client.newWorkflowStub(
                BidContentGenerationWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(QUEUE).build());
        workflow.generate("task-1", "bid-1", "owner-1", "snapshot-1", "hash-1");
    }

    private static BidGenerationPlan plan(int maxConcurrency, BidGenerationPlan.Lane... lanes) {
        return new BidGenerationPlan(List.of(lanes), maxConcurrency);
    }

    private static BidGenerationPlan.Lane lane(String root, int characters, String... unitIds) {
        return new BidGenerationPlan.Lane(root, root, characters, List.of(unitIds));
    }

    /** 不可重试：让工作流立刻看到这次失败，由工作流自己的修复逻辑决定要不要再跑。 */
    private static ApplicationFailure recoverable(String type, String message) {
        return ApplicationFailure.newNonRetryableFailure(message, type);
    }

    static final class RecordingActivities implements BidGenerationActivities {
        final List<String> calls = new CopyOnWriteArrayList<>();
        final Map<String, Deque<RuntimeException>> unitFailures = new ConcurrentHashMap<>();
        volatile BidGenerationPlan plan;
        volatile RuntimeException planFailure;
        volatile RuntimeException completeFailure;

        void failNext(String unitId, RuntimeException failure) {
            unitFailures.computeIfAbsent(unitId, ignored -> new ArrayDeque<>()).add(failure);
        }

        List<String> unitCalls() {
            return calls.stream().filter(call -> call.startsWith("unit:"))
                    .map(call -> call.substring("unit:".length())).toList();
        }

        List<String> failures() {
            return calls.stream().filter(call -> call.startsWith("fail:"))
                    .map(call -> call.substring("fail:".length())).toList();
        }

        @Override
        public List<String> prepare(String taskId, String bidId, String ownerId,
                                    String snapshotId, String snapshotHash) {
            calls.add("prepare");
            return List.of();
        }

        @Override
        public BidGenerationPlan preparePlan(String taskId, String bidId, String ownerId,
                                             String snapshotId, String snapshotHash) {
            calls.add("preparePlan");
            if (planFailure != null) {
                throw planFailure;
            }
            return plan;
        }

        @Override
        public void generateUnit(String taskId, String bidId, String ownerId,
                                 String snapshotId, String snapshotHash, String unitId) {
            calls.add("unit:" + unitId);
            Deque<RuntimeException> pending = unitFailures.get(unitId);
            RuntimeException failure;
            synchronized (this) {
                failure = pending == null ? null : pending.poll();
            }
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void complete(String taskId, String bidId, String ownerId,
                             String snapshotId, String snapshotHash) {
            calls.add("complete");
            if (completeFailure != null) {
                throw completeFailure;
            }
        }

        @Override
        public void fail(String taskId, String bidId, String errorMessage) {
            calls.add("fail:" + errorMessage);
        }
    }
}
