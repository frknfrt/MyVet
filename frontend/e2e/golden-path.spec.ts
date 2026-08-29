import { test, expect, Page } from '@playwright/test';

/**
 * Backend Field.tsx'teki FieldWrap, <label> ile <input>/<select>/<textarea>'yı
 * htmlFor/id ile eşlemiyor (bkz. components/ui/Field.tsx) -- bu yüzden
 * getByLabel() güvenilir çalışmıyor. Bunun yerine label metninin hemen
 * bir sonraki kardeş elemanını (FieldWrap'in gerçek DOM yapısı: <div><label/>
 * {children}</div>) buluyoruz. Checkbox'lar (KVKK, saldırgan vb.) <label>
 * içine SARILI olduğu için onlarda getByLabel() sorunsuz çalışıyor.
 */
function field(page: Page, label: string) {
  return page.locator(`xpath=//label[normalize-space(.)="${label}"]/following-sibling::*[1]`);
}

test('Platform admin → Randevu → Muayene → Fatura altın yolu', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  const runId = Date.now();
  const clinicName = `E2E Klinik ${runId}`;
  const adminEmail = `e2e-admin-${runId}@example.com`;
  const password = 'password123';
  const ownerName = `E2E Sahip ${runId}`;
  const ownerPhone = `555${String(runId).slice(-7)}`;
  const patientName = `E2E Pati ${runId}`;
  const speciesName = `E2E Tür ${runId}`;
  const serviceName = `E2E Hizmet ${runId}`;
  const serviceOptionLabel = `${serviceName} (30 dk)`;

  await test.step('1. Platform admin yeni klinik oluşturur', async () => {
    await page.goto('/platform-admin/login');
    await field(page, 'E-posta').fill('admin@myvet.local');
    await field(page, 'Şifre').fill('change-me-local-dev-only');
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants/);

    await page.getByRole('button', { name: '+ Yeni Klinik' }).click();
    await field(page, 'Klinik adı').fill(clinicName);
    await field(page, 'Vergi numarası').fill('1234567890');
    await field(page, 'Şube adı').fill('Merkez');
    await field(page, 'Yetkili adı soyadı').fill('E2E Admin');
    await field(page, 'Yetkili e-postası').fill(adminEmail);
    await field(page, 'Geçici şifre').fill(password);
    await page.getByRole('button', { name: 'Klinik Oluştur' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants\//);
  });

  await test.step('2. Klinik yöneticisi ilk girişini yapar', async () => {
    await page.goto('/login');
    await field(page, 'E-posta').fill(adminEmail);
    await field(page, 'Şifre').fill(password);
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    // LoginPage her zaman /panel'e yönlendiriyor (RegisterClinicPage'in eskiden
    // yaptığı gibi otomatik /kurulum'a değil) -- token'ın gerçekten saklandığından
    // emin olmak icin once /panel'e geçişi bekliyoruz, sonra /kurulum'a gidiyoruz.
    await expect(page).toHaveURL(/\/panel/);
    await page.goto('/kurulum');
  });

  await test.step('3. Kurulum sihirbazını tamamla', async () => {
    // SetupWizardPage mount olduğunda mevcut şube bilgisini çekip formu
    // üzerine yazıyor (setForm) -- bu bitmeden dolduruyorsak değerler silinir.
    await expect(field(page, 'Adres')).toBeEnabled();
    await field(page, 'Adres').fill('Test Cad. No:1');
    await field(page, 'Şehir').fill('İstanbul');
    await page.getByRole('button', { name: 'Kurulumu tamamla' }).click();
    await expect(page).toHaveURL(/\/panel/);
  });

  await test.step('4. Tür ekle (Ayarlar > Tür & Irk)', async () => {
    await page.goto('/ayarlar/tur-irk');
    const speciesForm = page.locator('form', { has: page.getByPlaceholder('Yeni tür adı (örn. Kemirgen)') });
    await speciesForm.getByPlaceholder('Yeni tür adı (örn. Kemirgen)').fill(speciesName);
    await speciesForm.getByRole('button', { name: 'Ekle' }).click();
    await expect(page.getByText(speciesName, { exact: true }).first()).toBeVisible();
  });

  await test.step('5. Hizmet tipi ekle (Ayarlar > Hizmetler)', async () => {
    await page.goto('/ayarlar/hizmetler');
    await field(page, 'Hizmet adı').fill(serviceName);
    await field(page, 'Fiyat (₺)').fill('500');
    await page.getByRole('button', { name: 'Ekle' }).click();
    await expect(page.getByText(serviceName)).toBeVisible();
  });

  await test.step('6. Yeni müşteri oluştur (KVKK onayı dahil)', async () => {
    await page.goto('/musteriler/yeni');
    await field(page, 'Ad Soyad').fill(ownerName);
    await field(page, 'Telefon').fill(ownerPhone);
    await page.getByLabel(/KVKK Aydınlatma Metni/).check();
    await page.getByRole('button', { name: 'Müşteriyi Kaydet' }).click();
    await expect(page).toHaveURL(/\/musteriler\//);
    await expect(page.getByText(ownerName)).toBeVisible();
  });

  await test.step('7. Yeni hasta oluştur', async () => {
    await page.goto('/hastalar/yeni');
    await page.getByPlaceholder('Müşteri adı veya telefonuyla arayın...').fill(ownerName);
    await page.getByText(ownerName).first().click();
    await field(page, 'Adı').fill(patientName);
    await field(page, 'Tür').selectOption({ label: speciesName });
    await page.getByRole('button', { name: 'Hastayı Kaydet' }).click();
    await expect(page).toHaveURL(/\/hastalar\//);
    await expect(page.getByText(patientName)).toBeVisible();
  });

  await test.step('8. Randevu oluştur', async () => {
    await page.goto('/randevu');
    await page.getByRole('button', { name: 'Yeni Randevu' }).click();
    await field(page, 'Hasta veya sahip ara').fill(patientName);
    await page.getByText(patientName, { exact: false }).first().click();
    await field(page, 'Hizmet').selectOption({ label: serviceOptionLabel });
    await page.getByRole('button', { name: 'Randevu Oluştur' }).click();
    await expect(page.getByText(patientName)).toBeVisible();
  });

  await test.step('9. Check-in yap → muayeneye başla', async () => {
    // ScheduleAppointmentModal source varsayılanı WALK_IN -- Appointment.schedule()
    // WIDGET olmayan kaynaklarda randevuyu doğrudan CONFIRMED açıyor, "Onayla"
    // adımı yalnızca web sitesi widget'ından gelen REQUESTED randevular için var.
    await page.getByText(patientName).first().click();
    await page.getByRole('button', { name: 'Check-in yap' }).click();

    await page.getByText(patientName).first().click();
    await page.getByRole('button', { name: "Muayeneyi Başlat (SOAP'a Git)" }).click();

    await expect(page).toHaveURL(/\/muayene\//);
  });

  await test.step('10. Vital bulguları gir', async () => {
    await field(page, 'Ağırlık (kg)').fill('12.5');
    await field(page, 'Ateş (°C)').fill('38.5');
    await field(page, 'Nabız (bpm)').fill('90');
    await field(page, 'Solunum (/dk)').fill('20');
    await page.getByRole('button', { name: 'Vitalleri Kaydet' }).click();
    // React StrictMode (dev) bazen aynı flash mesajını iki kez render ediyor -- .first() ile toleranslı.
    await expect(page.getByText('Vital bulgular kaydedildi').first()).toBeVisible();
  });

  await test.step('11. Fiziksel Muayene — bir sistemi Anormal işaretle', async () => {
    const cardioRow = page.locator('xpath=//div[normalize-space(text())="Kardiyovasküler"]/parent::div');
    await cardioRow.getByRole('button', { name: 'Anormal' }).click();
    await cardioRow.locator('textarea').fill('Üfürüm duyuldu (E2E test notu)');
    await page.getByRole('button', { name: 'Fiziksel Muayeneyi Kaydet' }).click();
    await expect(page.getByText('Fiziksel muayene kaydedildi').first()).toBeVisible();
  });

  await test.step('12. SOAP notunu doldur ve muayeneyi tamamla', async () => {
    await field(page, 'Subjective (S)').fill('Sahip, hastanın son 2 gündür iştahsız olduğunu belirtti.');
    await field(page, 'Objective (O)').fill('Genel durum iyi, hafif letarji mevcut.');
    await field(page, 'Assessment (A)').fill('Hafif gastroenterit şüphesi.');
    await field(page, 'Plan (P)').fill('Destekleyici tedavi, 3 gün sonra kontrol.');
    await page.getByRole('button', { name: 'SOAP Kaydet' }).click();
    await expect(page.getByText('SOAP kaydedildi').first()).toBeVisible();

    await page.getByRole('button', { name: 'Muayeneyi Tamamla' }).click();
    await expect(field(page, 'Subjective (S)')).toBeDisabled();
  });

  await test.step('13. Finans — otomatik taslak faturayı bul, kalem ekle, fatura kes', async () => {
    await page.goto('/finans');
    await page.getByText(ownerName).first().click();

    await field(page, 'Hizmet/Ürün (opsiyonel)').selectOption({ label: serviceName });
    await page.getByRole('button', { name: 'Ekle' }).click();
    // Aynı metin, sıfırlanan "Hizmet/Ürün" select'inde gizli <option> olarak da
    // eşleşiyor (strict-mode collision) -- .first() görünür kalem satırını alır.
    await expect(page.getByText(serviceName).first()).toBeVisible();

    await page.getByRole('button', { name: 'Faturayı Kes' }).click();
    await expect(page.getByText(/e-Fatura/)).toBeVisible();
  });

  await test.step('14. Ödeme al', async () => {
    await field(page, 'Tutar').fill('500');
    await page.getByRole('button', { name: 'Ödeme Al' }).click();
    // "Yöntem" select'indeki "Nakit" option'ıyla strict-mode collision -- .first() ödeme satırını alır.
    await expect(page.getByText('Nakit').first()).toBeVisible();
  });
});
