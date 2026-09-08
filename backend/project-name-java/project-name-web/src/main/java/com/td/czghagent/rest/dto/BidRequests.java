// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class BidRequests {
    private BidRequests() {
    }

    public record Create(
            @NotBlank String writingMethod,
            @NotBlank @Size(min = 2, max = 160) String title,
            @Min(20) @Max(2000) int targetPages,
            @NotBlank String biddingMode
    ) {
    }

    public record Setup(
            @NotBlank @Size(min = 2, max = 160) String title,
            @Min(20) @Max(2000) int targetPages,
            @NotBlank String biddingMode,
            @Min(0) long revision
    ) {
    }

    public record Criteria(
            @NotEmpty List<@Valid Criterion> items,
            @Min(0) long revision
    ) {
    }

    public record Criterion(
            String id,
            @NotBlank String type,
            @NotBlank @Size(max = 200) String title,
            @NotNull @Size(max = 500_000) String description,
            Double score,
            @Size(max = 5000) String sourceExcerpt,
            @Size(max = 255) String sourceLocator,
            @Size(max = 24) String scope,
            @Size(max = 16) String confidence
    ) {
    }

    public record AssetSelections(@NotNull List<String> assetIds) {
    }

    public record Outline(
            @NotEmpty List<@Valid OutlineNode> nodes,
            boolean confirm,
            @Min(0) long revision
    ) {
    }

    public record OutlineNode(
            @NotBlank String clientId,
            String parentClientId,
            @Min(1) @Max(3) int level,
            @NotBlank @Size(max = 200) String title,
            @Min(0) @Max(2000) int plannedPages,
            @Size(max = 5000) String taskBrief,
            List<@Size(max = 100) String> mustKeywords,
            List<@Size(max = 200) String> scoringPointIds
    ) {
    }

    public record Revision(@Min(0) long revision) {
    }

    public record Chapter(
            @NotNull @Size(max = 1_000_000) String content,
            @Min(0) long revision
    ) {
    }

    public record SectionRevision(
            @NotBlank String mode,
            @NotBlank @Size(max = 200_000) String selectedHtml,
            @Size(max = 20_000) String beforeContext,
            @Size(max = 20_000) String afterContext,
            @Size(max = 2_000) String instruction,
            @Min(0) long revision
    ) {
    }
}
