// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
import { useEffect, useMemo, useState } from 'react'
import { isRouteErrorResponse, useRouteError } from 'react-router'

import {
  Button,
  Icon,
  ResultPageTemplate,
  Spinner,
} from '@vxture/design-system'

const CHUNK_RELOAD_WINDOW_MS = 30_000
const CHUNK_ERROR_PATTERN =
  /Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk .* failed|ChunkLoadError/i

export default function RouteErrorPage() {
  const error = useRouteError()
  const message = useMemo(() => errorMessage(error), [error])
  const staleChunk = CHUNK_ERROR_PATTERN.test(message)
  const [reloading, setReloading] = useState(staleChunk)

  useEffect(() => {
    if (!staleChunk) return
    const key = reloadKey()
    const lastReload = Number(sessionStorage.getItem(key) ?? 0)
    if (Date.now() - lastReload <= CHUNK_RELOAD_WINDOW_MS) {
      setReloading(false)
      return
    }
    sessionStorage.setItem(key, String(Date.now()))
    window.location.reload()
  }, [staleChunk])

  const reload = () => {
    sessionStorage.removeItem(reloadKey())
    setReloading(true)
    window.location.reload()
  }

  return (
    <ResultPageTemplate
      className="min-h-dvh"
      tone="warning"
      icon={reloading ? 'spinner' : 'warning'}
      title={reloading ? '正在加载最新版本' : '页面加载失败'}
      description={
        staleChunk
          ? '平台已更新，正在重新载入最新页面。'
          : '当前页面暂时无法打开，请刷新后重试。'
      }
      actions={
        reloading ? (
          <Spinner label="正在重新加载" />
        ) : (
          <Button onClick={reload}>
            <Icon name="refresh" size="sm" />
            重新加载
          </Button>
        )
      }
    />
  )
}

function errorMessage(error: unknown) {
  if (isRouteErrorResponse(error)) return `${error.status} ${error.statusText}`
  if (error instanceof Error) return error.message
  return String(error ?? '')
}

function reloadKey() {
  return `tenderagent:chunk-reload:${window.location.pathname}`
}
