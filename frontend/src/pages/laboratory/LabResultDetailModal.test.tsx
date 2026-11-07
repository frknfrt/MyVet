// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/labApi', () => ({
  labApi: {
    get: vi.fn(),
    complete: vi.fn(),
    evaluate: vi.fn(),
    ocrFromImages: vi.fn(),
    cancel: vi.fn(),
    uploadFile: vi.fn(),
    fileUrl: vi.fn(() => 'http://example.test/file'),
    fetchFile: vi.fn(),
  },
}));

// Fotograf hazirlama canvas ister (jsdom'da yok); dosyayi aynen dondurur.
vi.mock('../../lib/prepareImage', () => ({
  prepareImage: vi.fn(async (file: File) => file),
  ImagePrepareError: class ImagePrepareError extends Error {},
}));

import { ApiError } from '../../api/client';
import { labApi, LabResultDetail } from '../../api/labApi';
import { prepareImage } from '../../lib/prepareImage';
import { LabResultDetailModal } from './LabResultDetailModal';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) -- Task 6.
const BASE_DETAIL: LabResultDetail = {
  id: 'r1',
  patientId: 'p1',
  patientName: 'Boncuk',
  ownerId: 'o1',
  ownerFullName: 'Furkan',
  testName: 'Tam Kan Sayımı',
  status: 'PENDING',
  requestedAt: '2026-10-29T10:00:00Z',
  resultedAt: null,
  orderingStaffName: 'Dr. X',
  resultSummary: null,
  notes: null,
  items: [],
  files: [
    { id: 'f1', fileName: 'rapor.jpg', contentType: 'image/jpeg', fileSize: 1000, uploadedAt: '2026-10-29T10:00:00Z' },
    { id: 'f2', fileName: 'eski-rapor.pdf', contentType: 'application/pdf', fileSize: 2000, uploadedAt: '2026-10-29T10:00:00Z' },
  ],
};

let container: HTMLDivElement;
beforeEach(() => vi.clearAllMocks());
afterEach(() => container?.remove());

async function render(resultId: string | null, autoOcrFileIds?: string[]) {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(
      <LabResultDetailModal
        resultId={resultId}
        autoOcrFileIds={autoOcrFileIds}
        onClose={() => undefined}
        onChanged={() => undefined}
      />
    );
  });
}

