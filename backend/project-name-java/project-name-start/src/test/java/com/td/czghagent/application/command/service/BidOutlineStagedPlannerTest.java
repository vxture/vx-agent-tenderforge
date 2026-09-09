// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.port.BidOutlineStageStore;
import com.td.czghagent.domain.port.TenderAiGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 目录分阶段生成的断点恢复。
 *
 * <p>这一组保护的是<strong>钱和一致性</strong>，不是功能：一份 500 页标书的三级展开
 * 有十几批模型调用。不恢复的表现不是报错——是重试时从策略开始重跑，
 * 已经成功的十几批被丢掉重新付一遍钱，而且重跑出来的目录和上一次并不相同。
 */
class BidOutlineStagedPlannerTest {

    private static final String TASK = "task-1";
    private static final String BID = "bid-1";

    private final BidAiExecutionService ai = mock(BidAiExecutionService.class);
    private final RecordingStore store = new RecordingStore();
    private final BidOutlineStagedPlanner planner =
            new BidOutlineStagedPlanner(ai, store, "fast-model", "quality-model");

    // ── 正常一轮 ────────────────────────────────────────────────────────────

    @Test
    void runsEveryStageOnceAndAssemblesTheResult() {
        givenAllStagesSucceed(2);

        planner.plan(TASK, BID, request());

        verify(ai).planOutlineStrategy(eq(BID), any());
        verify(ai).planOutlineSkeleton(eq(BID), any());
        assertThat(store.begun)
                .as("两个批次各自成为一个可恢复的阶段")
                .containsExactly("STRATEGY#0", "SKELETON#0", "EXPANSION#1", "EXPANSION#2");
        assertThat(store.completed).hasSize(4);
    }

    /** 空批次不产生阶段——一个没有分支要展开的批次不该占一个可恢复的位置。 */
    @Test
    void skipsAnEmptyBatchInsteadOfRecordingAnEmptyStage() {
        givenAllStagesSucceed(0);

        planner.plan(TASK, BID, request());

        assertThat(store.begun).containsExactly("STRATEGY#0", "SKELETON#0");
        verify(ai, never()).expandOutline(anyString(), any());
    }

    // ── 断点恢复 ────────────────────────────────────────────────────────────

    /**
     * 已存的阶段直接复用，<strong>不再调模型</strong>。
     *
     * <p>这条不成立时不会有任何报错：重试照样能跑完，只是每一次都把整份目录
     * 从头重做一遍。发现它的唯一方式是有人去看账单。
     */
    @Test
    void resumesAStoredStageWithoutPayingForItAgain() {
        givenAllStagesSucceed(2);
        store.storeEverything = true;

        planner.plan(TASK, BID, request());

        verify(ai, never()).planOutlineStrategy(anyString(), any());
        verify(ai, never()).planOutlineSkeleton(anyString(), any());
        verify(ai, never()).expandOutline(anyString(), any());
        assertThat(store.begun).as("命中恢复的阶段不再开一次新的尝试").isEmpty();
    }

    /**
     * 只有<strong>输入一模一样</strong>时才复用。
     *
     * <p>用「这个阶段做完了」作判据，会在解读被重新冻结、评分要求变了之后
     * 继续复用旧结果——新的评分要求悄悄没进目录，而任务显示成功。
     */
    @Test
    void refusesToResumeWhenTheInputChanged() {
        givenAllStagesSucceed(1);
        store.storedStrategyHash = "a-hash-from-an-earlier-input";

        planner.plan(TASK, BID, request());

        verify(ai).planOutlineStrategy(eq(BID), any());
        assertThat(store.begun).contains("STRATEGY#0");
    }

    /** 同样的输入两次算出同样的哈希，否则恢复永远命不中。 */
    @Test
    void hashesTheSameInputToTheSameKey() {
        givenAllStagesSucceed(1);
        planner.plan(TASK, BID, request());
        List<String> first = List.copyOf(store.hashes);

        store.reset();
        planner.plan(TASK, BID, request());

        assertThat(store.hashes).isEqualTo(first);
    }

    // ── 失败要留下痕迹 ──────────────────────────────────────────────────────

    /**
     * 失败记下错误码和批次。
     *
     * <p>不记的话，一个反复在第 7 批失败的任务，在日志里和一个从头就起不来的
     * 任务长得一模一样——而这两者要做的事完全不同。
     */
    @Test
    void recordsWhichBatchFailedAndWhy() {
        givenAllStagesSucceed(3);
        when(ai.expandOutline(eq(BID), any()))
                .thenReturn(expansion())
                .thenThrow(new BusinessException("AI_OUTPUT_INVALID", "结构校验未通过", 502));

        assertThatThrownBy(() -> planner.plan(TASK, BID, request()))
                .isInstanceOf(BusinessException.class);

        assertThat(store.failed).containsExactly("EXPANSION#2:AI_OUTPUT_INVALID");
        assertThat(store.completed)
                .as("第 1 批已经成功，它的结果必须留着给下一次恢复")
                .contains("EXPANSION#1");
    }

