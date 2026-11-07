// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/imagingApi', () => ({
  imagingApi: {
    get: vi.fn(),
    complete: vi.fn(),
    ocrFromImages: vi.fn(),
    cancel: vi.fn(),
    uploadFile: vi.fn(),
    fileUrl: vi.fn(() => 'http://example.test/file'),
    fetchFile: vi.fn().mockResolvedValue({ ok: false }),
  },
}));

// Fotograf hazirlama canvas ister (jsdom'da yok); dosyayi aynen dondurur.
vi.mock('../../lib/prepareImage', () => ({
  prepareImage: vi.fn(async (file: File) => file),
  ImagePrepareError: class ImagePrepareError extends Error {},
}));

import { ApiError } from '../../api/client';
import { imagingApi, ImagingRecordDetail } from '../../api/imagingApi';
import { prepareImage } from '../../lib/prepareImage';
import { ImagingRecordDetailModal } from './ImagingRecordDetailModal';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) -- Task 6.
const BASE_DETAIL: ImagingRecordDetail = {
  id: 'i1',
  patientId: 'p1',
  patientName: 'Boncuk',
  ownerId: 'o1',
  ownerFullName: 'Furkan',
  modality: 'XRAY',
  bodyRegion: 'Toraks',
  status: 'PENDING',
  requestedAt: '2026-10-29T10:00:00Z',
  resultedAt: null,
  orderingStaffName: 'Dr. X',
  findings: null,
  notes: null,
  files: [{ id: 'f1', fileName: 'rapor.jpg', contentType: 'image/jpeg', fileSize: 1000, uploadedAt: '2026-10-29T10:00:00Z' }],
};

let container: HTMLDivElement;

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(imagingApi.fetchFile).mockResolvedValue({ ok: false } as Response);
  // ImagingRecordDetailModal her image/* dosya icin onizleme yuklemek uzere fetch() cagirir --
  // bu testte gercek bir dosya sunucusu yok, basarisiz (ok:false) donup sessizce atlanmasi yeterli.
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }));
});

afterEach(() => {
  container?.remove();
  vi.unstubAllGlobals();
});

async function render(recordId: string | null, autoOcrFileIds?: string[]) {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(
      <ImagingRecordDetailModal
        recordId={recordId}
        autoOcrFileIds={autoOcrFileIds}
        onClose={() => undefined}
        onChanged={() => undefined}
      />
    );
  });
}

