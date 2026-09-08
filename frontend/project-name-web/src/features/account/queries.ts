// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { useMutation } from '@tanstack/react-query'

import { accountApi } from '@/api/modules/auth'

export const useUploadAvatarMutation = () =>
  useMutation({ mutationFn: (file: File) => accountApi.uploadAvatar(file) })

export const useUpdateProfileMutation = () =>
  useMutation({ mutationFn: (displayName: string) => accountApi.updateProfile(displayName) })

export const useChangePasswordMutation = () =>
  useMutation({
    mutationFn: (input: { currentPassword: string; newPassword: string }) =>
      accountApi.changePassword(input.currentPassword, input.newPassword),
  })