    /** 非业务异常也要记，用一个稳定的码，而不是让这个阶段悄无声息地消失。 */
    @Test
    void recordsAnUnexpectedFailureUnderAStableCode() {
        givenAllStagesSucceed(1);
        when(ai.planOutlineSkeleton(eq(BID), any()))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> planner.plan(TASK, BID, request()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.failed).containsExactly("SKELETON#0:OUTLINE_STAGE_FAILED");
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private void givenAllStagesSucceed(int batchCount) {
        when(ai.planOutlineStrategy(eq(BID), any())).thenReturn(strategy());
        when(ai.planOutlineSkeleton(eq(BID), any())).thenReturn(skeleton(batchCount));
        when(ai.expandOutline(eq(BID), any())).thenReturn(expansion());
        when(ai.assembleOutline(any())).thenReturn(
                new TenderAiGateway.OutlinePlan(
                        List.of(), List.of(),
                        new TenderAiGateway.FrozenDictionary(
                                List.of(), List.of(), List.of()),
                        List.of()));
    }

    private static TenderAiGateway.OutlineRequest request() {
        return new TenderAiGateway.OutlineRequest(
                "trace-1", "测试技术标", 80, "BLIND",
                List.of(new TenderAiGateway.Criterion(
                        "c1", "PROJECT_OVERVIEW", "概述", "建设统一技术平台",
                        null, "", "")),
                List.of());
    }

    private static TenderAiGateway.BidStrategy strategy() {
        return new TenderAiGateway.BidStrategy(
                "PLATFORM", "统一技术边界", List.of("可验证"), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static TenderAiGateway.OutlineSkeletonPlan skeleton(int batchCount) {
        List<List<TenderAiGateway.OutlineBranchTarget>> batches = new ArrayList<>();
        for (int index = 1; index <= batchCount; index++) {
            batches.add(List.of(new TenderAiGateway.OutlineBranchTarget(
                    "branch-" + index, "技术方案", "建设任务" + index,
                    "细化建设对象", List.of("验收"), 3)));
        }
        if (batches.isEmpty()) {
            batches.add(List.of());
        }
        return new TenderAiGateway.OutlineSkeletonPlan(
                List.of(), List.of(), batches, List.of("SP-001"));
    }

    private static TenderAiGateway.OutlineExpansion expansion() {
        return new TenderAiGateway.OutlineExpansion(List.of(), List.of());
    }

    /** 记下每个阶段被开始、完成还是失败，以及它用的输入哈希。 */
    private static final class RecordingStore implements BidOutlineStageStore {
        private final List<String> begun = new ArrayList<>();
        private final List<String> completed = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private final List<String> hashes = new ArrayList<>();
        private final Map<String, String> seenHash = new LinkedHashMap<>();
        private boolean storeEverything;
        private String storedStrategyHash;

        private void reset() {
            begun.clear();
            completed.clear();
            failed.clear();
            hashes.clear();
            seenHash.clear();
        }

        @Override
        public Optional<TenderAiGateway.BidStrategy> findLatestStrategy(String taskId) {
            return Optional.empty();
        }

        @Override
        public Optional<TenderAiGateway.BidStrategy> findStrategy(
                String taskId, String inputHash) {
            record("STRATEGY#0", inputHash);
            if (storedStrategyHash != null) {
                // 存着的是另一份输入的结果——哈希对不上就不该被复用。
                return storedStrategyHash.equals(inputHash)
                        ? Optional.of(strategy()) : Optional.empty();
            }
            return storeEverything ? Optional.of(strategy()) : Optional.empty();
        }

        @Override
        public Optional<TenderAiGateway.OutlineSkeletonPlan> findSkeleton(
                String taskId, String inputHash) {
            record("SKELETON#0", inputHash);
            return storeEverything ? Optional.of(skeleton(2)) : Optional.empty();
        }

        @Override
        public Optional<TenderAiGateway.OutlineExpansion> findExpansion(
                String taskId, int batchIndex, String inputHash) {
            record("EXPANSION#" + batchIndex, inputHash);
            return storeEverything ? Optional.of(expansion()) : Optional.empty();
        }

        @Override
        public void begin(String taskId, String stage, int batchIndex,
                          String inputHash, String modelName) {
            begun.add(stage + "#" + batchIndex);
        }

        @Override
        public void completeStrategy(String taskId, String inputHash,
                                     TenderAiGateway.BidStrategy strategy,
                                     String outputHash, long durationMillis) {
            completed.add("STRATEGY#0");
        }

        @Override
        public void completeSkeleton(String taskId, String inputHash,
                                     TenderAiGateway.OutlineSkeletonPlan skeleton,
                                     String outputHash, long durationMillis) {
            completed.add("SKELETON#0");
        }

        @Override
        public void completeExpansion(String taskId, int batchIndex, String inputHash,
                                      TenderAiGateway.OutlineExpansion expansion,
                                      String outputHash, long durationMillis) {
            completed.add("EXPANSION#" + batchIndex);
        }

        @Override
        public void fail(String taskId, String stage, int batchIndex, String inputHash,
                         String errorCode, String errorMessage, long durationMillis) {
            failed.add(stage + "#" + batchIndex + ":" + errorCode);
        }

        private void record(String stage, String inputHash) {
            hashes.add(inputHash);
            seenHash.put(stage, inputHash);
        }
    }
}
