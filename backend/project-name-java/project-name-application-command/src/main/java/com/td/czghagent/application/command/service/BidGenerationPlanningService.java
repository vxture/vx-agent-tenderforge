// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidReferenceChunk;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BidGenerationPlanningService {
    static final String PROMPT_VERSION = "chapter-semantic-v6";
    private static final int MIN_CHARACTERS_PER_PAGE = 700;
    private static final int MAX_CHARACTERS_PER_PAGE = 1_100;

    private final BidRepository bidRepository;
    private final BidProductionRepository productionRepository;
    private final int charactersPerPage;

    public BidGenerationPlanningService(BidRepository bidRepository,
                                        BidProductionRepository productionRepository,
                                        @Value("${app.tender.generation.characters-per-page:700}")
                                        int charactersPerPage) {
        if (charactersPerPage < MIN_CHARACTERS_PER_PAGE
                || charactersPerPage > MAX_CHARACTERS_PER_PAGE) {
            throw new IllegalArgumentException(
                    "每页正文字符预算必须在700至1100之间");
        }
        this.bidRepository = bidRepository;
        this.productionRepository = productionRepository;
        this.charactersPerPage = charactersPerPage;
    }

    /**
     * 在一个事务内创建任务、不可变快照和全部写作单元。
     *
     * <p><b>Preconditions:</b> 解读和目录均已冻结。</p>
     * <p><b>Side Effects:</b> 创建任务、快照、单元并将标书置为生成中。</p>
     * <p><b>Error Semantics:</b> 任一写入失败时整体回滚。</p>
     */
    @Transactional
    public Launch create(BidWorkspace workspace) {
        List<BidReferenceChunk> chunks = bidRepository.listAssetChunks(workspace.selectedAssetIds())
                .stream().filter(chunk -> "TEMPLATE".equals(chunk.category())).toList();
        String snapshotHash = BidProductionRules.generationSnapshotHash(
                workspace, chunks, PROMPT_VERSION);
        List<UnitSeed> seeds = unitSeeds(workspace);
        String taskId = bidRepository.createGenerationTask(workspace.bid().id(), seeds.size());
        List<BidProductionRepository.GenerationUnitPlan> plans = seeds.stream()
                .map(seed -> plan(taskId, snapshotHash, seed)).toList();
        String snapshotId = productionRepository.createGenerationSnapshot(
                taskId, workspace.bid().ownerId(), workspace, snapshotHash,
                "",
                writingBible(workspace), termRegistry(workspace), commitmentRegistry(workspace),
                PROMPT_VERSION, chunks, plans);
        productionRepository.appendEvent(
                taskId, workspace.bid().id(), null, "TASK_CREATED",
                "正文生成任务已创建，共 " + plans.size() + " 个分段");
        return new Launch(taskId, snapshotId, snapshotHash);
    }

    private List<UnitSeed> unitSeeds(BidWorkspace workspace) {
        List<UnitSeed> result = new ArrayList<>();
        for (Map.Entry<String, Integer> chapterBudget
                : BidChapterBudgetAllocator.allocate(workspace, charactersPerPage).entrySet()) {
            List<BidSemanticUnitPlanner.SemanticUnit> units =
                    BidSemanticUnitPlanner.plan(chapterBudget.getValue());
            units.forEach(unit -> result.add(new UnitSeed(
                    chapterBudget.getKey(), unit.index(), units.size(),
                    unit.role(), unit.characterBudget())));
        }
        return result;
    }

    private BidProductionRepository.GenerationUnitPlan plan(
            String taskId, String snapshotHash, UnitSeed seed
    ) {
        String unitId = UUID.randomUUID().toString();
        String idempotencyKey = String.join(":", taskId, seed.chapterId(),
                String.valueOf(seed.index()), PROMPT_VERSION, snapshotHash);
        return new BidProductionRepository.GenerationUnitPlan(
                unitId, seed.chapterId(), seed.index(),
                seed.role(),
                seed.wordBudget(), idempotencyKey);
    }

    private String writingBible(BidWorkspace workspace) {
        StringBuilder value = new StringBuilder();
        value.append("全文主题：").append(workspace.bid().title()).append('\n');
        value.append("写作范围：仅编写技术投标文件，不生成商务内容。\n");
        value.append("事实边界：招标事实必须可追溯到冻结要求；不得虚构投标人身份、人员、案例、资质、"
                + "证书和既有产品能力；允许提出不改变冻结事实的工程架构、模块关系、实施方法和验证方案。\n");
        value.append("表达要求：术语一致、承诺一致、章节衔接清晰；工程方案必须说明选择理由、"
                + "执行动作、交付物和验证方式，不得将设计方案伪装为招标原文。\n");
        value.append("专业文风：采用技术负责人面向评审专家的方案语言，先给判断和做法，再说明依据、"
                + "执行动作、责任角色、输入输出及验收方式；没有冻结依据时不得虚构参数。\n");
        value.append("行文节奏：长短句自然组合，一个段落只承担一个明确作用；避免每节重复项目背景、"
                + "总体目标、建设意义和总结，不以同义句填充篇幅。\n");
        value.append("禁用套话：避免连续使用“通过……实现……确保……”，删除“全面赋能、意义重大、"
                + "形成完善闭环、全面提升”等不能提供新增技术信息的口号。\n");
        value.append("内容约束：不生成流程图；不得使用“对应段落”等编制过程说明，"
                + "需要说明追踪关系时写“对应要求：”并列明具体要求。\n");
        if ("BLIND".equals(workspace.bid().biddingMode())) {
            value.append("暗标约束：不得出现投标人名称、标识、人员身份或其他可识别信息。\n");
        }
        if (!workspace.production().frozenFacts().isEmpty()) {
            value.append("冻结事实基准：");
            workspace.production().frozenFacts().stream()
                    .filter(fact -> !"TERM".equals(fact.type()))
                    .forEach(fact -> value.append(fact.name()).append('=')
                            .append(fact.value()).append('；'));
            value.append('\n');
        }
        value.append("目录主线：");
        workspace.outline().stream().filter(node -> node.level() <= 2)
                .sorted(Comparator.comparingInt(BidWorkspace.OutlineNode::sortOrder))
                .forEach(node -> value.append(node.title()).append("；"));
        return value.substring(0, Math.min(3_600, value.length()));
    }

    private String termRegistry(BidWorkspace workspace) {
        Set<String> terms = new LinkedHashSet<>();
        workspace.production().frozenFacts().stream()
                .filter(fact -> "TERM".equals(fact.type()))
                .forEach(fact -> terms.add(fact.name() + "=" + fact.value()));
        return String.join("\n", terms);
    }

    private String commitmentRegistry(BidWorkspace workspace) {
        return "全局承诺必须以冻结事实字典和当前章节的招标要求为准；"
                + "没有来源依据不得新增工期、服务时限、人员数量、性能指标或证明材料承诺。"
                + "当要求同时包含物理隔离和互联网出口时，必须统一说明安全域边界："
                + "核心计算与数据区保持物理隔离，互联网出口设置在隔离区外的受控前置接入区，"
                + "仅通过前置代理、访问白名单、安全防护和全量审计提供受控访问，"
                + "不得写成核心计算或数据区直接连接互联网。";
    }

    private record UnitSeed(
            String chapterId, int index, int count, String role, int wordBudget
    ) {
    }

    public record Launch(String taskId, String snapshotId, String snapshotHash) {
    }
}
