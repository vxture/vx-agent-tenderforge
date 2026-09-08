// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.model.Entitlement;

/**
 * 读取权益（产品接入通则 C2）。
 *
 * <p><strong>短 TTL 缓存 + 失效，不做本地持久化副本。</strong>
 * 落库就有第二份真相，而失效管不到它。
 *
 * <p>失效链必须闭合，由<strong>三种事件</strong>驱逐缓存：
 * C3 下发的 {@code tenant.provisioned} 与 {@code tenant.deprovisioned}，
 * 以及 consume 响应里的 {@code gated: true}（用量侧发现权益变了）。
 * 只做 45 秒 TTL 不接失效链，档位变更会有最长 45 秒的错误答案窗口——
 * 而那正好是用户刚付完钱回来点一下的那几十秒。
 */
public interface EntitlementResolver {

    /**
     * 解析某个工作空间对本产品的权益。
     *
     * <p><strong>永不抛出。</strong>平台不可达时返回
     * {@link Entitlement#none} 而不是异常：把「读不到权益」变成一个错误页面，
     * 等于让平台的一次抖动关掉整个产品；而 fail-closed 的空信封会让界面
     * 优雅降级成「未订阅」，用户看到的是一个可解释的状态。
     */
    Entitlement resolve(String workspaceId);

    /** 驱逐一个工作空间的缓存。失效链的三个触发点都调它。 */
    void invalidate(String workspaceId);

    /** 是否在用替身。{@code /api/status} 用它自报降级。 */
    boolean isMock();
}
