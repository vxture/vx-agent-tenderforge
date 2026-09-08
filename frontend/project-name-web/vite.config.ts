// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-14
import { fileURLToPath, URL } from 'node:url'
import type { ConfigEnv, UserConfig } from 'vite'
import { defineConfig, loadEnv } from 'vite'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }: ConfigEnv): UserConfig => {
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)))

  return {
    base: env.VITE_BASE_PATH || '/',
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT) || 5173,
      open: env.VITE_OPEN === 'true',
      cors: true,
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8081',
          changeOrigin: true,
        },
      },
    },
    build: {
      sourcemap: env.VITE_SOURCEMAP === 'true',
      outDir: 'dist',
      minify: 'esbuild',
      cssCodeSplit: true,
      chunkSizeWarningLimit: 500,
      assetsInlineLimit: 4096,
      reportCompressedSize: false,
      rollupOptions: {
        maxParallelFileOps: 2,
        output: {
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
          manualChunks: (id) => {
            const moduleId = id.replaceAll('\\', '/')
            if (!moduleId.includes('/node_modules/')) return undefined
            if (moduleId.includes('/@tiptap/') || moduleId.includes('/prosemirror-')) {
              return 'editor'
            }
            if (moduleId.includes('/@tanstack/')) return 'query'
            if (
              moduleId.includes('/react/') ||
              moduleId.includes('/react-dom/') ||
              moduleId.includes('/react-router/') ||
              moduleId.includes('/scheduler/')
            ) {
              return 'react-vendor'
            }
            return undefined
          },
        },
      },
      target: 'esnext',
    },
  }
})
