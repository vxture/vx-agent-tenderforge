// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-14
import { useSearchParams } from 'react-router'

import { ShellBrand } from '@vxture/design-system'

import { safeReturnTo } from './returnTo'

import './login.css'

/** 平台登记的产品名（vxture-platform#263）。登录页展示它，而不是内部代号。 */
const PRODUCT_NAME = '标书编写智能体'

/**
 * 产品登录页，照组织标准登录页（`vxtureagents/login-page.html`）实现：
 * 产品名、一句说明、一个「登录」、一行提示，背景是缓慢漂移的线条。
 *
 * 只有一种登录方式：平台账号（OIDC）。原先的「本地账号登录（过渡通道）」与
 * 「记住密码」已移除（owner 2026-09-14）——后者会把明文密码写进 localStorage。
 *
 * 登录是一次整页导航而不是 fetch：授权码流程要把浏览器交给身份服务、再由它送回
 * 回调地址，fetch 拿不到回调时种下的 cookie。所以「登录」是一个链接，不是按钮。
 */
export default function Login() {
  const [searchParams] = useSearchParams()
  const returnTo = safeReturnTo(searchParams.get('redirect'))
  const loginHref = `/api/auth/oidc/login?returnTo=${encodeURIComponent(returnTo)}`

  return (
    <main className="vx-gate">
      <div className="vx-gate__flow" aria-hidden="true">
        <svg viewBox="0 0 1600 1000" preserveAspectRatio="none">
          <path d="M-80 690 C220 430,360 790,650 570 S1080 300,1680 480" />
          <path d="M-100 760 C220 500,390 850,690 620 S1130 360,1700 540" />
          <path d="M-120 830 C230 570,420 900,720 675 S1180 420,1710 600" />
          <path d="M-60 600 C240 360,390 680,610 500 S1070 240,1640 410" />
        </svg>
      </div>
      <div className="vx-gate__wash" aria-hidden="true" />

      <section className="vx-gate__panel">
        <ShellBrand href="/" label={PRODUCT_NAME} className="vx-gate__brand" />

        <p className="vx-gate__message">请登录以验证您的订阅并访问产品。</p>

        <a className="vx-gate__action" href={loginHref}>
          登录
        </a>

        <p className="vx-gate__hint">登录后将自动返回当前产品</p>
      </section>
    </main>
  )
}
