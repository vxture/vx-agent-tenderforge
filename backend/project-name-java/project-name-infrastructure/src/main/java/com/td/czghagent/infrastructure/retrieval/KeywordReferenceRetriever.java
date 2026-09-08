// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.infrastructure.retrieval;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.port.ReferenceRetriever;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 小规模素材集合的确定性关键词检索实现。
 */
@Component
public class KeywordReferenceRetriever implements ReferenceRetriever {
    private static final Pattern SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public List<BidGenerationSnapshot.ReferenceChunk> retrieve(
            String query,
            List<BidGenerationSnapshot.ReferenceChunk> candidates,
            int maxChunks,
            int maxCharacters
    ) {
        if (candidates == null || candidates.isEmpty() || maxChunks <= 0 || maxCharacters <= 0) {
            return List.of();
        }
        Set<String> terms = queryTerms(query);
        List<ScoredChunk> ranked = candidates.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, terms)))
                .filter(item -> item.score() > 0 || terms.isEmpty())
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                        .thenComparing(item -> item.chunk().assetId())
                        .thenComparingInt(item -> item.chunk().chunkIndex()))
                .toList();
        List<BidGenerationSnapshot.ReferenceChunk> result = new ArrayList<>();
        Set<String> selectedIds = new LinkedHashSet<>();
        Set<String> selectedSections = new LinkedHashSet<>();
        int characters = 0;
        for (ScoredChunk item : ranked) {
            String section = item.chunk().assetId() + "|" + normalize(item.chunk().heading());
            if (selectedSections.contains(section)) {
                continue;
            }
            int updated = addIfFits(
                    item.chunk(), result, selectedIds, maxChunks, maxCharacters, characters);
            if (updated >= 0) {
                characters = updated;
                selectedSections.add(section);
            }
        }
        for (ScoredChunk item : ranked) {
            int updated = addIfFits(
                    item.chunk(), result, selectedIds, maxChunks, maxCharacters, characters);
            if (updated >= 0) {
                characters = updated;
            }
        }
        return List.copyOf(result);
    }

    private int addIfFits(
            BidGenerationSnapshot.ReferenceChunk chunk,
            List<BidGenerationSnapshot.ReferenceChunk> result,
            Set<String> selectedIds,
            int maxChunks,
            int maxCharacters,
            int characters
    ) {
        String chunkKey = chunk.assetId() + ":" + chunk.chunkIndex();
        if (selectedIds.contains(chunkKey) || result.size() >= maxChunks) {
            return -1;
        }
        int length = chunk.content().length();
        if (!result.isEmpty() && characters + length > maxCharacters) {
            return -1;
        }
        result.add(chunk);
        selectedIds.add(chunkKey);
        return characters + length;
    }

    private int score(BidGenerationSnapshot.ReferenceChunk chunk, Set<String> terms) {
        String heading = normalize(chunk.heading());
        String content = normalize(chunk.content());
        int score = 0;
        for (String term : terms) {
            if (heading.contains(term)) {
                score += 12 + Math.min(term.length(), 8);
            }
            int position = 0;
            int occurrences = 0;
            while ((position = content.indexOf(term, position)) >= 0 && occurrences < 8) {
                occurrences++;
                position += Math.max(1, term.length());
            }
            score += occurrences * (2 + Math.min(term.length(), 6));
        }
        return score;
    }

    private Set<String> queryTerms(String value) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : SEPARATOR.split(normalize(value))) {
            if (token.length() >= 2) {
                terms.add(token);
            }
            if (containsHan(token)) {
                addHanNgrams(token, terms);
            }
        }
        return terms;
    }

    private void addHanNgrams(String token, Set<String> terms) {
        int[] points = token.codePoints().toArray();
        for (int size = 2; size <= Math.min(4, points.length); size++) {
            for (int index = 0; index + size <= points.length; index++) {
                terms.add(new String(points, index, size));
            }
        }
    }

    private boolean containsHan(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private record ScoredChunk(BidGenerationSnapshot.ReferenceChunk chunk, int score) {
    }
}
