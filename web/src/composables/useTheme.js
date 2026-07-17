import { ref, watchEffect } from 'vue'

const STORAGE_KEY = 'theme'

const theme = ref(localStorage.getItem(STORAGE_KEY) || 'system')
const isDark = ref(false)

let mediaQuery = null

function getSystemDark() {
  if (typeof window === 'undefined') return false
  if (!mediaQuery) {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  }
  return mediaQuery.matches
}

export function useTheme() {
  function applyTheme() {
    const dark = theme.value === 'dark' || (theme.value === 'system' && getSystemDark())
    isDark.value = dark
    document.documentElement.classList.toggle('dark', dark)
  }

  watchEffect(applyTheme)

  if (typeof window !== 'undefined' && mediaQuery) {
    mediaQuery.addEventListener('change', applyTheme)
  }

  function toggle() {
    theme.value = isDark.value ? 'light' : 'dark'
    localStorage.setItem(STORAGE_KEY, theme.value)
  }

  function setMode(mode) {
    theme.value = mode
    localStorage.setItem(STORAGE_KEY, mode)
  }

  return { theme, isDark, toggle, setMode }
}
