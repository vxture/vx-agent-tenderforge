// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicTechnicalFactExtractorTest {

    @Test
    void extractsAndNormalizesConflictingProxyServerResourceFacts() {
        List<BidWorkspace.Criterion> result = new DeterministicTechnicalFactExtractor().merge(
                List.of(),
                List.of(
                        evidence("段落 810", "前置代理云服务器:cpu:8核，内存:64G硬盘:300G 1台。"),
                        evidence("段落 837", "提供一台8C/16G/300GB云服务器，用于部署AI应用代理服务。")
                )
        );

        assertThat(result).extracting(BidWorkspace.Criterion::title)
                .containsExactly("前置代理服务器配置", "前置代理服务器配置");
        assertThat(result).extracting(BidWorkspace.Criterion::description)
                .containsExactly("前置代理服务器 8核/64GB/300GB", "前置代理服务器 8核/16GB/300GB");
        assertThat(result).extracting(BidWorkspace.Criterion::sourceLocator)
                .containsExactly("段落 810", "段落 837");
    }

    @Test
    void ignoresUnrelatedMetricsAndDoesNotDuplicateAnExistingFact() {
        BidWorkspace.Criterion existing = new BidWorkspace.Criterion(
                "fact-1", "FACT", "前置代理服务器配置",
                "前置代理服务器 8核/64GB/300GB", null,
                "原文", "段落 810", "TECHNICAL", "HIGH", 0, false
        );
        List<BidWorkspace.Criterion> result = new DeterministicTechnicalFactExtractor().merge(
                List.of(existing),
                List.of(
                        evidence("段落 810", "前置代理服务器 8C/64G/300GB"),
                        evidence("段落 811", "数据库服务器 16C/64G/2TB")
                )
        );

        assertThat(result).containsExactly(existing);
    }

    private ParsedDocument.Evidence evidence(String locator, String excerpt) {
        return new ParsedDocument.Evidence("PARAGRAPH", locator, excerpt);
    }
}
