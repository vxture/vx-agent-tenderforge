// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BidProductionService {
    private final BidRepository bidRepository;
    private final BidProductionRepository productionRepository;
    private final AuditRepository auditRepository;

    public BidProductionService(BidRepository bidRepository,
                                BidProductionRepository productionRepository,
                                AuditRepository auditRepository) {
        this.bidRepository = bidRepository;
        this.productionRepository = productionRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public BidWorkspace synchronizeInterpretation(String bidId, OperationContext context) {
        BidDocument bid = requireBid(bidId, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        productionRepository.markInterpretationReview(bidId, "招标文件解读内容已调整");
        return workspace(bidId, context);
    }

    @Transactional
    public BidWorkspace freezeInterpretation(String bidId, long revision,
                                             OperationContext context) {
        BidDocument bid = requireRevision(bidId, revision, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        BidProductionRules.validateInterpretationFreeze(workspace.criteria());
        String hash = BidProductionRules.interpretationHash(workspace.criteria());
        if ("FROZEN".equals(workspace.production().interpretationStatus())
                && hash.equals(workspace.production().interpretationHash())) {
            return workspace;
        }
        productionRepository.freezeInterpretation(
                bidId, context.user().id(), hash, workspace.criteria(), List.of());
        audit(context, bidId, "BID_INTERPRETATION_FREEZE", "确认并冻结招标文件技术解读");
        return workspace(bidId, context);
    }

    @Transactional
    public BidWorkspace freezeOutline(String bidId, long revision, OperationContext context) {
        BidDocument bid = requireRevision(bidId, revision, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        if (!"FROZEN".equals(workspace.production().interpretationStatus())) {
            throw new BusinessException("BID_INTERPRETATION_NOT_FROZEN", "请先冻结招标文件解读", 409);
        }
        BidProductionRules.validateOutlineFreeze(workspace.outline(), workspace.criteria());
        String hash = BidProductionRules.outlineHash(workspace.outline());
        if ("FROZEN".equals(workspace.production().outlineStatus())
                && hash.equals(workspace.production().outlineHash())) {
            return workspace;
        }
        productionRepository.freezeOutline(bidId, hash);
        audit(context, bidId, "BID_OUTLINE_FREEZE", "确认并冻结三级目录");
        return workspace(bidId, context);
    }

    @Transactional
    public BidWorkspace freezeContent(String bidId, long revision, OperationContext context) {
        BidDocument bid = requireRevision(bidId, revision, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        BidProductionRules.validateContentFreeze(
                workspace.chapters(), workspace.production().reviewIssues());
        String hash = BidProductionRules.contentHash(workspace.chapters());
        productionRepository.freezeContent(bidId, hash);
        audit(context, bidId, "BID_CONTENT_FREEZE", "成稿审查通过并冻结正文");
        return workspace(bidId, context);
    }

    public void markOutlineReview(String bidId) {
        productionRepository.markOutlineReview(bidId, "标书目录已调整");
    }


    private BidDocument requireRevision(String bidId, long revision, OperationContext context) {
        BidDocument bid = requireBid(bidId, context);
        if (bid.revision() != revision) {
            throw conflict();
        }
        return bid;
    }

    private BidDocument requireBid(String bidId, OperationContext context) {
        return bidRepository.findBid(bidId, context.user().id()).orElseThrow(() ->
                bidRepository.existsBid(bidId)
                        ? new BusinessException("BID_ACCESS_DENIED", "无权访问该标书", 403)
                        : new BusinessException("BID_NOT_FOUND", "标书不存在", 404));
    }

    private BidWorkspace workspace(String bidId, OperationContext context) {
        return bidRepository.loadWorkspace(requireBid(bidId, context));
    }

    private void audit(OperationContext context, String bidId, String action, String summary) {
        auditRepository.append(context.user().id(), action, "BID", bidId,
                "SUCCESS", summary, context.traceId(), context.ipAddress());
    }

    private BusinessException conflict() {
        return new BusinessException("BID_REVISION_CONFLICT", "内容已被其他请求修改，请刷新后重试", 409);
    }

}
