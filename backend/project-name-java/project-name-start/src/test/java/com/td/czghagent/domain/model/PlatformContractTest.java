// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 《产品接入通则》里那些「不写测试就会慢慢漂走」的条款。
 *
 * <p>这一组用例保护的不是实现细节，而是<strong>契约本身</strong>：错误封套的必备字段、
 * 拒绝码的拼写、游标的不透明性、租户键不可为空、审计的最小字段集。
 * 它们的共同点是——违反了不会崩，只会安静地给出一个看起来正常的错误答案。
 */
class PlatformContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── X-1 错误封套 ────────────────────────────────────────────────────────

    @Test
    void errorEnvelopeAlwaysCarriesTheThreeMandatoryFields() throws Exception {
        String json = objectMapper.writeValueAsString(
                com.td.czghagent.rest.support.ErrorEnvelope.of("BID_NOT_FOUND", "标书不存在", false));

        assertThat(objectMapper.readTree(json).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "retryable");
    }

    /**
     * {@code field} 为空时<strong>整体省略</strong>，不是置 null。
     *
     * <p>一个恒为 null 的键会让调用方以为它有时候有值，然后写一条永远不进的分支。
     */
    @Test
    void errorEnvelopeOmitsTheFieldSlotInsteadOfSendingNull() throws Exception {
        String withoutField = objectMapper.writeValueAsString(
                com.td.czghagent.rest.support.ErrorEnvelope.of("X", "y", false));
        String withField = objectMapper.writeValueAsString(
                com.td.czghagent.rest.support.ErrorEnvelope.ofField("X", "y", false, "title"));

        assertThat(withoutField).doesNotContain("field");
        assertThat(objectMapper.readTree(withField).path("field").asText()).isEqualTo("title");
    }

    /**
     * 四个跨平面同义拒绝码的<strong>拼写</strong>被钉住。
     *
     * <p>同一件事曾经有 {@code quota_exceeded} / {@code QUOTA_EXCEEDED} /
     * {@code quota_exhausted} 三种写法，收敛的代价由每个消费方分摊。
     * 这里比对的是字面量本身，所以任何「顺手改个大小写」都会红。
     */
    @Test
    void sharedRejectionCodesKeepTheirExactSpelling() {
        assertThat(List.of(
                com.td.czghagent.rest.support.RejectionCodes.NOT_ENTITLED,
                com.td.czghagent.rest.support.RejectionCodes.POLICY_DENIED,
                com.td.czghagent.rest.support.RejectionCodes.APPROVAL_REQUIRED,
                com.td.czghagent.rest.support.RejectionCodes.QUOTA_EXCEEDED,
                com.td.czghagent.rest.support.RejectionCodes.RATE_LIMITED
        )).containsExactly(
                "NOT_ENTITLED", "POLICY_DENIED", "APPROVAL_REQUIRED",
                "QUOTA_EXCEEDED", "RATE_LIMITED");
    }

    // ── retryable 的派生 ────────────────────────────────────────────────────

    /**
     * 判据只有一条：同一个请求原样重发，过一会儿会不会成功。
     *
     * <p>业务状态类与并发冲突类都不算——它们要调用方先做点别的，等待改变不了任何事。
     */
    @Test
    void retryableIsDerivedFromWhetherWaitingCanHelp() {
        assertThat(new BusinessException("AI_MODEL_TIMEOUT", "超时", 504).isRetryable()).isTrue();
        assertThat(new BusinessException("PARSER_UNAVAILABLE", "不可用", 502).isRetryable()).isTrue();

        assertThat(new BusinessException("BID_OUTLINE_NOT_FROZEN", "未冻结", 409).isRetryable())
                .as("业务状态要用户先推进流程，重发只会得到同一个拒绝").isFalse();
        assertThat(new BusinessException("BID_REVISION_CONFLICT", "冲突", 409).isRetryable())
                .as("并发冲突要调用方带新 revision 重来，不是原样重发").isFalse();
        assertThat(new BusinessException("AI_PROVIDER_NOT_CONFIGURED", "未配置", 503).isRetryable())
                .as("配置缺失需要人介入，等多久都不会变").isFalse();
    }

    @Test
    void explicitRetryableOverridesTheCodeDerivedDefault() {
        BusinessException forced = new BusinessException("BID_NOT_FOUND", "x", 404, true, null);
        assertThat(forced.isRetryable()).isTrue();
    }

    /** 求值拒绝是 403 不是 401——把 403 当 401 重试会得到一个永远失败的循环。 */
    @Test
    void evaluationRejectionUsesForbiddenNotUnauthorized() {
        assertThat(BusinessException.forbidden().getHttpStatus()).isEqualTo(403);
    }

    // ── A-3 / A-4 列表形状 ──────────────────────────────────────────────────

    @Test
    void listLimitIsClampedByTheServerRatherThanTrusted() {
        assertThat(CursorPage.clampLimit(null)).isEqualTo(CursorPage.DEFAULT_LIMIT);
        assertThat(CursorPage.clampLimit(0)).isEqualTo(1);
        assertThat(CursorPage.clampLimit(-5)).isEqualTo(1);
        assertThat(CursorPage.clampLimit(10_000)).isEqualTo(CursorPage.MAX_LIMIT);
    }

    @Test
    void cursorPageCarriesTheCollectionUnderItemsAndNothingElse() throws Exception {
        String json = objectMapper.writeValueAsString(CursorPage.of(List.of("a"), "next"));

        assertThat(objectMapper.readTree(json).fieldNames()).toIterable()
                .as("集合键一律叫 items，不是 rows / data / content")
                .containsExactlyInAnyOrder("items", "nextCursor");
    }

    // ── 游标 ────────────────────────────────────────────────────────────────

    @Test
    void cursorSurvivesAnEncodeDecodeRoundTrip() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 8, 17, 43, 53);
        PageCursor decoded = PageCursor.decode(new PageCursor(at, "event-1").encode());

        assertThat(decoded).isEqualTo(new PageCursor(at, "event-1"));
    }

    /**
     * 游标是不透明的：编出来的串里不该能直接读出锚点。
     *
     * <p>一旦它长得像可读的时间戳或偏移量，调用方就会开始<strong>构造</strong>它，
     * 而那一刻服务端就再也不能改排序键了。
     */
    @Test
    void cursorIsOpaqueSoCallersDoNotStartConstructingIt() {
        String encoded = new PageCursor(LocalDateTime.of(2026, 9, 8, 17, 43, 53), "event-1").encode();

        assertThat(encoded).doesNotContain("2026").doesNotContain("event-1");
    }

    /**
     * 坏游标从头开始，而不是报错。
     *
     * <p>游标会因为服务端换了排序键而失效，那时用户手里的旧游标不该表现为一个报错页面。
     */
    @Test
    void malformedCursorsRestartFromTheBeginningInsteadOfFailing() {
        assertThat(PageCursor.decode(null)).isNull();
        assertThat(PageCursor.decode("")).isNull();
        assertThat(PageCursor.decode("not-base64!!")).isNull();
        assertThat(PageCursor.decode(java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("no-separator".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isNull();
    }

    // ── 租户轴 ──────────────────────────────────────────────────────────────

    /** 可空的租户键会让忘记加过滤的查询静默返回全部行，所以构造期就拒绝 null。 */
    @Test
    void tenantScopeRefusesToBeConstructedWithoutAWorkspace() {
        assertThatThrownBy(() -> new TenantScope("org", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantScope(null, "ws"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void localTenantScopeIsMarkedAndCannotCollideWithARealWorkspace() {
        TenantScope scope = TenantScope.local("user-1");

        assertThat(scope.workspaceId()).isEqualTo("local:user-1");
        assertThat(scope.usesLocalPlaceholder()).isTrue();
        assertThat(new TenantScope("11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222").usesLocalPlaceholder()).isFalse();
    }

    /**
     * 过渡标志<strong>不能</strong>出现在对外契约上。
     *
     * <p>这条曾经真的漏出去过：方法叫 {@code isLocal()} 时符合 JavaBean getter 约定，
     * Jackson 把它当属性序列化，于是标书响应里多了一个 {@code "local": true}。
     * 契约上出现过的字段，早晚会有人依赖它。
     */
    @Test
    void tenantScopeDoesNotPublishItsInternalPlaceholderFlag() throws Exception {
        String json = objectMapper.writeValueAsString(TenantScope.local("user-1"));

        assertThat(objectMapper.readTree(json).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("orgId", "workspaceId");
    }

    // ── X-2 task_id ─────────────────────────────────────────────────────────

    @Test
    void taskIdNormalizationRejectsBlankAndOverlongValues() {
        assertThat(TaskContext.normalize(null)).isNull();
        assertThat(TaskContext.normalize("   ")).isNull();
        assertThat(TaskContext.normalize("  task-1  ")).isEqualTo("task-1");
        assertThat(TaskContext.normalize("x".repeat(TaskContext.MAX_LENGTH))).isNotNull();
        assertThat(TaskContext.normalize("x".repeat(TaskContext.MAX_LENGTH + 1)))
                .as("超长视为未提供而不是截断——截断会让两侧的键对不上").isNull();
    }

    @Test
    void taskContextRestoresThePreviousValueSoNestedScopesDoNotLeak() {
        assertThat(TaskContext.current()).isNull();

        TaskContext.run("outer", () -> {
            assertThat(TaskContext.current()).isEqualTo("outer");
            TaskContext.run("inner", () -> assertThat(TaskContext.current()).isEqualTo("inner"));
            assertThat(TaskContext.current()).isEqualTo("outer");
        });

        assertThat(TaskContext.current())
                .as("退出作用域必须清干净：池化线程带着上一个任务的 id 会让两个任务的消耗被合并")
                .isNull();
    }

    @Test
    void taskContextIsClearedEvenWhenTheBodyThrows() {
        assertThatThrownBy(() -> TaskContext.run("boom", () -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(TaskContext.current()).isNull();
    }

    // ── X-3 审计最小字段集 ──────────────────────────────────────────────────

    /**
     * 用户发起的写记产品码作为 actorConsole；后台通道留空。
     *
     * <p>通则明确 MUST NOT 硬编一个控制台名——编出来的名字会让审计员按控制台筛查时
     * 收到一批根本不是从那里发起的动作，而他没有办法发现这一点。
     */
    @Test
    void auditEventDistinguishesConsoleWritesFromBackgroundWrites() {
        TenantScope tenant = TenantScope.local("user-1");
        CurrentUser user = new CurrentUser("user-1", "u", "U", "PLANNER", null, tenant);

        AuditEvent byUser = AuditEvent.byUser(
                new OperationContext(user, "trace-1", "127.0.0.1"),
                "BID_CREATE", "BID", "bid-1", AuditEvent.SUCCESS, "创建");
        AuditEvent bySystem = AuditEvent.bySystem(
                "user-1", tenant, "BID_LAYOUT_COMPLETE", "BID", "bid-1",
                AuditEvent.SUCCESS, "排版完成", "layout-1");

        assertThat(byUser.actorConsole()).isEqualTo(ProductIdentity.PRODUCT_CODE);
        assertThat(bySystem.actorConsole()).isNull();
        assertThat(bySystem.actorId()).as("后台动作仍记归属人，它确实是为这个人跑的")
                .isEqualTo("user-1");
        assertThat(byUser.workspaceId()).isEqualTo(tenant.workspaceId());
    }

    /** 审计事件自动带上当前 task_id，不靠每个写入点各传一次。 */
    @Test
    void auditEventPicksUpTheAmbientTaskId() {
        CurrentUser user = new CurrentUser(
                "user-1", "u", "U", "PLANNER", null, TenantScope.local("user-1"));
        OperationContext context = new OperationContext(user, "trace-1", "127.0.0.1");

        AuditEvent outside = AuditEvent.byUser(
                context, "A", "BID", "b", AuditEvent.SUCCESS, "x");
        AuditEvent inside = TaskContext.run("task-9", () -> AuditEvent.byUser(
                context, "A", "BID", "b", AuditEvent.SUCCESS, "x"));

        assertThat(outside.taskId()).isNull();
        assertThat(inside.taskId()).isEqualTo("task-9");
    }

    // ── 产品身份 ────────────────────────────────────────────────────────────

    /**
     * 产品码是源码字面量，且必须匹配平台的登记格式。
     *
     * <p>把它做成环境变量意味着同一份镜像可以冒充另一个产品上报用量。
     */
    @Test
    void productCodeMatchesThePlatformRegistrationFormat() {
        assertThat(ProductIdentity.PRODUCT_CODE).matches("^[a-z][a-z0-9_-]{0,31}$");
        assertThat(ProductIdentity.PRODUCT_CODE).isEqualTo("tenderforge");
    }
}
