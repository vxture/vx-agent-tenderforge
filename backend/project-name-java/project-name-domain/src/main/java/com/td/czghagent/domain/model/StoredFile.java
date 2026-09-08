// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.model;

public record StoredFile(
        String objectKey,
        String fileName,
        String mediaType,
        long size,
        String contentHash,
        byte[] content
) {
}
