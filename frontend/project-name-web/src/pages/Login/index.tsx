// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'

import {
  Banner,
  Button,
  Card,
  CardContent,
  CardHeader,
  Checkbox,
  Field,
  FieldGroup,
  FieldLabel,
  Icon,
  Input,
  Spinner,
} from '@vxture/design-system'

import { authApi } from '@/api/modules/auth'
import { useAuthStore } from '@/stores/auth'
import { storage } from '@/utils/storage'

const REMEMBERED_LOGIN_KEY = 'tender-agent-remembered-login'

type RememberedLogin = {
  username: string
  password: string
}

const readRememberedLogin = (): RememberedLogin | null => {
  try {
    const raw = storage.get(REMEMBERED_LOGIN_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<RememberedLogin>
    if (
      typeof parsed.username !== 'string' ||
      typeof parsed.password !== 'string' ||
      !parsed.username ||
      !parsed.password
    ) {
      return null
    }
    return { username: parsed.username, password: parsed.password }
  } catch {
    return null
  }
}

const updateRememberedLogin = (
  rememberPassword: boolean,
  username: string,
  password: string
) => {
  try {
    if (rememberPassword) {
      storage.set(REMEMBERED_LOGIN_KEY, JSON.stringify({ username, password }))
    } else {
      storage.remove(REMEMBERED_LOGIN_KEY)
    }
  } catch {
    // 本地存储不可用时不影响正常登录。
  }
}

export default function Login() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const setSession = useAuthStore((state) => state.setSession)
  const [rememberedLogin] = useState(readRememberedLogin)
  const [username, setUsername] = useState(rememberedLogin?.username ?? '')
  const [password, setPassword] = useState(rememberedLogin?.password ?? '')
  const [rememberPassword, setRememberPassword] = useState(Boolean(rememberedLogin))
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [showLocalForm, setShowLocalForm] = useState(false)

  const requestedRedirect = searchParams.get('redirect')
  const returnTo =
    requestedRedirect?.startsWith('/') && !requestedRedirect.startsWith('/login')
      ? requestedRedirect
      : '/'

  /**
   * 平台登录是一次<b>整页导航</b>，不是 fetch。
   *
   * 授权码流程要把浏览器交给 IdP，再由 IdP 把它送回我们的回调地址；
   * 用 fetch 发起会被同源策略挡住，而且拿不到回调时种下的 cookie。
   * 所以这里换的是 window.location，不是路由跳转。
   */
  const signInWithPlatform = () => {
    window.location.href = `/api/auth/oidc/login?returnTo=${encodeURIComponent(returnTo)}`
  }

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const normalizedUsername = username.trim()
    if (!normalizedUsername || !password) return
    setSubmitting(true)
    setError('')
    try {
      const result = await authApi.login(normalizedUsername, password)
      setSession(result.token, result.user)
      updateRememberedLogin(rememberPassword, normalizedUsername, password)
      const fallback = result.user.admin ? '/console/users' : '/planner/writing'
      navigate(returnTo === '/' ? fallback : returnTo, { replace: true })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '登录失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center bg-background px-page-inset py-3xl">
      <Card className="w-full max-w-panel-md border border-border" surface="strong">
        <CardHeader className="flex-row items-center gap-sm border-b border-border pb-lg">
          <span className="flex size-control-xl items-center justify-center rounded-md bg-primary text-primary-foreground">
            <Icon name="file-text" size="md" />
          </span>
          <h1 className="vx-brand-name">TenderAgent</h1>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-md">
            <Button size="lg" className="w-full" onClick={signInWithPlatform}>
              <Icon name="sign-in" size="sm" />
              使用平台账号登录
            </Button>
            {/*
              本地口令入口是过渡通道，不是并列的第二种登录方式。
              标注出来而不是让它看起来同等重要：平台身份接通并验证后，
              连同 app_user 一起退役。
            */}
            {showLocalForm ? null : (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="w-full text-muted-foreground"
                onClick={() => setShowLocalForm(true)}
              >
                使用本地账号登录（过渡通道）
              </Button>
            )}
          </div>
          {showLocalForm ? (
          <form autoComplete="off" onSubmit={submit} className="mt-lg border-t border-border pt-lg">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="login-username">账号</FieldLabel>
                <Input
                  id="login-username"
                  name="username"
              autoFocus
              autoComplete="off"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
              </Field>
              <Field>
                <FieldLabel htmlFor="login-password">密码</FieldLabel>
                <span className="relative block">
                  <Input
                    id="login-password"
                    name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                    className="pr-control-xl"
              />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-md"
                    className="absolute inset-y-none right-none"
                onClick={() => setShowPassword((current) => !current)}
                aria-label={showPassword ? '隐藏密码' : '显示密码'}
                title={showPassword ? '隐藏密码' : '显示密码'}
              >
                    <Icon name={showPassword ? 'eye-slash' : 'eye'} size="sm" />
                  </Button>
                </span>
              </Field>
              <label className="flex w-fit items-center gap-xs text-body-sm text-muted-foreground">
                <Checkbox
                  checked={rememberPassword}
                  disabled={submitting}
                  onCheckedChange={(checked) => {
                    const enabled = checked === true
                    setRememberPassword(enabled)
                    if (!enabled) updateRememberedLogin(false, '', '')
                  }}
                />
                <span>记住密码</span>
              </label>
              {error ? <Banner tone="danger" title={error} /> : null}
              <Button type="submit" size="lg" className="w-full" disabled={submitting}>
                {submitting ? <Spinner size="sm" /> : <Icon name="sign-in" size="sm" />}
                登录
              </Button>
            </FieldGroup>
          </form>
          ) : null}
        </CardContent>
      </Card>
    </main>
  )
}
