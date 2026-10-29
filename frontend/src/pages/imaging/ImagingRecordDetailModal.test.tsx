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
    authHeader: vi.fn(() => ({})),
  },
}));

import { imagingApi, ImagingRecordDetail } from '../../api/imagingApi';
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
  // ImagingRecordDetailModal her image/* dosya icin onizleme yuklemek uzere fetch() cagirir --
  // bu testte gercek bir dosya sunucusu yok, basarisiz (ok:false) donup sessizce atlanmasi yeterli.
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }));
});

afterEach(() => {
  container?.remove();
  vi.unstubAllGlobals();
});

async function render(recordId: string | null) {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(
      <ImagingRecordDetailModal recordId={recordId} onClose={() => undefined} onChanged={() => undefined} />
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
