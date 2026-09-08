// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
import { type FormEvent, useState } from 'react'

import {
  ActionMenu,
  type ActionMenuItem,
  Banner,
  Button,
  DataTable,
  type DataTableColumn,
  EmptyState,
  FilterBar,
  Icon,
  Input,
  ListPageTemplate,
  NativeSelect,
  UserAvatar,
  ViewHeader,
} from '@vxture/design-system'

import { MutationError, QueryError } from '@/components/QueryState'
import { useAuthStore } from '@/stores/auth'
import type { ManagedUser, ManagedUserFilters } from '@/types/admin'

import { DeactivateUserDialog } from './components/DeactivateUserDialog'
import { StatusBadge } from './components/StatusBadge'
import { UserFormDialog, type UserFormValue } from './components/UserFormDialog'
import { formatDateTime } from './formatters'
import {
  useAdminUsersQuery,
  useCreateAdminUserMutation,
  useDeactivateAdminUserMutation,
  useUpdateAdminUserMutation,
} from './queries'

// 账号是有界集合，一次取完；limit 只是服务端钳制上限的本地表达，不是翻页。
const DEFAULT_FILTERS: ManagedUserFilters = {
  limit: 200,
  keyword: '',
  roleCode: '',
  enabled: '',
}

const roleLabel = (roleCode: ManagedUser['roleCode']) =>
  roleCode === 'ADMIN' ? '系统管理员' : '投标编制员'

