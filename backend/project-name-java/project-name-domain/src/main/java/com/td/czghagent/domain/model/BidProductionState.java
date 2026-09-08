// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public record BidProductionState(
        String interpretationStatus,
        int interpretationVersion,
        String interpretationHash,
        String outlineStatus,
        int outlineVersion,
        String outlineHash,
        String contentStatus,
        int contentVersion,
        String contentHash,
        String staleReason,
        List<FrozenFact> frozenFacts,
        List<GenerationUnit> generationUnits,
        List<GenerationEvent> generationEvents,
        List<ReviewIssue> reviewIssues,
        LayoutJob layoutJob
) {
    public record FrozenFact(
            String id, String type, String name, String value,
            String sourceLocator, List<String> forbiddenValues, int sortOrder
    ) {
    }

    public record GenerationUnit(
            String id, String taskId, String chapterId, int unitIndex,
            String status, int attemptCount, int wordBudget, String summary,
            String errorMessage, LocalDateTime updatedAt
    ) {
    }

    public record GenerationEvent(
            String id, String taskId, String chapterId, String type,
            String message, LocalDateTime occurredAt
    ) {
    }

    public record ReviewIssue(
            String id, String chapterId, String severity, String code,
            String message, String suggestion, String status
    ) {
    }

    public record LayoutJob(
            String id, String status, String workflowRunId, String inputHash,
            int targetPages, Integer actualPages,
            String qaStatus, String qaSummary, String errorMessage,
            LocalDateTime createdAt, LocalDateTime finishedAt
    ) {
    }
}
