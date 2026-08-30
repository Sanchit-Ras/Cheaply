import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server proxies API calls to the backend, so in development the app
// and the API share an origin and CORS never enters the picture. Production
// does the same thing with nginx (Phase 5). VITE_API_BASE_URL exists for the
// odd setup where a proxy is not possible.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
})
