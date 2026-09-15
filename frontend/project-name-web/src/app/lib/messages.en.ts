// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { Dictionary } from './i18n/dictionary'

// en-US 词典（参照 vx-agent-yucer app/(app)/lib/messages.en.ts）。
//
// 每一项都按中文那一项做类型检查：改了键名构建就断，函数也不能悄悄改掉参数个数。
// 门禁页的常量全部译完——这几页是英文读者最先看到的东西。

export const en: Dictionary = {
  SHELL_TEXT: {
    brandName: 'TenderForge',
    website: 'ruyin',
    workspaceFallback: 'Current workspace',
    identityCardLabel: 'Signed-in person and workspace',
    noAccessTitle: 'This workspace has no subscription',
    subscribeCta: 'Subscribe',
    noRolesTitle: 'No role has been assigned to you yet',
    noRolesDescription: 'Ask whoever opened the subscription to assign you a role.',
  },
  SIGNIN_TEXT: {
    cta: 'Sign in',
    ariaLabel: 'Sign in',
    title: 'Welcome',
    description: 'An intelligent workbench for the people who write bids',
    chainLabel: 'The flow',
    chain: ['Tender file', 'Scoring', 'Sign-off', 'Outline', 'Drafting', 'Review', 'Layout', 'Delivery'],
  },
  NO_SUBSCRIPTION_TEXT: {
    badge: 'No subscription',
    description: 'Subscribe to continue, or ask a workspace administrator to.',
    ariaLabel: 'This workspace has no subscription',
    signOut: 'Sign out',

    lapsedTitle: "This workspace's subscription has lapsed",
    lapsedDescription: 'Renew to keep using the product.',
    retentionUntil: (date: string) => `Existing bids are kept until ${date}.`,
    renewCta: 'Renew',
    statusLabels: {
      expired: 'expired',
      cancelled: 'cancelled',
      suspended: 'suspended',
      overdue: 'overdue',
    },
    lapsedBadge: (status: string) => `Subscription ${status}`,

    unknownTierBadge: 'Unknown tier',
    unknownTierTitle: 'This subscription tier is not supported yet',
    unknownTierDescription: (tier: string) => `This product does not recognise the tier "${tier}". Ask an administrator.`,
    viewSubscriptionCta: 'View subscription',

    unavailableBadge: 'Unknown',
    unavailableTitle: 'Subscription status is unavailable right now',
    unavailableDescription: 'This does not mean anything is wrong with your subscription. Try again shortly.',
    retry: 'Try again',
  },
  SIGNED_OUT_TEXT: {
    ariaLabel: 'Signed out',
    title: 'You are signed out',
    description: 'On a shared computer, sign out of the browser account too.',
    signInAgain: 'Sign in again',
    toConsole: 'Go to the account console',
  },
  NO_ROLES_TEXT: {
    badge: 'No role',
    ariaLabel: 'No role has been assigned to you yet',
    recheck: 'Check again',
    signOut: 'Sign out',
  },
  GATE_PREVIEW_TEXT: {
    ariaLabel: 'Choose a screen to preview',
    signIn: 'Front door',
    noSubscription: 'No subscription',
    noRoles: 'No role',
    signedOut: 'Signed out',
    sampleUser: 'Sample member',
    sampleOrg: 'Sample organization',
    sampleWorkspace: 'Sample workspace',
  },
  HEADER_TEXT: {
    prefTitle: 'Preferences',
    prefLocale: 'Language',
    prefTheme: 'Theme',
    prefThemeLight: 'Light',
    prefThemeDark: 'Dark',
    fullscreen: 'Full screen',
    fullscreenExit: 'Exit full screen',
  },
}
