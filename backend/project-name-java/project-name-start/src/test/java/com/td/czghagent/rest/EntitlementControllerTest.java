// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest;

import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.rest.security.RequestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权益视图的形状，以及「我已完成订阅」的刷新。
 *
 * <p>订阅闸门页的出路全靠这份响应：少了 {@code unavailable}，平台一抖界面就对付了钱的人说
 * 「尚未订阅」；刷新不先驱逐缓存，刚付完钱的人点了也还是 45 秒前那份答案；
 * {@code subscribeUrl} 拼错了，「前往订阅」把人带到一个不能订阅的页面。
 */
class EntitlementControllerTest {

    private static final String WEBSITE = "https://vxture.com";

    @Test
    void anUnreachablePlatformIsReportedAsUnavailableNotAsUnsubscribed() {
        EntitlementController controller = new EntitlementController(
                fixed(Entitlement.unavailable("ws-1", "tenderforge")), WEBSITE);

        Map<String, Object> body = controller.current(requestFor("ws-1"));

        assertThat(body.get("unavailable")).isEqualTo(true);
        assertThat(body.get("allowsProductSurface")).as("没问到也不放行").isEqualTo(false);
    }

    /**
     * 转化深链是<strong>官网定价页</strong>（owner 2026-09-16）：套餐在那里发布。
     *
     * <p>此前拼的是 console 的 subscribe，而那不是订阅页面——目录里没有本产品套餐时，
     * console 把人降级回订阅首页，用户点「前往订阅」看到的与订阅无关。
     * 语言段不拼：官网按访客偏好自己 307 分流。
     */
    @Test
    void aWorkspaceThatNeverSubscribedIsAnAnswerNotAnOutage() {
        EntitlementController controller = new EntitlementController(
                fixed(Entitlement.none("ws-1", "tenderforge")), WEBSITE);

        Map<String, Object> body = controller.current(requestFor("ws-1"));

        assertThat(body.get("unavailable")).isEqualTo(false);
        assertThat(body.get("allowsProductSurface")).isEqualTo(false);
        assertThat(body.get("subscribeUrl"))
                .isEqualTo("https://vxture.com/pricing?product=tenderforge");
    }

    /** 刷新先驱逐<strong>调用者自己</strong>工作空间的缓存，再问一次——顺序反了等于没刷新。 */
    @Test
    void refreshEvictsTheCallersOwnWorkspaceBeforeAsking() {
        List<String> calls = new ArrayList<>();

        new EntitlementController(recording(calls), WEBSITE).refresh(requestFor("ws-1"));

        assertThat(calls).containsExactly("invalidate:ws-1", "resolve:ws-1");
    }

    /** 普通读取不驱逐：每个页面都驱逐一次，45 秒缓存就形同虚设，每次都打到平台。 */
    @Test
    void readingTheViewEvictsNothing() {
        List<String> calls = new ArrayList<>();

        new EntitlementController(recording(calls), WEBSITE).current(requestFor("ws-1"));

        assertThat(calls).containsExactly("resolve:ws-1");
    }

    private static MockHttpServletRequest requestFor(String workspaceId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdentity.USER, new CurrentUser("sub-1", "sub-1", "用户", "PLANNER",
                null, new TenantScope("org-1", workspaceId)));
        return request;
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
                return false;
            }
        };
    }

    private static EntitlementResolver recording(List<String> calls) {
        return new EntitlementResolver() {
            @Override
            public Entitlement resolve(String workspaceId) {
                calls.add("resolve:" + workspaceId);
                return Entitlement.none(workspaceId, "tenderforge");
            }

            @Override
            public void invalidate(String workspaceId) {
                calls.add("invalidate:" + workspaceId);
            }

            @Override
            public boolean isMock() {
                return false;
            }
        };
    }
}
