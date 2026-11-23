import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: '/trade/',
  build: {
    outDir: '../src/main/resources/static/trade',
    // Keep any existing files in the external output directory intact.
    emptyOutDir: false,
  },
  server: {
    proxy: {
      '/api/trade': 'http://127.0.0.1:18085',
    },
  },
})
