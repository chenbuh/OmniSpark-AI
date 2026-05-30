import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'icons.svg'],
      manifest: {
        name: 'OmniSpark AI - 统一生图与视频创作中台',
        short_name: 'OmniSpark',
        description: 'AI 图像与视频生成管理平台',
        theme_color: '#05070c',
        background_color: '#05070c',
        display: 'standalone',
        scope: '/',
        start_url: '/',
        icons: [
          { src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any maskable' }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024
      }
    })
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules/vue/') || id.includes('node_modules/pinia/') || id.includes('node_modules/vue-router/')) return 'vue-vendor'
          if (id.includes('node_modules/naive-ui/') || id.includes('node_modules/lucide-vue-next/')) return 'ui-vendor'
          if (id.includes('node_modules/@stomp/') || id.includes('node_modules/sockjs-client/')) return 'stomp-vendor'
        }
      }
    },
    chunkSizeWarningLimit: 600
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
