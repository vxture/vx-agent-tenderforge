package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidWorkspace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BidChapterBudgetAllocator {
    private static final int TOC_ENTRIES_PER_PAGE = 32;
    private static final int BODY_HEADINGS_PER_PAGE = 48;

    private BidChapterBudgetAllocator() {
    }

    static Map<String, Integer> allocate(BidWorkspace workspace, int charactersPerPage) {
        Map<String, BidWorkspace.OutlineNode> nodes = new HashMap<>();
        workspace.outline().forEach(node -> nodes.put(node.id(), node));
        List<ChapterNode> chapters = new ArrayList<>();
        for (BidWorkspace.Chapter chapter : workspace.chapters()) {
            BidWorkspace.OutlineNode node = nodes.get(chapter.outlineNodeId());
            if (node == null) {
                throw new IllegalStateException("The chapter outline node does not exist");
            }
            chapters.add(new ChapterNode(chapter.id(), node, root(node, nodes)));
        }
        chapters.sort(Comparator.comparingInt(item -> item.node().sortOrder()));

        Map<String, List<ChapterNode>> chaptersByRoot = new LinkedHashMap<>();
        chapters.forEach(item -> chaptersByRoot
                .computeIfAbsent(item.root().id(), ignored -> new ArrayList<>()).add(item));
        List<List<ChapterNode>> groups = new ArrayList<>(chaptersByRoot.values());
        int totalPlannedPages = groups.stream().mapToInt(BidChapterBudgetAllocator::rootPages).sum();
        if (totalPlannedPages <= 0) {
            throw new IllegalStateException("The level-one page budget must be greater than zero");
        }

        int frontMatterPages = 1 + Math.max(
                1, (workspace.outline().size() + TOC_ENTRIES_PER_PAGE - 1) / TOC_ENTRIES_PER_PAGE);
        int headingReservePages = Math.max(
                1, (workspace.outline().size() + BODY_HEADINGS_PER_PAGE - 1)
                        / BODY_HEADINGS_PER_PAGE);
        int bodyPages = Math.max(
                1, totalPlannedPages - frontMatterPages - headingReservePages);
        int totalCharacters = Math.max(
                chapters.size(), Math.multiplyExact(bodyPages, charactersPerPage));
        int distributableCharacters = totalCharacters - chapters.size();

        Map<String, Integer> result = new LinkedHashMap<>();
        int distributed = 0;
        for (int index = 0; index < groups.size(); index++) {
            List<ChapterNode> group = groups.get(index);
            int weighted = index == groups.size() - 1
                    ? distributableCharacters - distributed
                    : (int) ((long) distributableCharacters * rootPages(group) / totalPlannedPages);
            distributed += weighted;
            allocateRoot(group, group.size() + weighted, result);
        }
        return result;
    }

    private static void allocateRoot(
            List<ChapterNode> chapters,
            int totalCharacters,
            Map<String, Integer> result
    ) {
        int base = totalCharacters / chapters.size();
        int remainder = totalCharacters % chapters.size();
        for (int index = 0; index < chapters.size(); index++) {
            result.put(chapters.get(index).chapterId(), base + (index < remainder ? 1 : 0));
        }
    }

    private static int rootPages(List<ChapterNode> chapters) {
        int pages = chapters.getFirst().root().plannedPages();
        if (pages <= 0) {
            pages = chapters.stream().mapToInt(item -> Math.max(0, item.node().plannedPages())).sum();
        }
        return pages;
    }

    private static BidWorkspace.OutlineNode root(
            BidWorkspace.OutlineNode node,
            Map<String, BidWorkspace.OutlineNode> nodes
    ) {
        BidWorkspace.OutlineNode current = node;
        Set<String> visited = new HashSet<>();
        while (current.level() > 1 && visited.add(current.id())) {
            current = nodes.get(current.parentId());
            if (current == null) {
                throw new IllegalStateException("The outline parent-child relationship is incomplete");
            }
        }
        if (current.level() != 1) {
            throw new IllegalStateException("The chapter cannot be assigned to a level-one node");
        }
        return current;
    }

    private record ChapterNode(
            String chapterId,
            BidWorkspace.OutlineNode node,
            BidWorkspace.OutlineNode root
    ) {
    }
}
