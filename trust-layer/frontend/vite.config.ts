import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const gatewayTarget = process.env.VITE_API_BASE_URL || 'http://localhost:8080'
const keycloakTarget = process.env.VITE_KC_BASE_URL || 'http://localhost:8180'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api/vci': gatewayTarget,
      '/api/tx': gatewayTarget,
      '/api/ekyc': gatewayTarget,
      '/api/admin': gatewayTarget,
      '/api/tenants': gatewayTarget,
      '/api/sessions': gatewayTarget,
      '/api/identity': gatewayTarget,
      '/api/credentials': gatewayTarget,
      '/oauth2': gatewayTarget,
      '/ws': {
        target: gatewayTarget.replace(/^http/, 'ws'),
        ws: true
      },
      '/realms': {
        target: keycloakTarget,
        changeOrigin: true,
      }
    }
  }
})
