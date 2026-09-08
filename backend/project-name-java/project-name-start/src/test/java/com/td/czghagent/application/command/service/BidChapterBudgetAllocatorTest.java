package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidWorkspace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BidChapterBudgetAllocatorTest {
    @Test
    void splitsOneTopLevelPageAcrossMultipleLeafChapters() {
        BidWorkspace.OutlineNode root = node("root", null, 1, 1, 0);
        BidWorkspace.OutlineNode section = node("section", "root", 2, 0, 1);
        BidWorkspace.OutlineNode leaf1 = node("leaf-1", "section", 3, 0, 2);
        BidWorkspace.OutlineNode leaf2 = node("leaf-2", "section", 3, 0, 3);
        BidWorkspace.OutlineNode leaf3 = node("leaf-3", "section", 3, 0, 4);
        BidWorkspace workspace = workspace(
                List.of(root, section, leaf1, leaf2, leaf3),
                List.of(chapter("chapter-1", "leaf-1"), chapter("chapter-2", "leaf-2"),
                        chapter("chapter-3", "leaf-3")));

        Map<String, Integer> budgets = BidChapterBudgetAllocator.allocate(workspace, 875);

        assertThat(budgets).containsExactly(
                Map.entry("chapter-1", 292),
                Map.entry("chapter-2", 292),
                Map.entry("chapter-3", 291));
        assertThat(budgets.values()).allMatch(value -> value < 875);
    }

    @Test
    void supportsLegacyLeafBudgetsUntilTheOutlineIsSavedAgain() {
        BidWorkspace.OutlineNode root = node("root", null, 1, 0, 0);
        BidWorkspace.OutlineNode section = node("section", "root", 2, 0, 1);
        BidWorkspace.OutlineNode leaf1 = node("leaf-1", "section", 3, 1, 2);
        BidWorkspace.OutlineNode leaf2 = node("leaf-2", "section", 3, 2, 3);
        BidWorkspace workspace = workspace(
                List.of(root, section, leaf1, leaf2),
                List.of(chapter("chapter-1", "leaf-1"), chapter("chapter-2", "leaf-2")));

        Map<String, Integer> budgets = BidChapterBudgetAllocator.allocate(workspace, 875);

        assertThat(budgets.values()).containsExactly(438, 437);
    }

    @Test
    void givesScoringMappedChapterMoreWritingBudget() {
        BidWorkspace.OutlineNode root = node("root", null, 1, 4, 0);
        BidWorkspace.OutlineNode section = node("section", "root", 2, 0, 1);
        BidWorkspace.OutlineNode scored = new BidWorkspace.OutlineNode(
                "leaf-1", "section", 3, "核心技术", 0, 2, 0,
                "重点评分技术方案", List.of("架构", "验证"),
                List.of("SP-001", "SP-002", "SP-003"));
        BidWorkspace.OutlineNode regular = node("leaf-2", "section", 3, 0, 3);
        BidWorkspace workspace = workspace(
                List.of(root, section, scored, regular),
                List.of(chapter("chapter-1", "leaf-1"), chapter("chapter-2", "leaf-2")));

        Map<String, Integer> budgets = BidChapterBudgetAllocator.allocate(workspace, 875);

        assertThat(budgets.get("chapter-1")).isGreaterThan(budgets.get("chapter-2"));
        assertThat(budgets.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(875);
    }

    @Test
    void preservesWholeDocumentBudgetForRealisticHundredPageOutline() {
        List<BidWorkspace.OutlineNode> outline = new ArrayList<>();
        List<BidWorkspace.Chapter> chapters = new ArrayList<>();
        int sortOrder = 0;
        int leafIndex = 0;
        for (int rootIndex = 0; rootIndex < 11; rootIndex++) {
            String rootId = "root-" + rootIndex;
            outline.add(node(rootId, null, 1, rootIndex == 10 ? 10 : 9, sortOrder++));
            for (int sectionIndex = 0; sectionIndex < 4; sectionIndex++) {
                String sectionId = "section-" + rootIndex + "-" + sectionIndex;
                outline.add(node(sectionId, rootId, 2, 0, sortOrder++));
                int leafCount = rootIndex == 0 && sectionIndex < 3 ? 4 : 3;
                for (int index = 0; index < leafCount; index++) {
                    String leafId = "leaf-" + leafIndex;
                    outline.add(node(leafId, sectionId, 3, 0, sortOrder++));
                    chapters.add(chapter("chapter-" + leafIndex, leafId));
                    leafIndex++;
                }
            }
        }

        Map<String, Integer> budgets = BidChapterBudgetAllocator.allocate(
                workspace(outline, chapters), 700);

        assertThat(outline).hasSize(190);
        assertThat(budgets).hasSize(135);
        assertThat(budgets.values()).allMatch(value -> value > 0);
        assertThat(budgets.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(62_300);
    }

    @Test
    void reservesBodySpaceForDenseThreeLevelHeadings() {
        List<BidWorkspace.OutlineNode> outline = new ArrayList<>();
        List<BidWorkspace.Chapter> chapters = new ArrayList<>();
        int sortOrder = 0;
        for (int rootIndex = 0; rootIndex < 5; rootIndex++) {
            String rootId = "root-" + rootIndex;
            outline.add(node(rootId, null, 1, 4, sortOrder++));
            for (int sectionIndex = 0; sectionIndex < 8; sectionIndex++) {
                String sectionId = "section-" + rootIndex + "-" + sectionIndex;
                outline.add(node(sectionId, rootId, 2, 0, sortOrder++));
                for (int leafIndex = 0; leafIndex < 2; leafIndex++) {
                    String leafId = "leaf-" + rootIndex + "-" + sectionIndex + "-" + leafIndex;
                    outline.add(node(leafId, sectionId, 3, 0, sortOrder++));
                    chapters.add(chapter("chapter-" + leafId, leafId));
                }
            }
        }

        Map<String, Integer> budgets = BidChapterBudgetAllocator.allocate(
                workspace(outline, chapters), 700);

        assertThat(outline).hasSize(125);
        assertThat(chapters).hasSize(80);
        assertThat(budgets.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(8_400);
    }

    private BidWorkspace workspace(
            List<BidWorkspace.OutlineNode> outline,
            List<BidWorkspace.Chapter> chapters
    ) {
        return new BidWorkspace(
                null, null, List.of(), outline, chapters, null, null,
                List.of(), List.of(), null);
    }

    private BidWorkspace.OutlineNode node(
            String id, String parentId, int level, int pages, int sortOrder
    ) {
        return new BidWorkspace.OutlineNode(
                id, parentId, level, id, pages, sortOrder, 0,
                "", List.of(), List.of());
    }

    private BidWorkspace.Chapter chapter(String id, String outlineNodeId) {
        return new BidWorkspace.Chapter(
                id, outlineNodeId, id, "", "PENDING", null, 0);
    }
}
