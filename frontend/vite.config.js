import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backend = process.env.GTC_BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  base: '/gtc/',
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/invoices': backend,
      '/governance': backend,
      '/approvals': backend,
      '/workflow-demo': backend,
    },
  },
  build: {
    outDir: '../app/src/main/resources/static/gtc',
    emptyOutDir: true,
    sourcemap: true,
  },
})
