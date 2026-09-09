package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.AuditEvent;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
final class BidCommandSupport {
    private final BidRepository bidRepository;
    private final AuditRepository auditRepository;

    BidCommandSupport(BidRepository bidRepository, AuditRepository auditRepository) {
        this.bidRepository = bidRepository;
        this.auditRepository = auditRepository;
    }

    BidDocument requireBid(String bidId, OperationContext context) {
        return requireBid(bidId, context.user().id());
    }

    /**
     * 按归属人取标书。
     *
     * <p>后台路径（Temporal 活动）需要这个重载：它必须<strong>先拿到标书才知道租户轴</strong>，
     * 而合成一个身份去取标书就得先有租户轴——鸡生蛋。解法是承认这条链的顺序：
     * 归属校验用 ownerId 就够，租户轴由取回来的标书提供，它才是权威。
     *
     * <p>「不存在」与「无权访问」<strong>刻意分开报</strong>：这一面的调用方是自家 worker，
     * 两者的处置完全不同（前者是数据被删了，后者是归属算错了）。
     */
    BidDocument requireBid(String bidId, String ownerId) {
        return bidRepository.findBid(bidId, ownerId).orElseThrow(() ->
                bidRepository.existsBid(bidId)
                        ? new BusinessException("BID_ACCESS_DENIED", "无权访问该标书", 403)
                        : new BusinessException("BID_NOT_FOUND", "标书不存在", 404));
    }

    BidWorkspace workspace(String bidId, OperationContext context) {
        return bidRepository.loadWorkspace(requireBid(bidId, context));
    }

    void audit(OperationContext context, String bidId, String action, String detail) {
        auditRepository.append(AuditEvent.byUser(
                context, action, "BID", bidId, AuditEvent.SUCCESS, detail
        ));
    }

    BusinessException conflict() {
        return new BusinessException("BID_REVISION_CONFLICT", "标书已被更新，请刷新后重试", 409);
    }

    String safe(String value) {
        return value == null ? "" : value.trim();
    }

    List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank()).toList();
    }

    List<String> validScoringPointIds(
            List<String> references, List<BidWorkspace.Criterion> criteria) {
        Set<String> validIds = criteria == null ? Set.of() : criteria.stream()
                .map(BidWorkspace.Criterion::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return safeList(references).stream()
                .map(reference -> canonicalScoringPointId(reference, validIds))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String canonicalScoringPointId(String reference, Set<String> validIds) {
        if (validIds.contains(reference)) {
            return reference;
        }
        return validIds.stream()
                .filter(id -> reference.startsWith(id + "-"))
                .findFirst()
                .orElse(null);
    }

    boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
