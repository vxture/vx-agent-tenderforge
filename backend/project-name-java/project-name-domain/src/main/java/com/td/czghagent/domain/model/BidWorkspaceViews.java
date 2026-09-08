// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大文档工作台的轻量查询视图。
 */
public final class BidWorkspaceViews {
    private BidWorkspaceViews() {
    }

    public record Metadata(
            BidDocument bid,
            BidWorkspace.SourceFile sourceFile,
            BidWorkspace.OutlineTask outlineTask,
            BidWorkspace.GenerationTask generationTask,
            List<String> selectedAssetIds,
            List<BidExport> exports,
            BidProductionState production
    ) {
    }

    public record ChapterDetail(
            BidWorkspace.Chapter chapter,
            List<BidProductionState.ReviewIssue> reviewIssues
    ) {
    }

    public record OutlineView(
            List<BidWorkspace.OutlineNode> nodes,
            List<ChapterSummary> chapters
    ) {
    }

    public record ChapterSummary(
            String id,
            String outlineNodeId,
            String title,
            String generationStatus,
            int tableCount,
            LocalDateTime updatedAt,
            long revision
    ) {
    }

    public record GenerationProgress(
            String taskId,
            String status,
            int totalUnits,
            int completedUnits,
            int progress,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime finishedAt,
            List<BidProductionState.GenerationEvent> recentEvents
    ) {
        public static GenerationProgress idle() {
            return new GenerationProgress(
                    null, "IDLE", 0, 0, 0, null, null, null, List.of());
        }
    }
}
