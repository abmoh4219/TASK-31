// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * Playwright configuration for retail campaign e2e tests.
 *
 * Headless mode is REQUIRED — Docker has no display server.
 * Base URL is configurable via BASE_URL env var so tests work both locally
 * (http://localhost:8080) and inside Docker (http://app:8080).
 */
module.exports = defineConfig({
  testDir: './tests',
  timeout: 45000,
  retries: 1,
  workers: 1,

  use: {
    headless: true,
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    screenshot: 'only-on-failure',
    video: 'off',
    ignoreHTTPSErrors: true,
    actionTimeout: 15000,
    navigationTimeout: 30000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
