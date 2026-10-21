import process from 'node:process'
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 5000 },
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'on-first-retry',
    headless: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], channel: process.env.PLAYWRIGHT_CHROMIUM_CHANNEL },
    },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --strictPort',
    url: 'http://localhost:5174',
    reuseExistingServer: !process.env.CI,
  },
})
