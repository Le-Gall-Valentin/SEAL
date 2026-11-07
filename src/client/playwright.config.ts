import { defineConfig, devices } from '@playwright/test'

const ci = Boolean(process.env['CI'])

// Bout en bout contre SEAL lancé par docker compose (https://localhost:8443). Le certificat mkcert
// est reconnu par Chromium une fois l'autorité locale installée (scripts/gen-certs.sh).
export default defineConfig({
  testDir: './e2e',
  forbidOnly: ci,
  retries: ci ? 1 : 0,
  reporter: ci ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['SEAL_BASE_URL'] ?? 'https://localhost:8443',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
