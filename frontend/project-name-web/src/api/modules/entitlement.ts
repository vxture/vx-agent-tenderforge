// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { apiRequest } from '@/api/client'
import type { EntitlementView } from '@/types/entitlement'

export const entitlementApi = {
  current: () => apiRequest<EntitlementView>('/api/entitlement'),
}
