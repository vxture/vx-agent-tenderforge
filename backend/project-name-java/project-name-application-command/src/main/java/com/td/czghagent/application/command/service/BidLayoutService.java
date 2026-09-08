// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.port.BidLayoutOrchestrator;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class BidLayoutService {
    private final BidRepository bidRepository;
    private final BidProductionRepository productionRepository;
    private final BidLayoutOrchestrator orchestrator;
    private final AuditRepository auditRepository;

    public BidLayoutService(BidRepository bidRepository,
                            BidProductionRepository productionRepository,
                            BidLayoutOrchestrator orchestrator,
                            AuditRepository auditRepository) {
        this.bidRepository = bidRepository;
        this.productionRepository = productionRepository;
        this.orchestrator = orchestrator;
        this.auditRepository = auditRepository;
    }

    public BidWorkspace start(String bidId, OperationContext context) {
        BidDocument bid = requireBid(bidId, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        if (!"FROZEN".equals(workspace.production().contentStatus()) || bid.contentStale()) {
            throw new BusinessException(
                    "BID_CONTENT_NOT_FROZEN", "请先完成成稿审查并冻结正文", 409
            );
        }
        String inputHash = workspace.production().contentHash();
        if (inputHash == null || inputHash.isBlank()) {
            throw new BusinessException("BID_CONTENT_NOT_FROZEN", "冻结正文缺少内容指纹", 409);
        }
        BidProductionState.LayoutJob latest = workspace.production().layoutJob();
        if (latest != null && Set.of("PENDING", "RUNNING").contains(latest.status())
                && inputHash.equals(latest.inputHash())) {
            throw new BusinessException("BID_LAYOUT_RUNNING", "标书正在排版，请勿重复提交", 409);
        }
        String jobId = productionRepository.createLayoutJob(
                bidId, context.user().id(), inputHash, bid.targetPages());
        try {
            String runId = orchestrator.start(jobId, bidId, context.user().id());
            productionRepository.assignLayoutWorkflow(jobId, bidId, runId);
        } catch (RuntimeException exception) {
            productionRepository.failLayoutJob(jobId, bidId, safeMessage(exception));
            throw new BusinessException("BID_LAYOUT_START_FAILED", "排版任务启动失败", 502);
        }
        auditRepository.append(context.user().id(), "BID_LAYOUT_START", "BID", bidId,
                "SUCCESS", "成稿审查通过，启动正式排版", context.traceId(), context.ipAddress());
        return bidRepository.loadWorkspace(requireBid(bidId, context));
    }

    private BidDocument requireBid(String bidId, OperationContext context) {
        return bidRepository.findBid(bidId, context.user().id()).orElseThrow(() ->
                bidRepository.existsBid(bidId)
                        ? new BusinessException("BID_ACCESS_DENIED", "无权访问该标书", 403)
                        : new BusinessException("BID_NOT_FOUND", "标书不存在", 404));
    }

    private String safeMessage(RuntimeException exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? "排版任务启动失败"
                : value.substring(0, Math.min(500, value.length()));
    }
}
