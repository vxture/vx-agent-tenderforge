package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
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
        assertThat(selected).allMatch(item -> !item.sourceExcerpt().isEmpty());
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
}
