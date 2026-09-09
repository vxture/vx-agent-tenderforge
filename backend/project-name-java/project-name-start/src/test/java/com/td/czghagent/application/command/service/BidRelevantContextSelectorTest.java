package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidRelevantContextSelectorTest {
    @Test
    void selectsOnlyChapterRelevantSectionsFromTwoLongBusinessObjects() {
        String overview = "## 项目背景\n" + "经营决策背景说明。".repeat(320)
                + "\n\n## 算力基础设施\n" + "建设云算力和物理隔离区域。".repeat(80);
        String scoring = "#### 算力基础设施租赁方案\n"
                + "高性能推算、物理隔离和7×24小时应急响应。".repeat(90)
                + "\n\n#### 人员配置\n" + "项目经理和团队证书要求。".repeat(320);
        BidGenerationSnapshot snapshot = snapshot(List.of(
                requirement("overview", "PROJECT_OVERVIEW", overview),
                requirement("scoring", "TECHNICAL_SCORING", scoring)
        ));
        BidGenerationSnapshot.Outline node = new BidGenerationSnapshot.Outline(
                "leaf", "chapter", "PENDING", "section", 3,
                "算力基础设施租赁方案", 0, 2, "编写物理隔离和云算力服务方案",
                List.of("云算力", "物理隔离"), List.of("scoring"));

        List<BidGenerationSnapshot.Requirement> selected =
                new BidRelevantContextSelector().select(snapshot, node);

        assertThat(selected).hasSize(2);
        String selectedOverview = selected.stream()
                .filter(item -> item.type().equals("PROJECT_OVERVIEW"))
                .findFirst().orElseThrow().description();
        String selectedScoring = selected.stream()
                .filter(item -> item.type().equals("TECHNICAL_SCORING"))
                .findFirst().orElseThrow().description();
        assertThat(selectedOverview).contains("算力基础设施").doesNotContain("经营决策背景说明");
        assertThat(selectedScoring).contains("物理隔离").doesNotContain("项目经理和团队证书");
        assertThat(selectedScoring.length()).isLessThan(scoring.length());
    }

    @Test
    void ignoresLegacyScoringMappingsAndSelectsByChapterSemantics() {
        BidGenerationSnapshot snapshot = snapshot(List.of(
                requirement("score-a", "TECHNICAL_SCORING", "算力基础设施得6分"),
                requirement("score-b", "TECHNICAL_SCORING", "培训方案得6分")
        ));
        BidGenerationSnapshot.Outline node = new BidGenerationSnapshot.Outline(
                "leaf", "chapter", "PENDING", "section", 3, "培训方案", 0, 2,
                "培训内容和现场计划", List.of("培训"), List.of("score-a"));

        List<BidGenerationSnapshot.Requirement> selected =
                new BidRelevantContextSelector().select(snapshot, node);

        assertThat(selected).extracting(BidGenerationSnapshot.Requirement::sourceCriterionId)
                .containsExactly("score-b");
    }

    private BidGenerationSnapshot snapshot(
            List<BidGenerationSnapshot.Requirement> requirements
    ) {
        return new BidGenerationSnapshot(
                "snapshot", "task", "bid", "owner", "hash", "测试标书", "BLIND",
                80, 1, 1, "解决方案契约", "", "", "", "chapter-unit-v4",
                LocalDateTime.now(),
                requirements, List.of(), List.of(), List.of());
    }

    private BidGenerationSnapshot.Requirement requirement(
            String id, String type, String description
    ) {
        return new BidGenerationSnapshot.Requirement(
                id, type, type, description, null, description, "招标文件", 0);
    }

    /**
     * 筛选后的条目<strong>保留出处指针、不再带原文副本</strong>。
     *
     * <p>这条曾经断言 {@code sourceExcerpt} 非空，并因此把整个用例挂起过——
     * 当时读代码定不了它是刻意还是回归。现在能定了，依据有三条：
     *
     * <ol>
     *   <li>详细设计 §5.2：技术评分的 {@code description} 由确定性规则
     *       <em>直接用源条款组装</em>，模型不得改写分值与证明材料。也就是说
     *       description 本身就是原文，再带一份 excerpt 是同一段文字的第二份副本。</li>
     *   <li>筛选器保留了 {@code sourceLocator}——出处没有丢，丢的只是重复的正文。</li>
     *   <li>Python 侧对空 excerpt 的处理是<em>整个字段不发</em>，而不是发一个空串；
     *       那是「没有单独原文」的干净表达，不是对脏数据的兼容。</li>
     * </ol>
     *
     * <p>所以这里断言解出来的契约而不是当初的断言。谁要把原文副本加回来，
     * 这条会红——那时需要先回答：模型为什么需要同一段文字的两份拷贝。
     */
    @Test
    void keepsTheLocatorAndDropsTheNowDuplicatedExcerpt() {
        BidGenerationSnapshot snapshot = snapshot(List.of(
                requirement("score-a", "TECHNICAL_SCORING", "算力基础设施得6分")));
        BidGenerationSnapshot.Outline node = new BidGenerationSnapshot.Outline(
                "leaf", "chapter", "PENDING", "section", 3, "算力基础设施", 0, 1,
                "算力和隔离要求", List.of("算力"), List.of());

        List<BidGenerationSnapshot.Requirement> selected =
                new BidRelevantContextSelector().select(snapshot, node);

        assertThat(selected).isNotEmpty();
        assertThat(selected)
                .as("出处指针必须留着，正文才可以标注来源")
                .allMatch(item -> !item.sourceLocator().isBlank());
        assertThat(selected)
                .as("description 已经装着筛选后的原文，excerpt 不再是第二份副本")
                .allMatch(item -> item.sourceExcerpt().isEmpty());
    }
}