describe('ImagingRecordDetailModal — fotograftan okuma (2026-10-29)', () => {
  it('shows a selection checkbox on the image thumbnail and disables the OCR button until one is selected', async () => {
    vi.mocked(imagingApi.get).mockResolvedValue(BASE_DETAIL);
    await render('i1');

    const checkbox = document.querySelector<HTMLInputElement>('input[type="checkbox"]');
    expect(checkbox).not.toBeNull();
    const ocrButton = Array.from(document.querySelectorAll('button')).find((b) => b.textContent === '📷 Fotoğraftan Oku')!;
    expect(ocrButton.disabled).toBe(true);
  });

  it('selecting a thumbnail enables the button, and clicking fills the findings field from OCR', async () => {
    vi.mocked(imagingApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(imagingApi.ocrFromImages).mockResolvedValue({
      findings: 'Sol akciğer alt lobda hafif infiltrasyon.',
      modelConnected: true,
    });
    await render('i1');

    const checkbox = document.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    await act(async () => checkbox.click());

    const ocrButton = Array.from(document.querySelectorAll('button')).find((b) => b.textContent === '📷 Fotoğraftan Oku')!;
    expect(ocrButton.disabled).toBe(false);
    await act(async () => ocrButton.click());

    expect(imagingApi.ocrFromImages).toHaveBeenCalledWith('i1', ['f1']);
    const textarea = document.querySelector<HTMLTextAreaElement>('textarea');
    expect(textarea?.value).toBe('Sol akciğer alt lobda hafif infiltrasyon.');
  });
});

describe('ImagingRecordDetailModal — kamera fotografinin otomatik okunmasi (2026-11-07)', () => {
  async function uploadViaDetail(file: File) {
    const input = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    await act(async () => {
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });
  }

  it('reads the camera files once after the record loads and fills findings', async () => {
    vi.mocked(imagingApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(imagingApi.uploadFile).mockResolvedValue('f2');
    vi.mocked(imagingApi.ocrFromImages).mockResolvedValue({ findings: 'Kalp silueti normal.', modelConnected: true });
    await render('i1', ['f1']);

    expect(imagingApi.ocrFromImages).toHaveBeenCalledTimes(1);
    expect(imagingApi.ocrFromImages).toHaveBeenCalledWith('i1', ['f1']);
    expect(document.querySelector<HTMLTextAreaElement>('textarea')?.value).toBe('Kalp silueti normal.');

    // Dosya yukleme load()'u yeniden calistirir -- okuma tekrar etmemeli (AI kotasi).
    await uploadViaDetail(new File(['x'], 'ek.jpg', { type: 'image/jpeg' }));
    expect(imagingApi.get).toHaveBeenCalledTimes(2);
    expect(imagingApi.ocrFromImages).toHaveBeenCalledTimes(1);
  });

  it('shows the error banner and keeps findings editable when auto reading fails (quota)', async () => {
    vi.mocked(imagingApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(imagingApi.ocrFromImages).mockRejectedValue(new ApiError('Aylık AI kotanız doldu.', 'AI_QUOTA_EXCEEDED', 429));
    await render('i1', ['f1']);

    expect(document.body.textContent).toContain('Aylık AI kotanız doldu.');
    expect(document.querySelector<HTMLTextAreaElement>('textarea')!.disabled).toBe(false);
  });

  it('does not read automatically when no autoOcrFileIds are given', async () => {
    vi.mocked(imagingApi.get).mockResolvedValue(BASE_DETAIL);
    await render('i1');

    expect(imagingApi.ocrFromImages).not.toHaveBeenCalled();
  });

  it('passes images added from the detail through prepareImage without reading them', async () => {
    vi.mocked(imagingApi.get).mockResolvedValue(BASE_DETAIL);
    vi.mocked(imagingApi.uploadFile).mockResolvedValue('f2');
    await render('i1');

    const photo = new File(['x'], 'ek.jpg', { type: 'image/jpeg' });
    await uploadViaDetail(photo);

    expect(prepareImage).toHaveBeenCalledWith(photo, 'document');
    expect(imagingApi.uploadFile).toHaveBeenCalledWith('i1', photo);
    expect(imagingApi.ocrFromImages).not.toHaveBeenCalled();
  });
});

describe('ImagingRecordDetailModal — kayit degisimi ve otomatik okuma', () => {
  function mount(recordId: string | null) {
    container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);
    const show = async (id: string | null) => {
      await act(async () => {
        root.render(
          <ImagingRecordDetailModal recordId={id} autoOcrFileIds={['f1']} onClose={() => undefined} onChanged={() => undefined} />
        );
      });
    };
    return { show, initial: show(recordId) };
  }

  it('drops a late OCR result when another record was opened meanwhile', async () => {
    vi.mocked(imagingApi.get).mockImplementation(async (id: string) =>
      id === 'i1' ? BASE_DETAIL : { ...BASE_DETAIL, id: 'i2', findings: 'I2 bulgu' }
    );
    let resolveOcr!: (v: Awaited<ReturnType<typeof imagingApi.ocrFromImages>>) => void;
    vi.mocked(imagingApi.ocrFromImages).mockReturnValue(new Promise((r) => (resolveOcr = r)));

    const m = mount('i1');
    await m.initial;
    expect(imagingApi.ocrFromImages).toHaveBeenCalledTimes(1);

    await m.show('i2');
    await act(async () => {
      resolveOcr({ findings: 'Eski kayit bulgusu', modelConnected: true });
    });

    expect(document.querySelector<HTMLTextAreaElement>('textarea')?.value).toBe('I2 bulgu');
  });

  it('does not start auto reading when the modal was closed before the record loaded', async () => {
    let resolveGet!: (v: ImagingRecordDetail) => void;
    vi.mocked(imagingApi.get).mockReturnValue(new Promise((r) => (resolveGet = r)));

    const m = mount('i1');
    await m.initial;
    await m.show(null);
    await act(async () => {
      resolveGet(BASE_DETAIL);
    });

    expect(imagingApi.ocrFromImages).not.toHaveBeenCalled();
  });
});
