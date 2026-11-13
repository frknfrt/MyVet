import { randomUUID } from 'node:crypto';
import { test, expect, Page } from '@playwright/test';

/**
 * Deneme süresi akışı (spec 2026-11-13): deneme bandı → süre dolunca üyelik sayfasına kilit →
 * paket seçip ödeme → panelin açılması.
 *
 * Ön koşul: backend, ödeme simülasyonuna izin veren bir profille çalışmalı
 * (SPRING_PROFILES_ACTIVE=dev ya da test) — iyzico kimlik bilgisi yokken simülasyon yalnızca
 * bu profillerde açıktır. SMS ile kayıt adımı burada sınanmaz (doğrulama kodu yalnızca
 * backend log'unda görünür); o adım backend'deki TrialSignupIntegrationTest ile kapsanır.
 */
const API = process.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const CLIENT_HEADER = { 'X-Vetly-Client': 'web' };

function field(page: Page, label: string) {
  return page.locator(`xpath=//label[normalize-space(.)="${label}"]/following-sibling::*[1]`);
}

function isoDate(daysFromToday: number): string {
  const date = new Date();
  date.setDate(date.getDate() + daysFromToday);
  return date.toISOString().slice(0, 10);
}

test('Deneme → süre doldu → paket seç ve öde → panel açılır', async ({ page }) => {
  const runId = Date.now();
  const clinicName = `E2E Deneme ${runId}`;
  const adminEmail = `e2e-deneme-${randomUUID()}@example.com`;
  const password = 'password123';
  const planCode = `E2E-${runId}`;
  const planName = `E2E Paket ${runId}`;
  let tenantId = '';

  await test.step('1. Platform admin bir paket ve deneme kliniği oluşturur', async () => {
    await page.goto('/platform-admin/login');
    await field(page, 'E-posta').fill('admin@myvet.local');
    await field(page, 'Şifre').fill('change-me-local-dev-only');
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/overview$/);

    const planResponse = await page.request.post(`${API}/api/v1/platform-admin/plans`, {
      headers: CLIENT_HEADER,
      data: { code: planCode, name: planName, monthlyPrice: 1490 },
    });
    expect(planResponse.ok()).toBeTruthy();

    await page.getByRole('link', { name: 'Kiracılar', exact: true }).click();
    await page.getByRole('button', { name: '+ Yeni Klinik' }).click();
    await field(page, 'Klinik adı').fill(clinicName);
    await field(page, 'Vergi numarası').fill('1234567890');
    await field(page, 'Şube adı').fill('Merkez');
    await field(page, 'Adres').fill('Test Cad. No:1');
    await field(page, 'Şehir').fill('İzmir');
    await field(page, 'Yetkili adı soyadı').fill('E2E Deneme Admin');
    await field(page, 'Yetkili e-postası').fill(adminEmail);
    await field(page, 'Geçici şifre').fill(password);
    await page.getByRole('button', { name: 'Klinik Oluştur' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants\/[0-9a-f-]{36}$/);
    tenantId = page.url().split('/').pop()!;
  });

  await test.step('2. Klinik yöneticisi giriş yapar ve deneme bandını görür', async () => {
    await page.goto('/login');
    await field(page, 'E-posta').fill(adminEmail);
    await field(page, 'Şifre').fill(password);
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    await expect(page).toHaveURL(/\/panel/);

    const banner = page.getByTestId('trial-banner');
    await expect(banner).toContainText('Denemenizin bitmesine 7 gün kaldı.');
    await expect(banner.getByRole('link', { name: 'Üyelik Başlat' })).toBeVisible();
  });

  await test.step('3. Deneme süresi dolunca panel üyelik sayfasına kilitlenir', async () => {
    const expire = await page.request.put(`${API}/api/v1/platform-admin/tenants/${tenantId}/subscription`, {
      headers: CLIENT_HEADER,
      data: { planCode: 'TRIAL', billingStatus: 'TRIAL', renewsAt: isoDate(-2) },
    });
    expect(expire.ok()).toBeTruthy();

    await page.goto('/hastalar');
    await expect(page).toHaveURL(/\/uyelik$/);
    await expect(page.getByRole('heading', { name: 'Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız.' })).toBeVisible();
    await expect(page.locator(`[data-plan-code="${planCode}"]`)).toContainText(planName);

    // Sunucu da kilitli: arayüz atlansa bile istek reddedilir.
    const blocked = await page.request.get(`${API}/api/v1/patients`, { headers: CLIENT_HEADER });
    expect(blocked.status()).toBe(402);
  });

  await test.step('4. Yönetici paket seçip öder, panel açılır', async () => {
    await page.locator(`[data-plan-code="${planCode}"]`).getByRole('button', { name: 'Bu Paketi Seç' }).click();

    await expect(page).toHaveURL(/\/ayarlar\/abonelik/);
    await expect(page.getByText('Ödemeniz alındı, teşekkürler.')).toBeVisible();
    await expect(page.getByText(planName).first()).toBeVisible();
    await expect(page.getByTestId('trial-banner')).toHaveCount(0);

    await page.goto('/hastalar');
    await expect(page).toHaveURL(/\/hastalar$/);
    const unlocked = await page.request.get(`${API}/api/v1/patients`, { headers: CLIENT_HEADER });
    expect(unlocked.status()).toBe(200);
  });
});
