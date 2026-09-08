// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
import { storage } from '@/utils/storage'

/**
 * 平台统一错误封套（产品接入通则 X-1）。
 *
 * `retryable` 不是可选的：把它当作「可能不存在」来读，等于悄悄把这个字段要终结的
 * 「按状态码猜重试」又请了回来。传输层根本没到达应用（代理 502、超时）时读不到 body，
 * 那一种由 `isRetryable` 兜底，而不是把类型放松成可选。
 */
type ErrorEnvelope = {
  code: string
  message: string
  retryable: boolean
  field?: string
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  formData?: FormData
  authenticated?: boolean
  /**
   * 401 时是否触发全局跳转登录页。
   *
   * 默认触发。鉴权守卫要设成 'ignore'：它<b>本来就是在问「我登录了吗」</b>，
   * 得到「没有」是一个正常答案，不是一次会话失效。让它走全局跳转会把
   * 首次访问变成一次整页刷新，而软跳转本可以在应用内完成。
   */
  onUnauthorized?: 'redirect' | 'ignore'
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    /** 被调方自己的答复，照读即可，不要按状态码另行推断 */
    readonly retryable: boolean,
    readonly field?: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 跨平面同义的四个拒绝码 + 舰队约定的限流码（X-1）。 */
export const REJECTION_CODES = [
  'NOT_ENTITLED',
  'POLICY_DENIED',
  'APPROVAL_REQUIRED',
  'QUOTA_EXCEEDED',
  'RATE_LIMITED',
] as const

export type RejectionCode = (typeof REJECTION_CODES)[number]

export const isRejection = (error: unknown): error is ApiError =>
  error instanceof ApiError && (REJECTION_CODES as readonly string[]).includes(error.code)

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
 * 只有传输层失败时才按状态码猜；一旦拿到带 `retryable` 的封套，以封套为准。
 * 包括封套在 429 上说 false 的情况——商业配额上限确实会以 429 到达，而它重试无益。
 */
const inferRetryable = (envelope: Partial<ErrorEnvelope> | null, status: number): boolean => {
  if (typeof envelope?.retryable === 'boolean') return envelope.retryable
  return status === 429 || (status >= 500 && status < 600)
}

const toApiError = (envelope: Partial<ErrorEnvelope> | null, status: number, fallback: string) =>
  new ApiError(
    envelope?.message || fallback,
    status,
    envelope?.code || 'TRANSPORT_ERROR',
    inferRetryable(envelope, status),
    envelope?.field
  )

/**
 * 调用业务 API。成功时直接返回载荷本体——服务端不再包 `{success, data}` 空壳信封。
 * @preconditions - path 为 /api 开头的站内业务路径
 * @sideEffects - 发起网络请求；登录失效时清理本地会话并跳转登录页
 * @errorHandling - 失败抛 ApiError，携带 code / retryable / field 三项契约字段
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

  if (response.status === 401 && authenticated && options.onUnauthorized !== 'ignore') {
    unauthorized()
  }
  if (!response.ok) {
    const envelope = (await response.json().catch(() => null)) as ErrorEnvelope | null
    throw toApiError(envelope, response.status, `请求失败（${response.status}）`)
  }
  // 204 与空体：没有载荷要回显的操作不返回一个空对象来占位。
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const response = await fetch(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${storage.get('token')}` },
  })
  if (response.status === 401) unauthorized()
  if (!response.ok) {
    const envelope = (await response.json().catch(() => null)) as ErrorEnvelope | null
    throw toApiError(envelope, response.status, '文件下载失败')
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
    const envelope = (await response.json().catch(() => null)) as ErrorEnvelope | null
    throw toApiError(envelope, response.status, '图片读取失败')
  }
  return response.blob()
}
