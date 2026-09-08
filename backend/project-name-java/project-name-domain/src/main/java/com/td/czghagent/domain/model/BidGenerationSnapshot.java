// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 正文任务创建时持久化的不可变输入。
 */
public record BidGenerationSnapshot(
        String id,
        String taskId,
        String bidId,
        String ownerId,
        String snapshotHash,
        String bidTitle,
        String biddingMode,
        int targetPages,
        int interpretationVersion,
        int outlineVersion,
        String solutionContract,
        String writingBible,
        String termRegistry,
        String commitmentRegistry,
        String promptVersion,
        LocalDateTime createdAt,
        List<Requirement> requirements,
        List<Outline> outline,
        List<Fact> facts,
        List<ReferenceChunk> referenceChunks
) {
    public record Requirement(
            String sourceCriterionId, String type, String title, String description,
            Double score, String sourceExcerpt, String sourceLocator, int sortOrder
    ) {
    }

    public record Outline(
            String sourceOutlineId, String chapterId, String chapterGenerationStatus,
            String parentSourceOutlineId,
            int level, String title, int plannedPages, int sortOrder,
            String taskBrief, List<String> mustKeywords, List<String> scoringPointIds
    ) {
    }

    public record Fact(
            String type, String name, String value, String sourceLocator,
            List<String> forbiddenValues, int sortOrder
    ) {
    }

    public record ReferenceChunk(
            String sourceChunkId, String assetId, String category, String assetName,
            String heading, String sourceLocator, String content, String contentHash,
            int chunkIndex
    ) {
    }
}
