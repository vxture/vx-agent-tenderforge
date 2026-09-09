// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.port.UsageConsumeClient;
import com.td.czghagent.domain.repository.UsageBufferRepository.BufferedUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 本地开发用的 consume 替身。
 *
 * <p>它照样把「记下了」这件事说出来，好让整条缓冲—认领—冲洗的链路在空 .env 下
 * 能被完整走一遍——否则缓冲区只会越积越多，而积压和「冲洗坏了」在本地长得一模一样。
 *
 * <p>{@code MOCK_USAGE_GATED=true} 让它答 {@code gated}。这条分支平时永不发生，
 * 却牵着权益缓存的失效——不给它一个本地开关，第一次执行就会是在某个真实用户
 * 用超了配额的时候。
 */
public class MockUsageConsumeClient implements UsageConsumeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockUsageConsumeClient.class);

    private final boolean gated;

    public MockUsageConsumeClient(boolean gated) {
        this.gated = gated;
    }

    @Override
    public Outcome consume(BufferedUsage usage) {
        LOGGER.info("[MOCK consume] workspace={} metric={} amount={} key={} gated={}",
                usage.workspaceId(), usage.metric(), usage.amount(),
                usage.idempotencyKey(), gated);
        return new Outcome(200, gated, false, UUID.randomUUID().toString(),
                gated ? "mock quota pool exhausted" : null);
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
