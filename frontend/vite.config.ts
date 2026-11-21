/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies /api to the Spring Boot backend, so the browser never talks to the model provider
// and no secret is needed in the frontend (Constitution II).
// BACKEND_URL overrides the backend address (run.sh sets it when port 8080 is taken).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': process.env.BACKEND_URL ?? 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['tests/setup.ts'],
    include: ['tests/**/*.test.{ts,tsx}'],
    // bundleSecrets.test.ts runs a full production build
    testTimeout: 60000,
  },
})
