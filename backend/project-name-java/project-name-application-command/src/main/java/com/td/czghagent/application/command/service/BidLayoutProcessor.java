// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.BidDocumentExporter;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class BidLayoutProcessor {
    private final BidRepository bidRepository;
    private final BidProductionRepository productionRepository;
    private final BidDocumentExporter exporter;
    private final FileStorage fileStorage;
    private final AuditRepository auditRepository;

    public BidLayoutProcessor(BidRepository bidRepository,
                              BidProductionRepository productionRepository,
                              BidDocumentExporter exporter,
                              FileStorage fileStorage,
                              AuditRepository auditRepository) {
        this.bidRepository = bidRepository;
        this.productionRepository = productionRepository;
        this.exporter = exporter;
        this.fileStorage = fileStorage;
        this.auditRepository = auditRepository;
    }

    public void process(String layoutJobId, String bidId, String ownerId) {
        process(layoutJobId, bidId, ownerId, null);
    }

    void process(String layoutJobId, String bidId, String ownerId,
                 BidDocumentExporter.RenderedDocument preflight) {
        BidDocument bid = requireBid(bidId, ownerId);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        if (!"FROZEN".equals(workspace.production().contentStatus()) || bid.contentStale()) {
            throw new BusinessException("BID_CONTENT_NOT_FROZEN", "排版输入已失效", 409);
        }
        productionRepository.startLayoutJob(layoutJobId, bidId);
        BidDocumentExporter.RenderedDocument rendered = preflight == null
                ? render(workspace) : preflight;
        if (rendered.docx() == null || rendered.docx().length == 0
                || "FAILED".equals(rendered.qaStatus())) {
            String detail = rendered.qaSummary() == null || rendered.qaSummary().isBlank()
                    ? "未返回质量检查明细" : rendered.qaSummary();
            throw new BusinessException(
                    "BID_LAYOUT_QA_FAILED", "标书排版质量检查未通过：" + detail, 502);
        }
        int version = bidRepository.nextExportVersion(bidId);
        String fileName = safeFileName(bid.title()) + "-V"
                + String.format(Locale.ROOT, "%02d", version) + ".docx";
        StoredFile stored = fileStorage.store(
                "bids/" + bidId + "/exports", fileName,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                rendered.docx());
        String exportId = UUID.randomUUID().toString();
        try {
            bidRepository.insertExport(new BidRepository.ExportRecord(
                    exportId, bidId, version, fileName, stored.objectKey(), stored.size(), ownerId,
                    layoutJobId, rendered.qaStatus()
            ));
            productionRepository.completeLayoutJob(
                    layoutJobId, bidId, rendered.actualPages(),
                    rendered.qaStatus(), rendered.qaSummary());
        } catch (RuntimeException exception) {
            fileStorage.delete(stored.objectKey());
            throw exception;
        }
        auditRepository.append(ownerId, "BID_LAYOUT_COMPLETE", "BID", bidId,
                "SUCCESS", "正式排版和质量检查完成", "layout-" + layoutJobId, "internal");
    }

    /**
     * 预排版并完成全部密度重试，但不创建排版任务、文件或导出版本。
     *
     * <p><b>Preconditions:</b> 工作区正文完整且属于已校验的当前用户。</p>
     * <p><b>Side Effects:</b> 仅调用文档服务；不写业务数据。</p>
     * <p><b>Error Semantics:</b> 文档服务失败直接抛出，QA未通过作为结果返回。</p>
     */
    BidDocumentExporter.RenderedDocument preflight(BidWorkspace workspace) {
        return render(workspace);
    }

    public void fail(String layoutJobId, String bidId, String errorMessage) {
        String message = errorMessage == null || errorMessage.isBlank()
                ? "标书排版失败" : errorMessage.substring(0, Math.min(500, errorMessage.length()));
        productionRepository.failLayoutJob(layoutJobId, bidId, message);
    }

    private BidDocument requireBid(String bidId, String ownerId) {
        return bidRepository.findBid(bidId, ownerId).orElseThrow(() ->
                new BusinessException("BID_NOT_FOUND", "排版任务对应标书不存在", 404));
    }

    private BidDocumentExporter.RenderedDocument render(BidWorkspace workspace) {
        return exporter.render(
                workspace.bid(), workspace.outline(), workspace.chapters(),
                workspace.production().frozenFacts().stream()
                        .flatMap(fact -> fact.forbiddenValues().stream()).distinct().toList());
    }

    private String safeFileName(String title) {
        String cleaned = title.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        return cleaned.isBlank() ? "投标文件" : cleaned;
    }
}
