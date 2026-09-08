// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** Creates one reusable technical blueprint for every level-two outline branch. */
@Service
class BidBranchBlueprintService {
    static final String PROMPT_VERSION = "branch-blueprint-v1";

    private final BidProductionRepository repository;
    private final BidRelevantContextSelector contextSelector;
    private final BidAiExecutionService aiExecutionService;

    BidBranchBlueprintService(
            BidProductionRepository repository,
            BidRelevantContextSelector contextSelector,
            BidAiExecutionService aiExecutionService) {
        this.repository = repository;
        this.contextSelector = contextSelector;
        this.aiExecutionService = aiExecutionService;
    }

    /**
     * Resolve an immutable branch blueprint, generating it only when absent.
     *
     * <p><b>Preconditions:</b> snapshot contains a complete three-level outline.</p>
     * <p><b>Side Effects:</b> may call the quality model and persist one validated blueprint.</p>
     * <p><b>Error Semantics:</b> model or persistence failures keep the current unit retryable.</p>
     */
    TenderAiGateway.BranchBlueprint resolve(
            BidGenerationSnapshot snapshot, BidGenerationUnit unit) {
        BidGenerationSnapshot.Outline leaf = outlineForChapter(snapshot, unit.chapterId());
        BidGenerationSnapshot.Outline branch = outlineById(
                snapshot, leaf.parentSourceOutlineId(), 2);
        TenderAiGateway.BranchBlueprintRequest request = request(snapshot, branch);
        String inputHash = aiExecutionService.inputHash(request, PROMPT_VERSION);
        return repository.findBranchBlueprint(snapshot.id(), branch.sourceOutlineId(), inputHash)
                .orElseGet(() -> generate(snapshot, branch, request, inputHash));
    }

    private TenderAiGateway.BranchBlueprint generate(
            BidGenerationSnapshot snapshot, BidGenerationSnapshot.Outline branch,
            TenderAiGateway.BranchBlueprintRequest request, String inputHash) {
        TenderAiGateway.BranchBlueprint blueprint = aiExecutionService.planBranchBlueprint(
                snapshot.bidId(), snapshot.taskId(), snapshot.id(), PROMPT_VERSION, request);
        repository.saveBranchBlueprint(
                snapshot.id(), branch.sourceOutlineId(), inputHash, blueprint,
                aiExecutionService.inputHash(blueprint, PROMPT_VERSION), PROMPT_VERSION);
        return repository.findBranchBlueprint(snapshot.id(), branch.sourceOutlineId(), inputHash)
                .orElse(blueprint);
    }

    private TenderAiGateway.BranchBlueprintRequest request(
            BidGenerationSnapshot snapshot, BidGenerationSnapshot.Outline branch) {
        List<BidGenerationSnapshot.Requirement> requirements = contextSelector.select(snapshot, branch);
        return new TenderAiGateway.BranchBlueprintRequest(
                snapshot.id() + ":" + branch.sourceOutlineId(), snapshot.bidTitle(), snapshot.biddingMode(),
                snapshot.solutionContract(), branchContract(branch), childContracts(snapshot, branch),
                criteria(requirements), dictionary(snapshot), snapshot.writingBible(),
                snapshot.termRegistry(), snapshot.commitmentRegistry());
    }

    private TenderAiGateway.BranchContract branchContract(BidGenerationSnapshot.Outline branch) {
        return new TenderAiGateway.BranchContract(
                branch.sourceOutlineId(), branch.title(), branch.taskBrief(), branch.mustKeywords());
    }

    private List<TenderAiGateway.ChapterContract> childContracts(
            BidGenerationSnapshot snapshot, BidGenerationSnapshot.Outline branch) {
        return snapshot.outline().stream()
                .filter(node -> node.level() == 3)
                .filter(node -> branch.sourceOutlineId().equals(node.parentSourceOutlineId()))
                .sorted(Comparator.comparingInt(BidGenerationSnapshot.Outline::sortOrder))
                .map(node -> new TenderAiGateway.ChapterContract(
                        node.chapterId(), node.title(), node.plannedPages(), node.taskBrief(),
                        node.mustKeywords(), node.scoringPointIds()))
                .toList();
    }

    private List<TenderAiGateway.Criterion> criteria(
            List<BidGenerationSnapshot.Requirement> requirements) {
        return requirements.stream().map(item -> new TenderAiGateway.Criterion(
                item.sourceCriterionId(), item.type(), item.title(), item.description(), item.score(),
                item.sourceLocator(), item.sourceExcerpt())).toList();
    }

    private TenderAiGateway.FrozenDictionary dictionary(BidGenerationSnapshot snapshot) {
        return new TenderAiGateway.FrozenDictionary(
                snapshot.facts().stream().filter(fact -> "METRIC".equals(fact.type()))
                        .map(fact -> new TenderAiGateway.Metric(
                                fact.name(), fact.value(), fact.sourceLocator())).toList(),
                snapshot.facts().stream().filter(fact -> "TERM".equals(fact.type()))
                        .map(fact -> new TenderAiGateway.Term(
                                fact.value(), fact.forbiddenValues())).toList(),
                snapshot.facts().stream().filter(fact -> !"METRIC".equals(fact.type())
                                && !"TERM".equals(fact.type()))
                        .map(fact -> new TenderAiGateway.FixedFact(
                                fact.name(), fact.value(), fact.sourceLocator())).toList());
    }

    private BidGenerationSnapshot.Outline outlineForChapter(
            BidGenerationSnapshot snapshot, String chapterId) {
        return snapshot.outline().stream().filter(node -> chapterId.equals(node.chapterId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("快照章节契约不存在"));
    }

    private BidGenerationSnapshot.Outline outlineById(
            BidGenerationSnapshot snapshot, String sourceId, int expectedLevel) {
        return snapshot.outline().stream()
                .filter(node -> sourceId.equals(node.sourceOutlineId()) && node.level() == expectedLevel)
                .findFirst().orElseThrow(() -> new IllegalStateException("快照二级目录契约不存在"));
    }
}
