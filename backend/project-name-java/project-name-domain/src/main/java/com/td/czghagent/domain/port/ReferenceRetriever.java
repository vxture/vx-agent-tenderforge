// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.model.BidGenerationSnapshot;

import java.util.List;

/**
 * 可替换的章节参考素材检索边界。
 */
public interface ReferenceRetriever {
    List<BidGenerationSnapshot.ReferenceChunk> retrieve(
            String query,
            List<BidGenerationSnapshot.ReferenceChunk> candidates,
            int maxChunks,
            int maxCharacters
    );
}
