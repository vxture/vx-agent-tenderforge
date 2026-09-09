// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

/**
 * 本产品上报的计量指标，逐个对应平台指标登记表里的键。
 *
 * <p><strong>这里是源码字面量，不是配置。</strong>指标名必须命中平台登记表，
 * 而登记是一次人工动作；把它做成可配置项意味着改一行环境变量就能把用量
 * 报到一个不存在的键上——平台会照收，只是那些数字再也不会出现在任何账单里，
 * 而且不报错。
 *
 * <p><strong>这里没有 token 指标，是刻意的。</strong>推理用量由 Atlas 作为唯一
 * 入口计量；本产品再报一次等于同一次推理被记两遍。产品该报的是自己的
 * <em>业务单元</em>——一次正文生成、一份成果文档——那些东西 Atlas 看不见。
 */
public enum UsageMetric {

    /**
     * 一次标书正文生成任务。
     *
     * <p>计的是<strong>任务</strong>而不是请求：正文生成有重入保护，
     * 用户连点三次只会产生一个任务，也就只该计一次。
     */
    BID_GENERATIONS("tenderforge.bid.generations"),

    /** 一份导出的成果文档。每个 export 行一次，重导一版算新的一次。 */
    DOCUMENT_EXPORTS("tenderforge.document.exports");

    private final String key;

    UsageMetric(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /**
     * 幂等键：指标名 + 被计量那个东西自己的标识。
     *
     * <p>刻意不用随机 UUID。随机键让「同一件事被上报两次」变得不可检测——
     * 而重试、重入、以及冲洗任务的重跑都会制造这种情况。用业务标识做键，
     * 重放天然是无操作，平台答 {@code replayed: true}，两侧对得上账。
     */
    public String idempotencyKeyFor(String entityId) {
        return key + ":" + entityId;
    }
}
