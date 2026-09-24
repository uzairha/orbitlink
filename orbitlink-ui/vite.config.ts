import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxy the API in development so the browser only ever talks to one
    // origin. Without this the app would call :8080 cross-origin and the
    // server would need CORS configured purely to support local development
    // — permanent production config existing only for a dev convenience.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
