// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/labApi', () => ({
  labApi: {
    get: vi.fn(),
    complete: vi.fn(),
    evaluate: vi.fn(),
    ocrFromImages: vi.fn(),
    cancel: vi.fn(),
    uploadFile: vi.fn(),
    fileUrl: vi.fn(() => 'http://example.test/file'),
    authHeader: vi.fn(() => ({})),
  },
}));

import { labApi, LabResultDetail } from '../../api/labApi';
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
afterEach(() => container?.remove());

async function render(resultId: string | null) {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(
      <LabResultDetailModal resultId={resultId} onClose={() => undefined} onChanged={() => undefined} />
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
