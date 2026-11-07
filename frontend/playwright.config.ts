import { defineConfig, devices } from '@playwright/test';

/**
 * Backend + Postgres bu config tarafından otomatik başlatılmaz (yavaş/kırılgan
 * olurdu, ayrıca geliştiricinin zaten çalışan bir backend'ine çakışabilir) --
 * testler çalıştırılmadan önce `docker compose up -d` (backend/) ve
 * `./mvnw spring-boot:run` ile backend'in http://localhost:8080'de ayakta
 * olması gerekir. Frontend dev sunucusu ise webServer ile otomatik
 * başlatılır/yeniden kullanılır.
 */
const port = Number(process.env.E2E_FRONTEND_PORT ?? 5173);
const baseURL = 'http://localhost:' + port;

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  fullyParallel: false,
  retries: 0,
  reporter: [['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npm run dev -- --strictPort --port ' + port,
    url: baseURL,
    reuseExistingServer: !process.env.CI && !process.env.E2E_FRONTEND_PORT,
    timeout: 30_000,
  },
});
