import { defineConfig, devices } from '@playwright/test';

/**
 * Backend + Postgres bu config tarafından otomatik başlatılmaz (yavaş/kırılgan
 * olurdu, ayrıca geliştiricinin zaten çalışan bir backend'ine çakışabilir) --
 * testler çalıştırılmadan önce `docker compose up -d` (backend/) ve
 * `./mvnw spring-boot:run` ile backend'in http://localhost:8080'de ayakta
 * olması gerekir. Frontend dev sunucusu ise webServer ile otomatik
 * başlatılır/yeniden kullanılır.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  fullyParallel: false,
  retries: 0,
  reporter: [['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 30_000,
  },
});
