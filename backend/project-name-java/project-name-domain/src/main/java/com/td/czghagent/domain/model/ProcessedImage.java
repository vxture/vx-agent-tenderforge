// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
package com.td.czghagent.domain.model;

public record ProcessedImage(
        String fileName,
        String mediaType,
        int widthPx,
        int heightPx,
        byte[] content
) {
    public ProcessedImage {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}

