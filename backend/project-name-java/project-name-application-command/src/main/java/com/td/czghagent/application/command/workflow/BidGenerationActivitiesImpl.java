// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidGenerationService;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidGenerationPlan;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class BidGenerationActivitiesImpl implements BidGenerationActivities {
    private static final Set<String> RETRYABLE_CONTENT_ERRORS = Set.of(
            "AI_OUTPUT_INVALID", "BID_CHAPTER_QUALITY_BLOCKED"
    );

    private final BidGenerationService generationService;

    public BidGenerationActivitiesImpl(BidGenerationService generationService) {
        this.generationService = generationService;
    }

    @Override
    public List<String> prepare(String taskId, String bidId, String ownerId,
                                String snapshotId, String snapshotHash) {
        return generationService.prepare(taskId, bidId, ownerId, snapshotId, snapshotHash);
    }

    @Override
    public BidGenerationPlan preparePlan(String taskId, String bidId, String ownerId,
                                         String snapshotId, String snapshotHash) {
        return generationService.preparePlan(taskId, bidId, ownerId, snapshotId, snapshotHash);
    }

    @Override
    public void generateUnit(String taskId, String bidId, String ownerId,
                             String snapshotId, String snapshotHash, String unitId) {
        try {
            generationService.generateUnit(
                    taskId, bidId, ownerId, snapshotId, snapshotHash, unitId);
        } catch (BusinessException exception) {
            if (isNonRetryable(exception)) {
                throw ApplicationFailure.newNonRetryableFailure(
                        exception.getMessage(), exception.getErrorCode());
            }
            throw ApplicationFailure.newFailure(
                    exception.getMessage(), exception.getErrorCode());
        }
    }

    @Override
    public void complete(String taskId, String bidId, String ownerId,
                         String snapshotId, String snapshotHash) {
        try {
            generationService.complete(taskId, bidId, ownerId, snapshotId, snapshotHash);
        } catch (BusinessException exception) {
            if (isNonRetryable(exception)) {
                throw ApplicationFailure.newNonRetryableFailure(
                        exception.getMessage(), exception.getErrorCode());
            }
            throw ApplicationFailure.newFailure(
                    exception.getMessage(), exception.getErrorCode());
        }
    }

    @Override
    public void fail(String taskId, String bidId, String errorMessage) {
        generationService.fail(taskId, bidId, errorMessage);
    }

    static boolean isNonRetryable(BusinessException exception) {
        if (RETRYABLE_CONTENT_ERRORS.contains(exception.getErrorCode())) {
            return false;
        }
        return exception.getErrorCode().startsWith("BID_")
                || exception.getHttpStatus() < 500
                || Set.of(
                "AI_PROVIDER_NOT_CONFIGURED",
                "BID_AUTO_REVIEW_FAILED",
                "BID_AUTO_REVIEW_UNRESOLVED",
                "BID_AUTO_REVIEW_CHAPTER_MISSING",
                "BID_AUTO_REVIEW_CHAPTER_EMPTY",
                "BID_AUTO_REVIEW_SAVE_FAILED"
        ).contains(exception.getErrorCode());
    }
}
