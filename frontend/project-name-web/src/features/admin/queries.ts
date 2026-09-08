// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-01
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { adminApi } from '@/api/modules/admin'
import type {
  AuditLogFilters,
  CreateManagedUserInput,
  ManagedUserFilters,
  UpdateManagedUserInput,
} from '@/types/admin'

const adminKeys = {
  users: (filters: ManagedUserFilters) => ['admin-users', filters] as const,
  user: (userId: string) => ['admin-user', userId] as const,
  auditLogs: (filters: AuditLogFilters) => ['admin-audit-logs', filters] as const,
}

export const useAdminUsersQuery = (filters: ManagedUserFilters) =>
  useQuery({ queryKey: adminKeys.users(filters), queryFn: () => adminApi.listUsers(filters) })

export function useCreateAdminUserMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateManagedUserInput) => adminApi.createUser(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      void queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
    },
  })
}

export function useUpdateAdminUserMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, input }: { userId: string; input: UpdateManagedUserInput }) =>
      adminApi.updateUser(userId, input),
    onSuccess: (user) => {
      queryClient.setQueryData(adminKeys.user(user.id), user)
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      void queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
    },
  })
}

export function useDeactivateAdminUserMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => adminApi.deactivateUser(userId),
    onSuccess: (user) => {
      queryClient.setQueryData(adminKeys.user(user.id), user)
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      void queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
    },
  })
}

export const useAuditLogsQuery = (filters: AuditLogFilters) =>
  useQuery({
    queryKey: adminKeys.auditLogs(filters),
    queryFn: () => adminApi.listAuditLogs(filters),
  })
