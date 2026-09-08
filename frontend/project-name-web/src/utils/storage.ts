// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-27
export const storage = {
  get(key: string) {
    return window.localStorage.getItem(key) ?? ''
  },
  set(key: string, value: string) {
    window.localStorage.setItem(key, value)
  },
  remove(key: string) {
    window.localStorage.removeItem(key)
  },
  clear() {
    window.localStorage.clear()
  },
}
