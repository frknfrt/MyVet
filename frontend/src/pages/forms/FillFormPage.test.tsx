// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FormItem, FormTemplateContent } from '../../forms/formModel';
import { FillFormPage } from './FillFormPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const mocks = vi.hoisted(() => ({
  getTemplate: vi.fn(),
  getPrivacyNotice: vi.fn(),
  createSignedForm: vi.fn(),
  applyWriteBacks: vi.fn(),
  fetchFile: vi.fn(),
  getProfile: vi.fn(),
  getOwnerProfile: vi.fn(),
}));

vi.mock('../../api/formsApi', async (importActual) => {
  const actual = await importActual<typeof import('../../api/formsApi')>();
  return {
    ...actual,
    formsApi: {
      getTemplate: mocks.getTemplate,
      getPrivacyNotice: mocks.getPrivacyNotice,
      createSignedForm: mocks.createSignedForm,
      applyWriteBacks: mocks.applyWriteBacks,
      fetchFile: mocks.fetchFile,
      signedFormPdfUrl: (id: string) => `http://x/signed-forms/${id}/pdf`,
    },
  };
});
vi.mock('../../api/patientApi', () => ({
  patientApi: { getProfile: mocks.getProfile, getOwnerProfile: mocks.getOwnerProfile },
}));
vi.mock('../../forms/SignaturePad', () => ({
  SignaturePad: ({ onChange }: { onChange: (b: Blob | null) => void }) => (
    <div>
      <button type="button" onClick={() => onChange(new Blob(['png'], { type: 'image/png' }))}>mock-imzala</button>
      <button type="button" onClick={() => onChange(null)}>Temizle</button>
    </div>
  ),
}));

function item(p: Partial<FormItem>): FormItem {
  return { type: 'SHORT_TEXT', code: null, label: null, text: null, required: false, binding: null, unit: null, options: null, multiple: false, ...p };
}

const content: FormTemplateContent = {
  title: 'Anestezi Onayı',
  sections: [
    {
      title: null,
      items: [
        item({ code: 'ad', label: 'Hasta adı', binding: 'patient.name', required: true }),
        item({ type: 'PHONE', code: 'tel', label: 'Telefon no', binding: 'owner.phone' }),
        item({ code: 'tc', label: 'TC no', binding: 'owner.nationalId' }),
        item({ type: 'NUMBER', code: 'kilo', label: 'Kilo', binding: 'patient.weightKg', unit: 'kg' }),
        item({ code: 'ek', label: 'Ek not', required: true }),
        item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' }),
      ],
    },
  ],
};

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  window.scrollTo = vi.fn() as never;
  mocks.getTemplate.mockResolvedValue({ id: 't1', title: 'Anestezi Onayı', status: 'ACTIVE', content, sourceImageId: null, clinicName: 'Vetly Klinik', contentHash: 'opened-template-hash' });
  mocks.getProfile.mockResolvedValue({ id: 'p1', name: 'Pamuk', ownerId: 'o1', speciesName: 'Kedi', breedName: null, sex: null, birthDate: null, color: null, weightKg: 10, microchipNumber: null });
  mocks.getOwnerProfile.mockResolvedValue({ id: 'o1', fullName: 'Ayşe Yılmaz', phone: '0532 111 22 33', secondaryPhone: null, email: null, address: null, birthDate: null, nationalId: null });
  mocks.getPrivacyNotice.mockResolvedValue({version:1,text:'Kliniğin test aydınlatma metni',hash:'notice-v1'});
  mocks.createSignedForm.mockResolvedValue('sf1');
  mocks.applyWriteBacks.mockResolvedValue({ applied: [], skipped: [] });
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

async function render(url = '/formlar/doldur?templateId=t1&patientId=p1') {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () =>
    root.render(
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/formlar/doldur" element={<FillFormPage />} />
          <Route path="/hastalar/:id" element={<div>HASTA_KARTI</div>} />
        </Routes>
      </MemoryRouter>,
    ),
  );
  await act(async () => {});
}

