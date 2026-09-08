// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BidContentQualityGuard {
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SPACE_ENTITY_PATTERN = Pattern.compile(
            "(?i)(?:&#x0*20;|&#0*32;|&#x0*a0;|&#0*160;|&nbsp;)"
    );
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "(?is)<p\\b[^>]*>(.*?)</p>"
    );
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"
    );
    private static final Pattern INTERNAL_FIELD_PATTERN = Pattern.compile(
            "(?i)\\b(?:biddingMode|chapterId|criterionId|outlineNodeId|sourceLocator|generationStatus)\\s*[:=]"
    );
    private static final Pattern FIRST_PERSON_PATTERN = Pattern.compile(
            "(我方|我司|我公司|本公司|本单位|我们)"
    );
    private static final Pattern PROCESS_EXPLANATION_PATTERN = Pattern.compile(
            "(对应段落|本段响应|本章节响应|生成过程|编制过程|写作过程)"
    );
    private static final Pattern TOTAL_DURATION_PATTERN = Pattern.compile(
            "(?:计划工期|项目总工期|总工期|项目建设周期|整体建设周期|总体建设周期|"
                    + "项目周期|合同工期|项目实施周期|整体实施周期|总体实施周期)[^\\d]{0,12}"
                    + "(\\d{2,5})\\s*(?:日历)?天"
    );
    private static final List<String> PHASE_NAMES = List.of(
            "启动与规划", "智能体开发", "需求分析", "平台部署", "集成测试",
            "试运行", "验收", "交付", "培训", "运维", "部署", "开发", "测试", "实施", "建设", "启动"
    );
    private static final String PHASE_TOKEN = PHASE_NAMES.stream()
            .map(Pattern::quote)
            .collect(java.util.stream.Collectors.joining("|"));
    private static final String DAY_RANGE =
            "第\\s*(?<start>\\d{1,4})\\s*[-—–至到]\\s*(?<end>\\d{1,4})\\s*天";
    private static final Pattern PHASE_THEN_RANGE_PATTERN = Pattern.compile(
            "(?<phase>" + PHASE_TOKEN + ")\\s*(?:工作)?(?:阶段|环节)?\\s*"
                    + "(?:(?:计划|安排|时间|周期|起止时间)\\s*)?"
                    + "(?:为|是|：|:|安排在|计划于|位于)?\\s*" + DAY_RANGE
    );
    private static final Pattern RANGE_THEN_PHASE_PATTERN = Pattern.compile(
            DAY_RANGE + "(?:\\s*(?:为|是|：|:|安排(?:为|用于)?|用于|进入|开展|执行|完成)\\s*|\\s+)"
                    + "(?<phase>" + PHASE_TOKEN + ")\\s*(?:工作)?(?:阶段|环节)?"
    );
    private static final Pattern RESOLVE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(分钟|小时|天)\\s*(?:内|以内)?[^。；;，,\\n<]{0,18}(?:解决|修复|恢复|处置|闭环)"
    );
    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(分钟|小时|天)"
    );
    private static final Pattern THROUGH_ACHIEVE_ENSURE_PATTERN = Pattern.compile(
            "通过[^。！？]{0,70}实现[^。！？]{0,70}确保"
    );
    private static final List<String> EMPTY_STYLE_PHRASES = List.of(
            "随着信息技术的发展", "具有重要意义", "全面赋能", "全面提升",
            "形成完善闭环", "形成完整闭环", "通过上述措施", "综上所述"
    );
    private BidContentQualityGuard() {
    }

    static List<BidProductionState.ReviewIssue> reviewWorkspace(BidWorkspace workspace) {
        List<TextSample> texts = workspace.chapters().stream()
                .map(chapter -> new TextSample(chapter.id(), chapter.title(), chapter.content()))
                .toList();
        return reviewTextSequence(workspace.bid().biddingMode(), texts);
    }

    static List<BidProductionState.ReviewIssue> reviewSnapshot(
            BidGenerationSnapshot snapshot, List<BidWorkspace.Chapter> chapters
    ) {
        Map<String, BidWorkspace.Chapter> chapterById = new LinkedHashMap<>();
        chapters.forEach(chapter -> chapterById.put(chapter.id(), chapter));
        List<TextSample> texts = snapshot.outline().stream()
                .filter(node -> node.chapterId() != null)
                .map(node -> {
                    BidWorkspace.Chapter chapter = chapterById.get(node.chapterId());
                    String html = chapter == null ? "" : safe(chapter.content());
                    return new TextSample(node.chapterId(), node.title(), html);
                })
                .toList();
        return reviewTextSequence(snapshot.biddingMode(), texts);
    }

    static List<BidProductionState.ReviewIssue> reviewDraft(
            BidGenerationSnapshot snapshot, BidGenerationUnit current,
            List<BidGenerationUnit> allUnits, String currentHtml
    ) {
        int currentIndex = indexOf(allUnits, current.id());
        List<TextSample> texts = new ArrayList<>();
        for (int index = 0; index < currentIndex; index++) {
            BidGenerationUnit unit = allUnits.get(index);
            if (unit.content() == null || unit.content().isBlank()) {
                continue;
            }
            texts.add(new TextSample(unit.chapterId(), unit.unitTitle(), unit.content()));
        }
        texts.add(new TextSample(current.chapterId(), current.unitTitle(), currentHtml));
        return reviewTextSequence(snapshot.biddingMode(), texts);
    }

    static String globalCommitmentLedger(
            BidGenerationSnapshot snapshot, List<BidGenerationUnit> allUnits, BidGenerationUnit current
    ) {
        int currentIndex = indexOf(allUnits, current.id());
        List<TextSample> priorTexts = new ArrayList<>();
        for (int index = 0; index < currentIndex; index++) {
            BidGenerationUnit unit = allUnits.get(index);
            if (unit.content() == null || unit.content().isBlank()) {
                continue;
            }
            priorTexts.add(new TextSample(unit.chapterId(), unit.unitTitle(), unit.content()));
        }
        List<String> lines = new ArrayList<>();
        extractTotalDays(snapshot.writingBible()).ifPresent(days ->
                lines.add("计划工期固定基准：500天（不可改写为其他天数）".replace("500", String.valueOf(days))));
        lines.add("暗标称谓固定基准：统一使用“投标人”或“本项目实施方”，禁止使用“我方”“我司”“我公司”“本公司”等第一人称。");

        Map<String, String> phaseBaselines = new LinkedHashMap<>();
        for (TextSample sample : priorTexts) {
            for (PhaseClaim claim : extractPhaseClaims(sample)) {
                phaseBaselines.putIfAbsent(claim.phase(), claim.range());
            }
        }
        phaseBaselines.forEach((phase, range) ->
                lines.add(phase + "基准：" + range + "，后续章节必须保持一致。"));

        // Do not promote a response time first mentioned in generated prose to
        // a global fact. SLA values are commonly tiered by incident severity;
        // only source requirements in the frozen commitment registry are
        // authoritative and those are already supplied separately.

        return String.join("\n", lines);
    }

    static List<String> protectedFacts(BidGenerationSnapshot snapshot) {
        List<String> result = new ArrayList<>();
        snapshot.facts().forEach(fact -> result.add(fact.name() + "：" + fact.value()));
        return List.copyOf(result);
    }

    static List<String> protectedFacts(
            BidGenerationSnapshot snapshot,
            List<com.td.czghagent.domain.port.TenderAiGateway.Criterion> criteria
    ) {
        List<String> result = new ArrayList<>(protectedFacts(snapshot));
        criteria.forEach(item -> result.add(item.title() + "：" + item.description()));
        return List.copyOf(result);
    }

    static String normalizeGeneratedLanguage(String biddingMode, String html) {
        String normalized = SPACE_ENTITY_PATTERN.matcher(safe(html)).replaceAll(" ")
                .replace("对应段落", "对应要求")
                .replace("本段响应", "本节方案")
                .replace("本章节响应", "本章方案")
                .replace("生成过程", "处理过程")
                .replace("编制过程", "实施过程")
                .replace("写作过程", "处理过程");
        if ("BLIND".equals(biddingMode)) {
            normalized = FIRST_PERSON_PATTERN.matcher(normalized).replaceAll("投标人");
        }
        return normalized.strip();
    }

    static int visibleCharacterCount(String html) {
        String withoutEntities = SPACE_ENTITY_PATTERN.matcher(safe(html)).replaceAll(" ");
        return TAG_PATTERN.matcher(withoutEntities).replaceAll("")
                .replaceAll("\\s+", "").length();
    }

    static String reviewInstruction(List<BidProductionState.ReviewIssue> issues) {
        String details = issues.stream().map(issue ->
                "问题：" + issue.message() + "；修订要求：" + issue.suggestion())
                .collect(java.util.stream.Collectors.joining("\n"));
        return "仅修订下列阻断问题，保留章节结构、事实、表格和其他合格内容不变。"
                + "不得出现“对应段落”“生成过程”等编制说明。"
                + "暗标正文不得出现我方、我司、我公司、本公司等第一人称。"
                + "出现时限、工期、里程碑时，必须与全局台账保持一致。\n"
                + details;
    }

    static String styleRevisionInstruction(List<BidProductionState.ReviewIssue> issues) {
        String details = issues.stream().map(issue -> issue.code() + "：" + issue.message())
                .collect(java.util.stream.Collectors.joining("\n"));
        return "对本节执行一次保守的专业编辑。删除完全重复段落和无新增信息的套话，"
                + "改变重复的段落开头及‘通过……实现……确保……’机械句式，"
                + "将空泛表述改为原文已经具备的责任角色、输入、动作、输出或检查方法。"
                + "保留全部事实、数字、参数、时限、承诺、专业术语、HTML表格及表题表注；"
                + "不得新增事实，不得增删表格行列，不得输出标题或编辑说明。\n" + details;
    }

    private static List<BidProductionState.ReviewIssue> reviewTextSequence(
            String biddingMode, List<TextSample> texts
    ) {
        List<BidProductionState.ReviewIssue> issues = new ArrayList<>();
        Map<String, String> totalDaysByChapter = new LinkedHashMap<>();
        Map<String, PhaseClaim> phaseBaselines = new LinkedHashMap<>();
        Map<String, ParagraphClaim> paragraphBaselines = new LinkedHashMap<>();
        Double resolveCeilingHours = null;

        for (TextSample sample : texts) {
            String visible = visible(sample.html());
            if (visible.isBlank()) {
                continue;
            }
            issues.addAll(internalLeaks(sample.chapterId(), visible));
            issues.addAll(processLanguageIssues(sample.chapterId(), visible));
            issues.addAll(firstPersonIssues(sample.chapterId(), biddingMode, visible));
            issues.addAll(totalDurationIssues(sample.chapterId(), visible, totalDaysByChapter));
            issues.addAll(phaseIssues(sample.chapterId(), sample.title(), visible, phaseBaselines));
            issues.addAll(styleIssues(sample, paragraphBaselines));
            resolveCeilingHours = resolveIssues(sample.chapterId(), sample.title(), visible,
                    resolveCeilingHours, issues);
        }

        return dedupe(issues);
    }

    private static List<BidProductionState.ReviewIssue> styleIssues(
            TextSample sample, Map<String, ParagraphClaim> paragraphBaselines
    ) {
        List<BidProductionState.ReviewIssue> issues = new ArrayList<>();
        List<String> paragraphs = visibleParagraphs(sample.html());
        Map<String, Integer> openings = new LinkedHashMap<>();
        for (String paragraph : paragraphs) {
            String normalized = normalizedParagraph(paragraph);
            if (normalized.length() >= 60) {
                ParagraphClaim previous = paragraphBaselines.putIfAbsent(
                        normalized, new ParagraphClaim(sample.chapterId(), sample.title()));
                if (previous != null) {
                    issues.add(issue(sample.chapterId(), "ERROR", "STYLE_DUPLICATE_PARAGRAPH",
                            "正文与“" + previous.title() + "”存在完全重复的长段落",
                            "保留必要事实一次，其余位置改写为本节专属的动作、产物或验收内容"));
                }
            }
            String opening = paragraph.replaceFirst(
                    "^(?:\\d+\\.|（\\d+）|第[一二三四五六七八九十]+[，、])\\s*", "");
            opening = normalizedParagraph(opening);
            if (opening.length() >= 10) {
                String key = opening.substring(0, 10);
                openings.merge(key, 1, Integer::sum);
            }
        }
        int repeatedOpenings = openings.values().stream().mapToInt(
                count -> count >= 3 ? count : 0).sum();
        if (repeatedOpenings >= 3) {
            issues.add(issue(sample.chapterId(), "WARN", "STYLE_REPETITIVE_OPENING",
                    "本节有" + repeatedOpenings + "个段落使用相同或高度相似的开头",
                    "按判断、动作、产物或验证结果分别起句，避免连续使用同一主语和句式"));
        }
        int cliches = clichéCount(visible(sample.html()));
        int visibleLength = Math.max(1, visible(sample.html()).length());
        if (cliches >= 4 && cliches * 1_000 / visibleLength >= 3) {
            issues.add(issue(sample.chapterId(), "WARN", "STYLE_CLICHE_DENSITY",
                    "本节检测到" + cliches + "处高频套话或机械连接句",
                    "删除宣传性结论，改为已有依据下的责任、动作、输出和检查方法"));
        }
        return issues;
    }

    private static int clichéCount(String value) {
        int count = 0;
        Matcher matcher = THROUGH_ACHIEVE_ENSURE_PATTERN.matcher(value);
        while (matcher.find()) {
            count++;
        }
        for (String phrase : EMPTY_STYLE_PHRASES) {
            int offset = 0;
            while ((offset = value.indexOf(phrase, offset)) >= 0) {
                count++;
                offset += phrase.length();
            }
        }
        return count;
    }

    private static List<String> visibleParagraphs(String html) {
        List<String> result = new ArrayList<>();
        Matcher matcher = PARAGRAPH_PATTERN.matcher(safe(html));
        while (matcher.find()) {
            String value = visible(matcher.group(1));
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        if (result.isEmpty() && !visible(html).isBlank()) {
            result.add(visible(html));
        }
        return result;
    }

    private static String normalizedParagraph(String value) {
        return safe(value).replaceAll("[\\s，。；：、！？,.!?;:'‘’“”()（）\\-—]", "")
                .toLowerCase(Locale.ROOT);
    }

    private static List<BidProductionState.ReviewIssue> internalLeaks(String chapterId, String visible) {
        List<BidProductionState.ReviewIssue> issues = new ArrayList<>();
        Matcher uuidMatcher = UUID_PATTERN.matcher(visible);
        if (uuidMatcher.find()) {
            issues.add(issue(chapterId, "ERROR", "INTERNAL_IDENTIFIER_LEAK",
                    "正文出现内部标识：" + uuidMatcher.group(),
                    "删除内部标识并改为面向评审人的业务表达"));
        }
        Matcher fieldMatcher = INTERNAL_FIELD_PATTERN.matcher(visible);
        if (fieldMatcher.find()) {
            issues.add(issue(chapterId, "ERROR", "INTERNAL_FIELD_LEAK",
                    "正文出现内部字段标记：" + fieldMatcher.group(),
                    "删除内部字段标记并改成自然语言"));
        }
        return issues;
    }

    private static List<BidProductionState.ReviewIssue> firstPersonIssues(
            String chapterId, String biddingMode, String visible
    ) {
        if (!"BLIND".equals(biddingMode) || !FIRST_PERSON_PATTERN.matcher(visible).find()) {
            return List.of();
        }
        return List.of(issue(chapterId, "ERROR", "ANONYMOUS_COMPLIANCE_RISK",
                "暗标正文出现可能泄露投标人身份的第一人称表述",
                "统一替换为“投标人”或“本项目实施方”，并删除可识别表述"));
    }

    private static List<BidProductionState.ReviewIssue> processLanguageIssues(
            String chapterId, String visible
    ) {
        if (!PROCESS_EXPLANATION_PATTERN.matcher(visible).find()) {
            return List.of();
        }
        return List.of(issue(chapterId, "ERROR", "PROCESS_EXPLANATION_LEAK",
                "正文出现编制过程或对应段落说明",
                "改写为对应要求或业务响应内容，删除编制过程说明"));
    }

    private static List<BidProductionState.ReviewIssue> totalDurationIssues(
            String chapterId, String visible, Map<String, String> totalDaysByChapter
    ) {
        Matcher matcher = TOTAL_DURATION_PATTERN.matcher(visible);
        List<BidProductionState.ReviewIssue> issues = new ArrayList<>();
        while (matcher.find()) {
            String declaredDays = matcher.group(1);
            String previous = totalDaysByChapter.putIfAbsent("TOTAL", declaredDays);
            if (previous != null && !previous.equals(declaredDays)) {
                issues.add(issue(chapterId, "WARN", "TOTAL_DURATION_CONFLICT",
                        "项目总工期表述不一致：当前为" + declaredDays + "天，基准为" + previous + "天",
                        "统一为同一个工期口径，禁止在不同章节写入不同总工期"));
            }
        }
        return issues;
    }

    private static List<BidProductionState.ReviewIssue> phaseIssues(
            String chapterId, String title, String visible, Map<String, PhaseClaim> phaseBaselines
    ) {
        List<BidProductionState.ReviewIssue> issues = new ArrayList<>();
        for (PhaseClaim claim : extractPhaseClaims(new TextSample(chapterId, title, visible))) {
            PhaseClaim previous = phaseBaselines.putIfAbsent(claim.phase(), claim);
            if (previous != null && !previous.range().equals(claim.range())) {
                issues.add(issue(chapterId, "WARN", "PROGRESS_INCONSISTENCY",
                        "进度阶段“" + claim.phase() + "”与前文不一致：当前为" + claim.range()
                                + "，基准为" + previous.range(),
                        "统一采用前文已确认的阶段时间区间，不得在后续章节改写"));
            }
        }
        return issues;
    }

    private static Double resolveIssues(
            String chapterId, String title, String visible, Double resolveCeilingHours,
            List<BidProductionState.ReviewIssue> issues
    ) {
        List<Double> currentGlobalHours = extractResolveHours(visible, true);
        List<Double> currentHours = extractResolveHours(visible, false);
        Double currentCeiling = currentGlobalHours.isEmpty() ? resolveCeilingHours : currentGlobalHours.stream()
                .min(Double::compareTo).orElse(resolveCeilingHours);
        if (currentCeiling == null && !currentHours.isEmpty() && isServiceParagraph(visible)) {
            currentCeiling = currentHours.stream().min(Double::compareTo).orElse(null);
        }
        if (resolveCeilingHours != null) {
            double currentMax = currentHours.stream().mapToDouble(Double::doubleValue).max().orElse(0d);
            if (currentMax > resolveCeilingHours * 1.25d && currentMax > resolveCeilingHours + 0.01d) {
                issues.add(issue(chapterId, "WARN", "RESPONSE_TIME_CONFLICT",
                        "服务解决时限与前文口径可能不一致：当前章节出现" + formatHours(currentMax)
                                + "，前文出现" + formatHours(resolveCeilingHours),
                        "核对是否属于不同故障等级；若为同一等级则统一时限，若为分级 SLA 则保留并明确等级"));
            }
        }
        return currentCeiling;
    }

    private static boolean isServiceParagraph(String visible) {
        return visible.contains("服务") || visible.contains("售后") || visible.contains("运维")
                || visible.contains("响应") || visible.contains("故障") || visible.contains("工单")
                || visible.contains("驻场") || visible.contains("保障");
    }

    private static List<Double> extractResolveHours(String visible, boolean globalOnly) {
        List<Double> result = new ArrayList<>();
        String lower = visible.toLowerCase(Locale.ROOT);
        if (globalOnly && !(lower.contains("所有") || lower.contains("全部")
                || lower.contains("统一") || lower.contains("整体") || lower.contains("总体")
                || lower.contains("承诺"))) {
            return result;
        }
        Matcher matcher = RESOLVE_PATTERN.matcher(visible);
        while (matcher.find()) {
            result.add(normalizeHours(matcher.group(1), matcher.group(2)));
        }
        Matcher tokenMatcher = TIME_TOKEN_PATTERN.matcher(visible);
        while (tokenMatcher.find()) {
            int start = Math.max(0, tokenMatcher.start() - 24);
            int end = Math.min(visible.length(), tokenMatcher.end() + 24);
            String window = visible.substring(start, end);
            if (window.contains("解决") || window.contains("修复") || window.contains("恢复")
                    || window.contains("处置") || window.contains("闭环")) {
                result.add(normalizeHours(tokenMatcher.group(1), tokenMatcher.group(2)));
            }
        }
        return result;
    }

    private static Optional<Double> extractGlobalResolveCeiling(List<TextSample> texts) {
        return texts.stream()
                .map(sample -> visible(sample.html()))
                .filter(BidContentQualityGuard::isServiceParagraph)
                .flatMap(text -> extractResolveHours(text, true).stream())
                .min(Double::compareTo);
    }

    private static List<PhaseClaim> extractPhaseClaims(TextSample sample) {
        String visible = visible(sample.html());
        Map<String, PhaseClaim> unique = new LinkedHashMap<>();
        collectPhaseClaims(PHASE_THEN_RANGE_PATTERN, visible, sample, unique, false);
        collectPhaseClaims(RANGE_THEN_PHASE_PATTERN, visible, sample, unique, true);
        return List.copyOf(unique.values());
    }

    private static void collectPhaseClaims(
            Pattern pattern, String visible, TextSample sample, Map<String, PhaseClaim> target,
            boolean skipClaimedRange
    ) {
        Matcher matcher = pattern.matcher(visible);
        while (matcher.find()) {
            String phase = matcher.group("phase");
            String range = "第" + matcher.group("start") + "-" + matcher.group("end") + "天";
            if (skipClaimedRange && target.values().stream().anyMatch(claim -> claim.range().equals(range))) {
                continue;
            }
            target.putIfAbsent(phase + "|" + range,
                    new PhaseClaim(phase, range, sample.chapterId(), sample.title()));
        }
    }

    private static Optional<Integer> extractTotalDays(String content) {
        Matcher matcher = TOTAL_DURATION_PATTERN.matcher(visible(content));
        if (matcher.find()) {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return Optional.empty();
    }

    private static List<BidProductionState.ReviewIssue> dedupe(
            List<BidProductionState.ReviewIssue> issues
    ) {
        Map<String, BidProductionState.ReviewIssue> unique = new LinkedHashMap<>();
        for (BidProductionState.ReviewIssue issue : issues) {
            String key = String.join("|",
                    Objects.toString(issue.chapterId(), ""),
                    issue.code(), issue.severity(), issue.message(), issue.suggestion());
            unique.putIfAbsent(key, issue);
        }
        return List.copyOf(unique.values());
    }

    private static BidProductionState.ReviewIssue issue(
            String chapterId, String severity, String code, String message, String suggestion
    ) {
        return new BidProductionState.ReviewIssue(
                java.util.UUID.randomUUID().toString(), chapterId, severity, code, message, suggestion, "OPEN");
    }

    private static String visible(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String withoutEntities = SPACE_ENTITY_PATTERN.matcher(html).replaceAll(" ");
        return TAG_PATTERN.matcher(withoutEntities).replaceAll(" ").replace('\u00a0', ' ').trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static double normalizeHours(String value, String unit) {
        double amount = Double.parseDouble(value);
        return switch (unit) {
            case "分钟" -> amount / 60d;
            case "天" -> amount * 24d;
            default -> amount;
        };
    }

    private static String formatHours(double hours) {
        if (Math.abs(hours - Math.rint(hours)) < 0.001d) {
            return ((int) Math.rint(hours)) + "小时";
        }
        return String.format(Locale.ROOT, "%.1f小时", hours);
    }

    private static int indexOf(List<BidGenerationUnit> units, String unitId) {
        for (int index = 0; index < units.size(); index++) {
            if (Objects.equals(units.get(index).id(), unitId)) {
                return index;
            }
        }
        throw new IllegalStateException("生成单元不存在");
    }

    private record TextSample(String chapterId, String title, String html) {
    }

    private record PhaseClaim(String phase, String range, String chapterId, String title) {
    }

    private record ParagraphClaim(String chapterId, String title) {
    }
}
