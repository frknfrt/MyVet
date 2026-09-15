import { test, expect, Page } from '@playwright/test';

function field(page: Page, label: string) {
  return page.locator(`xpath=//label[normalize-space(.)="${label}"]/following-sibling::*[1]`);
}

/**
 * DashboardPage.tsx: ADMIN rolü için varsayılan viewMode 'summary' (İşletme
 * özeti) -- "Hızlı ekle" menüsü ve "Bugünkü satış" KPI kartı yalnızca
 * 'operational' modda render ediliyor. Bu yüzden /panel'e her gidişte/reload
 * sonrasında "Operasyonel" sekmesine geçmek gerekiyor (state kalıcı değil,
 * useState ile başlatılıyor).
 */
async function goToOperationalView(page: Page) {
  await page.getByText('Operasyonel', { exact: true }).click();
}

test('Hızlı Satış: anonim müşteriyle ürün satışı ve günlük ciroya yansıması', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  const runId = Date.now();
  const clinicName = `E2E Klinik ${runId}`;
  const adminEmail = `e2e-admin-${runId}@example.com`;
  const password = 'password123';
  const productName = `E2E Ürün ${runId}`;

  await test.step('Klinik oluştur ve giriş yap', async () => {
    await page.goto('/platform-admin/login');
    await field(page, 'E-posta').fill('admin@myvet.local');
    await field(page, 'Şifre').fill('change-me-local-dev-only');
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants/);

    await page.getByRole('button', { name: '+ Yeni Klinik' }).click();
    await field(page, 'Klinik adı').fill(clinicName);
    await field(page, 'Vergi numarası').fill('1234567890');
    await field(page, 'Şube adı').fill('Merkez');
    await field(page, 'Adres').fill('Test Cad. No:1');
    await field(page, 'Şehir').fill('İstanbul');
    await field(page, 'Yetkili adı soyadı').fill('E2E Admin');
    await field(page, 'Yetkili e-postası').fill(adminEmail);
    await field(page, 'Geçici şifre').fill(password);
    await page.getByRole('button', { name: 'Klinik Oluştur' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants\//);

    await page.goto('/login');
    await field(page, 'E-posta').fill(adminEmail);
    await field(page, 'Şifre').fill(password);
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    await expect(page).toHaveURL(/\/panel/);
  });

  await test.step('Stok kalemi oluştur', async () => {
    await page.goto('/stok');
    await page.getByRole('button', { name: 'Yeni Ürün' }).click();
    await field(page, 'Ürün adı').fill(productName);
    await field(page, 'Başlangıç miktarı').fill('20');
    await field(page, 'Kritik stok eşiği').fill('2');
    await page.getByRole('button', { name: 'Kaydet' }).click();
    await expect(page.getByText(productName).first()).toBeVisible();
  });

  await test.step("Dashboard'dan Hızlı Satış aç, anonim müşteriyle ürün sat", async () => {
    await page.goto('/panel');
    // Klinik admini için varsayılan görünüm "İşletme özeti" -- Hızlı ekle
    // menüsü ve KPI şeridi "Operasyonel" modda.
    await goToOperationalView(page);
    await page.getByRole('button', { name: 'Hızlı ekle' }).click();
    await page.getByText('Yeni satış').click();

    await page.getByRole('button', { name: 'Anonim / Günlük Müşteri Olarak Devam Et' }).click();

    await field(page, 'Ürün').selectOption({ label: `${productName} (20 adet stokta)` });
    await field(page, 'Adet').fill('2');
    await field(page, 'Birim Fiyat').fill('150');
    // KDV % alani varsayilan 20 ile geliyor -- 2 x 150 = 300 + %20 KDV = 360.
    await expect(field(page, 'KDV %')).toHaveValue('20');
    await page.getByRole('button', { name: 'Sepete Ekle' }).click();

    await expect(page.getByText('360.00 ₺').first()).toBeVisible();

    await page.getByRole('button', { name: 'Satışı Tamamla' }).click();
    await expect(page.getByRole('button', { name: 'Satışı Tamamla' })).not.toBeVisible();
  });

  await test.step("Bugünkü satış KPI'ının arttığını doğrula", async () => {
    await page.reload();
    await goToOperationalView(page);
    await expect(page.getByText('Bugünkü satış')).toBeVisible();
    await expect(page.getByText('360 ₺')).toBeVisible();
  });

  await test.step('Stok düşümünü doğrula (20 - 2 = 18)', async () => {
    await page.goto('/stok');
    const nameCell = page.getByText(productName, { exact: true });
    const row = nameCell.locator('xpath=..');
    await expect(row).toContainText('18');
  });
});
