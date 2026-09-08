// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.domain.model;

/**
 * 一次解析后可重复检索的参考素材文本块。
 */
public record BidReferenceChunk(
        String id,
        String assetId,
        String category,
        String assetName,
        int chunkIndex,
        String heading,
        String sourceLocator,
        String content,
        String contentHash,
        int characterCount
) {
}
