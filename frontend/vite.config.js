import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backend = process.env.GTC_BACKEND_URL || 'http://localhost:8080'

export default defineConfig(({ command }) => ({
  plugins: [vue()],
  // Keep the dev server convenient at / while Spring serves the packaged
  // console from /gtc/.
  base: command === 'build' ? '/gtc/' : '/',
  server: {
    port: 3001,
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
}))
