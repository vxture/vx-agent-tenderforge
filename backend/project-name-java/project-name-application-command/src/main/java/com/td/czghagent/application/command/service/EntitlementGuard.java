// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidCapability;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.port.EntitlementResolver;
import org.springframework.stereotype.Service;

/**
 * C2 权益的<strong>强制点</strong>：命令执行之前，按工作空间的权益判定这项能力放不放行。
 *
 * <p>此前权益只被 {@code GET /api/entitlement} 读出来给界面看，没有任何一条命令会因为
 * 权益不足而被拒绝——任何能登录的平台用户，无论订阅与否、档位高低，都能用全部功能。
 * 读得出权益却从不拒绝，等于没有权益。
 *
 * <p><strong>判定放在应用服务入口，不放在控制器、也不放在活动里</strong>：
 * <ul>
 *   <li>控制器里判，同一条规则要在每个端点各写一遍，漏一个端点不会有任何症状；</li>
 *   <li>Temporal 活动里判，会把一个已经放行、正在跑的任务在中途打断——用户付费期内
 *       发起的生成，不该因为过程中权益缓存过期而变成半截标书。门控只管「能不能发起」；</li>
 *   <li>应用服务的命令方法是 HTTP 入口唯一经过的地方，也是
 *       {@code scripts/guardrails/check_entitlement_gates.py} 逐个方法核对的地方。</li>
 * </ul>
 *
 * <p>判定只用两件现成的东西：{@link BidCapability#of}（档位→能力的唯一映射点，
 * 已含通则的界面门控公式 {@code tier != null}）与 {@link EntitlementResolver}
 * （永不抛出、读不到即空信封）。所以平台不可达时是<strong>拒绝</strong>而不是放行，
 * 与解析器既有的 fail-closed 语义一致——这里不另立一套。
 *
 * <p><strong>不做配额判定。</strong>{@code QuotaPool.remaining} 是最多 45 秒前的快照，
 * 只用于展示；通则 C3 的 consume「记账不裁决」，没余量时怎么办由产品决定，
 * 但依据必须是 consume 的 {@code gated} 回执而不是这份快照。那条路径另行接入。
 */
@Service
public class EntitlementGuard {

    /**
     * 与 {@code RejectionCodes.NOT_ENTITLED} 同一个字面量（通则 X-1 跨平面同义码）。
     * 常量定义在 web 模块，应用层引用不到；两者一致由 {@code EntitlementGuardTest} 断言。
     */
    static final String NOT_ENTITLED = "NOT_ENTITLED";

    private final EntitlementResolver resolver;

    public EntitlementGuard(EntitlementResolver resolver) {
        this.resolver = resolver;
    }

    /** 权益没问到（平台暂时不可达）时的拒绝码。本产品局部码，带模块前缀（通则 X-1）。 */
    static final String ENTITLEMENT_UNAVAILABLE = "ENTITLEMENT_UNAVAILABLE";

    /**
     * 要求当前工作空间拥有这项能力，否则拒绝。
     *
     * <p>平台答了「没有」：403 {@code NOT_ENTITLED}、{@code retryable=false}——订阅是运营与用户的
     * 动作，原样重发永远是同一个拒绝。转化深链不进错误封套（X-1 的形状是固定的），前端从
     * {@code GET /api/entitlement} 取 {@code subscribeUrl}，并且只在用户显式点击时打开。
     *
     * <p>没问到：同样拒绝（fail-closed），但是 503 {@code ENTITLEMENT_UNAVAILABLE}、
     * {@code retryable=true}——稍后重试才是正确出路，对付了钱的人说「尚未订阅」是说错话。
     */
    public void require(OperationContext context, BidCapability capability) {
        Entitlement entitlement = resolver.resolve(workspaceOf(context));
        if (BidCapability.of(entitlement).contains(capability)) {
            return;
        }
        if (entitlement.unavailable()) {
            throw new BusinessException(ENTITLEMENT_UNAVAILABLE,
                    "暂时无法确认当前工作空间的订阅状态，请稍后重试", 503, true, null);
        }
        throw rejection(entitlement, capability);
    }

    static BusinessException rejection(Entitlement entitlement, BidCapability capability) {
        return new BusinessException(NOT_ENTITLED, message(entitlement, capability), 403, false, null);
    }

    /**
     * 说清楚是哪一种「不能」，但不做商业推断——该买什么、什么价，归 console。
     *
     * <p>三种情形对应三种行动：从未订阅（去订阅）、订阅已失效（去续订）、
     * 有订阅但档位里没有这项能力（去看档位）。未知档位落在第三种：
     * {@link BidCapability} 对未知档位给空集，这里如实说出档位值，让它能被看见。
     */
    static String message(Entitlement entitlement, BidCapability capability) {
        if (entitlement.allowsProductSurface()) {
            return "当前订阅档位（" + entitlement.tier() + "）不包含「" + label(capability) + "」";
        }
        if (entitlement.status() == null) {
            return "当前工作空间尚未订阅标书编写智能体，无法" + label(capability);
        }
        return "当前工作空间的订阅已失效（" + entitlement.status() + "），无法" + label(capability);
    }

    private static String label(BidCapability capability) {
        return switch (capability) {
            case BID_AUTHORING -> "新建或编写标书";
            case AI_GENERATION -> "使用 AI 解读与生成";
            case DOCUMENT_EXPORT -> "导出成稿";
            case ASSET_LIBRARY -> "上传素材";
            case CONSISTENCY_REVIEW -> "进行成稿审查";
        };
    }

    private static String workspaceOf(OperationContext context) {
        if (context == null || context.user() == null || context.user().tenant() == null) {
            return null;
        }
        return context.user().tenant().workspaceId();
    }
}
