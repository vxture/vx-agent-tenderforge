// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.td.czghagent.domain.model.S2SToken;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.S2STokenMinter;

import java.time.LocalDateTime;

/**
 * 本地开发用的换票替身。
 *
 * <p>它铸出来的票<strong>在任何真实被调方那里都会被拒</strong>——这是刻意的。
 * 替身的用途是让本地能把调用链跑到发出请求那一步，而不是让它假装成功；
 * 一张能骗过本地断言的假票，会让「其实没接通」一路瞒到对量的时候。
 *
 * <p>票值带 {@code mock-s2s-} 前缀，抓包或日志里一眼可辨。
 */
public class MockS2STokenMinter implements S2STokenMinter {

    private static final String PREFIX = "mock-s2s-";

    @Override
    public S2SToken onBehalfOf(String audience, String userAccessToken) {
        return token(audience, S2SToken.Mode.ON_BEHALF_OF, "mock-subject", null);
    }

    @Override
    public S2SToken forService(String audience, TenantScope tenant) {
        return token(audience, S2SToken.Mode.SERVICE, null, tenant.workspaceId());
    }

    @Override
    public void invalidate(S2SToken token) {
        // 替身不缓存，没有什么可作废的。
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    private static S2SToken token(String audience, S2SToken.Mode mode,
                                  String subject, String tenantId) {
        return new S2SToken(PREFIX + audience, audience, mode, subject, tenantId,
                LocalDateTime.now().plusSeconds(300));
    }
}
