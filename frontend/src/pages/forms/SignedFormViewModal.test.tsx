// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FormItem } from '../../forms/formModel';
import { SignedFormViewModal } from './SignedFormViewModal';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const mocks = vi.hoisted(() => ({
  getSignedForm: vi.fn(),
  voidSignedForm: vi.fn(),
  fetchFile: vi.fn(),
  role: 'VET' as string,
}));

vi.mock('../../api/formsApi', () => ({
  formsApi: {
    getSignedForm: mocks.getSignedForm,
    voidSignedForm: mocks.voidSignedForm,
    fetchFile: mocks.fetchFile,
    signatureUrl: (id: string) => `http://x/${id}/signature`,
    signedFormPdfUrl: (id: string) => `http://x/${id}/pdf`,
  },
}));
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ session: { role: mocks.role } }) }));

function item(p: Partial<FormItem>): FormItem {
  return { type: 'SHORT_TEXT', code: null, label: null, text: null, required: false, binding: null, unit: null, options: null, multiple: false, ...p };
}

function detail(over: Record<string, unknown> = {}) {
  return {
    id: 'f1',
    templateTitle: 'Anestezi Onayı',
    content: {
      title: 'Anestezi Onayı',
      sections: [{ title: null, items: [item({ code: 'ad', label: 'Hasta adı' }), item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' })] }],
    },
    values: { ad: 'Pamuk' },
    signer: { type: 'OWNER', name: 'Ali Veli', nationalId: null, phone: null },
    signedAt: '2026-11-07T10:30:00',
    signedByName: 'Dr. Ayşe',
    patientId: 'p1',
    ownerId: 'o1',
    encounterId: null,
    status: 'VALID',
    voidReason: null,
    voidedAt: null,
    integrityOk: true,
    ...over,
  };
}

let root: Root;
let container: HTMLDivElement;
const onChanged = vi.fn();

async function mount(id: string | null = "f1") {
  await act(async () => {
    root.render(<SignedFormViewModal signedFormId={id} onClose={() => {}} onChanged={onChanged} />);
  });
}

function buttonByText(text: string): HTMLButtonElement | undefined {
  return Array.from(document.body.querySelectorAll('button')).find((b) => b.textContent === text) as HTMLButtonElement | undefined;
}

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  mocks.role = 'VET';
  mocks.getSignedForm.mockReset();
  mocks.voidSignedForm.mockReset();
  mocks.fetchFile.mockReset();
  onChanged.mockReset();
  mocks.fetchFile.mockResolvedValue({ ok: false });
  URL.createObjectURL = vi.fn(() => 'blob:sig');
  URL.revokeObjectURL = vi.fn();
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

async function startVoid() {
  await act(async () => { buttonByText('İptal et')!.click(); });
  await act(async () => {
    const input = document.body.querySelector('textarea')!;
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(input, 'Düzeltme');
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await act(async () => { buttonByText('İptali onayla')!.click(); });
}

describe('SignedFormViewModal', () => {
  it('gecerli formu readonly gosterir; bütünlük uyarisi yok', async () => {
    mocks.getSignedForm.mockResolvedValue(detail());
    await mount();
    expect(document.body.textContent).toContain('Pamuk');
    expect(document.body.textContent).toContain('Ali Veli');
    expect(document.body.textContent).toContain('07.11.2026 10:30');
    expect(document.body.textContent).not.toContain('Bütünlük doğrulanamadı');
  });

  it('integrityOk=false ise uyari gosterir; content null ise yalniz uyari + metadata', async () => {
    mocks.getSignedForm.mockResolvedValue(detail({ integrityOk: false, content: null, values: null }));
    await mount();
    expect(document.body.textContent).toContain('Bütünlük doğrulanamadı — bu belgenin içeriği imzadan sonra değişmiş olabilir.');
    expect(document.body.textContent).toContain('Ali Veli');
    expect(document.body.textContent).not.toContain('Hasta adı');
  });

  it('TECHNICIAN icin "İptal et" yok', async () => {
    mocks.role = 'TECHNICIAN';
    mocks.getSignedForm.mockResolvedValue(detail());
    await mount();
    expect(buttonByText('İptal et')).toBeUndefined();
    expect(buttonByText('PDF olarak indir')).toBeDefined();
  });

  it('VET icin "İptal et" var; gerekcesiz onay engellenir; gerekceyle iptal eder', async () => {
    mocks.getSignedForm.mockResolvedValue(detail());
    mocks.voidSignedForm.mockResolvedValue(undefined);
    await mount();
    await act(async () => {
      buttonByText('İptal et')!.click();
    });
    const confirm = buttonByText('İptali onayla')!;
    expect(confirm.disabled).toBe(true);

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement;
    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!;
      setter.call(textarea, '   ');
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
    });
    expect(buttonByText('İptali onayla')!.disabled).toBe(true);

    mocks.getSignedForm.mockResolvedValue(detail({ status: 'VOIDED', voidReason: 'Yanlış hasta', voidedAt: '2026-11-07T11:00:00' }));
    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!;
      setter.call(textarea, 'Yanlış hasta');
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => {
      buttonByText('İptali onayla')!.click();
    });
    expect(mocks.voidSignedForm).toHaveBeenCalledWith('f1', 'Yanlış hasta');
    expect(onChanged).toHaveBeenCalled();
    expect(document.body.textContent).toContain('İPTAL EDİLDİ');
  });

  it('VOIDED formda banner + gerekce gorunur, "İptal et" yok', async () => {
    mocks.getSignedForm.mockResolvedValue(detail({ status: 'VOIDED', voidReason: 'Yanlış hasta', voidedAt: '2026-11-07T11:00:00' }));
    await mount();
    expect(document.body.textContent).toContain('İPTAL EDİLDİ');
    expect(document.body.textContent).toContain('Yanlış hasta');
    expect(buttonByText('İptal et')).toBeUndefined();
  });

  it('imza gorselini fetchFile ile yukleyip img olarak koyar', async () => {
    mocks.getSignedForm.mockResolvedValue(detail());
    mocks.fetchFile.mockResolvedValue({ ok: true, blob: async () => new Blob(['png']) });
    await mount();
    await act(async () => {});
    expect(mocks.fetchFile).toHaveBeenCalledWith('http://x/f1/signature');
    expect(document.body.querySelector('img[alt="İmza"]')?.getAttribute('src')).toBe('blob:sig');
  });

  it('eski detay gec gelirse yeni formun metnini ve imzasini ezmez', async () => {
    const first = deferred<ReturnType<typeof detail>>();
    mocks.getSignedForm.mockImplementation((id: string) => id === 'f1'
      ? first.promise : Promise.resolve(detail({ id: 'f2', templateTitle: 'İkinci Form', values: { ad: 'İkinci Hasta' } })));
    mocks.fetchFile.mockResolvedValue({ ok: true, blob: async () => new Blob(['png']) });
    vi.mocked(URL.createObjectURL).mockReturnValueOnce('blob:first').mockReturnValueOnce('blob:second');
    await mount('f1');
    await mount('f2');
    expect(document.body.textContent).toContain('İkinci Hasta');
    await act(async () => first.resolve(detail({ values: { ad: 'Eski Hasta' } })));
    expect(document.body.textContent).toContain('İkinci Form');
    expect(document.body.textContent).toContain('İkinci Hasta');
    expect(document.body.textContent).not.toContain('Eski Hasta');
    expect(document.body.querySelector('img[alt="İmza"]')?.getAttribute('src')).toBe('blob:second');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:first');
  });

  it('eski detay hatasi yeni acilan formu hata durumuna sokmaz', async () => {
    const first = deferred<ReturnType<typeof detail>>();
    mocks.getSignedForm.mockReturnValueOnce(first.promise).mockResolvedValue(detail({ id: 'f2', templateTitle: 'İkinci Form' }));
    await mount('f1');
    await mount('f2');
    await act(async () => first.reject(new Error('eski istek')));
    expect(document.body.textContent).toContain('İkinci Form');
    expect(document.body.textContent).not.toContain('Form yüklenemedi');
  });

  it('kapatilip ayni form yeniden acilinca ilk acilisin yanitini yok sayar', async () => {
    const first = deferred<ReturnType<typeof detail>>();
    mocks.getSignedForm.mockReturnValueOnce(first.promise).mockResolvedValue(detail({ templateTitle: 'Yeni Açılış' }));
    await mount('f1');
    await mount(null);
    await mount('f1');
    await act(async () => first.resolve(detail({ templateTitle: 'Önceki Açılış' })));
    expect(document.body.textContent).toContain('Yeni Açılış');
    expect(document.body.textContent).not.toContain('Önceki Açılış');
  });

  it('eski imza blobu gec cozulurse yeni formda gosterilmez ve URL temizlenir', async () => {
    const image = deferred<Blob>();
    mocks.getSignedForm.mockImplementation((id: string) => Promise.resolve(detail({ id, templateTitle: id })));
    mocks.fetchFile.mockReturnValueOnce(Promise.resolve({ ok: true, blob: () => image.promise }))
      .mockResolvedValue({ ok: true, blob: async () => new Blob(['new']) });
    vi.mocked(URL.createObjectURL).mockReturnValueOnce('blob:new').mockReturnValueOnce('blob:old');
    await mount('f1');
    await mount('f2');
    await act(async () => image.resolve(new Blob(['old'])));
    expect(document.body.querySelector('img[alt="İmza"]')?.getAttribute('src')).toBe('blob:new');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:old');
  });

  it('eski formun iptali tamamlaninca yeni formu yeniden yuklemez', async () => {
    const pending = deferred<void>();
    mocks.getSignedForm.mockImplementation((id: string) => Promise.resolve(detail({ id, templateTitle: id })));
    mocks.voidSignedForm.mockReturnValue(pending.promise);
    await mount('f1');
    await startVoid();
    await mount('f2');
    expect(buttonByText('PDF olarak indir')!.disabled).toBe(false);
    await act(async () => pending.resolve(undefined));
    expect(mocks.getSignedForm).toHaveBeenCalledTimes(2);
    expect(document.body.querySelector('h3')?.textContent).toBe('f2');
    expect(onChanged).toHaveBeenCalledTimes(1);
    expect(mocks.voidSignedForm).toHaveBeenCalledWith('f1', 'Düzeltme');
  });

  it('iptal sonrasi eski yeniden yukleme yaniti yeni formu ezmez', async () => {
    const refreshed = deferred<ReturnType<typeof detail>>();
    mocks.getSignedForm.mockResolvedValueOnce(detail()).mockReturnValueOnce(refreshed.promise)
      .mockResolvedValue(detail({ id: 'f2', templateTitle: 'İkinci Form' }));
    mocks.voidSignedForm.mockResolvedValue(undefined);
    await mount('f1');
    await startVoid();
    await mount('f2');
    await act(async () => refreshed.resolve(detail({ status: 'VOIDED', templateTitle: 'Eski Form' })));
    expect(document.body.textContent).toContain('İkinci Form');
    expect(document.body.textContent).not.toContain('Eski Form');
    expect(document.body.textContent).not.toContain('İPTAL EDİLDİ');
  });

  it('eski PDF isteginin hatasi yeni formun mesaji veya busy durumunu degistirmez', async () => {
    const oldPdf = deferred<{ ok: boolean }>();
    const newPdf = deferred<{ ok: boolean }>();
    mocks.getSignedForm.mockImplementation((id: string) => Promise.resolve(detail({ id })));
    mocks.fetchFile.mockImplementation((url: string) => {
      if (url === 'http://x/f1/pdf') return oldPdf.promise;
      if (url === 'http://x/f2/pdf') return newPdf.promise;
      return Promise.resolve({ ok: false });
    });
    await mount('f1');
    await act(async () => { buttonByText('PDF olarak indir')!.click(); });
    await mount('f2');
    await act(async () => { buttonByText('PDF olarak indir')!.click(); });
    await act(async () => oldPdf.reject(new Error('eski pdf')));
    expect(document.body.textContent).not.toContain('PDF indirilemedi');
    expect(buttonByText('PDF olarak indir')!.disabled).toBe(true);
    await act(async () => newPdf.reject(new Error('yeni pdf')));
    expect(document.body.textContent).toContain('PDF indirilemedi');
    expect(buttonByText('PDF olarak indir')!.disabled).toBe(false);
  });

  it.each([404, 500])('imza HTTP %s hatasinda yukleniyor yerine hata ve tekrar dugmesi gosterir', async status => {
    mocks.getSignedForm.mockResolvedValue(detail());
    mocks.fetchFile.mockResolvedValue({ ok: false, status });
    await mount();
    expect(document.body.textContent).toContain('İmza görseli yüklenemedi');
    expect(document.body.textContent).not.toContain('İmza görseli yükleniyor');
    expect(document.body.textContent).toContain('Pamuk');
    expect(buttonByText('İmzayı yeniden yükle')).toBeDefined();
  });

  it('imza ag hatasinda yukleme durumunu bitirir', async () => {
    mocks.getSignedForm.mockResolvedValue(detail());
    mocks.fetchFile.mockRejectedValue(new Error('network'));
    await mount();
    expect(document.body.textContent).toContain('İmza görseli yüklenemedi');
    expect(document.body.textContent).not.toContain('İmza görseli yükleniyor');
  });

  it('imza govdesi okunamazsa hata gosterir', async () => {
    mocks.getSignedForm.mockResolvedValue(detail());
    mocks.fetchFile.mockResolvedValue({ ok: true, blob: async () => { throw new Error('body'); } });
    await mount();
    expect(document.body.textContent).toContain('İmza görseli yüklenemedi');
    expect(document.body.textContent).not.toContain('İmza görseli yükleniyor');
  });

  it('tarayici gorseli cozemeyince tekrar yuklemeye izin verir ve eski URL temizlenir', async () => {
    mocks.getSignedForm.mockResolvedValue(detail());
    mocks.fetchFile.mockResolvedValue({ ok: true, blob: async () => new Blob(['png']) });
    vi.mocked(URL.createObjectURL).mockReturnValueOnce('blob:broken').mockReturnValueOnce('blob:fixed');
    await mount();
    await act(async () => { document.body.querySelector('img[alt="İmza"]')!.dispatchEvent(new Event('error')); });
    expect(document.body.textContent).toContain('İmza görseli yüklenemedi');
    expect(document.body.querySelector('img[alt="İmza"]')).toBeNull();
    await act(async () => { buttonByText('İmzayı yeniden yükle')!.click(); });
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:broken');
    expect(document.body.querySelector('img[alt="İmza"]')?.getAttribute('src')).toBe('blob:fixed');
    expect(document.body.textContent).not.toContain('İmza görseli yüklenemedi');
  });

  it('basarili tekrar yalniz imzayi yukler ve hata durumunu temizler', async () => {
    const retry = deferred<{ ok: boolean; blob: () => Promise<Blob> }>();
    mocks.getSignedForm.mockResolvedValue(detail());
    mocks.fetchFile.mockResolvedValueOnce({ ok: false }).mockReturnValueOnce(retry.promise);
    await mount();
    await act(async () => { buttonByText('İmzayı yeniden yükle')!.click(); });
    expect(document.body.textContent).toContain('İmza görseli yükleniyor');
    expect(document.body.textContent).not.toContain('İmza görseli yüklenemedi');
    expect(buttonByText('İmzayı yeniden yükle')).toBeUndefined();
    await act(async () => retry.resolve({ ok: true, blob: async () => new Blob(['png']) }));
    expect(document.body.querySelector('img[alt="İmza"]')?.getAttribute('src')).toBe('blob:sig');
    expect(document.body.textContent).not.toContain('İmza görseli yükleniyor');
    expect(mocks.getSignedForm).toHaveBeenCalledTimes(1);
    expect(mocks.fetchFile).toHaveBeenCalledTimes(2);
  });

  it('eski formun gec gelen imza hatasi yeni formu etkilemez', async () => {
    const old = deferred<{ ok: boolean }>();
    mocks.getSignedForm.mockImplementation((id: string) => Promise.resolve(detail({ id })));
    mocks.fetchFile.mockReturnValueOnce(old.promise)
      .mockResolvedValue({ ok: true, blob: async () => new Blob(['png']) });
    await mount('f1');
    await mount('f2');
    await act(async () => old.reject(new Error('old image')));
    expect(document.body.querySelector('img[alt="İmza"]')?.getAttribute('src')).toBe('blob:sig');
    expect(document.body.textContent).not.toContain('İmza görseli yüklenemedi');
    expect(buttonByText('İmzayı yeniden yükle')).toBeUndefined();
  });
  it('successful void followed by reload failure preserves document and retries only the read', async () => {
    mocks.getSignedForm.mockResolvedValueOnce(detail()).mockRejectedValueOnce(new Error('offline'));
    mocks.voidSignedForm.mockResolvedValue(undefined);
    await mount(); await startVoid();
    expect(document.body.textContent).toContain('İPTAL EDİLDİ');
    expect(document.body.textContent).toContain('Pamuk');
    expect(document.body.textContent).toContain('Form iptal edildi, ancak güncel bilgiler yüklenemedi.');
    expect(document.body.textContent).not.toContain('Form yüklenemedi');
    expect(buttonByText('İptal et')).toBeUndefined();
    mocks.getSignedForm.mockResolvedValue(detail({ status: 'VOIDED', voidReason: 'Düzeltme', voidedAt: '2026-11-07T11:00:00Z' }));
    await act(async () => { buttonByText('Bilgileri yeniden yükle')!.click(); });
    expect(document.body.textContent).not.toContain('ancak güncel bilgiler');
    expect(mocks.voidSignedForm).toHaveBeenCalledTimes(1);
  });

  it('revokes PDF object URL after download gets a chance to start', async () => {
    mocks.getSignedForm.mockResolvedValue(detail());
    mocks.fetchFile.mockImplementation(async url => String(url).endsWith('/pdf')
      ? new Response('pdf', { headers: { 'Content-Type': 'application/pdf' } }) : { ok: false });
    await mount();
    URL.createObjectURL = vi.fn(() => 'blob:pdf');
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.useFakeTimers();
    try {
      await act(async () => { buttonByText('PDF olarak indir')!.click(); });
      expect(click).toHaveBeenCalledTimes(1);
      expect((click.mock.instances[0] as HTMLAnchorElement).isConnected).toBe(false);
      expect(URL.revokeObjectURL).not.toHaveBeenCalledWith('blob:pdf');
      vi.advanceTimersByTime(10_000);
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:pdf');
    } finally { vi.clearAllTimers(); vi.useRealTimers(); }
  });

});
