// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.AuditEvent;
import com.td.czghagent.domain.model.ProductIdentity;
import com.td.czghagent.domain.model.ProvisioningEvent;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.ProvisioningRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 处理平台下发的开通/停用事件。
 *
 * <p>平台只保证<strong>至少一次</strong>投递，所以重复与乱序<strong>一定</strong>会发生。
 * 幂等靠投递标识抢占，顺序靠每 (workspace, product) 的 seq 单调。
 *
 * <p>最容易接错的是<strong>什么时候该让平台重试</strong>。
 * 「重复」和「过期」不是错误，是至少一次投递的正常产物——把它们答成失败，
 * 平台会永远重试一个本来就已经处理好的事件。只有<em>我们自己没处理成</em>
 * 才该让它重试。
 */
@Service
public class ProvisioningCommandService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProvisioningCommandService.class);

    /** 审计里的发起人。平台是发起人，而它没有本地用户行。 */
    private static final String ACTOR_PLATFORM = "platform";

    /** 一次投递的处置。除 {@link #PROCESSED} 外都不代表失败，都应当回 2xx。 */
    public enum Outcome {
        /** 正常处理。 */
        PROCESSED,
        /** 重复投递，副作用不再执行一次。 */
        DUPLICATE,
        /** seq 不比已处理的更新，忽略。 */
        STALE,
        /** 事件不是发给本产品的。 */
        WRONG_PRODUCT,
        /** 类型没见过：记下投递，不做动作。 */
        UNKNOWN_TYPE
    }

    private final ProvisioningRepository provisioning;
    private final EntitlementResolver entitlements;
    private final AuditRepository audits;

    public ProvisioningCommandService(ProvisioningRepository provisioning,
                                      EntitlementResolver entitlements,
                                      AuditRepository audits) {
        this.provisioning = provisioning;
        this.entitlements = entitlements;
        this.audits = audits;
    }

    public Outcome handle(ProvisioningEvent event, String traceId, LocalDateTime now) {
        if (!ProductIdentity.PRODUCT_CODE.equals(event.product())) {
            // 发错产品的事件不处理也不记录：它不是我们的账，
            // 但要回 2xx——重试同一个投递不会让它变成我们的。
            LOGGER.warn("Provisioning event {} addressed to product {}, not ours",
                    event.deliveryId(), event.product());
            return Outcome.WRONG_PRODUCT;
        }

        // 抢占即幂等。抢不到说明这条已经被处理过——包括被<strong>另一个副本</strong>
        // 同时处理：api 与 worker 跑同一个镜像，两边都可能收到同一次重投。
        if (!provisioning.claimDelivery(event.deliveryId(), event.type(),
                event.workspaceId(), event.seq(), now)) {
            return Outcome.DUPLICATE;
        }

        long lastSeq = provisioning.lastSeq(event.workspaceId(), ProductIdentity.PRODUCT_CODE);
        if (event.seq() <= lastSeq) {
            // 乱序到达的旧事件。用它去覆盖状态会把一个已经停用的空间改回开通——
            // 而那是网络抖动一次就能造成的。
            provisioning.recordOutcome(event.deliveryId(), Outcome.STALE.name());
            return Outcome.STALE;
        }

        Outcome outcome = switch (event.type()) {
            case ProvisioningEvent.PROVISIONED -> apply(
                    event, ProvisioningEvent.STATE_PROVISIONED, traceId, now);
            case ProvisioningEvent.DEPROVISIONED -> apply(
                    event, ProvisioningEvent.STATE_DEPROVISIONED, traceId, now);
            default -> {
                // 没见过的类型：记下投递、不做动作。这是通则要求的姿态——
                // 报错只会让平台把一个我们本来就不关心的事件重试到天荒地老。
                LOGGER.info("Ignoring unknown provisioning event type {} ({})",
                        event.type(), event.deliveryId());
                yield Outcome.UNKNOWN_TYPE;
            }
        };
        provisioning.recordOutcome(event.deliveryId(), outcome.name());
        return outcome;
    }

    private Outcome apply(ProvisioningEvent event, String state,
                          String traceId, LocalDateTime now) {
        provisioning.upsertInstance(
                event.workspaceId(), ProductIdentity.PRODUCT_CODE, state, event.seq(), now);
        // 开通与停用<strong>就是</strong>权益变更，这正是 C2 那个短缓存留着
        // invalidate 的原因：把「刚买完回来点一下」的等待从 45 秒 TTL 压到一次点击。
        evictEntitlement(event.workspaceId());
        // 发起人是平台，不是某个人。这里刻意不去编一个用户 id——
        // 审计里出现一个没做过这件事的人，比 actor 写着「平台」更难查。
        // 投递标识进摘要，让本地审计和平台侧的投递记录能按同一个 id 对上。
        audits.append(AuditEvent.bySystem(
                ACTOR_PLATFORM, event.tenant(), "PLATFORM_PROVISIONING",
                "WORKSPACE", event.workspaceId(), AuditEvent.SUCCESS,
                event.type() + " delivery=" + event.deliveryId(), traceId));
        return Outcome.PROCESSED;
    }

    /**
     * 驱逐权益缓存，<strong>吞掉自己的异常</strong>。
     *
     * <p>这一步跑在一个「2xx 即已记录」的 webhook 里。让驱逐的一次失败冒泡出去，
     * 等于把一次验签正确、也确实处理成功的投递变成 500，而平台会永远重试它。
     * 漏一次驱逐最多让某个空间的档位陈旧 45 秒；一场重试风暴的代价大得多。
     */
    private void evictEntitlement(String workspaceId) {
        try {
            entitlements.invalidate(workspaceId);
        } catch (RuntimeException exception) {
            LOGGER.error("Entitlement eviction failed for workspace {}", workspaceId, exception);
        }
    }
}
