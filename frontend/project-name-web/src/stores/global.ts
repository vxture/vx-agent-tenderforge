// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

interface ThemeConfig {
  isCollapsed: boolean
}

interface GlobalState {
  themeConfig: ThemeConfig
  setThemeConfig: (config: Partial<ThemeConfig>) => void
}

export const useGlobalStore = create<GlobalState>()(
  persist(
    (set) => ({
      themeConfig: {
        isCollapsed: false,
      },
      setThemeConfig: (config) =>
        set((state) => ({
          themeConfig: { ...state.themeConfig, ...config },
        })),
    }),
    {
      name: 'global-storage',
      storage: createJSONStorage(() => localStorage),
    }
  )
)
