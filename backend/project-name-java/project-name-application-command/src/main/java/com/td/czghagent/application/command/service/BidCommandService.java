// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidCapability;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidReferenceAsset;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.BidWorkspaceViews;
import com.td.czghagent.domain.model.OperationContext;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stable command facade for the tender-writing API.
 *
 * <p>Business behavior lives in focused source, outline, content and asset services so each
 * workflow can evolve independently without expanding the controller contract.</p>
 *
 * <p><strong>C2 权益在这里判定</strong>（{@link EntitlementGuard}）：每个命令方法的第一行，
 * 先于任何取数与状态校验。没有 {@link BidCapability#BID_AUTHORING} 时产品只剩只读——
 * 编辑、冻结、发起都拒绝；刻意放行的只有暂停生成（停止消耗不该被拦）与删除素材
 * （清理自己的数据不该被拦），读路径不经过这里。新增命令方法必须二选一：调用
 * {@code guard.require}，或进入 {@code check_entitlement_gates.py} 的放行名单并写明理由。</p>
 */
@Service
public class BidCommandService {
    private final BidSourceCommandService sourceCommands;
    private final BidOutlineCommandService outlineCommands;
    private final BidContentCommandService contentCommands;
    private final BidAssetCommandService assetCommands;
    private final EntitlementGuard guard;

    public BidCommandService(BidSourceCommandService sourceCommands,
                             BidOutlineCommandService outlineCommands,
                             BidContentCommandService contentCommands,
                             BidAssetCommandService assetCommands,
                             EntitlementGuard guard) {
        this.sourceCommands = sourceCommands;
        this.outlineCommands = outlineCommands;
        this.contentCommands = contentCommands;
        this.assetCommands = assetCommands;
        this.guard = guard;
    }

    public BidWorkspace create(String writingMethod, String title, int targetPages,
                               String biddingMode, OperationContext context) {
        guard.require(context, BidCapability.BID_AUTHORING);
        return sourceCommands.create(writingMethod, title, targetPages, biddingMode, context);
    }

    public BidWorkspace saveSetup(String bidId, String title, int targetPages,
                                  String biddingMode, long revision, OperationContext context) {
        guard.require(context, BidCapability.BID_AUTHORING);
        return sourceCommands.saveSetup(
                bidId, title, targetPages, biddingMode, revision, context);
    }

    public BidWorkspace uploadSource(String bidId, String fileName, String mediaType,
                                     byte[] content, OperationContext context) {
        guard.require(context, BidCapability.BID_AUTHORING);
        return sourceCommands.uploadSource(bidId, fileName, mediaType, content, context);
    }

    public BidWorkspace parseSource(String bidId, OperationContext context) {
        guard.require(context, BidCapability.AI_GENERATION);
        return sourceCommands.parseSource(bidId, context);
    }

    public BidWorkspace saveCriteria(String bidId, List<CriterionInput> inputs,
                                     long revision, OperationContext context) {
        guard.require(context, BidCapability.BID_AUTHORING);
        return sourceCommands.saveCriteria(bidId, inputs, revision, context);
    }

    public BidWorkspace selectAssets(String bidId, List<String> assetIds,
                                     OperationContext context) {
        guard.require(context, BidCapability.BID_AUTHORING);
        return sourceCommands.selectAssets(bidId, assetIds, context);
    }

    public BidWorkspace generateOutline(String bidId, OperationContext context) {
        guard.require(context, BidCapability.AI_GENERATION);
        return outlineCommands.generateOutline(bidId, context);
    }

    public BidWorkspace saveOutline(String bidId, List<OutlineInput> inputs, boolean confirm,
                                    long revision, OperationContext context) {
        guard.require(context, BidCapability.BID_AUTHORING);
        return outlineCommands.saveOutline(bidId, inputs, confirm, revision, context);
    }

    public BidWorkspace startGeneration(String bidId, OperationContext context) {
        guard.require(context, BidCapability.AI_GENERATION);
        return contentCommands.startGeneration(bidId, context);
    }

    public BidWorkspace pauseGeneration(String bidId, OperationContext context) {
        return contentCommands.pauseGeneration(bidId, context);
    }

    public BidWorkspace resumeGeneration(String bidId, OperationContext context) {
        guard.require(context, BidCapability.AI_GENERATION);
        return contentCommands.resumeGeneration(bidId, context);
    }

    public BidWorkspace reviewContent(String bidId, OperationContext context) {
        guard.require(context, BidCapability.CONSISTENCY_REVIEW);
        return contentCommands.reviewContent(bidId, context);
    }

    public BidWorkspaceViews.ChapterDetail saveChapter(
            String bidId, String chapterId, String content,
            long revision, OperationContext context) {
        guard.require(context, BidCapability.BID_AUTHORING);
        return contentCommands.saveChapter(bidId, chapterId, content, revision, context);
    }

    public SectionRevisionCandidate reviseChapter(
            String bidId, String chapterId, String mode, String selectedHtml,
            String beforeContext, String afterContext, String instruction,
            long revision, OperationContext context) {
        guard.require(context, BidCapability.AI_GENERATION);
        return contentCommands.reviseChapter(
                bidId, chapterId, mode, selectedHtml, beforeContext,
                afterContext, instruction, revision, context);
    }

    public BidExport createExport(String bidId, OperationContext context) {
        guard.require(context, BidCapability.DOCUMENT_EXPORT);
        return contentCommands.createExport(bidId, context);
    }

    public BidReferenceAsset uploadAsset(String category, String fileName, String mediaType,
                                         byte[] content, OperationContext context) {
        guard.require(context, BidCapability.ASSET_LIBRARY);
        return assetCommands.uploadAsset(category, fileName, mediaType, content, context);
    }

    public void removeAsset(String assetId, OperationContext context) {
        assetCommands.removeAsset(assetId, context);
    }

    public record CriterionInput(
            String id, String type, String title, String description,
            Double score, String sourceExcerpt, String sourceLocator,
            String scope, String confidence) {
    }

    public record OutlineInput(
            String clientId, String parentClientId, int level,
            String title, int plannedPages, String taskBrief,
            List<String> mustKeywords, List<String> scoringPointIds) {
    }

    public record SectionRevisionCandidate(
            String id, String mode, long baseRevision, String replacementHtml,
            String changeSummary, List<String> preservedFacts, List<String> warnings) {
    }
}
