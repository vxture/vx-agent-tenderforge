// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 解读、目录、排版三个单步工作流的共同契约：成功走完步骤；任何一步失败都先调 {@code fail}
 * 把根因写回任务，再让工作流失败。漏掉 {@code fail} 的表现是任务永远停在「进行中」，界面一直转圈。
 *
 * <p>时间跳跃环境里活动重试的退避不真的等待，所以「失败一次后重试成功」这类用例也跑得很快。
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class BidStageWorkflowsTest {

    private static final String QUEUE = "bid-stage-workflows-test";

    private TestWorkflowEnvironment environment;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        worker = environment.newWorker(QUEUE);
        client = environment.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    private WorkflowOptions options() {
        return WorkflowOptions.newBuilder().setTaskQueue(QUEUE).build();
    }

    @Nested
    class Interpretation {

        private final List<String> calls = new CopyOnWriteArrayList<>();
        private final AtomicInteger parseAttempts = new AtomicInteger();
        private volatile RuntimeException parseFailureOnFirstAttempt;
        private volatile RuntimeException overviewFailure;

        private final BidInterpretationActivities activities = new BidInterpretationActivities() {
            @Override
            public void parseDocument(String sourceId, String bidId, String ownerId, String traceId, String ip) {
                calls.add("parse");
                if (parseAttempts.incrementAndGet() == 1 && parseFailureOnFirstAttempt != null) {
                    throw parseFailureOnFirstAttempt;
                }
            }

            @Override
            public void generateProjectOverview(String sourceId, String bidId, String ownerId, String traceId, String ip) {
                calls.add("overview");
                if (overviewFailure != null) {
                    throw overviewFailure;
                }
            }

            @Override
            public void generateTechnicalScoring(String sourceId, String bidId, String ownerId, String traceId, String ip) {
                calls.add("scoring");
            }

            @Override
            public void complete(String sourceId, String bidId, String ownerId, String traceId, String ip) {
                calls.add("complete");
            }

            @Override
            public void fail(String sourceId, String bidId, String ownerId, String traceId, String ip, String message) {
                calls.add("fail:" + message);
            }
        };

        private void run() {
            worker.registerWorkflowImplementationTypes(BidInterpretationWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
            environment.start();
            client.newWorkflowStub(BidInterpretationWorkflow.class, options())
                    .interpret("job-1", "source-1", "bid-1", "owner-1", "trace-1", "127.0.0.1");
        }

        @Test
        void parsesThenWritesOverviewThenScoringThenCompletes() {
            run();

            assertThat(calls).containsExactly("parse", "overview", "scoring", "complete");
        }

        /** 解析服务偶发失败时 Temporal 重试一次就过，用户看不到失败。 */
        @Test
        void retriesATransientParseFailureOnce() {
            parseFailureOnFirstAttempt = ApplicationFailure.newFailure("解析服务暂时不可用", "AI_PROVIDER_ERROR");

            run();

            assertThat(parseAttempts).hasValue(2);
            assertThat(calls).containsExactly("parse", "parse", "overview", "scoring", "complete");
        }

        @Test
        void stopsAtTheFailingStepAndRecordsTheRootCause() {
            overviewFailure = ApplicationFailure.newNonRetryableFailure("招标文件缺少项目概况", "AI_OUTPUT_INVALID");

            assertThatThrownBy(this::run).isInstanceOf(WorkflowFailedException.class);

            assertThat(calls).startsWith("parse", "overview").doesNotContain("scoring", "complete");
            assertThat(calls.getLast()).isEqualTo("fail:招标文件缺少项目概况");
        }
    }

    @Nested
    class Outline {

        private final List<String> calls = new CopyOnWriteArrayList<>();
        private volatile RuntimeException processFailure;

        private final BidOutlineActivities activities = new BidOutlineActivities() {
            @Override
            public void process(String taskId, String bidId, String ownerId, String traceId, String ip) {
                calls.add("process");
                if (processFailure != null) {
                    throw processFailure;
                }
            }

            @Override
            public void fail(String taskId, String bidId, String message) {
                calls.add("fail:" + message);
            }
        };

        private void run() {
            worker.registerWorkflowImplementationTypes(BidOutlineWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
            environment.start();
            client.newWorkflowStub(BidOutlineWorkflow.class, options())
                    .generate("task-1", "bid-1", "owner-1", "trace-1", "127.0.0.1");
        }

        @Test
        void processesOnceAndDoesNotRecordAFailure() {
            run();

            assertThat(calls).containsExactly("process");
        }

        @Test
        void recordsTheRootCauseWhenGenerationFails() {
            processFailure = ApplicationFailure.newNonRetryableFailure("评分项为空，无法生成目录", "BID_NOT_READY");

            assertThatThrownBy(this::run).isInstanceOf(WorkflowFailedException.class);

            assertThat(calls).hasSize(2);
            assertThat(calls.getLast()).isEqualTo("fail:评分项为空，无法生成目录");
        }
    }

    @Nested
    class Layout {

        private final List<String> calls = new CopyOnWriteArrayList<>();
        private volatile RuntimeException renderFailure;

        private final BidLayoutActivities activities = new BidLayoutActivities() {
            @Override
            public void render(String layoutJobId, String bidId, String ownerId) {
                calls.add("render");
                if (renderFailure != null) {
                    throw renderFailure;
                }
            }

            @Override
            public void fail(String layoutJobId, String bidId, String message) {
                calls.add("fail:" + message);
            }
        };

        private void run() {
            worker.registerWorkflowImplementationTypes(BidLayoutWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
            environment.start();
            client.newWorkflowStub(BidLayoutWorkflow.class, options()).layout("layout-1", "bid-1", "owner-1");
        }

        @Test
        void rendersOnceAndDoesNotRecordAFailure() {
            run();

            assertThat(calls).containsExactly("render");
        }

        @Test
        void recordsTheRootCauseWhenRenderingFails() {
            renderFailure = ApplicationFailure.newNonRetryableFailure("字体缺失，无法排版", "LAYOUT_FAILED");

            assertThatThrownBy(this::run).isInstanceOf(WorkflowFailedException.class);

            assertThat(calls).hasSize(2);
            assertThat(calls.getLast()).isEqualTo("fail:字体缺失，无法排版");
        }
    }
}
