import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { generateManifest } from './scripts/generate-manifest.mjs'

// Watches public/docs and live-regenerates src/wiki.data.js so adding or editing
// a markdown file instantly updates the sidebar / home / search via HMR.
function wikiContentWatcher() {
  const DOCS_DIR = path.resolve(__dirname, 'public/docs')
  let rebuildTimer = null
  const rebuild = (server, reason) => {
    clearTimeout(rebuildTimer)
    rebuildTimer = setTimeout(() => {
      try {
        const m = generateManifest({ useGit: false }) // mtime is enough in dev (fast)
        server.config.logger.info(
          `\x1b[36m[wiki]\x1b[0m 内容已更新 (${reason}) → ${m.pages.length} 篇文档`
        )
      } catch (e) {
        server.config.logger.error(`[wiki] 生成清单失败: ${e.message}`)
      }
    }, 80)
  }
  return {
    name: 'wiki-content-watcher',
    apply: 'serve',
    configureServer(server) {
      server.watcher.add(DOCS_DIR)
      const onChange = (file) => {
        if (!file.startsWith(DOCS_DIR)) return
        if (/\.(md|json)$/i.test(file)) rebuild(server, path.basename(file))
      }
      server.watcher.on('add', onChange)
      server.watcher.on('change', onChange)
      server.watcher.on('unlink', onChange)
    },
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  // base: '/SurviveXMUM/', // <--- 部署到子路径时取消注释
  plugins: [
    vue(),
    wikiContentWatcher(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // 将 /api 开头的请求代理到你的后端服务器
      '/api': {
        target: 'http://localhost:8080', // 你的后端 API 地址
        changeOrigin: true, // 需要虚拟主机站点
        rewrite: (path) => path.replace(/^\/api/, ''), // 转发时移除 /api 前缀
      },
    },
  },
})
