import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/invoices': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/approvals': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/governance': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../../resources/static',
    emptyOutDir: true,
  },
})
