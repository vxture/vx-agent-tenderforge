// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;

public record BidReferenceAsset(
        String id, String ownerId, String category, String displayName,
        String originalFileName, String mediaType, long fileSize,
        LocalDateTime createdAt, LocalDateTime updatedAt, long revision
) {
}
