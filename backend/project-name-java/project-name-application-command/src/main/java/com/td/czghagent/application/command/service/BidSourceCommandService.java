package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.BidInterpretationOrchestrator;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
class BidSourceCommandService {
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            ".doc", ".docx", ".pdf", ".xlsx", ".xlsm", ".csv", ".txt", ".md");

    private final BidRepository bidRepository;
    private final FileStorage fileStorage;
    private final BidInterpretationOrchestrator interpretationOrchestrator;
    private final BidProductionService productionService;
    private final BidCommandSupport support;

    BidSourceCommandService(BidRepository bidRepository, FileStorage fileStorage,
                            BidInterpretationOrchestrator interpretationOrchestrator,
                            BidProductionService productionService, BidCommandSupport support) {
        this.bidRepository = bidRepository;
        this.fileStorage = fileStorage;
        this.interpretationOrchestrator = interpretationOrchestrator;
        this.productionService = productionService;
        this.support = support;
    }

    @Transactional
    BidWorkspace create(String writingMethod, String title, int targetPages,
                        String biddingMode, OperationContext context) {
        BidDocument.validateWritingMethod(writingMethod);
        BidDocument.validateSetup(title, targetPages, biddingMode);
        String id = UUID.randomUUID().toString();
        BidDocument bid = new BidDocument(
                id, context.user().id(), "TA-" + Year.now().getValue() + "-"
                + id.substring(0, 8).toUpperCase(Locale.ROOT), writingMethod,
                title.trim(), targetPages, biddingMode, "INTERPRETATION", "DRAFT", false,
                null, LocalDateTime.now(), LocalDateTime.now(), 0);
        bidRepository.insertBid(bid);
        support.audit(context, id, "BID_CREATE", "保存标书设置并创建投标文件");
        return bidRepository.loadWorkspace(support.requireBid(id, context));
    }

    @Transactional
    BidWorkspace saveSetup(String bidId, String title, int targetPages,
                           String biddingMode, long revision, OperationContext context) {
        BidDocument bid = support.requireBid(bidId, context);
        BidDocument.validateSetup(title, targetPages, biddingMode);
        if (bid.revision() != revision) {
            throw support.conflict();
        }
        String normalizedTitle = title.trim();
        if (support.safe(bid.title()).equals(normalizedTitle) && bid.targetPages() == targetPages
                && bid.biddingMode().equals(biddingMode)) {
            return bidRepository.loadWorkspace(bid);
        }
        if (!bidRepository.updateSetup(bidId, normalizedTitle, targetPages, biddingMode, revision)) {
            throw support.conflict();
        }
        support.audit(context, bidId, "BID_SETUP_UPDATE", "保存标书设置");
        return support.workspace(bidId, context);
    }

    BidWorkspace uploadSource(String bidId, String fileName, String mediaType,
                              byte[] content, OperationContext context) {
        support.requireBid(bidId, context);
        validateDocument(fileName, content);
        BidRepository.SourceFileRecord old = bidRepository.findSourceFile(bidId).orElse(null);
        if (old != null && "PARSING".equals(old.parseStatus())) {
            throw new BusinessException(
                    "BID_SOURCE_PARSE_RUNNING", "当前招标文件正在解析，请等待完成后再重新上传", 409);
        }
        StoredFile stored = fileStorage.store("bids/" + bidId + "/source", fileName, mediaType, content);
        try {
            bidRepository.insertSourceFile(new BidRepository.SourceFileRecord(
                    UUID.randomUUID().toString(), bidId, fileName, stored.objectKey(), mediaType,
                    stored.size(), stored.contentHash(), "PENDING", null, null));
        } catch (RuntimeException exception) {
            fileStorage.delete(stored.objectKey());
            throw exception;
        }
        if (old != null && !old.objectKey().equals(stored.objectKey())) {
            fileStorage.delete(old.objectKey());
        }
        support.audit(context, bidId, "BID_SOURCE_UPLOAD", "上传招标文件");
        return support.workspace(bidId, context);
    }

    BidWorkspace parseSource(String bidId, OperationContext context) {
        BidDocument bid = support.requireBid(bidId, context);
        BidRepository.SourceFileRecord source = bidRepository.findSourceFile(bidId)
                .orElseThrow(() -> new BusinessException(
                        "BID_SOURCE_REQUIRED", "请先上传招标文件", 400));
        if ("PARSING".equals(source.parseStatus())) {
            return bidRepository.loadWorkspace(bid);
        }
        if (!bidRepository.beginSourceParse(source.id(), bidId)) {
            return support.workspace(bidId, context);
        }
        String jobId = UUID.randomUUID().toString();
        try {
            interpretationOrchestrator.start(
                    jobId, source.id(), bidId, context.user().id(),
                    context.traceId(), context.ipAddress());
            support.audit(context, bidId, "BID_SOURCE_PARSE_SUBMIT", "提交招标文件异步解读任务");
        } catch (RuntimeException exception) {
            String message = safeMessage(exception);
            bidRepository.failSourceParse(source.id(), bidId, message);
            throw new BusinessException("BID_SOURCE_PARSE_SUBMIT_FAILED", message, 503);
        }
        return support.workspace(bidId, context);
    }

    @Transactional
    BidWorkspace saveCriteria(String bidId, List<BidCommandService.CriterionInput> inputs,
                              long revision, OperationContext context) {
        BidDocument bid = support.requireBid(bidId, context);
        if (bid.revision() != revision) {
            throw support.conflict();
        }
        List<BidWorkspace.Criterion> criteria = normalizeCriteria(inputs);
        BidWorkspace current = bidRepository.loadWorkspace(bid);
        if (sameCriteria(current.criteria(), criteria)) {
            return current;
        }
        bidRepository.replaceCriteria(bidId, criteria, true);
        support.audit(context, bidId, "BID_CRITERIA_UPDATE", "人工修订招标文件解读结果");
        return productionService.synchronizeInterpretation(bidId, context);
    }

    @Transactional
    BidWorkspace selectAssets(String bidId, List<String> assetIds, OperationContext context) {
        support.requireBid(bidId, context);
        List<String> unique = assetIds == null ? List.of() : assetIds.stream().distinct().toList();
        for (String assetId : unique) {
            BidRepository.AssetRecord asset = bidRepository.findAsset(assetId, context.user().id())
                    .orElseThrow(() -> new BusinessException(
                            "BID_ASSET_INVALID", "所选素材不可用", 400));
            if ("GALLERY".equals(asset.category())) {
                throw new BusinessException(
                        "BID_ASSET_INVALID", "图库不能作为范本或大纲引用", 400);
            }
        }
        bidRepository.replaceAssetSelections(bidId, context.user().id(), unique);
        return support.workspace(bidId, context);
    }

    private List<BidWorkspace.Criterion> normalizeCriteria(
            List<BidCommandService.CriterionInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException("BID_CRITERIA_REQUIRED", "项目概述和技术部分评分要求不能为空", 400);
        }
        Map<String, String> titles = Map.of(
                "PROJECT_OVERVIEW", "项目概述",
                "TECHNICAL_SCORING", "技术部分评分要求");
        Map<String, Integer> orders = Map.of(
                "PROJECT_OVERVIEW", 0,
                "TECHNICAL_SCORING", 1);
        List<BidWorkspace.Criterion> result = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (BidCommandService.CriterionInput input : inputs) {
            String description = support.safe(input.description()).trim();
            if (!titles.containsKey(input.type()) || !seen.add(input.type())) {
                throw new BusinessException(
                        "BID_CRITERION_INVALID", "解读结果必须且只能包含两个固定业务对象", 400);
            }
            if (description.isBlank() || description.length() > 500_000) {
                throw new BusinessException(
                        "BID_CRITERION_INVALID", titles.get(input.type()) + "不能为空或过长", 400);
            }
            result.add(new BidWorkspace.Criterion(
                    support.validUuid(input.id()) ? input.id() : UUID.randomUUID().toString(),
                    input.type(), titles.get(input.type()), description, null,
                    "", "", "TECHNICAL", "HIGH", orders.get(input.type()), true));
        }
        if (!seen.equals(titles.keySet())) {
            throw new BusinessException(
                    "BID_CRITERIA_REQUIRED", "项目概述和技术部分评分要求都必须填写", 400);
        }
        return result.stream().sorted(java.util.Comparator.comparingInt(
                BidWorkspace.Criterion::sortOrder)).toList();
    }

    private boolean sameCriteria(List<BidWorkspace.Criterion> current,
                                 List<BidWorkspace.Criterion> updated) {
        if (current.size() != updated.size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index++) {
            BidWorkspace.Criterion left = current.get(index);
            BidWorkspace.Criterion right = updated.get(index);
            if (!Objects.equals(left.id(), right.id())
                    || !Objects.equals(left.type(), right.type())
                    || !support.safe(left.title()).equals(support.safe(right.title()))
                    || !Objects.equals(left.description(), right.description())
                    || !Objects.equals(left.score(), right.score())
                    || !Objects.equals(left.sourceExcerpt(), right.sourceExcerpt())
                    || !Objects.equals(left.sourceLocator(), right.sourceLocator())
                    || !Objects.equals(left.scope(), right.scope())
                    || !Objects.equals(left.confidence(), right.confidence())
                    || left.sortOrder() != right.sortOrder()) {
                return false;
            }
        }
        return true;
    }

    private void validateDocument(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank() || content == null || content.length == 0) {
            throw new BusinessException("BID_SOURCE_INVALID", "请选择有效的招标文件", 400);
        }
        if (content.length > 50L * 1024 * 1024) {
            throw new BusinessException("BID_SOURCE_INVALID", "招标文件不能超过50MB", 400);
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (DOCUMENT_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new BusinessException("BID_SOURCE_INVALID", "暂不支持该招标文件格式", 400);
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "招标文件解析失败" : message.substring(0, Math.min(500, message.length()));
    }
}
