// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidCapability;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.infrastructure.platform.MockEntitlementResolver;
import com.td.czghagent.rest.support.RejectionCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 判定本身：放行谁、拒绝谁、拒绝长什么样。
 *
 * <p>「经过的是不是这道判定」由 {@code EntitlementEnforcementIntegrationTest} 在 HTTP 层验；
 * 这里只验判定逻辑，所以用替身解析器按 MOCK_TIER / MOCK_STATUS 的真实语义造信封，
 * 而不是手搓一个替身不会发出的组合（那正是替身类注释里记过的坑）。
 */
class EntitlementGuardTest {

    private static final OperationContext CONTEXT = new OperationContext(
            new CurrentUser("sub-1", "sub-1", "用户", "PLANNER", null,
                    new TenantScope("org-1", "ws-1")),
            "trace-1", "127.0.0.1");

    @ParameterizedTest
    @EnumSource(BidCapability.class)
    void anActiveKnownTierIsGrantedEveryCapability(BidCapability capability) {
        EntitlementGuard guard = new EntitlementGuard(new MockEntitlementResolver("pro", "active", false));

        assertThatCode(() -> guard.require(CONTEXT, capability)).doesNotThrowAnyException();
    }

    @Test
    void aWorkspaceThatNeverSubscribedIsRefusedWithTheSharedRejectionCode() {
        BusinessException refused = refusal(new MockEntitlementResolver("none", "", false));

        assertThat(refused.getErrorCode()).isEqualTo(RejectionCodes.NOT_ENTITLED);
        assertThat(refused.getHttpStatus()).isEqualTo(403);
        assertThat(refused.isRetryable()).isFalse();
        assertThat(refused.getMessage()).contains("尚未订阅");
    }

    @Test
    void aLapsedSubscriptionIsRefusedAndNamedAsLapsed() {
        BusinessException refused = refusal(new MockEntitlementResolver("pro", "expired", false));

        assertThat(refused.getErrorCode()).isEqualTo(RejectionCodes.NOT_ENTITLED);
        assertThat(refused.getMessage()).contains("已失效").contains("expired");
    }

    /**
     * 未知档位按 {@link BidCapability} 的既有语义得到空能力集，于是被拒绝——
     * 并且拒绝里说出了那个档位值，让「产品与平台档位表不同步」这件事能被看见。
     */
    @Test
    void anUnknownTierIsRefusedAndTheTierIsNamed() {
        BusinessException refused = refusal(new MockEntitlementResolver("platinum", "active", false));

        assertThat(refused.getMessage()).contains("platinum");
    }

    /** 捆绑覆盖只开数据面，不开界面命令（通则门控公式，BidCapability 注释）。 */
    @Test
    void bundledCoverageWithoutADirectPurchaseDoesNotOpenCommands() {
        Entitlement bundledOnly = new Entitlement("ws-1", "tenderforge", null, null, null, false,
                null, null, true, null, null);
        EntitlementGuard guard = new EntitlementGuard(fixed(bundledOnly));

        assertThat(catchThrowableOfType(BusinessException.class,
                () -> guard.require(CONTEXT, BidCapability.AI_GENERATION)).getErrorCode())
                .isEqualTo(RejectionCodes.NOT_ENTITLED);
    }

    /** 判定按调用者自己的工作空间解析，不是别的什么键。 */
    @Test
    void resolvesTheCallersOwnWorkspace() {
        List<String> asked = new ArrayList<>();
        EntitlementResolver recording = new EntitlementResolver() {
            @Override
            public Entitlement resolve(String workspaceId) {
                asked.add(workspaceId);
                return new MockEntitlementResolver("pro", "active", false).resolve(workspaceId);
            }

            @Override
            public void invalidate(String workspaceId) {
            }

            @Override
            public boolean isMock() {
                return true;
            }
        };

        new EntitlementGuard(recording).require(CONTEXT, BidCapability.DOCUMENT_EXPORT);

        assertThat(asked).containsExactly("ws-1");
    }

    @Test
    void theApplicationLayerCodeMatchesTheWebLayerConstant() {
        assertThat(EntitlementGuard.NOT_ENTITLED).isEqualTo(RejectionCodes.NOT_ENTITLED);
    }

    private static BusinessException refusal(EntitlementResolver resolver) {
        BusinessException refused = catchThrowableOfType(BusinessException.class,
                () -> new EntitlementGuard(resolver).require(CONTEXT, BidCapability.AI_GENERATION));
        assertThat(refused).as("预期被拒绝，但放行了").isNotNull();
        return refused;
    }

    private static EntitlementResolver fixed(Entitlement entitlement) {
        return new EntitlementResolver() {
            @Override
            public Entitlement resolve(String workspaceId) {
                return entitlement;
            }

            @Override
            public void invalidate(String workspaceId) {
            }

            @Override
            public boolean isMock() {
                return true;
            }
        };
    }
}
