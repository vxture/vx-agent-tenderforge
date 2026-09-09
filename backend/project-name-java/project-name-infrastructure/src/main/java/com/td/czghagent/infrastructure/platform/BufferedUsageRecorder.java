// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.model.UsageEvent;
import com.td.czghagent.domain.port.UsageRecorder;
import com.td.czghagent.domain.repository.UsageBufferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 把用量写进本地缓冲区，由冲洗任务异步上报。
 *
 * <p>写入刻意<strong>不开新事务</strong>，也不用 {@code REQUIRES_NEW}：它要跟着调用方
 * 的事务走。导出那条路径是事务性的，于是「导出行」和「这次导出的用量」同生同死——
 * 事务回滚了就没有这笔账，不需要任何补偿。
 */
@Component
public class BufferedUsageRecorder implements UsageRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BufferedUsageRecorder.class);

    private final UsageBufferRepository buffer;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public BufferedUsageRecorder(UsageBufferRepository buffer) {
        // 系统默认时区，与仓内其余写入一致。混用 UTC 与本地时区会让
        // occurred_at 的排序在两种写入之间错开——那正是本仓已经踩过一次的坑。
        this(buffer, Clock.systemDefaultZone());
    }

    BufferedUsageRecorder(UsageBufferRepository buffer, Clock clock) {
        this.buffer = buffer;
        this.clock = clock;
    }

    @Override
    public void record(UsageEvent event) {
        try {
            if (!shouldReport(event)) {
                return;
            }
            buffer.buffer(event, LocalDateTime.now(clock));
        } catch (RuntimeException exception) {
            // 计量永不失败调用方。一笔报不上去的用量是账，
            // 一份因为计量而失败的导出是丢掉的成果。
            LOGGER.warn("Usage buffering failed for {}", event.idempotencyKey(), exception);
        }
    }

    /**
     * 该不该上报。
     *
     * <p>最要紧的一条是过渡租户：本地账号的 workspace 是
     * {@code local:<用户id>} 这样的占位值，平台那边根本不存在。把它报上去不会报错
     * ——平台照收，然后这些数字落进一个没有主人的空间，既不出现在任何人的账单里，
     * 也污染了对账。<strong>没有真实工作空间就没有可计量的事。</strong>
     */
    private boolean shouldReport(UsageEvent event) {
        String workspaceId = event.workspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            return false;
        }
        if (workspaceId.startsWith(TenantScope.LOCAL_PREFIX)) {
            LOGGER.debug("Skipping usage for placeholder workspace {}", workspaceId);
            return false;
        }
        if (event.amount() <= 0) {
            // 非正数量是调用方的 bug，不是「这次不计量」的表达方式。
            // 静默吞掉会让一整类漏计永远查不出来。
            LOGGER.warn("Refusing non-positive usage amount {} for metric {}",
                    event.amount(), event.metric().key());
            return false;
        }
        return event.idempotencyKey() != null && !event.idempotencyKey().isBlank();
    }
}
