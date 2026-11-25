import { defineConfig } from "@playwright/test";

/**
 * End-to-end tests. The backend, with PostgreSQL and Redis, must already be running on port 8080;
 * the web client is started here if it is not.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: { timeout: 15_000 },
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:5173",
    // Set PW_CHANNEL=chrome to use an installed Chrome instead of Playwright's own browser.
    channel: process.env.PW_CHANNEL || undefined,
    trace: "retain-on-failure",
  },
  webServer: {
    command: "npm run dev -- --port 5173 --strictPort",
    url: "http://localhost:5173",
    reuseExistingServer: true,
  },
});
