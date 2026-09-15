// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-28
import { Outlet, useLocation } from 'react-router'

import { ShellViewport, useBreakpoint } from '@vxture/design-system'

import { SubscriptionGate } from '@/features/entitlement/SubscriptionGate'
import { useGlobalStore } from '@/stores/global'

import LayoutHeader from './Header'
import LayoutMenu from './Menu'

/**
 * 不经订阅闸门的页面。个人资料归平台账号，与是否订阅本产品无关。
 *
 * 闸门包在整个布局外面：「当前工作区未订阅」是门禁页，不渲染产品外壳（门禁页规范）——
 * 没有可导航的东西。退出登录在门禁页自己的动作块里。
 */
const UNGATED_PATHS = ['/planner/account']

/**
 * 主布局组件
 * 用户端与系统管理端共用稳定的导航框架。
 */
export default function MainLayout() {
  const location = useLocation()
  const { isLg } = useBreakpoint()
  const { themeConfig } = useGlobalStore()
  const isCollapsed = themeConfig.isCollapsed
  const showPortalNavigation =
    location.pathname.startsWith('/console') || location.pathname.startsWith('/planner')
  const sidebarMode =
    showPortalNavigation && isLg ? (isCollapsed ? 'collapsed' : 'expanded') : 'hidden'

  const shell = (
    <div id="tenderagent-app-shell" className="h-dvh min-h-0">
      <ShellViewport
        className="h-full"
        header={<LayoutHeader />}
        sidebar={
          <div className="h-full border-r border-border">
            <LayoutMenu variant="sidebar" />
          </div>
        }
        sidebarMode={sidebarMode}
      >
        <div className="flex min-h-full min-w-0 flex-col">
          {showPortalNavigation ? (
            <div className="shrink-0 border-b border-border bg-card lg:hidden">
              <LayoutMenu variant="mobile" />
            </div>
          ) : null}
          <div className="min-h-0 min-w-0 flex-1">
            <Outlet />
          </div>
        </div>
      </ShellViewport>
    </div>
  )

  return UNGATED_PATHS.includes(location.pathname) ? shell : <SubscriptionGate>{shell}</SubscriptionGate>
}
