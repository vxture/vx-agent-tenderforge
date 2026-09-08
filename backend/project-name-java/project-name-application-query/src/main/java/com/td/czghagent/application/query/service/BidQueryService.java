// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.application.query.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidReferenceAsset;
import com.td.czghagent.domain.model.BidSummary;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.BidWorkspaceViews;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BidQueryService {

    private final BidRepository bidRepository;
    private final FileStorage fileStorage;

    public BidQueryService(BidRepository bidRepository, FileStorage fileStorage) {
        this.bidRepository = bidRepository;
        this.fileStorage = fileStorage;
    }

    public List<BidSummary> list(CurrentUser user) {
        return bidRepository.listBids(user.id());
    }

    public BidWorkspace workspace(String bidId, CurrentUser user) {
        return bidRepository.loadWorkspace(requireBid(bidId, user));
    }

    public BidWorkspaceViews.Metadata metadata(String bidId, CurrentUser user) {
        return bidRepository.loadMetadata(requireBid(bidId, user));
    }

    public BidWorkspaceViews.OutlineView outline(String bidId, CurrentUser user) {
        requireBid(bidId, user);
        return bidRepository.loadOutline(bidId);
    }

    public BidWorkspaceViews.GenerationProgress generationProgress(String bidId, CurrentUser user) {
        requireBid(bidId, user);
        return bidRepository.loadGenerationProgress(bidId);
    }

    public BidWorkspaceViews.ChapterDetail chapter(String bidId, String chapterId, CurrentUser user) {
        requireBid(bidId, user);
        return bidRepository.findChapterDetail(bidId, chapterId).orElseThrow(() ->
                new BusinessException("BID_CHAPTER_NOT_FOUND", "正文章节不存在", 404));
    }

    public List<BidReferenceAsset> assets(String category, String keyword, CurrentUser user) {
        return bidRepository.listAssets(user.id(), normalize(category), normalize(keyword));
    }

    public List<BidExport> exports(String bidId, CurrentUser user) {
        requireBid(bidId, user);
        return bidRepository.listExports(bidId);
    }

    public StoredFile latestExport(String bidId, CurrentUser user) {
        BidDocument bid = requireBid(bidId, user);
        BidRepository.ExportRecord export = bidRepository.findLatestExport(bid.id())
                .orElseThrow(() -> new BusinessException(
                        "BID_EXPORT_NOT_FOUND", "该标书尚无可下载成果", 404
                ));
        return fileStorage.read(
                export.objectKey(), export.fileName(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );
    }

    private BidDocument requireBid(String bidId, CurrentUser user) {
        return bidRepository.findBid(bidId, user.id()).orElseThrow(() ->
                bidRepository.existsBid(bidId)
                        ? new BusinessException("BID_ACCESS_DENIED", "无权访问该标书", 403)
                        : new BusinessException("BID_NOT_FOUND", "标书不存在", 404)
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
