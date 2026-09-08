// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BidProductionRulesTest {

    @Test
    void effectiveCriteriaReturnsOnlyTheTwoInterpretationObjects() {
        BidWorkspace.Criterion overview = criterion(
                "overview", "PROJECT_OVERVIEW", "项目概述", "建设统一技术平台", 0);
        BidWorkspace.Criterion scoring = criterion(
                "scoring", "TECHNICAL_SCORING", "技术部分评分要求", "实施方案30分", 1);
        BidWorkspace workspace = workspace(List.of(
                overview,
                criterion("legacy", "FACT", "旧事实", "不应进入新流程", 2),
                scoring
        ));

        List<BidWorkspace.Criterion> effective = BidProductionRules.effectiveCriteria(workspace);

        assertThat(effective).extracting(BidWorkspace.Criterion::id)
                .containsExactly("overview", "scoring");
    }

    @Test
    void completeInterpretationRequiresOneNonBlankObjectOfEachType() {
        List<BidWorkspace.Criterion> complete = List.of(
                criterion("overview", "PROJECT_OVERVIEW", "项目概述", "项目内容", 0),
                criterion("scoring", "TECHNICAL_SCORING", "技术部分评分要求", "技术评分", 1)
        );

        assertThat(BidProductionRules.hasCompleteInterpretation(complete)).isTrue();
        assertThat(BidProductionRules.hasCompleteInterpretation(complete.subList(0, 1))).isFalse();
    }

    @Test
    void freezeRejectsLegacyInterpretationItems() {
        List<BidWorkspace.Criterion> legacy = List.of(
                criterion("scoring", "SCORING", "实施方案", "完整得20分", 0));

        assertThatThrownBy(() -> BidProductionRules.validateInterpretationFreeze(legacy))
                .hasMessageContaining("项目概述和技术部分评分要求");
    }

    @Test
    void aiConflictIssueRequiresTheForbiddenValueInTheReferencedChapter() {
        BidWorkspace.Chapter chapter = new BidWorkspace.Chapter(
                "chapter-1", "node-1", "实施方案", "正文采用统一配置口径。", "DONE", null, 0
        );
        BidProductionState.FrozenFact fact = new BidProductionState.FrozenFact(
                "fact-1", "FIXED_FACT", "前置代理服务器配置", "8核/64GB/300GB",
                "人工冲突处理", List.of("16G", "16GB"), 0
        );

        assertThat(BidProductionRules.aiIssueHasForbiddenValueEvidence(
                "chapter-1", "存在引用16GB的风险", "删除16GB", List.of(chapter), List.of(fact)
        )).isFalse();

        BidWorkspace.Chapter conflicting = new BidWorkspace.Chapter(
                "chapter-1", "node-1", "实施方案", "配置为8C/16GB/300GB。", "DONE", null, 0
        );
        assertThat(BidProductionRules.aiIssueHasForbiddenValueEvidence(
                "chapter-1", "出现16GB冲突值", "改为64GB", List.of(conflicting), List.of(fact)
        )).isTrue();
    }

    @Test
    void aiIssueWithoutAForbiddenValueIsPreserved() {
        assertThat(BidProductionRules.aiIssueHasForbiddenValueEvidence(
                "chapter-1", "评分点尚未覆盖", "补充响应", List.of(), List.of()
        )).isTrue();
    }

    @Test
    void visibleInternalIdentifiersAreRejectedButHtmlMetadataIsIgnored() {
        String uuid = "85f1f06b-2295-4bc0-8889-87a7e15d18da";
        List<BidWorkspace.Chapter> chapters = List.of(
                new BidWorkspace.Chapter(
                        "chapter-1", "node-1", "实施方案",
                        "<p>依据条款" + uuid + "执行，biddingMode: BLIND。</p>",
                        "DONE", null, 0
                ),
                new BidWorkspace.Chapter(
                        "chapter-2", "node-2", "保障方案",
                        "<p data-source-refs='[\"" + uuid + "\"]'>正文没有内部标识。</p>",
                        "DONE", null, 0
                )
        );

        assertThat(BidProductionRules.findInternalContentLeaks(chapters))
                .extracting(BidProductionRules.ContentLeak::code)
                .containsExactly("INTERNAL_IDENTIFIER_LEAK", "INTERNAL_FIELD_LEAK");
        assertThat(BidProductionRules.findInternalContentLeaks(chapters))
                .extracting(BidProductionRules.ContentLeak::chapterId)
                .containsOnly("chapter-1");
    }

    private BidWorkspace.Criterion criterion(
            String id, String type, String title, String description, int order
    ) {
        return new BidWorkspace.Criterion(
                id, type, title, description, null, "", "", "TECHNICAL",
                "HIGH", order, false
        );
    }

    private BidWorkspace workspace(List<BidWorkspace.Criterion> criteria) {
        BidProductionState production = new BidProductionState(
                "REVIEW", 0, null, "DRAFT", 0, null, "DRAFT", 0, null, null,
                List.of(), List.of(), List.of(), List.of(), null
        );
        return new BidWorkspace(
                null, null, criteria, List.of(), List.of(), null, null,
                List.of(), List.of(), production
        );
    }
}
