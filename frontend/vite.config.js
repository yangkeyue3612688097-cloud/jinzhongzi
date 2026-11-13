import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: true,
    proxy: {
      '/comfy': {
        target: 'http://127.0.0.1:8188',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/comfy/, '')
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
