// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29

/**
 * 将服务端日期时间压缩为业务界面使用的分钟精度。
 * @preconditions - value 为 ISO 日期时间、数据库日期时间或空值
 * @sideEffects - 无
 * @errorHandling - 无法识别时保留原值，避免隐藏服务端数据
 */
export function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  return matched ? `${matched[1]}-${matched[2]}-${matched[3]} ${matched[4]}:${matched[5]}` : value
}
