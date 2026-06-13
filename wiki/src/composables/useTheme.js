// Light / dark theme management. Persists to localStorage, follows the OS on
// first visit, and toggles the `dark` class on <html> (which both our design
// tokens and Element Plus dark mode key off).
import { ref } from 'vue'

const STORAGE_KEY = 'wiki-theme'
const isDark = ref(false)

function apply(dark) {
  isDark.value = dark
  const html = document.documentElement
  html.classList.toggle('dark', dark)
}

export function initTheme() {
  const saved = localStorage.getItem(STORAGE_KEY)
  const prefersDark =
    window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
  apply(saved ? saved === 'dark' : prefersDark)
}

export function useTheme() {
  function toggleTheme() {
    apply(!isDark.value)
    localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light')
  }
  return { isDark, toggleTheme }
}
