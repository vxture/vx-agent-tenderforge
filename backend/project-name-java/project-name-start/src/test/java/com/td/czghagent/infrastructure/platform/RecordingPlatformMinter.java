// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-14
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.model.S2SToken;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.S2STokenMinter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 平台面换票的替身：记下为谁铸了票、作废了哪几张，也能被设成铸不出。
 *
 * <p>每次铸出的票值都不同（{@code s2s-1}、{@code s2s-2}…），
 * 这样「401 之后有没有换一张新票」才可断言。
 */
final class RecordingPlatformMinter implements S2STokenMinter {

    /** 每次铸币记一条 {@code audience|workspaceId}。 */
    final List<String> mintedFor = new CopyOnWriteArrayList<>();
    final List<S2SToken> invalidated = new CopyOnWriteArrayList<>();
    volatile RuntimeException failure;
    volatile boolean configured = true;

    private final AtomicInteger serial = new AtomicInteger();

    @Override
    public S2SToken onBehalfOf(String audience, String userAccessToken) {
        throw new UnsupportedOperationException("平台面通道不走 OBO");
    }

    @Override
    public S2SToken forService(String audience, TenantScope tenant) {
        throw new UnsupportedOperationException("平台面通道只声明工作空间，不编组织 id");
    }

    @Override
    public S2SToken forWorkspace(String audience, String workspaceId) {
        if (failure != null) {
            throw failure;
        }
        mintedFor.add(audience + "|" + workspaceId);
        return new S2SToken("s2s-" + serial.incrementAndGet(), audience, S2SToken.Mode.SERVICE,
                null, "tenant-uuid", LocalDateTime.now().plusMinutes(5));
    }

    @Override
    public void invalidate(S2SToken token) {
        invalidated.add(token);
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }
}
