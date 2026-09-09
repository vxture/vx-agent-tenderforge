// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 档位到能力的映射——<strong>唯一的映射点</strong>。
 *
 * <p>散在各处的 {@code if (tier == "pro")} 会让「专业版能做什么」这个问题
 * 没有一处可以回答，而商务改一次档位内容就要全仓翻一遍。
 *
 * <p><strong>档位值域属于平台，不属于本产品。</strong>下面这份档位名单是一面镜子，
 * 平台的五档一旦确定就必须同步。在那之前只有 {@code free} 是确知的
 * （首批计划里 karda-free / vxtpl-free 都已发布）。
 *
 * <p>未知档位<strong>按最低档处理</strong>而不是按最高档：通则说「未知即降级」。
 * 反过来（未知给全权限）意味着平台加一档、或者手滑写错一个字母，
 * 就把完整能力发给了不该有的人——而那不会报错。
 */
public enum BidCapability {

    /** 建标书、编辑目录与正文。没有它就只剩一个只读的空壳。 */
    BID_AUTHORING,
    /** 调用模型做解读、生成、修订。 */
    AI_GENERATION,
    /** 导出 DOCX 成稿。 */
    DOCUMENT_EXPORT,
    /** 个人素材库。 */
    ASSET_LIBRARY,
    /** 成稿一致性审查。 */
    CONSISTENCY_REVIEW;

    /** 免费档：够走通一次完整流程，但不含审查。 */
    private static final Set<BidCapability> FREE = EnumSet.of(
            BID_AUTHORING, AI_GENERATION, DOCUMENT_EXPORT, ASSET_LIBRARY);

    /** 付费档：全量。五档之间的差异目前落在配额数字上，不落在能力集上。 */
    private static final Set<BidCapability> PAID = EnumSet.allOf(BidCapability.class);

    private static final String FREE_TIER = "free";

    /**
     * 解析一个工作空间拥有的能力集。
     *
     * <p><strong>fail-closed</strong>：{@code tier} 为 null（没有生效的直接购买）时
     * 返回空集，即使 {@code bundled} 为真——被捆绑覆盖的工作空间可以被别的产品
     * 调用本产品的数据能力，但它自己不该拿到完整的界面能力。
     * 那条区别由 {@link Entitlement#allowsDataPlane()} 表达，不在这里。
     */
    public static Set<BidCapability> of(Entitlement entitlement) {
        if (entitlement == null || !entitlement.allowsProductSurface()) {
            return Set.of();
        }
        String tier = entitlement.tier().trim().toLowerCase(Locale.ROOT);
        return FREE_TIER.equals(tier) ? Set.copyOf(FREE) : Set.copyOf(PAID);
    }

    /**
     * 这个档位是否是已知的。
     *
     * <p>供自证与日志使用：未知档位会被按已知的最保守方式处理，
     * 但「我们遇到了一个没见过的档位」这件事本身值得被看见，
     * 而不是安静地降级。
     */
    public static boolean isKnownTier(String tier) {
        return tier != null && FREE_TIER.equals(tier.trim().toLowerCase(Locale.ROOT));
    }
}
