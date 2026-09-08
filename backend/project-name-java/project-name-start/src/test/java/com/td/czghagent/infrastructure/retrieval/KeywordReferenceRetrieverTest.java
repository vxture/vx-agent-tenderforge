// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-09
package com.td.czghagent.infrastructure.retrieval;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordReferenceRetrieverTest {
    @Test
    void prefersRelevantChunksFromDifferentTemplateSections() {
        KeywordReferenceRetriever retriever = new KeywordReferenceRetriever();
        List<BidGenerationSnapshot.ReferenceChunk> candidates = List.of(
                chunk("a-1", "算力方案", "算力算力算力资源池建设", 0),
                chunk("a-2", "算力方案", "算力资源配置与调度", 1),
                chunk("b-1", "数据安全", "算力平台的数据安全与隔离", 2)
        );

        List<BidGenerationSnapshot.ReferenceChunk> selected = retriever.retrieve(
                "算力平台建设", candidates, 2, 10_000);

        assertThat(selected).hasSize(2);
        assertThat(selected).extracting(BidGenerationSnapshot.ReferenceChunk::heading)
                .containsExactly("算力方案", "数据安全");
    }

    private BidGenerationSnapshot.ReferenceChunk chunk(
            String id, String heading, String content, int index
    ) {
        return new BidGenerationSnapshot.ReferenceChunk(
                id, "template", "TEMPLATE", "参考范本", heading,
                "第" + (index + 1) + "页", content, "hash-" + id, index);
    }
}
