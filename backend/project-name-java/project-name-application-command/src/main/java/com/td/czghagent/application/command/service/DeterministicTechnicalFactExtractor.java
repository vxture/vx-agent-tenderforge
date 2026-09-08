// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.ParsedDocument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeterministicTechnicalFactExtractor {
    private static final Pattern SERVER_RESOURCES = Pattern.compile(
            "(?i)(?:cpu[:：]?)?(\\d+)(?:核|C)[,，/]?"
                    + "(?:内存[:：]?)?(\\d+)G(?:B)?[,，/]?"
                    + "(?:硬盘[:：]?)?(\\d+)G(?:B)?"
    );

    public List<BidWorkspace.Criterion> merge(
            List<BidWorkspace.Criterion> modelCriteria,
            List<ParsedDocument.Evidence> evidence
    ) {
        List<BidWorkspace.Criterion> result = new ArrayList<>(modelCriteria);
        Set<String> knownFacts = new HashSet<>();
        result.stream().filter(item -> "FACT".equals(item.type())).forEach(item ->
                knownFacts.add(fingerprint(item.title(), item.description())));
        int nextOrder = result.stream().mapToInt(BidWorkspace.Criterion::sortOrder)
                .max().orElse(-1) + 1;
        for (ParsedDocument.Evidence source : evidence) {
            String excerpt = compact(source.excerpt());
            if (!excerpt.contains("代理") || !excerpt.contains("服务器")) {
                continue;
            }
            Matcher resources = SERVER_RESOURCES.matcher(excerpt);
            if (!resources.find()) {
                continue;
            }
            String description = "前置代理服务器 " + resources.group(1) + "核/"
                    + resources.group(2) + "GB/" + resources.group(3) + "GB";
            String title = "前置代理服务器配置";
            if (!knownFacts.add(fingerprint(title, description))) {
                continue;
            }
            result.add(new BidWorkspace.Criterion(
                    UUID.randomUUID().toString(), "FACT", title, description, null,
                    source.excerpt(), source.locator(), "TECHNICAL", "HIGH",
                    nextOrder++, false
            ));
        }
        return List.copyOf(result);
    }

    private String fingerprint(String title, String description) {
        return compact(title).toLowerCase(Locale.ROOT) + '\u001f'
                + compact(description).toLowerCase(Locale.ROOT);
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
