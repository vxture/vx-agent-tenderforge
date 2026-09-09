// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.model.UsageEvent;

/**
 * 用量记录口（C3 上行的产品侧入口）。
 *
 * <p>只<strong>缓冲</strong>，不上报，更不裁决配额。裁决归平台——产品自己判一次
 * 配额，就等于在平台之外多了一个说了算的地方，而两处的答案迟早会分叉。
 *
 * <p><strong>永不抛出。</strong>计量失败绝不能让用户已经做成的事失败：
 * 一次报不上去的用量是一笔可以补的账，一个因为计量而失败的导出是一份丢掉的成果。
 */
public interface UsageRecorder {

    /** 记一次用量。实现必须吞掉自己的所有异常。 */
    void record(UsageEvent event);
}
