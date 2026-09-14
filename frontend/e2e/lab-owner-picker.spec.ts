import { test, expect, Page } from '@playwright/test';

/**
 * bkz. golden-path.spec.ts -- FieldWrap label/children'ı htmlFor/id ile
 * eşlemiyor, bu yüzden aynı xpath yardımcıyı kullanıyoruz.
 */
function field(page: Page, label: string) {
  return page.locator(`xpath=//label[normalize-space(.)="${label}"]/following-sibling::*[1]`);
}

test('Laboratuvar sonucu yükleme: müşteri dropdown\'ında yavaş tıklama seçimi tamamlar', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  const runId = Date.now();
  const clinicName = `E2E Klinik ${runId}`;
  const adminEmail = `e2e-admin-${runId}@example.com`;
  const password = 'password123';
  const ownerName = `E2E Sahip ${runId}`;
  const ownerPhone = `555${String(runId).slice(-7)}`;

  await test.step('Klinik oluştur ve yönetici girişi yap', async () => {
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

  await test.step('Müşteri oluştur', async () => {
    await page.goto('/musteriler/yeni');
    await field(page, 'Ad Soyad').fill(ownerName);
    await field(page, 'Telefon').fill(ownerPhone);
    await field(page, 'Doğum Tarihi').fill('1990-01-01');
    await page.getByLabel(/KVKK Aydınlatma Metni/).check();
    await page.getByRole('button', { name: 'Müşteriyi Kaydet' }).click();
    await expect(page).toHaveURL(/\/musteriler\/[0-9a-f-]{36}$/);
  });

  await test.step('Laboratuvar modalında müşteri dropdown\'ından YAVAŞ tıklama ile seçim yap', async () => {
    await page.goto('/laboratuvar');
    await page.getByRole('button', { name: '+ Yeni Sonuç Yükle' }).click();

    await page.getByPlaceholder('Müşteri bilinmiyor').fill(ownerName);
    const option = page.getByText(ownerName).last();
    await expect(option).toBeVisible();

    // Gerçek kullanıcı tıklaması genelde mousedown->mouseup arasında
    // birkaç yüz ms sürebilir -- bileşendeki onBlur, 150ms sonra dropdown'ı
    // kapatıyor. Bu, o yarış durumunu kasıtlı olarak tetikliyor.
    const box = await option.boundingBox();
    if (!box) throw new Error('Dropdown seçeneği bulunamadı');
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    await page.waitForTimeout(300);
    await page.mouse.up();

    // Seçim başarılıysa "Müşteri" alanı artık chip olarak ownerName'i gösterir.
    await expect(page.getByText('Değiştir')).toBeVisible();
    await expect(page.locator('form').getByText(ownerName)).toBeVisible();
  });
});
