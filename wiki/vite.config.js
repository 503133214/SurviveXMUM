import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  base: '/SurviveXMUM/', // <--- 添加此行
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
      '@': '/src', // 如果你使用了 @ 别名，请确保已设置
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
