// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.port.ReferenceRetriever;
import com.td.czghagent.domain.port.TenderAiGateway;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class BidUnitDraftFactory {
    private static final int MAX_REFERENCE_CHUNKS = 4;
    private static final int MAX_REFERENCE_CHARACTERS = 8_000;
    private static final int MAX_PREVIOUS_SUMMARY_CHARACTERS = 1_600;
    private static final int MAX_COMMITMENT_CHARACTERS = 2_400;

    private final ReferenceRetriever referenceRetriever;
    private final BidRelevantContextSelector contextSelector;

    BidUnitDraftFactory(ReferenceRetriever referenceRetriever,
                        BidRelevantContextSelector contextSelector) {
        this.referenceRetriever = referenceRetriever;
        this.contextSelector = contextSelector;
    }

    TenderAiGateway.ChapterDraftRequest create(
            BidGenerationSnapshot snapshot,
            BidGenerationUnit unit,
            List<BidGenerationUnit> allUnits,
            TenderAiGateway.BranchBlueprint branchBlueprint
    ) {
        BidGenerationSnapshot.Outline node = outlineForChapter(snapshot, unit.chapterId());
        List<BidGenerationSnapshot.Requirement> requirements = contextSelector.select(snapshot, node);
        int unitCount = (int) allUnits.stream()
                .filter(candidate -> candidate.chapterId().equals(unit.chapterId())).count();
        return new TenderAiGateway.ChapterDraftRequest(
                unit.idempotencyKey(), snapshot.bidTitle(), snapshot.biddingMode(),
                new TenderAiGateway.ChapterContract(
                        unit.chapterId(), node.title(), node.plannedPages(), node.taskBrief(),
                        node.mustKeywords(), node.scoringPointIds()),
                branchBlueprint,
                criteria(requirements), dictionary(snapshot), evidence(snapshot, node, requirements),
                BidWritingContextBuilder.writingPlan(node, requirements, unit, allUnits),
                BidWritingContextBuilder.styleProfile(snapshot.referenceChunks()),
                previousSummary(snapshot, unit, allUnits),
                BidWritingContextBuilder.previousProse(snapshot, unit, allUnits),
                BidWritingContextBuilder.chapterOpening(unit, allUnits),
                BidWritingContextBuilder.recentTableCaption(snapshot, unit, allUnits),
                BidWritingContextBuilder.repetitionAvoidance(snapshot, unit, allUnits),
                nextBrief(snapshot, node), unit.wordBudget(),
                unit.unitIndex(), unitCount, unit.unitTitle(), snapshot.writingBible(),
                snapshot.termRegistry(), commitmentRegistry(snapshot, unit, allUnits));
    }

    TenderAiGateway.ChapterDraftRequest create(
            BidGenerationSnapshot snapshot,
            BidGenerationUnit unit,
            List<BidGenerationUnit> allUnits
    ) {
        return create(snapshot, unit, allUnits, null);
    }

    BidGenerationSnapshot.Outline outlineForChapter(
            BidGenerationSnapshot snapshot, String chapterId
    ) {
        return snapshot.outline().stream().filter(node -> chapterId.equals(node.chapterId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("快照章节契约不存在"));
    }

    String assembleChapter(List<BidGenerationUnit> units, BidGenerationUnit current, String html) {
        return units.stream().filter(unit -> unit.chapterId().equals(current.chapterId()))
                .sorted(Comparator.comparingInt(BidGenerationUnit::unitIndex))
                .map(unit -> unit.id().equals(current.id()) ? html : safe(unit.content()))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.joining());
    }

    String chapterSummary(List<BidGenerationUnit> units, BidGenerationUnit current, String summary) {
        String value = units.stream().filter(unit -> unit.chapterId().equals(current.chapterId()))
                .sorted(Comparator.comparingInt(BidGenerationUnit::unitIndex))
                .map(unit -> unit.id().equals(current.id()) ? summary : safe(unit.summary()))
                .filter(item -> !item.isBlank())
                .collect(java.util.stream.Collectors.joining("；"));
        return value.substring(0, Math.min(8_000, value.length()));
    }

    private List<TenderAiGateway.Criterion> criteria(
            List<BidGenerationSnapshot.Requirement> requirements
    ) {
        return requirements.stream().map(item -> new TenderAiGateway.Criterion(
                item.sourceCriterionId(), item.type(), item.title(), item.description(), item.score(),
                item.sourceLocator(), item.sourceExcerpt())).toList();
    }

    private TenderAiGateway.FrozenDictionary dictionary(BidGenerationSnapshot snapshot) {
        List<TenderAiGateway.Metric> metrics = new ArrayList<>();
        List<TenderAiGateway.Term> terms = new ArrayList<>();
        List<TenderAiGateway.FixedFact> facts = new ArrayList<>();
        snapshot.facts().forEach(fact -> {
            if ("METRIC".equals(fact.type())) {
                metrics.add(new TenderAiGateway.Metric(fact.name(), fact.value(), fact.sourceLocator()));
            } else if ("TERM".equals(fact.type())) {
                terms.add(new TenderAiGateway.Term(fact.value(), fact.forbiddenValues()));
            } else {
                facts.add(new TenderAiGateway.FixedFact(fact.name(), fact.value(), fact.sourceLocator()));
            }
        });
        return new TenderAiGateway.FrozenDictionary(metrics, terms, facts);
    }

    private String commitmentRegistry(
            BidGenerationSnapshot snapshot, BidGenerationUnit unit, List<BidGenerationUnit> allUnits
    ) {
        List<BidGenerationUnit> laneUnits = allUnits.stream()
                .filter(candidate -> sameRoot(snapshot, unit.chapterId(), candidate.chapterId()))
                .toList();
        String ledger = BidContentQualityGuard.globalCommitmentLedger(snapshot, laneUnits, unit);
        String value = ledger.isBlank() ? snapshot.commitmentRegistry()
                : snapshot.commitmentRegistry() + "\n\n【本一级章节不可改写承诺台账】\n" + ledger;
        return limit(value, MAX_COMMITMENT_CHARACTERS);
    }

    private List<TenderAiGateway.SourceSegment> evidence(
            BidGenerationSnapshot snapshot,
            BidGenerationSnapshot.Outline node,
            List<BidGenerationSnapshot.Requirement> requirements
    ) {
        List<TenderAiGateway.SourceSegment> result = new ArrayList<>();
        String query = node.title() + " " + node.taskBrief() + " "
                + String.join(" ", node.mustKeywords()) + " "
                + requirements.stream().map(BidGenerationSnapshot.Requirement::title)
                .collect(java.util.stream.Collectors.joining(" "));
        List<BidGenerationSnapshot.ReferenceChunk> templates = snapshot.referenceChunks().stream()
                .filter(chunk -> "TEMPLATE".equals(chunk.category())).toList();
        referenceRetriever.retrieve(
                query, templates, MAX_REFERENCE_CHUNKS, MAX_REFERENCE_CHARACTERS
        ).forEach(chunk -> result.add(
                new TenderAiGateway.SourceSegment(
                        "TEMPLATE_REFERENCE",
                        chunk.assetName() + " / " + chunk.heading() + " / " + chunk.sourceLocator(),
                        chunk.content())));
        return List.copyOf(result);
    }

    private String previousSummary(BidGenerationSnapshot snapshot, BidGenerationUnit current,
                                   List<BidGenerationUnit> units) {
        BidGenerationSnapshot.Outline currentNode = outlineForChapter(snapshot, current.chapterId());
        List<String> sameChapter = units.stream()
                .filter(unit -> unit.chapterId().equals(current.chapterId())
                        && unit.unitIndex() < current.unitIndex())
                .sorted(Comparator.comparingInt(BidGenerationUnit::unitIndex))
                .map(BidGenerationUnit::summary)
                .filter(summary -> !safe(summary).isBlank())
                .toList();
        String value = recentSummaries(sameChapter);
        if (value.isBlank()) {
            BidGenerationSnapshot.Outline previous = snapshot.outline().stream()
                    .filter(node -> node.chapterId() != null
                            && node.sortOrder() < currentNode.sortOrder())
                    .filter(node -> sameRoot(snapshot, current.chapterId(), node.chapterId()))
                    .max(Comparator.comparingInt(BidGenerationSnapshot.Outline::sortOrder))
                    .orElse(null);
            if (previous != null) {
                List<String> summaries = units.stream()
                        .filter(unit -> previous.chapterId().equals(unit.chapterId()))
                        .sorted(Comparator.comparingInt(BidGenerationUnit::unitIndex))
                        .map(BidGenerationUnit::summary)
                        .filter(summary -> !safe(summary).isBlank())
                        .toList();
                value = recentSummaries(summaries);
            }
        }
        return limit(value, MAX_PREVIOUS_SUMMARY_CHARACTERS);
    }

    private String nextBrief(BidGenerationSnapshot snapshot, BidGenerationSnapshot.Outline current) {
        return snapshot.outline().stream().filter(node -> node.chapterId() != null
                        && node.sortOrder() > current.sortOrder())
                .filter(node -> sameRoot(snapshot, current.chapterId(), node.chapterId()))
                .min(Comparator.comparingInt(BidGenerationSnapshot.Outline::sortOrder))
                .map(BidGenerationSnapshot.Outline::taskBrief).orElse("");
    }

    private String recentSummaries(List<String> summaries) {
        int start = Math.max(0, summaries.size() - 3);
        return summaries.subList(start, summaries.size()).stream()
                .collect(java.util.stream.Collectors.joining("；"));
    }

    private boolean sameRoot(
            BidGenerationSnapshot snapshot,
            String leftChapterId,
            String rightChapterId
    ) {
        return rootId(snapshot, leftChapterId).equals(rootId(snapshot, rightChapterId));
    }

    private String rootId(BidGenerationSnapshot snapshot, String chapterId) {
        Map<String, BidGenerationSnapshot.Outline> bySourceId = new HashMap<>();
        snapshot.outline().forEach(node -> bySourceId.put(node.sourceOutlineId(), node));
        BidGenerationSnapshot.Outline current = outlineForChapter(snapshot, chapterId);
        Set<String> visited = new HashSet<>();
        while (current.level() > 1 && visited.add(current.sourceOutlineId())) {
            current = bySourceId.get(current.parentSourceOutlineId());
            if (current == null) {
                throw new IllegalStateException("快照目录父子关系不完整");
            }
        }
        return current.sourceOutlineId();
    }

    private String limit(String value, int maximum) {
        String normalized = safe(value);
        return normalized.substring(0, Math.min(maximum, normalized.length()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