function field(labelStart: string): HTMLInputElement {
  const label = [...container.querySelectorAll('label')].find((l) => l.textContent?.startsWith(labelStart));
  if (!label) throw new Error(`label yok: ${labelStart}`);
  return document.getElementById(label.getAttribute('for')!) as HTMLInputElement;
}

function type(el: HTMLInputElement, value: string) {
  act(() => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function button(text: string): HTMLButtonElement {
  const b = [...container.querySelectorAll('button')].find((x) => x.textContent?.trim() === text);
  if (!b) throw new Error(`buton yok: ${text}`);
  return b as HTMLButtonElement;
}

async function click(text: string) {
  await act(async () => button(text).click());
  await act(async () => {});
}

async function pickRadio(labelText: string) {
  const label = [...container.querySelectorAll('label')].find((l) => l.textContent?.includes(labelText))!;
  await act(async () => (label.querySelector('input') as HTMLInputElement).click());
}

async function fillAndSign() {
  type(field('Ek not'), 'not');
  await click('mock-imzala');
}

describe('FillFormPage', () => {
  it('eksik parametrede dostane hata gosterir', async () => {
    await render('/formlar/doldur?templateId=t1');
    expect(container.textContent).toContain('Form açılamadı');
    expect(mocks.getTemplate).not.toHaveBeenCalled();
  });

  it('bagli alanlar dolu acilir', async () => {
    await render();
    expect(field('Hasta adı').value).toBe('Pamuk');
    expect(field('Telefon no').value).toBe('0532 111 22 33');
    expect(field('Kilo').value).toBe('10');
    expect(container.textContent).toContain('Vetly Klinik');
    expect(container.textContent).toContain('Hasta sahibi — Ayşe Yılmaz');
  });

  it('imzasiz gonderimi engeller ve mesaj gosterir', async () => {
    await render();
    type(field('Ek not'), 'not');
    await click('Onayla ve Gönder');
    expect(container.textContent).toContain('Lütfen imza atın');
    expect(mocks.createSignedForm).not.toHaveBeenCalled();
  });

  it('bos zorunlu alanda hata gosterir', async () => {
    await render();
    await click('mock-imzala');
    await click('Onayla ve Gönder');
    expect(container.textContent).toContain('Ek not');
    expect(container.textContent).toContain('zorunlu');
    expect(mocks.createSignedForm).not.toHaveBeenCalled();
  });

  it('fark yoksa onay adimi cikmaz, dogrudan gonderir', async () => {
    await render();
    await fillAndSign();
    await click('Onayla ve Gönder');
    expect(container.textContent).not.toContain('Kaydınız da güncellensin mi?');
    const payload = mocks.createSignedForm.mock.calls[0][0];
    expect(payload.templateContentHash).toBe("opened-template-hash");
    expect(payload).toMatchObject({
      templateId: 't1', patientId: 'p1', encounterId: null,
      signer: { type: 'OWNER', name: 'Ayşe Yılmaz', nationalId: null, phone: null },
    });
    expect(typeof payload.signaturePngBase64).toBe('string');
    expect(payload.signaturePngBase64.startsWith('data:')).toBe(false);
    expect(mocks.applyWriteBacks).not.toHaveBeenCalled();
    expect(container.textContent).toContain('Teşekkürler, form kaydedildi.');
  });

  it('fark varsa onay adimi cikar ve secilen satir geri yazilir', async () => {
    await render();
    type(field('Telefon no'), '0533 999 88 77');
    await fillAndSign();
    await click('Onayla ve Gönder');
    expect(container.textContent).toContain('Kaydınız da güncellensin mi?');
    expect(container.textContent).toContain('0532 111 22 33 → 0533 999 88 77');
    expect(mocks.createSignedForm).not.toHaveBeenCalled();
    await click('Gönder');
    expect(mocks.applyWriteBacks).toHaveBeenCalledWith({
      ownerId: 'o1', patientId: 'p1', signedFormId: 'sf1',
      items: [{ binding: 'owner.phone', value: '0533 999 88 77' }],
    });
    expect(container.textContent).toContain('Teşekkürler, form kaydedildi.');
  });

  it('Hayir secilen satir geri yazilmaz', async () => {
    await render();
    type(field('Telefon no'), '0533 999 88 77');
    await fillAndSign();
    await click('Onayla ve Gönder');
    await pickRadio('Hayır');
    await click('Gönder');
    expect(mocks.createSignedForm).toHaveBeenCalled();
    expect(mocks.applyWriteBacks).not.toHaveBeenCalled();
  });

  it('baska biri imzalarken owner satiri diff ve geri yazmada yoktur', async () => {
    await render();
    type(field('Telefon no'), '0533 999 88 77');
    type(field('Kilo'), '12');
    await pickRadio('Başka biri imzalıyor');
    await fillAndSign();
    await click('Onayla ve Gönder');
    expect(container.textContent).toContain('zorunlu');
    expect(mocks.createSignedForm).not.toHaveBeenCalled();

    type(field('Ad Soyad'), 'Mehmet Kaya');
    await click('Onayla ve Gönder');
    expect(container.textContent).toContain('Kaydınız da güncellensin mi?');
    expect(container.textContent).not.toContain('0533 999 88 77');
    await click('Gönder');
    expect(mocks.createSignedForm.mock.calls[0][0].signer).toEqual({ type: 'OTHER', name: 'Mehmet Kaya', nationalId: null, phone: null });
    expect(mocks.applyWriteBacks).toHaveBeenCalledWith({
      ownerId: 'o1', patientId: 'p1', signedFormId: 'sf1',
      items: [{ binding: 'patient.weightKg', value: '12' }],
    });
  });

  it('gecersiz TC satiri secilemez, gerekce gorunur', async () => {
    await render();
    type(field('TC no'), '12345678901');
    await fillAndSign();
    await click('Onayla ve Gönder');
    expect(container.textContent).toContain('Geçersiz TC kimlik no');
    const radios = [...container.querySelectorAll('input[type="radio"]')] as HTMLInputElement[];
    expect(radios.length).toBeGreaterThan(0);
    expect(radios.every((r) => r.disabled)).toBe(true);
    await click('Gönder');
    expect(mocks.createSignedForm).toHaveBeenCalled();
    expect(mocks.applyWriteBacks).not.toHaveBeenCalled();
  });

  it('applyWriteBacks hata verince basari ekrani ve uyari gosterir', async () => {
    mocks.applyWriteBacks.mockRejectedValue(new Error('boom'));
    await render();
    type(field('Telefon no'), '0533 999 88 77');
    await fillAndSign();
    await click('Onayla ve Gönder');
    await click('Gönder');
    expect(container.textContent).toContain('Teşekkürler, form kaydedildi.');
    expect(container.textContent).toContain('Form kaydedildi ama bazı bilgiler kayda yazılamadı');
  });

  it('sablon degistiginde yeniden okuma uyarisini gosterir ve geri yazmaz', async () => {
    const message = 'Form şablonu değişti. Formu kapatıp yeniden açın, güncel metni okuyup yeniden imzalayın.';
    mocks.createSignedForm.mockRejectedValue(new Error(message));
    await render();
    await fillAndSign();
    await click('Onayla ve Gönder');
    expect(container.textContent).toContain(message);
    expect(container.textContent).not.toContain('Teşekkürler');
    expect(mocks.applyWriteBacks).not.toHaveBeenCalled();
  });

  it('createSignedForm hatasinda formda kalir ve hata bandi gosterir', async () => {
    mocks.createSignedForm.mockRejectedValue(new Error('Sunucu hatası'));
    await render();
    await fillAndSign();
    await click('Onayla ve Gönder');
    expect(container.textContent).toContain('Sunucu hatası');
    expect(container.textContent).not.toContain('Teşekkürler');
    expect(field('Ek not').value).toBe('not');
  });

  it('kayip yanit sonrasi ayni form tekrarinda islem anahtarini korur', async () => {
    mocks.createSignedForm.mockRejectedValueOnce(new Error('Yanıt kayboldu')).mockResolvedValueOnce('sf1');
    await render();
    await fillAndSign();
    await click('Onayla ve Gönder');
    const first = mocks.createSignedForm.mock.calls[0][0];
    expect(first.requestId).toMatch(/^[a-f0-9-]{36}$/);
    expect(container.textContent).toContain('Yanıt kayboldu');
    await click('Onayla ve Gönder');
    expect(mocks.createSignedForm.mock.calls[1][0]).toEqual(first);
    expect(container.textContent).toContain('Teşekkürler, form kaydedildi.');
  });

  it('basarisiz gonderimden sonra icerik degisse de islem anahtarini korur', async () => {
    mocks.createSignedForm.mockRejectedValue(new Error('Yanıt kayboldu'));
    await render();
    await fillAndSign();
    await click('Onayla ve Gönder');
    const first = mocks.createSignedForm.mock.calls[0][0].requestId;
    type(field('Ek not'), 'değişen not');
    await click('Onayla ve Gönder');
    expect(mocks.createSignedForm.mock.calls[1][0].requestId).toBe(first);
  });

  it('basarili kayittan sonra yeniden acilan form yeni anahtar kullanir', async () => {
    await render();
    await fillAndSign();
    await click('Onayla ve Gönder');
    const first = mocks.createSignedForm.mock.calls[0][0].requestId;
    await act(async () => root.unmount());
    container.remove();
    await render();
    await fillAndSign();
    await click('Onayla ve Gönder');
    expect(mocks.createSignedForm.mock.calls[1][0].requestId).not.toBe(first);
  });

  it('hizli cift gonderimde tek kayit istegi baslatir', async () => {
    await render();
    await fillAndSign();
    await act(async () => {
      const submit = button('Onayla ve Gönder');
      submit.click();
      submit.click();
    });
    await act(async () => {});
    expect(mocks.createSignedForm).toHaveBeenCalledTimes(1);
    expect(container.textContent).toContain('Teşekkürler, form kaydedildi.');
  });
  it('OTHER signer invalid TC prevents submission', async () => {
    await render(); await pickRadio('Başka biri imzalıyor');
    type(field('Ad Soyad'), 'Yakın'); type(field('TC Kimlik No'), '12345678901');
    await fillAndSign(); await click('Onayla ve Gönder');
    expect(container.textContent).toContain('Geçersiz TC kimlik no');
    expect(mocks.createSignedForm).not.toHaveBeenCalled();
  });

  it('dirty close can be cancelled or confirmed before returning to patient', async () => {
    await render(); type(field('Ek not'), 'kaybolmasın');
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await click('Kapat'); expect(container.textContent).not.toContain('HASTA_KARTI');
    confirmation.mockReturnValue(true);
    await click('Kapat'); expect(container.textContent).toContain('HASTA_KARTI');
    expect(mocks.createSignedForm).not.toHaveBeenCalled();
  });

  it('server skipped values remain visible after successful signed form', async () => {
    mocks.applyWriteBacks.mockResolvedValue({ applied: [], skipped: [{ binding: 'owner.phone', reason: 'tekrarlanan alan' }] });
    await render(); type(field('Telefon no'), '0533 999 88 77');
    await fillAndSign(); await click('Onayla ve Gönder'); await click('Gönder');
    expect(container.textContent).toContain('Teşekkürler, form kaydedildi.');
    expect(container.textContent).toContain('owner.phone (tekrarlanan alan)');
  });

  it('downloads saved PDF without immediately revoking its object URL', async () => {
    await render(); await fillAndSign(); await click('Onayla ve Gönder');
    mocks.fetchFile.mockResolvedValue(new Response('pdf', { headers: { 'Content-Type': 'application/pdf' } }));
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:download') });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.useFakeTimers();
    try {
      await click('PDF indir'); expect(mocks.fetchFile).toHaveBeenCalledWith('http://x/signed-forms/sf1/pdf');
      expect(anchorClick).toHaveBeenCalledTimes(1);
      expect((anchorClick.mock.instances[0] as HTMLAnchorElement).download).toBe('Anestezi Onayı.pdf');
      expect(URL.revokeObjectURL).not.toHaveBeenCalled();
      vi.advanceTimersByTime(10_000); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:download');
    } finally { vi.clearAllTimers(); vi.useRealTimers(); }
    expect(mocks.createSignedForm).toHaveBeenCalledTimes(1);
  });

  it('PDF failure keeps successful form and permits retry', async () => {
    await render(); await fillAndSign(); await click('Onayla ve Gönder');
    mocks.fetchFile.mockResolvedValue(new Response('', { status: 500 }));
    await click('PDF indir');
    expect(container.textContent).toContain('PDF indirilemedi.');
    expect(container.textContent).toContain('Teşekkürler, form kaydedildi.');
    expect(mocks.createSignedForm).toHaveBeenCalledTimes(1);
  });

  it('applies gutter to document scroller and restores prior style on leave', async () => {
    const style = document.documentElement.style;
    style.setProperty('scrollbar-gutter', 'stable both-edges', 'important');
    try {
      await render(); expect(style.getPropertyValue('scrollbar-gutter')).toBe('stable');
      await act(async () => root.render(null));
      expect(style.getPropertyValue('scrollbar-gutter')).toBe('stable both-edges');
      expect(style.getPropertyPriority('scrollbar-gutter')).toBe('important');
    } finally { style.removeProperty('scrollbar-gutter'); }
  });

});

async function presentPrivacyForm() {
  mocks.getTemplate.mockResolvedValue({id:'t1',title:'Anestezi Onayı',status:'ACTIVE',content:{...content,privacyOptionsEnabled:true},clinicName:'Vetly Klinik',contentHash:'opened-template-hash'});
  await render();type(field('Ek not'),'not');await click('Hasta sahibine sun');
}

it('enabled forms show unchecked notice and independent optional channels after handoff', async () => {
  await presentPrivacyForm();
  const checks=Array.from(container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
  expect(checks).toHaveLength(4);expect(checks.every(c => !c.checked)).toBe(true);
  expect(container.textContent).not.toContain('Kliniğin test aydınlatma metni');
  await click('KVKK Aydınlatma Metni');
  expect(container.querySelector('[role="dialog"]')?.textContent).toContain('Kliniğin test aydınlatma metni');
  await click('Metni kapat');await click('mock-imzala');await click('Onayla ve Gönder');
  expect(mocks.createSignedForm).not.toHaveBeenCalled();
  expect(container.textContent).toContain('Lütfen aydınlatma beyanını işaretleyin.');
  await act(async () => checks[0].click());await click('mock-imzala');await click('Onayla ve Gönder');
  expect(mocks.createSignedForm.mock.calls[0][0]).toMatchObject({privacyNoticeAcknowledged:true,privacyNoticeHash:'notice-v1',smsNotification:false,whatsappNotification:false,emailNotification:false});
});

it('channel choices are independent and returning to preparation clears signature and choices', async () => {
  await presentPrivacyForm();
  let checks=Array.from(container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
  await act(async () => {checks[0].click();checks[2].click();});await click('mock-imzala');
  await click('Düzenlemeye dön');await click('Hasta sahibine sun');
  checks=Array.from(container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
  expect(checks.every(c => !c.checked)).toBe(true);
  await act(async () => {checks[0].click();checks[1].click();});await click('Onayla ve Gönder');
  expect(mocks.createSignedForm).not.toHaveBeenCalled();
  await click('mock-imzala');await click('Onayla ve Gönder');
  expect(mocks.createSignedForm.mock.calls[0][0]).toMatchObject({smsNotification:true,whatsappNotification:false,emailNotification:false});
});

it('does not request a notice or display its checkboxes for disabled templates', async () => {
  await render();expect(mocks.getPrivacyNotice).not.toHaveBeenCalled();
  expect(container.querySelectorAll('input[type="checkbox"]')).toHaveLength(0);
});
