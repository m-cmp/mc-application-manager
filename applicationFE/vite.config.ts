import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig(({ mode }) => ({
  // Allows an embedded document to use the host UI's origin while loading AM assets directly.
  base: loadEnv(mode, process.cwd(), '').VITE_ASSET_BASE_URL || '/',
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  // server: {
  //   proxy: {
  //     '/*': {
  //       target: import.meta.env.VITE_API_URL,
  //       changeOrigin: true,
  //     },
  //   },
  // }
}))
