// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-11
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为正文模型构造有界的写作策划、范文风格和真实相邻正文上下文。
 */
final class BidWritingContextBuilder {
    private static final int MAX_PREVIOUS_PROSE = 2_800;
    private static final int MAX_CHAPTER_OPENING = 1_200;
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "(?is)<p\\b[^>]*>.*?</p>"
    );
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern TABLE_TITLE_PATTERN = Pattern.compile(
            "(?is)<p\\b[^>]*data-table-title[^>]*>(.*?)</p>|<caption\\b[^>]*>(.*?)</caption>"
    );
    private static final List<String> TECHNICAL_VERBS = List.of(
            "配置", "部署", "采集", "校验", "监测", "分析", "调度", "隔离",
            "审计", "验证", "交付", "验收", "追踪", "处置", "归档", "复核"
    );
    private static final List<String> DEFAULT_AVOIDANCE = List.of(
            "本项目旨在", "随着信息技术的发展", "具有重要意义", "全面赋能",
            "全面提升", "形成完善闭环", "通过上述措施", "综上所述"
    );

    private BidWritingContextBuilder() {
    }

    static String writingPlan(
            BidGenerationSnapshot.Outline node,
            List<BidGenerationSnapshot.Requirement> requirements,
            BidGenerationUnit current,
            List<BidGenerationUnit> allUnits
    ) {
        List<BidGenerationUnit> chapterUnits = allUnits.stream()
                .filter(unit -> unit.chapterId().equals(current.chapterId()))
                .sorted(Comparator.comparingInt(BidGenerationUnit::unitIndex)).toList();
        String concerns = requirements.stream().limit(8)
                .map(item -> item.title() + "：" + limit(visible(item.description()), 220))
                .collect(java.util.stream.Collectors.joining("；"));
        String otherRoles = chapterUnits.stream().filter(unit -> !unit.id().equals(current.id()))
                .map(BidGenerationUnit::unitTitle).distinct()
                .collect(java.util.stream.Collectors.joining("、"));
        return limit("当前语义职责：" + current.unitTitle() + "。\n"
                + "写作目标：围绕“" + node.title() + "”完成可直接评审的技术响应；"
                + "任务边界为“" + visible(node.taskBrief()) + "”。\n"
                + "评委关注点：" + (concerns.isBlank() ? "目录任务简述与必含关键词" : concerns) + "。\n"
                + "必须体现：" + String.join("、", node.mustKeywords()) + "。\n"
                + paragraphPlan(current.unitTitle()) + "\n"
                + "证据表达：优先写清责任角色、输入、处理动作、输出成果、检查方法；"
                + "只有冻结输入明确提供时才写具体数值或时限。\n"
                + "表格策略：仅当存在多对象对比、职责映射、参数清单或验收矩阵时使用表格，"
                + "不为增加篇幅强行制表。\n"
                + (otherRoles.isBlank() ? ""
                : "避免重复：本章其他单元负责“" + otherRoles + "”，本单元不提前展开或重复总结。"),
                3_600);
    }

    static String styleProfile(List<BidGenerationSnapshot.ReferenceChunk> chunks) {
        String sample = chunks.stream().filter(chunk -> "TEMPLATE".equals(chunk.category()))
                .map(BidGenerationSnapshot.ReferenceChunk::content)
                .filter(content -> content != null && !content.isBlank()).limit(30)
                .collect(java.util.stream.Collectors.joining("\n"));
        sample = limit(sample, 40_000);
        if (sample.isBlank()) {
            return "专业技术方案文风；段落围绕判断、动作、产物和验收展开；"
                    + "句式长短自然，不使用宣传口号，不虚构参数。";
        }
        String plain = visible(sample);
        List<String> sentences = splitNonBlank(plain, "[。！？!?；;]+");
        List<String> paragraphs = splitNonBlank(sample, "(?i)(?:\\r?\\n)+|</p>");
        int averageSentence = averageLength(sentences);
        int averageParagraph = averageLength(paragraphs.stream().map(
                BidWritingContextBuilder::visible).toList());
        List<String> verbs = TECHNICAL_VERBS.stream()
                .sorted(Comparator.comparingInt((String verb) -> occurrences(plain, verb)).reversed())
                .filter(verb -> plain.contains(verb)).limit(6).toList();
        boolean tables = sample.toLowerCase().contains("<table")
                || Pattern.compile("(?m)^\\s*\\|.+\\|\\s*$").matcher(sample).find();
        return "范文风格统计（仅模仿表达，不继承事实）：平均句长约" + averageSentence
                + "字，平均段落约" + averageParagraph + "字；"
                + (tables ? "范文会在多维信息场景使用表格；" : "范文以连续技术段落为主；")
                + (verbs.isEmpty() ? "" : "常用技术动词包括" + String.join("、", verbs) + "；")
                + "保持同等专业密度和段落节奏，禁止复制范文项目名称、主体、案例、参数和承诺。";
    }

    static String previousProse(
            BidGenerationSnapshot snapshot, BidGenerationUnit current,
            List<BidGenerationUnit> allUnits
    ) {
        List<BidGenerationUnit> ordered = orderedPriorUnits(snapshot, current, allUnits);
        List<BidGenerationUnit> sameChapter = ordered.stream()
                .filter(unit -> unit.chapterId().equals(current.chapterId())).toList();
        List<BidGenerationUnit> source = sameChapter.isEmpty() ? ordered : sameChapter;
        String html = source.stream().skip(Math.max(0, source.size() - 2L))
                .map(BidGenerationUnit::content).collect(java.util.stream.Collectors.joining());
        return tailParagraphs(html, 4, MAX_PREVIOUS_PROSE);
    }

    static String chapterOpening(BidGenerationUnit current, List<BidGenerationUnit> allUnits) {
        String html = allUnits.stream()
                .filter(unit -> unit.chapterId().equals(current.chapterId()))
                .filter(unit -> unit.unitIndex() < current.unitIndex())
                .sorted(Comparator.comparingInt(BidGenerationUnit::unitIndex))
                .map(BidGenerationUnit::content).filter(content -> content != null && !content.isBlank())
                .collect(java.util.stream.Collectors.joining());
        return firstParagraphs(html, 2, MAX_CHAPTER_OPENING);
    }

    static String recentTableCaption(
            BidGenerationSnapshot snapshot, BidGenerationUnit current,
            List<BidGenerationUnit> allUnits
    ) {
        List<String> captions = new ArrayList<>();
        for (BidGenerationUnit unit : orderedPriorUnits(snapshot, current, allUnits)) {
            Matcher matcher = TABLE_TITLE_PATTERN.matcher(safe(unit.content()));
            while (matcher.find()) {
                captions.add(visible(matcher.group(1) == null ? matcher.group(2) : matcher.group(1)));
            }
        }
        return captions.isEmpty() ? "" : limit(captions.getLast(), 160);
    }

    static List<String> repetitionAvoidance(
            BidGenerationSnapshot snapshot, BidGenerationUnit current,
            List<BidGenerationUnit> allUnits
    ) {
        Map<String, String> unique = new LinkedHashMap<>();
        DEFAULT_AVOIDANCE.forEach(item -> unique.put(item, item));
        for (BidGenerationUnit unit : orderedPriorUnits(snapshot, current, allUnits)) {
            for (String paragraph : paragraphs(unit.content())) {
                String opening = visible(paragraph).replaceFirst(
                        "^(?:\\d+\\.|（\\d+）|第[一二三四五六七八九十]+[，、])\\s*", "");
                if (opening.length() >= 12) {
                    opening = opening.substring(0, Math.min(24, opening.length()));
                    unique.putIfAbsent(opening, opening);
                }
            }
        }
        return unique.values().stream().limit(12).toList();
    }

    private static List<BidGenerationUnit> orderedPriorUnits(
            BidGenerationSnapshot snapshot, BidGenerationUnit current,
            List<BidGenerationUnit> allUnits
    ) {
        Map<String, BidGenerationSnapshot.Outline> nodes = new HashMap<>();
        snapshot.outline().stream().filter(node -> node.chapterId() != null)
                .forEach(node -> nodes.put(node.chapterId(), node));
        String rootId = rootId(snapshot, current.chapterId());
        return allUnits.stream().filter(unit -> safe(unit.content()).length() > 0)
                .filter(unit -> rootId.equals(rootId(snapshot, unit.chapterId())))
                .filter(unit -> before(nodes, unit, current))
                .sorted(Comparator.comparingInt((BidGenerationUnit unit) ->
                                nodes.get(unit.chapterId()).sortOrder())
                        .thenComparingInt(BidGenerationUnit::unitIndex)).toList();
    }

    private static boolean before(
            Map<String, BidGenerationSnapshot.Outline> nodes,
            BidGenerationUnit candidate, BidGenerationUnit current
    ) {
        int candidateOrder = nodes.get(candidate.chapterId()).sortOrder();
        int currentOrder = nodes.get(current.chapterId()).sortOrder();
        return candidateOrder < currentOrder
                || (candidateOrder == currentOrder && candidate.unitIndex() < current.unitIndex());
    }

    private static String rootId(BidGenerationSnapshot snapshot, String chapterId) {
        Map<String, BidGenerationSnapshot.Outline> bySource = new HashMap<>();
        snapshot.outline().forEach(node -> bySource.put(node.sourceOutlineId(), node));
        BidGenerationSnapshot.Outline current = snapshot.outline().stream()
                .filter(node -> chapterId.equals(node.chapterId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("写作章节不在快照目录中"));
        while (current.level() > 1) {
            current = bySource.get(current.parentSourceOutlineId());
            if (current == null) {
                throw new IllegalStateException("快照目录父子关系不完整");
            }
        }
        return current.sourceOutlineId();
    }

    private static String paragraphPlan(String role) {
        if (role.contains("验收") || role.contains("交付")) {
            return "段落职责：先列交付物和责任边界，再写控制动作、检查记录、验收依据和异常闭环。";
        }
        if (role.contains("控制") || role.contains("保障")) {
            return "段落职责：按风险或控制对象说明触发条件、责任角色、处置动作、记录和复核方式。";
        }
        if (role.contains("判断") || role.contains("边界")) {
            return "段落职责：先解释对要求的工程判断，再界定建设范围、约束和后续设计依据。";
        }
        return "段落职责：按建设对象说明设计选择、组成关系、实施动作、输入输出和验证方法。";
    }

    private static String firstParagraphs(String html, int maximum, int characters) {
        List<String> items = paragraphs(html);
        return limit(String.join("", items.subList(0, Math.min(maximum, items.size()))), characters);
    }

    private static String tailParagraphs(String html, int maximum, int characters) {
        List<String> items = paragraphs(html);
        int start = Math.max(0, items.size() - maximum);
        String value = String.join("", items.subList(start, items.size()));
        return value.length() <= characters ? value : value.substring(value.length() - characters);
    }

    private static List<String> paragraphs(String html) {
        List<String> result = new ArrayList<>();
        Matcher matcher = PARAGRAPH_PATTERN.matcher(safe(html));
        while (matcher.find()) {
            result.add(matcher.group());
        }
        if (result.isEmpty() && !safe(html).isBlank()) {
            result.add(limit(html, MAX_PREVIOUS_PROSE));
        }
        return result;
    }

    private static List<String> splitNonBlank(String value, String expression) {
        return Pattern.compile(expression).splitAsStream(safe(value))
                .map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private static int averageLength(List<String> values) {
        return values.isEmpty() ? 0 : Math.max(1, (int) Math.round(
                values.stream().mapToInt(String::length).average().orElse(0)));
    }

    private static int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }

    private static String visible(String value) {
        return TAG_PATTERN.matcher(safe(value)).replaceAll(" ")
                .replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    private static String limit(String value, int maximum) {
        String safe = safe(value);
        return safe.substring(0, Math.min(maximum, safe.length()));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
