package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从两个冻结业务对象中为当前章节选择有界上下文。
 */
@Component
class BidRelevantContextSelector {
    private static final int MAX_OVERVIEW_CHARACTERS = 2_500;
    private static final int MAX_SCORING_CHARACTERS = 3_500;
    private static final int MAX_QUERY_TERMS = 32;
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.+$");
    private static final Pattern SPLIT = Pattern.compile("[^\\p{IsHan}A-Za-z0-9×]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "项目", "方案", "技术", "建设", "响应", "实施", "总体", "详细",
            "要求", "内容", "系统", "平台", "服务", "管理", "本项目");

    List<BidGenerationSnapshot.Requirement> select(
            BidGenerationSnapshot snapshot,
            BidGenerationSnapshot.Outline node
    ) {
        List<String> queryTerms = queryTerms(node);
        List<BidGenerationSnapshot.Requirement> result = new ArrayList<>();
        for (BidGenerationSnapshot.Requirement requirement : snapshot.requirements()) {
            boolean overview = "PROJECT_OVERVIEW".equals(requirement.type());
            boolean technicalScoring = isScoring(requirement.type());
            if (!overview && !technicalScoring) {
                continue;
            }
            String selected = selectSections(
                    requirement.description(), queryTerms,
                    overview ? MAX_OVERVIEW_CHARACTERS : MAX_SCORING_CHARACTERS,
                    overview);
            if (selected.isBlank()) {
                continue;
            }
            result.add(new BidGenerationSnapshot.Requirement(
                    requirement.sourceCriterionId(), requirement.type(), requirement.title(),
                    selected, requirement.score(), "", requirement.sourceLocator(),
                    requirement.sortOrder()));
        }
        return List.copyOf(result);
    }

    private String selectSections(
            String value,
            List<String> queryTerms,
            int maxCharacters,
            boolean includeFallback
    ) {
        String normalized = safe(value).trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= maxCharacters) {
            int score = relevance(normalized, queryTerms);
            return score > 0 || includeFallback ? normalized : "";
        }
        List<Section> sections = sections(normalized);
        List<Section> matched = sections.stream()
                .map(section -> new Section(
                        section.order(), section.text(), relevance(section.text(), queryTerms)))
                .filter(section -> section.score() > 0)
                .sorted(Comparator.comparingInt(Section::score).reversed()
                        .thenComparingInt(Section::order))
                .toList();
        if (matched.isEmpty()) {
            return includeFallback ? limit(normalized, maxCharacters) : "";
        }
        List<Section> selected = new ArrayList<>();
        int used = 0;
        for (Section section : matched) {
            if (!selected.isEmpty() && used + section.text().length() + 2 > maxCharacters) {
                continue;
            }
            selected.add(section);
            used += section.text().length() + 2;
            if (used >= maxCharacters) {
                break;
            }
        }
        selected.sort(Comparator.comparingInt(Section::order));
        return limit(selected.stream().map(Section::text)
                .collect(java.util.stream.Collectors.joining("\n\n")), maxCharacters);
    }

    private List<Section> sections(String value) {
        List<Section> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int order = 0;
        for (String line : value.lines().toList()) {
            if (HEADING.matcher(line.trim()).matches() && !current.isEmpty()) {
                result.add(new Section(order++, current.toString().trim(), 0));
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            result.add(new Section(order, current.toString().trim(), 0));
        }
        if (result.size() > 1) {
            return result;
        }
        result.clear();
        order = 0;
        for (String paragraph : value.split("\\n\\s*\\n")) {
            if (!paragraph.isBlank()) {
                result.add(new Section(order++, paragraph.trim(), 0));
            }
        }
        return result.isEmpty() ? List.of(new Section(0, value, 0)) : result;
    }

    private List<String> queryTerms(BidGenerationSnapshot.Outline node) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addTerms(result, node.title());
        node.mustKeywords().forEach(value -> addTerms(result, value));
        addTerms(result, node.taskBrief());
        return result.stream().limit(MAX_QUERY_TERMS).toList();
    }

    private void addTerms(Set<String> target, String value) {
        for (String token : SPLIT.split(safe(value))) {
            String normalized = token.trim();
            if (normalized.length() < 2 || STOP_WORDS.contains(normalized)) {
                continue;
            }
            target.add(normalized);
            String stripped = normalized;
            for (String suffix : List.of("总体方案", "详细响应", "建设方案", "实施方案", "方案")) {
                if (stripped.endsWith(suffix) && stripped.length() > suffix.length() + 1) {
                    stripped = stripped.substring(0, stripped.length() - suffix.length());
                    target.add(stripped);
                    break;
                }
            }
        }
    }

    private int relevance(String text, List<String> queryTerms) {
        String compact = text.replaceAll("\\s+", "");
        int score = 0;
        for (String term : queryTerms) {
            if (compact.contains(term)) {
                score += Math.min(12, term.length());
            } else if (term.length() >= 6 && compact.contains(term.substring(0, 4))) {
                score += 2;
            }
        }
        return score;
    }

    private boolean isScoring(String type) {
        return "TECHNICAL_SCORING".equals(type) || "SCORING".equals(type);
    }

    private String limit(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum).stripTrailing() + "…";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record Section(int order, String text, int score) {
    }
}
