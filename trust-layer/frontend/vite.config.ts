import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/vci': 'http://localhost:9004',
      '/api/tx': 'http://localhost:9003',
      '/api/ekyc': 'http://localhost:9002'
    }
  }
})
