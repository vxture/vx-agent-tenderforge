// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-11
package com.td.czghagent.domain.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 档位到能力的映射——<strong>唯一的映射点</strong>。
 *
 * <p>散在各处的 {@code if (tier == "pro")} 会让「专业版能做什么」这个问题
 * 没有一处可以回答，而商务改一次档位内容就要全仓翻一遍。
 *
 * <p><strong>档位值域属于平台，不属于本产品。</strong>平台的五档在
 * {@link #TIER_CAPABILITIES} 里逐个列出，<strong>哪怕当前五行内容完全相同</strong>。
 * 「上架四个套餐」与「认全五个档位」是两件事：商务上卖四个，不等于平台不会下发
 * 第五个；它一旦发来而表里没有，就会被当成未知档降到最低——一个付费最高的客户
 * 拿到最低的能力，而且不报错。
 *
 * <p>内容今天相同不代表明天相同。表在这里，差异化落地时改的是表里一行；
 * 没有表，改的是散在各处的条件判断。
 *
 * <p>未知档位<strong>按最低档处理</strong>而不是按最高档：通则说「未知即降级」。
 * 反过来（未知给全权限）意味着平台加一档、或者云端配置里手滑写错一个字母，
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

    /** 全量能力。当前五档都是这一份，差异落在平台侧的配额与 credits 上。 */
    private static final Set<BidCapability> ALL = EnumSet.allOf(BidCapability.class);

    /**
     * 未知档位的兜底能力集。
     *
     * <p><strong>刻意留空而不是给 free 那一份。</strong>「没见过的档」与「免费档」
     * 是两件不同的事：前者说明产品与平台的档位表已经不同步，那时候继续放行任何
     * 能力都是在猜。空集会让界面立刻显形，而不是让一个配错的档位安静地当免费用。
     */
    private static final Set<BidCapability> UNKNOWN = Set.of();

    /**
     * 平台五档 → 能力集。<strong>五行都要在，不能靠「不是 free 就是全量」推。</strong>
     *
     * <p>当前五行内容一致：产品侧不做数量门控（一致性审查也不限次），
     * 用多少由 AI credits 约束，而 credits 与席位是平台侧的数字。
     * 这不表示这张表多余——它是差异化真正落地时唯一要改的地方，
     * 也是「这个档位我们认不认得」这个问题唯一能回答的地方。
     */
    private static final Map<String, Set<BidCapability>> TIER_CAPABILITIES = Map.of(
            "free", ALL,
            "starter", ALL,
            "pro", ALL,
            "business", ALL,
            // 私有化交付。功能上与云端档位相同，差别在权益从哪里来——
            // 云端四档由平台下发，私有化实例没有平台可问。那是解析器的事，不是这里的事。
            "enterprise", ALL);

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
        return TIER_CAPABILITIES.getOrDefault(normalize(entitlement.tier()), UNKNOWN);
    }

    /**
     * 这个档位是否是已知的。
     *
     * <p>供自证与日志使用：未知档位会被按已知的最保守方式处理，
     * 但「我们遇到了一个没见过的档位」这件事本身值得被看见，
     * 而不是安静地降级。
     */
    public static boolean isKnownTier(String tier) {
        return tier != null && TIER_CAPABILITIES.containsKey(normalize(tier));
    }

    /**
     * 已知档位的全集，按平台值域。
     *
     * <p>给自证接口与测试用：让「产品认得哪几档」可以被读出来，
     * 而不是靠翻代码数分支。
     */
    public static Set<String> knownTiers() {
        return Set.copyOf(TIER_CAPABILITIES.keySet());
    }

    /**
     * 档位字符串的规范化。
     *
     * <p>大小写与首尾空白都归一：云端配置里多打一个空格、或者写成 {@code Pro}，
     * 不该让一个付费客户掉进未知档。<strong>但只归一这两样</strong>——
     * 别名映射（把 {@code professional} 当成 {@code pro}）不做，
     * 那等于产品替平台定义值域，而值域不归产品。
     */
    private static String normalize(String tier) {
        return tier == null ? "" : tier.trim().toLowerCase(Locale.ROOT);
    }
}
