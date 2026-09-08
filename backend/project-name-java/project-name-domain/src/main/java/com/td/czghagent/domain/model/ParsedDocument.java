// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.model;

import java.util.List;

public record ParsedDocument(
        String category,
        String summary,
        String confidence,
        List<Evidence> evidence,
        List<CandidateFact> facts,
        List<DetailCandidate> details,
        List<String> warnings
) {
    public ParsedDocument(String category, String summary, String confidence, List<Evidence> evidence,
                          List<CandidateFact> facts, List<String> warnings) {
        this(category, summary, confidence, evidence, facts, List.of(), warnings);
    }

    public record Evidence(String locatorType, String locator, String excerpt) {
    }

    public record CandidateFact(
            String fieldCode,
            String fieldName,
            String value,
            String unit,
            String sourceLocation,
            String confidence,
            String sourceExcerpt,
            String scope,
            String standard
    ) {
    }

    public record DetailCandidate(
            String fieldCode,
            String value,
            String sourceLocation,
            String confidence,
            String sourceExcerpt
    ) {
    }
}
