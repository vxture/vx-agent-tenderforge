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
import com.td.czghagent.domain.model.CursorPage;
import com.td.czghagent.domain.model.PageCursor;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;

@Service
public class BidQueryService {

    private final BidRepository bidRepository;
    private final FileStorage fileStorage;

    public BidQueryService(BidRepository bidRepository, FileStorage fileStorage) {
        this.bidRepository = bidRepository;
        this.fileStorage = fileStorage;
    }

    /**
     * 我的标书，游标分页（通则 A-3）。
     *
     * <p>一个人在一个工作空间里能建多少份标书，没有任何人写下过上限——所以不是裸数组。
     * 排序保持「最近更新在前」，锚点取 {@code (updatedAt, id)}。
     */
    public CursorPage<BidSummary> list(CurrentUser user, Integer limit, String cursor) {
        int size = CursorPage.clampLimit(limit);
        return CursorPage.fromOverfetch(
                bidRepository.listBids(user.id(), user.tenant(), PageCursor.decode(cursor), size + 1),
                size, bid -> new PageCursor(bid.updatedAt(), bid.id()));
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

    /** 素材库，游标分页；分类与关键字是筛选条件，走查询参数（A-2）。 */
    public CursorPage<BidReferenceAsset> assets(String category, String keyword, Integer limit,
                                                String cursor, CurrentUser user) {
        int size = CursorPage.clampLimit(limit);
        return CursorPage.fromOverfetch(
                bidRepository.listAssets(user.id(), user.tenant(), normalize(category),
                        normalize(keyword), PageCursor.decode(cursor), size + 1),
                size, asset -> new PageCursor(asset.updatedAt(), asset.id()));
    }

    /** 一份标书的成果导出，游标分页，最近生成的在前——「下载最新」取 {@code limit=1} 的第一条。 */
    public CursorPage<BidExport> exports(String bidId, Integer limit, String cursor, CurrentUser user) {
        requireBid(bidId, user);
        int size = CursorPage.clampLimit(limit);
        return CursorPage.fromOverfetch(
                bidRepository.listExports(bidId, PageCursor.decode(cursor), size + 1),
                size, export -> new PageCursor(export.createdAt(), export.id()));
    }

    /**
     * 按标识下载一次成果。
     *
     * <p>取代了原来的「下载最新」路由：{@code /exports/latest/download} 把一个筛选条件
     * 写进了路径段，而路径段只留给资源标识（产品接入通则 A-2）。
     * 前端从 {@code GET /exports} 拿到列表后自己挑，这样「最新」的定义留在调用方手里，
     * 而不是被服务端固化成一条无法参数化的路由。
     */
    public StoredFile exportFile(String bidId, String exportId, CurrentUser user) {
        BidDocument bid = requireBid(bidId, user);
        BidRepository.ExportRecord export = bidRepository.findExport(bid.id(), exportId)
                .orElseThrow(() -> new BusinessException(
                        "BID_EXPORT_NOT_FOUND", "该成果不存在", 404
                ));
        return fileStorage.read(
                export.objectKey(), export.fileName(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );
    }

    private BidDocument requireBid(String bidId, CurrentUser user) {
        return bidRepository.findBid(bidId, user.id(), user.tenant()).orElseThrow(() ->
                bidRepository.existsBid(bidId)
                        ? new BusinessException("BID_ACCESS_DENIED", "无权访问该标书", 403)
                        : new BusinessException("BID_NOT_FOUND", "标书不存在", 404)
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
