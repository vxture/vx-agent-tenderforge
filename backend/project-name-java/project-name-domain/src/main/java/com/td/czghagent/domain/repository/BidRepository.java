// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.domain.repository;

import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidReferenceChunk;
import com.td.czghagent.domain.model.BidReferenceAsset;
import com.td.czghagent.domain.model.BidSummary;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.BidWorkspaceViews;

import java.util.List;
import java.util.Optional;

public interface BidRepository {
    void insertBid(BidDocument bid);

    List<BidSummary> listBids(String ownerId);

    Optional<BidDocument> findBid(String bidId, String ownerId);

    boolean existsBid(String bidId);

    BidWorkspace loadWorkspace(BidDocument bid);

    BidWorkspaceViews.Metadata loadMetadata(BidDocument bid);

    BidWorkspaceViews.OutlineView loadOutline(String bidId);

    Optional<BidWorkspaceViews.ChapterDetail> findChapterDetail(String bidId, String chapterId);

    BidWorkspaceViews.GenerationProgress loadGenerationProgress(String bidId);

    boolean updateSetup(String bidId, String title, int targetPages, String biddingMode, long revision);

    void insertSourceFile(SourceFileRecord source);

    Optional<SourceFileRecord> findSourceFile(String bidId);

    boolean beginSourceParse(String sourceId, String bidId);

    boolean updateSourceParseProgress(String sourceId, String stage, int progress);

    boolean saveParsedSource(String sourceId, String bidId, String extractedText,
                             List<SourceSegmentRecord> segments);

    List<SourceSegmentRecord> listSourceSegments(String sourceId);

    boolean beginInterpretationObject(String sourceId, String objectType);

    boolean completeInterpretationObject(String sourceId, String bidId, String objectType,
                                         String content, BidWorkspace.Criterion criterion);

    boolean failInterpretationObject(String sourceId, String objectType, String errorMessage);

    boolean completeSourceParse(String sourceId, String bidId);

    boolean failSourceParse(String sourceId, String bidId, String errorMessage);

    void replaceCriteria(String bidId, List<BidWorkspace.Criterion> criteria, boolean manual);

    void replaceAssetSelections(String bidId, String ownerId, List<String> assetIds);

    void replaceOutline(String bidId, List<BidWorkspace.OutlineNode> nodes, boolean confirmed);

    void confirmOutline(String bidId);

    String createOutlineTask(String bidId);

    void assignOutlineWorkflowRun(String taskId, String workflowRunId);

    boolean updateOutlineTaskProgress(String taskId, String stage, int progress);

    boolean completeOutlineTask(String taskId, String bidId,
                                List<BidWorkspace.OutlineNode> nodes);

    boolean failOutlineTask(String taskId, String bidId, String errorMessage);

    String createGenerationTask(String bidId, int totalUnits);

    void startGenerationTask(String taskId);

    boolean saveGeneratedChapter(String chapterId, String content);

    void advanceGenerationTask(String taskId);

    boolean completeGenerationTask(String taskId, String bidId);

    boolean failGenerationTask(String taskId, String bidId, String message);

    GenerationTaskExecution pauseGenerationTask(String bidId);

    GenerationTaskExecution resumeGenerationTask(String bidId);

    boolean updateChapter(String bidId, String chapterId, String content, long revision);

    void markContentStale(String bidId);

    void insertAsset(AssetRecord asset);

    List<BidReferenceAsset> listAssets(String ownerId, String category, String keyword);

    Optional<AssetRecord> findAsset(String assetId, String ownerId);

    void replaceAssetChunks(String assetId, List<BidReferenceChunk> chunks);

    List<BidReferenceChunk> listAssetChunks(List<String> assetIds);

    void markAssetIngestion(String assetId, String status, String errorMessage);

    boolean removeAsset(String assetId, String ownerId);

    int nextExportVersion(String bidId);

    void insertExport(ExportRecord export);

    List<BidExport> listExports(String bidId);

    Optional<ExportRecord> findLatestExport(String bidId);

    record SourceFileRecord(
            String id, String bidId, String originalFileName, String objectKey,
            String mediaType, long fileSize, String contentHash, String parseStatus,
            String extractedText, String errorMessage,
            String overviewStatus, String overviewContent, String overviewErrorMessage,
            String scoringStatus, String scoringContent, String scoringErrorMessage
    ) {
        public SourceFileRecord(
                String id, String bidId, String originalFileName, String objectKey,
                String mediaType, long fileSize, String contentHash, String parseStatus,
                String extractedText, String errorMessage
        ) {
            this(id, bidId, originalFileName, objectKey, mediaType, fileSize, contentHash,
                    parseStatus, extractedText, errorMessage, "PENDING", null, null,
                    "PENDING", null, null);
        }
    }

    record SourceSegmentRecord(
            int sequence, String locatorType, String locator, String text
    ) {
    }

    record AssetRecord(
            String id, String ownerId, String category, String displayName,
            String originalFileName, String objectKey, String mediaType,
            long fileSize, String contentHash, String status
    ) {
    }

    record ExportRecord(
            String id, String bidId, int version, String fileName,
            String objectKey, long fileSize, String createdBy,
            String layoutJobId, String qaStatus
    ) {
    }

    record GenerationTaskExecution(
            String taskId,
            String bidId,
            String status,
            String snapshotId,
            String snapshotHash,
            String workflowRunId,
            int retryCount,
            boolean changed
    ) {
    }
}
