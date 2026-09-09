// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.PlatformCallerContext;
import com.td.czghagent.domain.model.TaskContext;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 在 Temporal 活动里重建平台调用上下文。
 *
 * <p><strong>为什么必须有这一层：</strong>{@code task_id} 和租户轴由入站 HTTP 过滤器
 * 建立，而工作流是异步的——活动在另一个线程、通常在另一个进程（worker）里跑，
 * 那两个 ThreadLocal 全是空的。而<strong>绝大多数模型调用恰好发生在活动里</strong>：
 * 正文生成、目录展开、一致性审查，没有一个走的是那条 HTTP 请求线程。
 *
 * <p>不补这一层的表现很具体：Atlas 从 v0.15.0 起强制要求 {@code taskId}，缺失即
 * {@code 400}。于是产品的主流程全部失败，而手工点一下试出来的那几个同步接口
 * 一切正常——最难查的那种分布。
 *
 * <p><strong>这里只能是 service 模式。</strong>活动跑起来时用户可能早已离开，
 * 没有 access token 可换。代价是 Atlas 的审计里只有产品没有终端用户；
 * 这是异步执行的固有代价，不是可以绕过的实现选择。
 */
@Component
public class PlatformActivityContext {

    private final BidRepository bidRepository;

    public PlatformActivityContext(BidRepository bidRepository) {
        this.bidRepository = bidRepository;
    }

    public <T> T run(String taskId, String bidId, String ownerId, Supplier<T> action) {
        TenantScope tenant = tenantOf(bidId, ownerId);
        return TaskContext.run(taskId, () ->
                PlatformCallerContext.run(tenant, null, action));
    }

    public void run(String taskId, String bidId, String ownerId, Runnable action) {
        run(taskId, bidId, ownerId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 租户轴从<strong>标书行</strong>上取，不是从 ownerId 拼。
     *
     * <p>拼出来的是 {@code local:<用户id>} 这个过渡值，而平台那边不存在这个工作空间。
     * 拿它去铸票会被拒，且拒绝理由读起来像是凭据配错了。标书行上的租户是这次工作
     * 真正归属的那个——切到平台身份之后它就是真值，在那之前它带着 {@code local:}
     * 前缀标记自己还没迁移。
     */
    private TenantScope tenantOf(String bidId, String ownerId) {
        try {
            Optional<BidDocument> bid = bidRepository.findBid(bidId, ownerId);
            return bid.map(BidDocument::tenant).orElse(null);
        } catch (RuntimeException exception) {
            // 取不到租户不该让活动本身失败：模型调用会因为铸不出票而给出明确拒绝，
            // 那个错误比一个「查库失败」更接近真相。
            return null;
        }
    }
}
