// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/labApi', () => ({
  labApi: { request: vi.fn(), uploadFile: vi.fn() },
}));

vi.mock('../../api/patientApi', () => ({
  patientApi: { searchOwners: vi.fn(), getOwnerProfile: vi.fn() },
}));

// Fotograf hazirlama canvas ister (jsdom'da yok); hazirlanmis dosyayi taklit eder.
vi.mock('../../lib/prepareImage', () => ({
  prepareImage: vi.fn(async (file: File) => new File(['jpeg'], `hazir-${file.name}`, { type: 'image/jpeg' })),
  ImagePrepareError: class ImagePrepareError extends Error {},
}));

import { labApi } from '../../api/labApi';
import { OwnerProfile, patientApi } from '../../api/patientApi';
import { prepareImage } from '../../lib/prepareImage';
import { NewLabResultModal } from './NewLabResultModal';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;

beforeEach(() => {
  vi.clearAllMocks();
  // Kamera onizlemesi object URL kullanir; jsdom'da yok.
  URL.createObjectURL = vi.fn(() => 'blob:onizleme');
  URL.revokeObjectURL = vi.fn();
  vi.mocked(patientApi.searchOwners).mockResolvedValue([{ id: 'o1', fullName: 'Ayşe Yılmaz', phone: '555' }] as never);
  vi.mocked(patientApi.getOwnerProfile).mockResolvedValue({ patients: [{ id: 'p1', name: 'Boncuk' }] } as unknown as OwnerProfile);
  vi.mocked(labApi.request).mockResolvedValue('r1');
});

afterEach(() => container?.remove());

async function render(onCreated: (id: string, opts: { autoOcrFileIds: string[] }) => void) {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<NewLabResultModal open onClose={() => undefined} onCreated={onCreated} />);
  });
}

function setInputValue(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  setter.call(input, value);
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

async function pickFile(input: HTMLInputElement, file: File) {
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  await act(async () => {
    input.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

async function fillRequiredFields() {
  const testName = document.querySelector<HTMLInputElement>('input[list="lab-test-names"]')!;
  await act(async () => setInputValue(testName, 'Hemogram'));
  const ownerInput = document.querySelector<HTMLInputElement>('input[placeholder="Hasta sahibi bilinmiyor"]')!;
  await act(async () => {
    ownerInput.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
  });
  // Sahip aramasi 250 ms gecikmeli calisir.
  await act(async () => {
    await new Promise((r) => setTimeout(r, 300));
  });
  const option = Array.from(document.querySelectorAll('span')).find((el) => el.textContent === 'Ayşe Yılmaz')!;
  await act(async () => option.click());
}

function cameraInput(): HTMLInputElement {
  return document.querySelector<HTMLInputElement>('input[type="file"][capture]')!;
}

function pickerInput(): HTMLInputElement {
  return document.querySelector<HTMLInputElement>('input[type="file"]:not([capture])')!;
}

describe('NewLabResultModal — fotograf cekme (2026-11-07)', () => {
  it('offers a camera input for images that opens the rear camera', async () => {
    await render(() => undefined);

    const input = cameraInput();
    expect(input).not.toBeNull();
    expect(input.getAttribute('accept')).toBe('image/*');
    expect(input.getAttribute('capture')).toBe('environment');
    const button = Array.from(document.querySelectorAll('button')).find((b) => b.textContent === '📷 Fotoğraf Çek');
    expect(button).toBeDefined();
    // Dosya secici PDF'e acik kalir.
    expect(pickerInput().hasAttribute('accept')).toBe(false);
  });

  it('prepares camera photos, uploads PDFs as-is and reports only camera file ids for auto reading', async () => {
    vi.mocked(labApi.uploadFile).mockResolvedValueOnce('f-cam').mockResolvedValueOnce('f-pdf');
    const onCreated = vi.fn();
    await render(onCreated);
    await fillRequiredFields();

    const photo = new File(['raw'], 'IMG_0001.HEIC', { type: 'image/heic' });
    await pickFile(cameraInput(), photo);
    const pdf = new File(['%PDF'], 'rapor.pdf', { type: 'application/pdf' });
    await pickFile(pickerInput(), pdf);

    expect(prepareImage).toHaveBeenCalledTimes(1);
    expect(prepareImage).toHaveBeenCalledWith(photo, 'document');
    expect(Array.from(document.querySelectorAll('button')).some((b) => b.textContent === '📷 Sayfa Ekle')).toBe(true);
    expect(document.querySelector('img')?.getAttribute('src')).toBe('blob:onizleme');

    const form = document.querySelector('form')!;
    await act(async () => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });

    expect(labApi.request).toHaveBeenCalledWith({ patientId: 'p1', testName: 'Hemogram' });
    const uploaded = vi.mocked(labApi.uploadFile).mock.calls.map((c) => (c[1] as File).name);
    expect(uploaded).toEqual(['hazir-IMG_0001.HEIC', 'rapor.pdf']);
    expect(vi.mocked(labApi.uploadFile).mock.calls[1][1]).toBe(pdf);
    expect(onCreated).toHaveBeenCalledWith('r1', { autoOcrFileIds: ['f-cam'] });
  });
});

describe('NewLabResultModal — kapanip acilma (2026-11-07)', () => {
  it('drops a photo whose preparation finished after the modal was closed and reopened', async () => {
    let resolvePrepare!: (f: File) => void;
    vi.mocked(prepareImage).mockReturnValueOnce(new Promise<File>((r) => (resolvePrepare = r)));

    container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);
    const show = async (open: boolean) => {
      await act(async () => {
        root.render(<NewLabResultModal open={open} onClose={() => undefined} onCreated={() => undefined} />);
      });
    };
    await show(true);
    await pickFile(cameraInput(), new File(['raw'], 'IMG_0002.HEIC', { type: 'image/heic' }));

    await show(false);
    await show(true);
    await act(async () => {
      resolvePrepare(new File(['jpeg'], 'hazir.jpg', { type: 'image/jpeg' }));
    });

    expect(document.querySelector('img')).toBeNull();
    expect(Array.from(document.querySelectorAll('button')).some((b) => b.textContent === '📷 Fotoğraf Çek')).toBe(true);
  });
});
