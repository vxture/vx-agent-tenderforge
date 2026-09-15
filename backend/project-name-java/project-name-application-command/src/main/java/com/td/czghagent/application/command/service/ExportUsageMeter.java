// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.UsageEvent;
import com.td.czghagent.domain.model.UsageMetric;
import com.td.czghagent.domain.port.UsageRecorder;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalLong;

/**
 * 一次 DOCX 导出产生的用量：一份成果文档，加上全文字数超出高水位的那部分。
 *
 * <p>两条导出路径（同步导出、正式排版）都走这里，不各写一遍——正式排版那条此前
 * 一次都没计，正是「各写各的」漏出来的。
 *
 * <p><strong>这是统计维度，不是配额计量。</strong>配额与计费走 token 换算的 credits
 * （推理经 Atlas 计量）；这里报的是 Atlas 看不见的业务量。
 *
 * <p><strong>字数按高水位计。</strong>全文字数 = 各章标题 + 正文的可见字符。只有超过
 * 这份标书已报过的最大值时才报差额，并把水位抬上去。于是累计报出的字数 = 它导出过的
 * 最大全文字数：反复导出不虚增，改短不回退，改长只补差额。
 *
 * <p>整个方法在一个事务里：同步导出并入导出事务；正式排版没有外层事务，这里自己开一个。
 * 抬水位与写缓冲一起提交或一起回滚——分开提交的话，缓冲写失败时水位已经抬过去，
 * 那段字数再也不会被报。
 */
@Component
class ExportUsageMeter {

    private final BidRepository bidRepository;
    private final UsageRecorder recorder;

    ExportUsageMeter(BidRepository bidRepository, UsageRecorder recorder) {
        this.bidRepository = bidRepository;
        this.recorder = recorder;
    }

    @Transactional
    void record(BidDocument bid, List<BidWorkspace.Chapter> chapters,
                String exportId, String endUserId) {
        recorder.record(UsageEvent.of(
                bid.tenant(), UsageMetric.DOCUMENT_EXPORTS, exportId, endUserId));
        long characters = documentCharacters(chapters);
        if (characters <= 0) {
            return;
        }
        OptionalLong previous = bidRepository.raiseMeteredCharacters(bid.id(), characters);
        if (previous.isEmpty()) {
            return;
        }
        // 键 = 标书 + 新水位。水位只增不减，同一个值不会被抬到第二次，键天然不重复；
        // 同一次抬升被重放时撞的也正是这个键。
        recorder.record(UsageEvent.of(
                bid.tenant(), UsageMetric.DOCUMENT_CHARACTERS, bid.id() + ":" + characters,
                endUserId, characters - previous.getAsLong()));
    }

    /**
     * 全文字数：各章标题 + 正文的可见字符，与界面「全文正文共 N 字」同一套剥离规则。
     *
     * <p>目标是与 docx 字数基本对应。封面、目录与没有落到章节上的上级标题不计——
     * 那是可接受的少量偏差，不值得为它把导出排版的逻辑复制一份到计量里。
     */
    static long documentCharacters(List<BidWorkspace.Chapter> chapters) {
        return chapters.stream()
                .mapToLong(chapter -> BidContentQualityGuard.visibleCharacterCount(chapter.title())
                        + (long) BidContentQualityGuard.visibleCharacterCount(chapter.content()))
                .sum();
    }
}