export default function UsersPage() {
  const currentUser = useAuthStore((state) => state.user)
  const [filters, setFilters] = useState(DEFAULT_FILTERS)
  const [draft, setDraft] = useState(DEFAULT_FILTERS)
  const [dialogUser, setDialogUser] = useState<ManagedUser | null | undefined>(undefined)
  const [deactivateTarget, setDeactivateTarget] = useState<ManagedUser | null>(null)
  const [notice, setNotice] = useState('')
  const usersQuery = useAdminUsersQuery(filters)
  const createMutation = useCreateAdminUserMutation()
  const updateMutation = useUpdateAdminUserMutation()
  const deactivateMutation = useDeactivateAdminUserMutation()

  const submitFilters = (event: FormEvent) => {
    event.preventDefault()
    setFilters({ ...draft })
  }

  const resetFilters = () => {
    setDraft(DEFAULT_FILTERS)
    setFilters(DEFAULT_FILTERS)
  }

  const openCreate = () => {
    createMutation.reset()
    setDialogUser(null)
  }

  const openEdit = (user: ManagedUser) => {
    updateMutation.reset()
    setDialogUser(user)
  }

  /**
   * 创建或更新用户。
   * @preconditions - dialogUser 已指明新建或编辑模式
   * @sideEffects - 调用用户管理 API，并刷新列表和审计查询
   * @errorHandling - 失败时弹窗保持打开，由 MutationError 展示原因
   */
  const saveUser = async (value: UserFormValue) => {
    if (dialogUser === undefined) return
    if (dialogUser === null) {
      await createMutation.mutateAsync({
        username: value.username,
        displayName: value.displayName,
        roleCode: value.roleCode,
        password: value.password,
      })
      setNotice(`用户 ${value.displayName} 已创建`)
    } else {
      await updateMutation.mutateAsync({
        userId: dialogUser.id,
        input: {
          displayName: value.displayName,
          roleCode: value.roleCode,
          enabled: value.enabled,
          password: value.password || undefined,
          revision: dialogUser.revision,
        },
      })
      setNotice(`用户 ${value.displayName} 已更新`)
    }
    setDialogUser(undefined)
  }

  const enableUser = async (user: ManagedUser) => {
    updateMutation.reset()
    await updateMutation.mutateAsync({
      userId: user.id,
      input: {
        displayName: user.displayName,
        roleCode: user.roleCode,
        enabled: true,
        revision: user.revision,
      },
    })
    setNotice(`用户 ${user.displayName} 已启用`)
  }

  const confirmDeactivate = async () => {
    if (!deactivateTarget) return
    const displayName = deactivateTarget.displayName
    await deactivateMutation.mutateAsync(deactivateTarget.id)
    setDeactivateTarget(null)
    setNotice(`用户 ${displayName} 已停用`)
  }

  const users = usersQuery.data ?? []
  const columns: DataTableColumn<ManagedUser>[] = [
    {
      id: 'user',
      header: '用户',
      cell: (user) => {
        const isSelf = user.id === currentUser?.id
        return (
          <div className="flex items-center gap-sm">
            <UserAvatar alt={user.displayName} className="size-control-md" />
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-xs">
                <span className="text-label-md text-foreground">{user.displayName}</span>
                {isSelf ? <span className="text-body-sm text-primary-text">当前账号</span> : null}
              </div>
              <div className="mt-2xs text-body-sm text-muted-foreground">{user.username}</div>
            </div>
          </div>
        )
      },
    },
    {
      id: 'role',
      header: '角色',
      width: 'sm',
      cell: (user) => roleLabel(user.roleCode),
    },
    {
      id: 'status',
      header: '状态',
      align: 'center',
      width: 'xs',
      cell: (user) => (
        <StatusBadge
          label={user.enabled ? '已启用' : '已停用'}
          status={user.enabled ? '已启用' : '已停用'}
        />
      ),
    },
    {
      id: 'createdAt',
      header: '创建时间',
      width: 'md',
      cell: (user) => (
        <span className="whitespace-nowrap text-muted-foreground">
          {formatDateTime(user.createdAt)}
        </span>
      ),
    },
  ]

  const userActions = (user: ManagedUser) => {
    const isSelf = user.id === currentUser?.id
    const items: ActionMenuItem[] = [
      {
        id: 'edit',
        label: '编辑用户',
        icon: 'edit',
        onSelect: () => openEdit(user),
      },
    ]

    if (user.enabled) {
      items.push({
        id: 'deactivate',
        label: '停用用户',
        icon: 'prohibit',
        danger: true,
        separatorBefore: true,
        disabled: isSelf,
        hint: isSelf ? '不能停用当前账号' : undefined,
        confirmExempt: '停用用户由业务确认对话框二次确认并展示服务端失败原因',
        onSelect: () => {
          deactivateMutation.reset()
          setDeactivateTarget(user)
        },
      })
    } else {
      items.push({
        id: 'enable',
        label: '启用用户',
        icon: 'success',
        disabled: updateMutation.isPending,
        onSelect: () => void enableUser(user).catch(() => undefined),
      })
    }

    return <ActionMenu label={`${user.displayName}的操作`} align="end" items={items} />
  }

  const mutationError = updateMutation.error ?? deactivateMutation.error
  const summary =
    notice || mutationError ? (
      <div className="flex flex-col gap-sm">
        {notice ? (
          <Banner
            tone="success"
            title={notice}
            onDismiss={() => setNotice('')}
            dismissLabel="关闭提示"
          />
        ) : null}
        <MutationError error={mutationError} />
      </div>
    ) : undefined

  return (
    <>
      <ListPageTemplate
        header={
          <ViewHeader
            icon="users"
            title="账号管理"
            description="维护登录账号、业务角色和使用状态。"
            action={
              <Button size="sm" onClick={openCreate}>
                <Icon name="user-plus" size="sm" />
                新建用户
              </Button>
            }
          />
        }
        summary={summary}
        filters={
          <form onSubmit={submitFilters}>
            <FilterBar
              count={`共 ${users.length} 条`}
              onReset={resetFilters}
              resetLabel="重置筛选"
              search={
                <label className="relative block w-full sm:w-panel-sm">
                  <Icon
                    name="search"
                    size="sm"
                    className="pointer-events-none absolute left-sm top-1/2 -translate-y-1/2 text-muted-foreground"
                  />
                  <Input
                    value={draft.keyword}
                    onChange={(event) => setDraft({ ...draft, keyword: event.target.value })}
                    className="pl-xl"
                    placeholder="搜索姓名或用户名"
                  />
                </label>
              }
              actions={
                <Button type="submit" variant="outline">
                  <Icon name="search" size="sm" />
                  查询
                </Button>
              }
            >
              <NativeSelect
                value={draft.roleCode}
                wrapperClassName="w-panel-sm"
                aria-label="角色筛选"
                onChange={(event) =>
                  setDraft({
                    ...draft,
                    roleCode: event.target.value as ManagedUserFilters['roleCode'],
                  })
                }
              >
                <option value="">全部角色</option>
                <option value="ADMIN">系统管理员</option>
                <option value="PLANNER">投标编制员</option>
              </NativeSelect>
              <NativeSelect
                value={draft.enabled}
                wrapperClassName="w-panel-sm"
                aria-label="状态筛选"
                onChange={(event) =>
                  setDraft({
                    ...draft,
                    enabled: event.target.value as ManagedUserFilters['enabled'],
                  })
                }
              >
                <option value="">全部状态</option>
                <option value="true">已启用</option>
                <option value="false">已停用</option>
              </NativeSelect>
            </FilterBar>
          </form>
        }
        table={
          usersQuery.isError ? (
            <QueryError error={usersQuery.error} retry={() => void usersQuery.refetch()} />
          ) : (
            <DataTable
              columns={columns}
              rows={users}
              rowKey={(user) => user.id}
              loading={usersQuery.isLoading}
              loadingRows={10}
              rowActions={userActions}
              labels={{ rowActions: '操作' }}
              empty={
                <EmptyState
                  icon="users"
                  title="没有符合条件的用户"
                  description="调整筛选条件后重新查询。"
                />
              }
            />
          )
        }
        footer={
          users.length >= filters.limit ? (
            <p className="text-sm text-muted-foreground">
              仅显示前 {filters.limit} 个账号，请用筛选条件缩小范围。
            </p>
          ) : undefined
        }
      />

      {dialogUser !== undefined && currentUser ? (
        <UserFormDialog
          key={dialogUser?.id ?? 'new-user'}
          user={dialogUser}
          currentUserId={currentUser.id}
          submission={{
            pending: dialogUser ? updateMutation.isPending : createMutation.isPending,
            error: dialogUser ? updateMutation.error : createMutation.error,
          }}
          onClose={() => setDialogUser(undefined)}
          onSubmit={saveUser}
        />
      ) : null}

      {deactivateTarget ? (
        <DeactivateUserDialog
          user={deactivateTarget}
          pending={deactivateMutation.isPending}
          error={deactivateMutation.error}
          onClose={() => setDeactivateTarget(null)}
          onConfirm={confirmDeactivate}
        />
      ) : null}
    </>
  )
}