describe('LabResultDetailModal — fotograftan okuma (2026-10-29)', () => {
  it('shows a selection checkbox only for image/* files, not for other attachments', async () => {
    vi.mocked(labApi.get).mockResolvedValue(BASE_DETAIL);
    await render('r1');

    const checkboxes = document.querySelectorAll('input[type="checkbox"]');
    expect(checkboxes.length).toBe(1);
  });

  it('keeps the regular evaluate button when no file is selected (no regression)', async () => {
    vi.mocked(labApi.get).mockResolvedValue(BASE_DETAIL);
    await render('r1');

    const buttons = Array.from(document.querySelectorAll('button'));
    expect(buttons.some((b) => b.textContent === '⚡ Otomatik Ön-Değerlendirme')).toBe(true);
    expect(buttons.some((b) => b.textContent === '📷 Oku + Değerlendir')).toBe(false);
  });

  it('selecting an image file switches the button to OCR and fills items (incl. reference range) on click', async () => {
    vi.mocked(labApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(labApi.ocrFromImages).mockResolvedValue({
      items: [{ parameterName: 'WBC', value: '12.3', unit: '10^3/uL', referenceRange: '6.0-17.0', flag: 'NORMAL' }],
      draftSummary: 'Değerlendirilen parametrelerin tümü referans aralığında.',
      modelConnected: true,
    });
    await render('r1');

    const checkbox = document.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    await act(async () => checkbox.click());

    const ocrButton = Array.from(document.querySelectorAll('button')).find((b) => b.textContent === '📷 Oku + Değerlendir');
    expect(ocrButton).toBeDefined();
    await act(async () => ocrButton!.click());

    expect(labApi.ocrFromImages).toHaveBeenCalledWith('r1', ['f1']);
    expect(labApi.evaluate).not.toHaveBeenCalled();
    // Item alanlari <input> olarak render edilir; deger input.value'da tutulur,
    // document.body.textContent'te GORUNMEZ (input/select degerleri metin dugumu degildir) --
    // bu yuzden gercek DOM degerlerini kontrol ediyoruz.
    const textInputs = Array.from(document.querySelectorAll('input')).filter(
      (el) => (el as HTMLInputElement).type !== 'checkbox'
    ) as HTMLInputElement[];
    const values = textInputs.map((el) => el.value);
    expect(values).toContain('WBC');
    expect(values).toContain('6.0-17.0');
  });

  it('shows a warning banner when the model could not be reached', async () => {
    vi.mocked(labApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(labApi.ocrFromImages).mockResolvedValue({
      items: [],
      draftSummary: '[AI modeli fotografi okuyamadi -- degerleri elle girin.]',
      modelConnected: false,
    });
    await render('r1');

    const checkbox = document.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    await act(async () => checkbox.click());
    const ocrButton = Array.from(document.querySelectorAll('button')).find((b) => b.textContent === '📷 Oku + Değerlendir')!;
    await act(async () => ocrButton.click());

    expect(document.body.textContent).toContain('AI modeline bağlanılamadı');
  });
});

describe('LabResultDetailModal — kamera fotografinin otomatik okunmasi (2026-11-07)', () => {
  function textInputValues(): string[] {
    return (Array.from(document.querySelectorAll('input')) as HTMLInputElement[])
      .filter((el) => el.type !== 'checkbox' && el.type !== 'file')
      .map((el) => el.value);
  }

  async function uploadViaDetail(file: File) {
    const input = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    await act(async () => {
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });
  }

  it('reads the camera files once after the record loads and fills the items', async () => {
    vi.mocked(labApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(labApi.uploadFile).mockResolvedValue('f3');
    vi.mocked(labApi.ocrFromImages).mockResolvedValue({
      items: [{ parameterName: 'WBC', value: '12.3', unit: '10^3/uL', referenceRange: '6.0-17.0', flag: 'NORMAL' }],
      draftSummary: 'Taslak',
      modelConnected: true,
    });
    await render('r1', ['f1']);

    expect(labApi.ocrFromImages).toHaveBeenCalledTimes(1);
    expect(labApi.ocrFromImages).toHaveBeenCalledWith('r1', ['f1']);
    expect(textInputValues()).toContain('WBC');
    expect(textInputValues()).toContain('6.0-17.0');

    // Dosya yukleme load()'u yeniden calistirir -- okuma tekrar etmemeli (AI kotasi).
    await uploadViaDetail(new File(['x'], 'ek.jpg', { type: 'image/jpeg' }));
    expect(labApi.get).toHaveBeenCalledTimes(2);
    expect(labApi.ocrFromImages).toHaveBeenCalledTimes(1);
  });

  it('shows the error banner and keeps the form editable when auto reading fails (quota)', async () => {
    vi.mocked(labApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(labApi.ocrFromImages).mockRejectedValue(new ApiError('Aylık AI kotanız doldu.', 'AI_QUOTA_EXCEEDED', 429));
    await render('r1', ['f1']);

    expect(document.body.textContent).toContain('Aylık AI kotanız doldu.');
    const textarea = document.querySelector<HTMLTextAreaElement>('textarea')!;
    expect(textarea.disabled).toBe(false);
    const paramInput = document.querySelector<HTMLInputElement>('input[placeholder="Örn. WBC"]')!;
    expect(paramInput.disabled).toBe(false);
  });

  it('does not read automatically when no autoOcrFileIds are given', async () => {
    vi.mocked(labApi.get).mockResolvedValue(BASE_DETAIL);
    await render('r1');

    expect(labApi.ocrFromImages).not.toHaveBeenCalled();
  });

  it('passes images added from the detail through prepareImage without reading them', async () => {
    vi.mocked(labApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(labApi.uploadFile).mockResolvedValue('f3');
    await render('r1');

    const photo = new File(['x'], 'ek.jpg', { type: 'image/jpeg' });
    await uploadViaDetail(photo);

    expect(prepareImage).toHaveBeenCalledWith(photo, 'document');
    expect(labApi.uploadFile).toHaveBeenCalledWith('r1', photo);
    expect(labApi.ocrFromImages).not.toHaveBeenCalled();
  });
});

describe('LabResultDetailModal — kayit degisimi ve otomatik okuma', () => {
  function mount(resultId: string | null) {
    container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);
    const show = async (id: string | null) => {
      await act(async () => {
        root.render(
          <LabResultDetailModal resultId={id} autoOcrFileIds={['f1']} onClose={() => undefined} onChanged={() => undefined} />
        );
      });
    };
    return { show, initial: show(resultId) };
  }

  it('drops a late OCR result when another record was opened meanwhile', async () => {
    vi.mocked(labApi.get).mockImplementation(async (id: string) =>
      id === 'r1' ? BASE_DETAIL : { ...BASE_DETAIL, id: 'r2', resultSummary: 'R2 özet' }
    );
    let resolveOcr!: (v: Awaited<ReturnType<typeof labApi.ocrFromImages>>) => void;
    vi.mocked(labApi.ocrFromImages).mockReturnValue(new Promise((r) => (resolveOcr = r)));

    const m = mount('r1');
    await m.initial;
    expect(labApi.ocrFromImages).toHaveBeenCalledTimes(1);

    await m.show('r2');
    await act(async () => {
      resolveOcr({
        items: [{ parameterName: 'WBC', value: '12.3', unit: '', referenceRange: '', flag: 'NORMAL' }],
        draftSummary: 'Eski kayit taslagi',
        modelConnected: true,
      });
    });

    expect(document.querySelector<HTMLTextAreaElement>('textarea')?.value).toBe('R2 özet');
    expect(Array.from(document.querySelectorAll('input')).map((i) => i.value)).not.toContain('WBC');
  });

  it('does not start auto reading when the modal was closed before the record loaded', async () => {
    let resolveGet!: (v: LabResultDetail) => void;
    vi.mocked(labApi.get).mockReturnValue(new Promise((r) => (resolveGet = r)));

    const m = mount('r1');
    await m.initial;
    await m.show(null);
    await act(async () => {
      resolveGet(BASE_DETAIL);
    });

    expect(labApi.ocrFromImages).not.toHaveBeenCalled();
  });
});
