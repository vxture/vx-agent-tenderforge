// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.repository.UsageBufferRepository.BufferedUsage;

/**
 * {@code POST /usage/consume} 的调用口。
 *
 * <p><strong>consume 永远答 200</strong>，配额没覆盖住也是 200。这一点是整份契约里
 * 最容易接反的地方：把 {@code gated} 当成「拒绝」去处理，就等于让产品替平台
 * 裁决了一次配额——而平台的语义是<strong>记录，不裁决</strong>。
 * 非 200 只意味着「这一条还没被记下」，行留在缓冲区里等下一轮。
 */
public interface UsageConsumeClient {

    Outcome consume(BufferedUsage usage);

    /** 是否替身。冲洗任务据此在日志里说实话，{@code /api/status} 据此标注降级。 */
    boolean isMock();

    /**
     * 一次 consume 的结果。
     *
     * @param status   HTTP 状态码；只有 200 算「已记下」
     * @param gated    平台记下了，但配额没覆盖住。这是<strong>信息</strong>，
     *                 不是指令——它唯一该触发的动作是刷新权益缓存，
     *                 好让界面上的余量在一次点击之内追上，而不是等一个 TTL
     * @param replayed 幂等重放。平台会连同<strong>原始</strong> event_id 一起答回来，
     *                 这正是两侧能对上账的原因
     * @param eventId  平台侧的事件标识
     * @param reason   gated 时的原因，供日志与界面提示
     */
    record Outcome(int status, boolean gated, boolean replayed, String eventId, String reason) {

        public boolean recorded() {
            return status == 200;
        }
    }
}
