// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.domain.port.UsageConsumeClient;
import com.td.czghagent.domain.repository.UsageBufferRepository;
import com.td.czghagent.domain.port.UsageConsumeClient;
import com.td.czghagent.domain.repository.UsageBufferRepository.BufferedUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 冲洗任务：把缓冲区里的用量报给平台。
 *
 * <p>它是 C2 失效链的其中一环。权益缓存有 45 秒 TTL，而「刚用超了配额」到
 * 「界面显示用超了」之间的那几十秒，正好是用户盯着屏幕想弄明白发生了什么的时候。
 * consume 答 {@code gated} 时立刻驱逐该空间的权益缓存，把那个窗口压到一次点击。
 */
public class UsageFlushJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsageFlushJob.class);

    /**
     * 认领租约。
     *
     * <p>比一次冲洗的最坏耗时长得多：租约过短会让一批还在飞的用量被另一个节点
     * 抢走重报。重报本身是安全的（幂等键在），但它会把两个节点锁在互相抢同一批行的
     * 循环里，而缓冲区看起来永远在冲洗、永远冲不完。
     */
    private static final Duration LEASE = Duration.ofMinutes(5);

    /** 对账窗口：报过的行留这么久再删。 */
    private static final Duration RETENTION = Duration.ofDays(14);

    private final UsageBufferRepository buffer;
    private final UsageConsumeClient consumeClient;
    private final EntitlementResolver entitlements;
    private final int batchSize;
    private final Clock clock;

    public UsageFlushJob(UsageBufferRepository buffer, UsageConsumeClient consumeClient,
                         EntitlementResolver entitlements, int batchSize, Clock clock) {
        this.buffer = buffer;
        this.consumeClient = consumeClient;
        this.entitlements = entitlements;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    /**
     * 冲一轮。
     *
     * <p><strong>永不抛出</strong>：它跑在调度线程上，一次未捕获的异常在某些调度器
     * 配置下会让这个任务再也不被执行——而表现是用量安静地停止上报。
     */
    public Summary flushOnce() {
        try {
            return drain();
        } catch (RuntimeException exception) {
            LOGGER.error("Usage flush round failed", exception);
            return new Summary(0, 0, 0, 0, 0);
        }
    }

    private Summary drain() {
        LocalDateTime now = LocalDateTime.now(clock);
        String claimToken = UUID.randomUUID().toString();
        List<BufferedUsage> claimed =
                buffer.claim(claimToken, batchSize, now, now.minus(LEASE));
        if (claimed.isEmpty()) {
            return new Summary(0, 0, 0, 0, 0);
        }

        List<String> recorded = new ArrayList<>();
        List<String> retry = new ArrayList<>();
        List<String> gatedWorkspaces = new ArrayList<>();
        int replayed = 0;
        String lastFailure = null;

        for (BufferedUsage usage : claimed) {
            UsageConsumeClient.Outcome outcome = consumeClient.consume(usage);
            if (!outcome.recorded()) {
                retry.add(usage.idempotencyKey());
                lastFailure = "HTTP " + outcome.status()
                        + (outcome.reason() == null ? "" : ": " + outcome.reason());
                continue;
            }
            recorded.add(usage.idempotencyKey());
            if (outcome.replayed()) {
                replayed++;
            }
            if (outcome.gated() && !gatedWorkspaces.contains(usage.workspaceId())) {
                gatedWorkspaces.add(usage.workspaceId());
            }
        }

        buffer.markFlushed(recorded, LocalDateTime.now(clock));
        buffer.release(retry, lastFailure);
        // 驱逐放在标记之后：先把账记清楚，再去动缓存。
        // 反过来的话，一次驱逐时的异常会让已经报成功的行留在缓冲区里被重报。
        gatedWorkspaces.forEach(entitlements::invalidate);

        if (!retry.isEmpty()) {
            LOGGER.warn("Usage flush: {} recorded, {} left buffered for retry ({})",
                    recorded.size(), retry.size(), lastFailure);
        }
        return new Summary(claimed.size(), recorded.size(),
                gatedWorkspaces.size(), replayed, retry.size());
    }

    /** 清掉过了对账窗口的已冲洗行。与冲洗分开，因为它跑挂了不该影响上报。 */
    public int purge() {
        try {
            return buffer.purgeFlushedBefore(LocalDateTime.now(clock).minus(RETENTION));
        } catch (RuntimeException exception) {
            LOGGER.warn("Usage buffer purge failed", exception);
            return 0;
        }
    }

    public boolean usesMockConsume() {
        return consumeClient.isMock();
    }

    /**
     * @param gated    子集：已记下，但配额没覆盖住
     * @param replayed 子集：幂等重放
     */
    public record Summary(int claimed, int recorded, int gated, int replayed, int retried) {
    }
}
