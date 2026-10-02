// 亮 / 暗主题：记在 localStorage，首次访问跟随系统，切换的是 <html> 上的
// dark 类（我们的设计令牌和 Element Plus 的暗色变量都以它为准）。
import { ref } from 'vue'

const STORAGE_KEY = 'wiki-theme'
const isDark = ref(false)

// 与 global.css 两套主题的 --header-bg 保持一致：手机浏览器的地址栏和顶栏的
// 实色蓝连成一条，不会出现白色地址栏接蓝色顶栏的断层
const THEME_COLORS = { light: '#1f4287', dark: '#1b2d55' }

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

  // 主题立即切换，不再做圆形揭幕动画：切主题是为了看清内容，动画只会拖慢它。
  // 保留事件参数是因为顶栏仍以 toggleTheme($event) 调用，这里用不到它。
  function toggleTheme(_event) {
    commit()
  }

  return { isDark, toggleTheme }
}
