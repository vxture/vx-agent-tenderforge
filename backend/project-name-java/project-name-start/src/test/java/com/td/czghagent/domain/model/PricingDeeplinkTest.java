// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-16
package com.td.czghagent.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 转化深链：官网定价页（owner 2026-09-16）。
 *
 * <p>这一组钉住的每一条，错了都不报错，只是把想付钱的人带到别处：
 * 拼成 console 的 subscribe（那不是订阅页面，未登记套餐的产品会被降级回订阅首页）、
 * 自作主张拼一个语言段（官网自己会按访客偏好 307 分流，拼错则落到官网没有的语言上）、
 * 或者把工作空间拼进查询串（谁被开通由一个可伪造的参数决定）。
 */
class PricingDeeplinkTest {

    private static final String WEBSITE = "https://vxture.com";

    @Test
    void buildsThePricingPageForTheProduct() {
        assertThat(PricingDeeplink.of(WEBSITE, "tenderforge"))
                .isEqualTo("https://vxture.com/pricing?product=tenderforge");
    }

    /** 官网地址带不带尾斜杠都不该拼出双斜杠——那是一条能打开但看着像故障的地址。 */
    @Test
    void toleratesATrailingSlashOnTheConfiguredWebsite() {
        assertThat(PricingDeeplink.of("https://vxture.com/", "tenderforge"))
                .isEqualTo("https://vxture.com/pricing?product=tenderforge");
    }

    /**
     * 只带 product：<strong>不拼语言段</strong>、不带 workspace_id、不带 intent。
     *
     * <p>语言由官网自己按访客的 {@code NEXT_LOCALE} cookie 与 {@code Accept-Language} 307 分流——
     * 产品侧那个语言 cookie 种在自己的域名上，官网读不到，拼进去只是用猜测覆盖访客在官网的选择。
     * workspace_id 会让一个可被伪造的查询参数决定给谁开通；intent 是拼给自己看的，定价页只认 product。
     */
    @Test
    void carriesNothingButTheProduct() {
        String url = PricingDeeplink.of(WEBSITE, "tenderforge");

        assertThat(url).doesNotContain("/zh-CN/").doesNotContain("/en-US/");
        assertThat(url).doesNotContain("workspace").doesNotContain("intent");
        assertThat(url).doesNotContain("console.vxture.com").doesNotContain("/subscribe");
    }

    /** 产品码原样编码：目录里的码是小写 kebab，但编码这件事不该依赖它一直是。 */
    @Test
    void encodesTheProductCode() {
        assertThat(PricingDeeplink.of(WEBSITE, "a b&c"))
                .isEqualTo("https://vxture.com/pricing?product=a+b%26c");
    }
}
