// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.application.command.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.port.BidOutlineStageStore;
import com.td.czghagent.domain.port.TenderAiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 按阶段生成目录，并把每个阶段的结果落库，好让重试从断点继续。
 *
 * <p><strong>为什么不是一次调用就完事：</strong>一份 500 页标书的三级展开有十几批
 * 模型调用，串起来要几分钟。中间任何一批失败——模型抖动、超时、结构校验没过——
 * 整个活动会被 Temporal 重试。不落阶段结果的话，重试意味着<em>从策略开始重跑</em>：
 * 已经成功的十几批被丢掉重新付一遍钱，而且重跑出来的目录和上一次并不相同。
 *
 * <p>恢复的判据是 {@code input_hash}：只有输入一模一样时才复用上次的结果。
 * 用「阶段做完了」作判据会在输入变化后复用陈旧结果，而那不会报错——
 * 只会让新的评分要求悄悄没进目录。
 */
@Service
public class BidOutlineStagedPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BidOutlineStagedPlanner.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String STAGE_STRATEGY = "STRATEGY";
    private static final String STAGE_SKELETON = "SKELETON";
    private static final String STAGE_EXPANSION = "EXPANSION";

    private final BidAiExecutionService aiExecutionService;
    private final BidOutlineStageStore stages;
    private final String fastModelName;
    private final String qualityModelName;

    public BidOutlineStagedPlanner(
            BidAiExecutionService aiExecutionService,
            BidOutlineStageStore stages,
            @Value("${app.ai.fast-model-name:deepseek-v4-flash}") String fastModelName,
            @Value("${app.ai.quality-model-name:deepseek-v4-pro}") String qualityModelName
    ) {
        this.aiExecutionService = aiExecutionService;
        this.stages = stages;
        this.fastModelName = fastModelName;
        this.qualityModelName = qualityModelName;
    }

    public TenderAiGateway.OutlinePlan plan(
            String taskId, String bidId, TenderAiGateway.OutlineRequest request) {
        TenderAiGateway.BidStrategy strategy = strategy(taskId, bidId, request);
        TenderAiGateway.OutlineSkeletonPlan skeleton =
                skeleton(taskId, bidId, request, strategy);
        List<TenderAiGateway.OutlineExpansion> expansions =
                expansions(taskId, bidId, request, strategy, skeleton);
        // 装配是确定性的，不调模型，所以不落阶段结果——它可以随时按同样的输入重算，
        // 而存一份反而多出一个可能和输入不一致的副本。
        return aiExecutionService.assembleOutline(
                new TenderAiGateway.OutlineAssemblyRequest(request, skeleton, expansions));
    }

    private TenderAiGateway.BidStrategy strategy(
            String taskId, String bidId, TenderAiGateway.OutlineRequest request) {
        String inputHash = hash(request);
        return resume(
                stages.findStrategy(taskId, inputHash), STAGE_STRATEGY, 0,
                taskId, inputHash, qualityModelName,
                () -> aiExecutionService.planOutlineStrategy(bidId, request),
                (value, millis) ->
                        stages.completeStrategy(taskId, inputHash, value, hash(value), millis));
    }

    private TenderAiGateway.OutlineSkeletonPlan skeleton(
            String taskId, String bidId, TenderAiGateway.OutlineRequest request,
            TenderAiGateway.BidStrategy strategy) {
        TenderAiGateway.OutlineSkeletonRequest skeletonRequest =
                new TenderAiGateway.OutlineSkeletonRequest(request, strategy);
        String inputHash = hash(skeletonRequest);
        return resume(
                stages.findSkeleton(taskId, inputHash), STAGE_SKELETON, 0,
                taskId, inputHash, qualityModelName,
                () -> aiExecutionService.planOutlineSkeleton(bidId, skeletonRequest),
                (value, millis) ->
                        stages.completeSkeleton(taskId, inputHash, value, hash(value), millis));
    }

    /**
     * 逐批展开。
     *
     * <p>串行而不是并行：并行的收益是墙钟时间，代价是一批失败时另外几批还在飞，
     * 它们的结果落不落库都不对——落了下次会被复用而调用方并不知道，
     * 不落则白付了钱。Python 侧在一次调用内部已经做了受限并发，
     * 这一层要的是<strong>可恢复</strong>，两者不冲突。
     */
    private List<TenderAiGateway.OutlineExpansion> expansions(
            String taskId, String bidId, TenderAiGateway.OutlineRequest request,
            TenderAiGateway.BidStrategy strategy,
            TenderAiGateway.OutlineSkeletonPlan skeleton) {
        List<TenderAiGateway.OutlineExpansion> results = new ArrayList<>();
        int batchIndex = 1;
        for (List<TenderAiGateway.OutlineBranchTarget> batch : skeleton.batches()) {
            if (batch.isEmpty()) {
                continue;
            }
            TenderAiGateway.OutlineExpansionRequest expansionRequest =
                    new TenderAiGateway.OutlineExpansionRequest(
                            request, strategy, batchIndex, batch);
            String inputHash = hash(expansionRequest);
            int index = batchIndex;
            results.add(resume(
                    stages.findExpansion(taskId, index, inputHash), STAGE_EXPANSION, index,
                    taskId, inputHash, fastModelName,
                    () -> aiExecutionService.expandOutline(bidId, expansionRequest),
                    (value, millis) -> stages.completeExpansion(
                            taskId, index, inputHash, value, hash(value), millis)));
            batchIndex++;
        }
        return results;
    }

    /**
     * 命中就复用，没命中就跑一次并记账。
     *
     * <p>失败也要记：{@code fail} 写下的错误码和耗时是事后回答「这个任务卡在哪一批、
     * 试了几次」的唯一材料。不记的话，一个反复在第 7 批失败的任务，
     * 在日志里和一个从头就起不来的任务长得一样。
     */
    private <T> T resume(
            Optional<T> stored, String stage, int batchIndex,
            String taskId, String inputHash, String modelName,
            Supplier<T> call, StageRecorder<T> record) {
        if (stored.isPresent()) {
            LOGGER.info("Outline task {} resumed stage {}#{} from a stored result",
                    taskId, stage, batchIndex);
            return stored.get();
        }
        stages.begin(taskId, stage, batchIndex, inputHash, modelName);
        long started = System.nanoTime();
        try {
            T value = call.get();
            record.accept(value, millisSince(started));
            return value;
        } catch (BusinessException failure) {
            stages.fail(taskId, stage, batchIndex, inputHash,
                    failure.getErrorCode(), failure.getMessage(), millisSince(started));
            throw failure;
        } catch (RuntimeException failure) {
            stages.fail(taskId, stage, batchIndex, inputHash,
                    "OUTLINE_STAGE_FAILED", failure.getMessage(), millisSince(started));
            throw failure;
        }
    }

    /**
     * 输入的 SHA-256。
     *
     * <p>算的是<strong>严格类型对象序列化后的字节</strong>，不是拼出来的字符串：
     * 记录类的字段顺序固定，同样的输入永远得到同样的哈希；而手工拼接会在
     * 加一个字段时悄悄改变哈希的含义，让所有在途任务的恢复一次性全部失效。
     */
    private static String hash(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = JSON.writeValueAsBytes(value);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Hashing an outline stage input failed", e);
        }
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    @FunctionalInterface
    private interface StageRecorder<T> {
        void accept(T value, long durationMillis);
    }
}
