// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

/**
 * 当前线程正在<strong>替谁</strong>调用下游平面。
 *
 * <p>与 {@link TaskContext} 是同一类东西、解决同一个问题：出站调用点埋在十几层
 * 业务函数底下，而它需要的上下文只有最外层知道。把租户和用户票写进每一层的签名，
 * 等于让每个纯业务函数都认识一个传输层概念。
 *
 * <p>分成两个类而不是一个，是因为两者的<strong>生命周期不同</strong>：
 * {@code task_id} 要穿过产品边界一路传下去（被调方也要落库），
 * 而这里的用户票<strong>绝不出本进程</strong>——它只是换票的原料。
 *
 * <p>为空是合法状态：本地口令登录的用户没有平台票，后台任务没有用户。
 * 空值让出站调用点显式拒绝，而不是让它带着一张伪造的身份出去。
 */
public final class PlatformCallerContext {

    private static final ThreadLocal<PlatformCallerContext> CURRENT = new ThreadLocal<>();

    private final TenantScope tenant;
    private final String userAccessToken;

    private PlatformCallerContext(TenantScope tenant, String userAccessToken) {
        this.tenant = tenant;
        this.userAccessToken = userAccessToken;
    }

    public static PlatformCallerContext current() {
        return CURRENT.get();
    }

    /**
     * 租户轴；没有上下文时为 null。
     *
     * <p>过渡租户（{@code local:} 前缀）在这里<strong>照样返回</strong>，不在这一层过滤——
     * 该不该用它是铸币那一侧的判断，而平台会拒绝一个本产品并未覆盖的工作空间。
     * 在这里提前吞掉它，只会让那个拒绝变成一句更含糊的「没有租户」。
     */
    public static TenantScope tenant() {
        PlatformCallerContext context = CURRENT.get();
        return context == null ? null : context.tenant;
    }

    /** 用户的平台 access token；没有平台会话时为 null。 */
    public static String userAccessToken() {
        PlatformCallerContext context = CURRENT.get();
        return context == null ? null : context.userAccessToken;
    }

    /**
     * 在给定上下文里执行。
     *
     * <p>{@code finally} 里恢复<strong>前一个值</strong>而不是清空：嵌套调用时清空会让
     * 外层剩下的部分丢掉上下文，而那个 bug 只在嵌套发生时出现。
     */
    public static <T> T run(TenantScope tenant, String userAccessToken,
                            java.util.function.Supplier<T> action) {
        PlatformCallerContext previous = CURRENT.get();
        CURRENT.set(new PlatformCallerContext(tenant, userAccessToken));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void run(TenantScope tenant, String userAccessToken, Runnable action) {
        run(tenant, userAccessToken, () -> {
            action.run();
            return null;
        });
    }
}
