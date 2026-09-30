// Light / dark theme management. Persists to localStorage, follows the OS on
// first visit, and toggles the `dark` class on <html> (which both our design
// tokens and Element Plus dark mode key off).
import { ref } from 'vue'

const STORAGE_KEY = 'wiki-theme'
const isDark = ref(false)

// 与 global.css 里两套主题的 --bg-page 保持一致，手机浏览器的地址栏颜色跟着变
const THEME_COLORS = { light: '#ffffff', dark: '#1b1b1f' }

function apply(dark) {
  isDark.value = dark
  const html = document.documentElement
  html.classList.toggle('dark', dark)
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', dark ? THEME_COLORS.dark : THEME_COLORS.light)
}

function readSaved() {
  try { return localStorage.getItem(STORAGE_KEY) } catch { return null }
}

export function initTheme() {
  const saved = readSaved()
  const prefersDark =
    window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
  apply(saved ? saved === 'dark' : prefersDark)
}

export function useTheme() {
  function commit() {
    apply(!isDark.value)
    try { localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light') } catch { /* 隐私模式 */ }
  }

  // 传入点击事件时，从按钮位置以圆形揭开新主题（View Transitions API）。
  // 不支持或用户要求减少动效时直接切换。
  function toggleTheme(event) {
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (!document.startViewTransition || reduce || !event) {
      commit()
      return
    }
    // 键盘触发时没有指针坐标，从按钮中心展开
    const rect = event.currentTarget?.getBoundingClientRect?.()
    const fromPointer = event.detail > 0 && event.clientX != null
    const x = fromPointer ? event.clientX : rect ? rect.left + rect.width / 2 : window.innerWidth / 2
    const y = fromPointer ? event.clientY : rect ? rect.top + rect.height / 2 : 0
    const r = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
    const transition = document.startViewTransition(commit)
    transition.ready.then(() => {
      document.documentElement.animate(
        { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`] },
        { duration: 480, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', pseudoElement: '::view-transition-new(root)' },
      )
    }).catch(() => {})
  }

  return { isDark, toggleTheme }
}
