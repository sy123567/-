import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // sockjs-client 依赖浏览器里不存在的 global，映射到 globalThis 避免运行时报错。
  define: {
    global: 'globalThis',
  },
})
