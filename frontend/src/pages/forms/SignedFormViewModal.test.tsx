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

async function mount() {
  await act(async () => {
    root.render(<SignedFormViewModal signedFormId="f1" onClose={() => {}} onChanged={onChanged} />);
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
});

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
});
