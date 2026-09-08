// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

/**
 * 一个消耗型配额池的<strong>只读视图</strong>（产品接入通则 C2）。
 *
 * <p>「只读」是要点：真正的扣减发生在 {@code POST /usage/consume}，不在这里。
 * 拿这里的 {@code remaining} 去做「够不够」的判断，等于用一份最多 45 秒前的快照
 * 替平台做决定——而平台那边同一时刻可能已经被别的会话扣完了。
 * 这里的数字只用于<strong>展示</strong>。
 *
 * <p>{@code metric} 必须命中平台的指标登记表；产品不自定义指标名，
 * 自定义的那个报上去会被 {@code unknown_metric} 拒绝。
 */
public record QuotaPool(String metric, long limit, long remaining, int priority) {
}
