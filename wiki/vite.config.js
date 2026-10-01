import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 内容已全部迁移到数据库，运行时通过 /api/wiki/manifest 与 /api/wiki/page 获取，
// 不再有构建期的 public/docs → wiki.data.js 流水线。
//
// 联调目标默认是本机后端。只改界面时不必起后端，直接读线上内容：
//   WIKI_API_TARGET=https://surivivexmum.wiki/api npm run dev
const API_TARGET = process.env.WIKI_API_TARGET || 'http://localhost:8080'
const REMOTE_API = !/\/\/(localhost|127\.0\.0\.1)(:|\/|$)/.test(API_TARGET)

// 连线上接口时去掉 track=1，本地调试不计入线上的浏览量
function stripTracking(p) {
  return p.replace(/([?&])track=1(&|$)/, (_m, lead, tail) => (tail ? lead : ''))
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
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
        target: API_TARGET,
        changeOrigin: true, // 需要虚拟主机站点
        rewrite: (p) => {
          const stripped = p.replace(/^\/api/, '') // 转发时移除 /api 前缀
          return REMOTE_API ? stripTracking(stripped) : stripped
        },
      },
    },
  },
})
