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
 * 拼出一个官网没有的语言段（404）、或者把工作空间拼进查询串（谁被开通由一个可伪造的参数决定）。
 */
class PricingDeeplinkTest {

    private static final String WEBSITE = "https://vxture.com";

    @Test
    void buildsThePricingPageForTheProductInTheChosenLanguage() {
        assertThat(PricingDeeplink.of(WEBSITE, "tenderforge", "zh-CN"))
                .isEqualTo("https://vxture.com/zh-CN/pricing?product=tenderforge");
        assertThat(PricingDeeplink.of(WEBSITE, "tenderforge", "en-US"))
                .isEqualTo("https://vxture.com/en-US/pricing?product=tenderforge");
    }

    /** 官网地址带不带尾斜杠都不该拼出双斜杠——那是一条能打开但看着像故障的地址。 */
    @Test
    void toleratesATrailingSlashOnTheConfiguredWebsite() {
        assertThat(PricingDeeplink.of("https://vxture.com/", "tenderforge", "zh-CN"))
                .isEqualTo("https://vxture.com/zh-CN/pricing?product=tenderforge");
    }

    /**
     * 深链<strong>不带 workspace_id，也不带 intent</strong>。
     *
     * <p>前者会让一个可被伪造的查询参数决定给谁开通；后者是拼给自己看的——定价页只认 product。
     */
    @Test
    void carriesNothingButTheProduct() {
        String url = PricingDeeplink.of(WEBSITE, "tenderforge", "zh-CN");

        assertThat(url).doesNotContain("workspace").doesNotContain("intent");
        assertThat(url).doesNotContain("console.vxture.com").doesNotContain("/subscribe");
    }

    /** 产品码原样编码：目录里的码是小写 kebab，但编码这件事不该依赖它一直是。 */
    @Test
    void encodesTheProductCode() {
        assertThat(PricingDeeplink.of(WEBSITE, "a b&c", "zh-CN"))
                .isEqualTo("https://vxture.com/zh-CN/pricing?product=a+b%26c");
    }

    /** 用户显式选过的语言优先于浏览器偏好——界面是中文，定价页就该是中文。 */
    @Test
    void prefersTheLanguageTheUserChoseOverTheBrowserPreference() {
        assertThat(PricingDeeplink.localeFrom("en-US", "zh-CN,zh;q=0.9")).isEqualTo("en-US");
        assertThat(PricingDeeplink.localeFrom("zh-CN", "en-US,en;q=0.9")).isEqualTo("zh-CN");
    }

    /** 没选过就看浏览器：带权重的头按顺序取第一个官网有的。 */
    @Test
    void fallsBackToTheBrowserPreference() {
        assertThat(PricingDeeplink.localeFrom(null, "en-US,en;q=0.9")).isEqualTo("en-US");
        assertThat(PricingDeeplink.localeFrom("", "en-GB,en;q=0.8")).as("按主语言归位").isEqualTo("en-US");
        assertThat(PricingDeeplink.localeFrom(null, "zh-HK,zh;q=0.9,en;q=0.5")).isEqualTo("zh-CN");
    }

    /** 不认得的语言回到默认，而不是拼出一个官网没有的语言段。 */
    @Test
    void degradesAnUnknownLanguageToTheDefault() {
        assertThat(PricingDeeplink.localeFrom("fr-FR", "fr-FR,fr;q=0.9")).isEqualTo("zh-CN");
        assertThat(PricingDeeplink.localeFrom(null, null)).isEqualTo("zh-CN");
        assertThat(PricingDeeplink.of(WEBSITE, "tenderforge", "fr-FR"))
                .isEqualTo("https://vxture.com/zh-CN/pricing?product=tenderforge");
    }
}
