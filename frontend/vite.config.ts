import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Forward /api calls to Spring Boot, so no CORS setup is needed in dev.
    proxy: { '/api': 'http://localhost:8080' },
  },
})
