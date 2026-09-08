// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-27
import { Navigate } from 'react-router'

import { useAuthStore } from '@/stores/auth'

export default function PortalRedirect() {
  const user = useAuthStore((state) => state.user)
  return <Navigate to={user?.admin ? '/console/users' : '/planner/writing'} replace />
}
