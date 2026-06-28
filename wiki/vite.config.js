import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 内容已全部迁移到数据库，运行时通过 /api/wiki/manifest 与 /api/wiki/page 获取，
// 不再有构建期的 public/docs → wiki.data.js 流水线。
// https://vitejs.dev/config/
export default defineConfig({
  // base: '/SurviveXMUM/', // <--- 部署到子路径时取消注释
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
        target: 'http://localhost:8080', // 你的后端 API 地址
        changeOrigin: true, // 需要虚拟主机站点
        rewrite: (p) => p.replace(/^\/api/, ''), // 转发时移除 /api 前缀
      },
    },
  },
})
