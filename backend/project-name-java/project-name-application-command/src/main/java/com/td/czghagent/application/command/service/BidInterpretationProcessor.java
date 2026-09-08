// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.ParsedDocument;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.DocumentParser;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class BidInterpretationProcessor {
    private final BidRepository bidRepository;
    private final FileStorage fileStorage;
    private final DocumentParser documentParser;
    private final BidAiExecutionService aiExecutionService;
    private final BidProductionService productionService;
    private final BidCommandSupport support;

    public BidInterpretationProcessor(
            BidRepository bidRepository,
            FileStorage fileStorage,
            DocumentParser documentParser,
            BidAiExecutionService aiExecutionService,
            BidProductionService productionService,
            BidCommandSupport support
    ) {
        this.bidRepository = bidRepository;
        this.fileStorage = fileStorage;
        this.documentParser = documentParser;
        this.aiExecutionService = aiExecutionService;
        this.productionService = productionService;
        this.support = support;
    }

    /**
     * Executes one persisted interpretation job.
     *
     * <p><b>Preconditions:</b> The source row is the bid's current file and is in PARSING state.</p>
     * <p><b>Side Effects:</b> Extracts the file, calls AI, replaces interpretation items and updates progress.</p>
     * <p><b>Error Semantics:</b> Exceptions are propagated so the orchestrator can mark the job failed.</p>
     */
    public void process(String sourceId, String bidId, String ownerId,
                        String traceId, String ipAddress) {
        parseDocument(sourceId, bidId, ownerId, traceId, ipAddress);
        generateProjectOverview(sourceId, bidId, ownerId, traceId, ipAddress);
        generateTechnicalScoring(sourceId, bidId, ownerId, traceId, ipAddress);
        complete(sourceId, bidId, ownerId, traceId, ipAddress);
    }

    public void parseDocument(String sourceId, String bidId, String ownerId,
                              String traceId, String ipAddress) {
        BidDocument bid = support.requireBid(bidId, ownerId);
        OperationContext context = context(bid, traceId, ipAddress);
        BidRepository.SourceFileRecord source = currentParsingSource(sourceId, bidId);
        if (source == null) {
            return;
        }
        if (hasParsedCheckpoint(source)) {
            progress(sourceId, "EXTRACTED", 25);
            return;
        }
        if (!progress(sourceId, "EXTRACTING", 10)) {
            return;
        }
        StoredFile stored = fileStorage.read(
                source.objectKey(), source.originalFileName(), source.mediaType());
        ParsedDocument parsed = documentParser.parse(source.id(), stored);
        List<BidRepository.SourceSegmentRecord> segments = IntStream
                .range(0, parsed.evidence().size())
                .mapToObj(index -> new BidRepository.SourceSegmentRecord(
                        index, parsed.evidence().get(index).locatorType(),
                        parsed.evidence().get(index).locator(),
                        parsed.evidence().get(index).excerpt()))
                .toList();
        if (segments.isEmpty()) {
            throw new IllegalStateException("招标文件未解析出可用文字或表格");
        }
        if (!bidRepository.saveParsedSource(sourceId, bidId, extractText(parsed), segments)) {
            throw new IllegalStateException("招标文件解析检查点保存失败");
        }
    }

    public void generateProjectOverview(String sourceId, String bidId, String ownerId,
                                        String traceId, String ipAddress) {
        BidDocument bid = support.requireBid(bidId, ownerId);
        OperationContext context = context(bid, traceId, ipAddress);
        BidRepository.SourceFileRecord source = currentParsingSource(sourceId, bidId);
        if (source == null || "SUCCEEDED".equals(source.overviewStatus())) {
            return;
        }
        if (!bidRepository.beginInterpretationObject(sourceId, "PROJECT_OVERVIEW")) {
            return;
        }
        progress(sourceId, "PROJECT_OVERVIEW", 30);
        try {
            TenderAiGateway.ProjectOverview result = aiExecutionService.extractProjectOverview(
                    bidId, request(source, bid, traceId));
            saveObject(sourceId, bidId, "PROJECT_OVERVIEW", "项目概述",
                    result.projectOverview(), 0);
            progress(sourceId, "PROJECT_OVERVIEW", 55);
        } catch (RuntimeException exception) {
            bidRepository.failInterpretationObject(
                    sourceId, "PROJECT_OVERVIEW", safeMessage(exception.getMessage()));
            throw exception;
        }
    }

    public void generateTechnicalScoring(String sourceId, String bidId, String ownerId,
                                         String traceId, String ipAddress) {
        BidDocument bid = support.requireBid(bidId, ownerId);
        OperationContext context = context(bid, traceId, ipAddress);
        BidRepository.SourceFileRecord source = currentParsingSource(sourceId, bidId);
        if (source == null || "SUCCEEDED".equals(source.scoringStatus())) {
            return;
        }
        if (!bidRepository.beginInterpretationObject(sourceId, "TECHNICAL_SCORING")) {
            return;
        }
        progress(sourceId, "TECHNICAL_SCORING", 60);
        try {
            TenderAiGateway.TechnicalScoring result = aiExecutionService.extractTechnicalScoring(
                    bidId, request(source, bid, traceId));
            saveObject(sourceId, bidId, "TECHNICAL_SCORING", "技术部分评分要求",
                    result.technicalScoringRequirements(), 1);
            progress(sourceId, "TECHNICAL_SCORING", 90);
        } catch (RuntimeException exception) {
            bidRepository.failInterpretationObject(
                    sourceId, "TECHNICAL_SCORING", safeMessage(exception.getMessage()));
            throw exception;
        }
    }

    @Transactional
    public void complete(String sourceId, String bidId, String ownerId,
                         String traceId, String ipAddress) {
        BidDocument bid = support.requireBid(bidId, ownerId);
        OperationContext context = context(bid, traceId, ipAddress);
        if (!progress(sourceId, "SAVING", 95)
                || !bidRepository.completeSourceParse(sourceId, bidId)) {
            return;
        }
        productionService.synchronizeInterpretation(bidId, context);
        support.audit(context, bidId, "BID_SOURCE_PARSE",
                "异步解析招标文件并提取项目概述和技术部分评分要求");
    }

    public void fail(String sourceId, String bidId, String ownerId,
                     String traceId, String ipAddress, String errorMessage) {
        bidRepository.failSourceParse(sourceId, bidId, safeMessage(errorMessage));
    }

    private BidRepository.SourceFileRecord currentParsingSource(String sourceId, String bidId) {
        return bidRepository.findSourceFile(bidId)
                .filter(source -> source.id().equals(sourceId))
                .filter(source -> "PARSING".equals(source.parseStatus()))
                .orElse(null);
    }

    private boolean progress(String sourceId, String stage, int percentage) {
        return bidRepository.updateSourceParseProgress(sourceId, stage, percentage);
    }

    private TenderAiGateway.InterpretationRequest request(
            BidRepository.SourceFileRecord source, BidDocument bid, String traceId
    ) {
        List<TenderAiGateway.SourceSegment> segments = bidRepository.listSourceSegments(source.id())
                .stream().map(item -> new TenderAiGateway.SourceSegment(
                        item.locatorType(), item.locator(), item.text())).toList();
        if (segments.isEmpty()) {
            throw new IllegalStateException("招标文件解析检查点不存在");
        }
        return new TenderAiGateway.InterpretationRequest(
                traceId, source.id(), bid.title(), bid.biddingMode(), segments);
    }

    private void saveObject(String sourceId, String bidId, String type,
                            String title, String description, int sortOrder) {
        String content = support.safe(description).trim();
        if (content.isBlank()) {
            throw new IllegalStateException("AI 未返回完整的" + title);
        }
        String criterionId = UUID.nameUUIDFromBytes(
                (sourceId + ":" + type).getBytes(StandardCharsets.UTF_8)).toString();
        BidWorkspace.Criterion criterion = new BidWorkspace.Criterion(
                criterionId, type, title, content, null, "", "",
                "TECHNICAL", "HIGH", sortOrder, false);
        if (!bidRepository.completeInterpretationObject(
                sourceId, bidId, type, content, criterion)) {
            throw new IllegalStateException(title + "保存检查点失败");
        }
    }

    private boolean hasParsedCheckpoint(BidRepository.SourceFileRecord source) {
        return source.extractedText() != null && !source.extractedText().isBlank()
                && !bidRepository.listSourceSegments(source.id()).isEmpty();
    }

    private String extractText(ParsedDocument parsed) {
        StringBuilder text = new StringBuilder();
        if (parsed.summary() != null) {
            text.append(parsed.summary()).append('\n');
        }
        parsed.evidence().forEach(evidence -> text.append('[').append(evidence.locator()).append("] ")
                .append(evidence.excerpt()).append('\n'));
        parsed.facts().forEach(fact -> text.append(fact.fieldName()).append(' ')
                .append(fact.value()).append(' ').append(fact.sourceExcerpt()).append('\n'));
        String value = text.toString();
        return value.length() <= 500_000 ? value : value.substring(0, 500_000);
    }

    /**
     * 为后台执行铸一个操作上下文。
     *
     * <p>租户轴取自<strong>标书本身</strong>而不是由 ownerId 现推：ownerId 只说明归属人，
     * 而一个人可以属于多个工作空间。从人推空间，在多空间场景下会把审计记到错误的空间上，
     * 且不报任何错。
     *
     * <p>显示名与用户名留空：后台路径拿不到，也不该拿——它们只用于界面呈现，
     * 编一个假的会让审计里出现一个查无此人的名字。
     */
    private OperationContext context(BidDocument bid, String traceId, String ipAddress) {
        return new OperationContext(
                new CurrentUser(bid.ownerId(), "", "", "PLANNER", null, bid.tenant()),
                traceId, ipAddress);
    }

    private String safeMessage(String message) {
        String value = message == null || message.isBlank() ? "招标文件解析失败" : message;
        return value.substring(0, Math.min(500, value.length()));
    }
}
