import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({ plugins: [vue()], base: '/platform/', build: { outDir: '../src/main/resources/static/platform', emptyOutDir: false }, server: { proxy: { '/api': 'http://127.0.0.1:18084' } } })
