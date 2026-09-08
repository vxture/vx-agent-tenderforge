// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import type { ComponentType } from 'react'
import type { RouteObject } from 'react-router'
import { createBrowserRouter, Navigate } from 'react-router'

import { ShellBootScreen } from '@vxture/design-system'

import { MainLayout } from '@/layouts'
import Forbidden from '@/pages/Forbidden'
import Login from '@/pages/Login'
import NotFound from '@/pages/NotFound'
import PortalRedirect from '@/pages/PortalRedirect'
import ServerError from '@/pages/ServerError'

import AuthGuard from './AuthGuard'
import RouteErrorPage from './RouteErrorPage'

type PageModule = { default: ComponentType }

const hydrateFallbackElement = (
  <ShellBootScreen label="TenderAgent" description="正在加载工作台" delayMs={250} />
)

const lazyPage = (loader: () => Promise<PageModule>) => async () => ({
  Component: (await loader()).default,
})

const lazyProtectedPage = (loader: () => Promise<PageModule>) => async () => {
  const Page = (await loader()).default
  return {
    Component: () => (
      <AuthGuard>
        <Page />
      </AuthGuard>
    ),
  }
}

const routes: RouteObject[] = [
  {
    path: '/',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement,
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <PortalRedirect /> },
      { path: 'planner', element: <Navigate to="/planner/writing" replace /> },
      {
        path: 'planner/writing',
        lazy: lazyPage(() => import('@/features/tender/WritingMethodPage')),
      },
      {
        path: 'planner/assets',
        lazy: lazyPage(() => import('@/features/tender/TenderAssetsPage')),
      },
      { path: 'planner/bids', lazy: lazyPage(() => import('@/features/tender/BidsPage')) },
      { path: 'planner/account', lazy: lazyPage(() => import('@/pages/PlannerAccount')) },
      { path: 'console', element: <Navigate to="/console/users" replace /> },
      { path: 'console/users', lazy: lazyPage(() => import('@/pages/AdminUsers')) },
      {
        path: 'console/audit-logs',
        lazy: lazyPage(() => import('@/pages/AdminAuditLogs')),
      },
      { path: 'admin/*', element: <Navigate to="/console/users" replace /> },
    ],
  },
  {
    path: '/planner/bids/new/setup',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement,
    lazy: lazyProtectedPage(() => import('@/features/tender/BidSetupPage')),
  },
  {
    path: '/planner/bids/:bidId/setup',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement,
    lazy: lazyProtectedPage(() => import('@/features/tender/BidSetupPage')),
  },
  {
    path: '/planner/bids/:bidId/interpretation',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement,
    lazy: lazyProtectedPage(() => import('@/features/tender/BidInterpretationPage')),
  },
  {
    path: '/planner/bids/:bidId/outline',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement,
    lazy: lazyProtectedPage(() => import('@/features/tender/BidOutlinePage')),
  },
  {
    path: '/planner/bids/:bidId/generating',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement,
    lazy: lazyProtectedPage(() => import('@/features/tender/BidGeneratingPage')),
  },
  {
    path: '/planner/bids/:bidId/content',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement,
    lazy: lazyProtectedPage(() => import('@/features/tender/BidContentPage')),
  },
  { path: '/login', element: <Login /> },
  { path: '/403', element: <Forbidden /> },
  { path: '/404', element: <NotFound /> },
  { path: '/500', element: <ServerError /> },
  { path: '*', element: <NotFound /> },
]

const router = createBrowserRouter(routes, { basename: import.meta.env.VITE_BASE_PATH || '/' })

export default router
