// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidInterpretationProcessor;
import org.springframework.stereotype.Component;

@Component
public class BidInterpretationActivitiesImpl implements BidInterpretationActivities {
    private final BidInterpretationProcessor processor;
    private final PlatformActivityContext platformContext;

    /**
     * 解读流程的聚合键前缀。
     *
     * <p>解读工作流没有自己的 taskId 字段，而 Atlas 强制要求一个。用 sourceId
     * 铸一个产品侧的稳定键：同一份源文件的重新解读<strong>就是</strong>同一个工作单元
     * 的重试，归到一起是对的。前缀让它一眼能和平台送来的 task_id 区分开。
     *
     * <p><strong>已知缺口</strong>：入站 HTTP 请求上的 task_id 目前<em>没有</em>传进
     * 工作流——工作流输入类型要加字段，而那对在途工作流是一次版本变更。
     * 后果是一次由 agent 发起的解读，在 Atlas 那边归到产品自己铸的键上，
     * 而不是发起方那条链上。
     */
    private static final String TASK_PREFIX = "interp:";

    public BidInterpretationActivitiesImpl(BidInterpretationProcessor processor,
                                           PlatformActivityContext platformContext) {
        this.processor = processor;
        this.platformContext = platformContext;
    }

    @Override
    public void parseDocument(String sourceId, String bidId, String ownerId,
                              String traceId, String ipAddress) {
        platformContext.run(TASK_PREFIX + sourceId, bidId, ownerId, () ->
                processor.parseDocument(sourceId, bidId, ownerId, traceId, ipAddress));
    }

    @Override
    public void generateProjectOverview(String sourceId, String bidId, String ownerId,
                                        String traceId, String ipAddress) {
        platformContext.run(TASK_PREFIX + sourceId, bidId, ownerId, () ->
                processor.generateProjectOverview(sourceId, bidId, ownerId, traceId, ipAddress));
    }

    @Override
    public void generateTechnicalScoring(String sourceId, String bidId, String ownerId,
                                         String traceId, String ipAddress) {
        platformContext.run(TASK_PREFIX + sourceId, bidId, ownerId, () ->
                processor.generateTechnicalScoring(sourceId, bidId, ownerId, traceId, ipAddress));
    }

    @Override
    public void complete(String sourceId, String bidId, String ownerId,
                         String traceId, String ipAddress) {
        platformContext.run(TASK_PREFIX + sourceId, bidId, ownerId, () ->
                processor.complete(sourceId, bidId, ownerId, traceId, ipAddress));
    }

    @Override
    public void fail(String sourceId, String bidId, String ownerId,
                     String traceId, String ipAddress, String errorMessage) {
        processor.fail(sourceId, bidId, ownerId, traceId, ipAddress, errorMessage);
    }
}
