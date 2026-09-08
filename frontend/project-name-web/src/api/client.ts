// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
import { storage } from '@/utils/storage'

type ApiEnvelope<T> = {
  success: boolean
  data: T
  errorCode: string | null
  message: string
  traceId: string
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  formData?: FormData
  authenticated?: boolean
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errorCode: string,
    readonly traceId?: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const baseUrl = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '')

const unauthorized = () => {
  storage.remove('token')
  storage.remove('userInfo')
  const redirect = window.location.pathname + window.location.search
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
  }
}

/**
 * 调用 Java 业务 API 并解包统一响应。
 * @preconditions - path 为 /api 开头的站内业务路径
 * @sideEffects - 发起网络请求；登录失效时清理本地会话并跳转登录页
 * @errorHandling - HTTP 或业务失败统一抛出 ApiError，保留 errorCode 与 traceId
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const authenticated = options.authenticated !== false
  const token = storage.get('token')
  const headers = new Headers()
  if (authenticated && token) headers.set('Authorization', `Bearer ${token}`)
  if (!options.formData) headers.set('Content-Type', 'application/json')

  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.formData
      ? options.formData
      : options.body === undefined
        ? undefined
        : JSON.stringify(options.body),
  })
  const payload = (await response.json().catch(() => null)) as ApiEnvelope<T> | null
  if (response.status === 401 && authenticated) unauthorized()
  if (!response.ok || !payload?.success) {
    throw new ApiError(
      payload?.message || `请求失败（${response.status}）`,
      response.status,
      payload?.errorCode || 'HTTP_ERROR',
      payload?.traceId
    )
  }
  return payload.data
}

export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const response = await fetch(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${storage.get('token')}` },
  })
  if (response.status === 401) unauthorized()
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ApiEnvelope<null> | null
    throw new ApiError(
      payload?.message || '文件下载失败',
      response.status,
      payload?.errorCode || 'DOWNLOAD_FAILED'
    )
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fallbackName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
}

/**
 * 读取需要 Bearer 鉴权的图片或文件，并返回浏览器 Blob。
 * @preconditions - path 为当前账号有权读取的站内文件接口
 * @sideEffects - 发起网络请求；登录失效时清理会话并跳转登录页
 * @errorHandling - 非成功响应转换为 ApiError，不暴露服务端对象键
 */
export async function fetchProtectedBlob(path: string): Promise<Blob> {
  const response = await fetch(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${storage.get('token')}` },
  })
  if (response.status === 401) unauthorized()
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ApiEnvelope<null> | null
    throw new ApiError(
      payload?.message || '图片读取失败',
      response.status,
      payload?.errorCode || 'IMAGE_READ_FAILED',
      payload?.traceId
    )
  }
  return response.blob()
}
