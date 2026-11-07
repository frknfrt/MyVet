// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../lib/prepareImage', () => {
  class ImagePrepareError extends Error {
    constructor() {
      super('Bu fotoğraf biçimi desteklenmiyor.');
    }
  }
  return { prepareImage: vi.fn(), ImagePrepareError };
});
vi.mock('../../api/patientApi', () => ({
  patientApi: { uploadPhoto: vi.fn(), removePhoto: vi.fn() },
}));

import { patientApi } from '../../api/patientApi';
import { prepareImage, ImagePrepareError } from '../../lib/prepareImage';
import { PatientPhotoMenu } from './PatientPhotoMenu';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;
const onChanged = vi.fn();
const onClose = vi.fn();

async function render(hasPhoto: boolean) {
  await act(async () => {
    root.render(<PatientPhotoMenu patientId="p1" hasPhoto={hasPhoto} onChanged={onChanged} onClose={onClose} />);
  });
}

async function pick(input: HTMLInputElement, file: File) {
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  await act(async () => {
    input.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  vi.clearAllMocks();
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe('PatientPhotoMenu', () => {
  it('kamera inputunda capture=environment, galeride capture yok', async () => {
    await render(false);
    const inputs = container.querySelectorAll('input[type=file]');
    expect(inputs[0].getAttribute('capture')).toBe('environment');
    expect(inputs[1].hasAttribute('capture')).toBe(false);
  });

  it('dosya secilince hazirlanir, yuklenir, onChanged cagrilir', async () => {
    const original = new File(['a'], 'a.jpg', { type: 'image/jpeg' });
    const prepared = new File(['b'], 'b.jpg', { type: 'image/jpeg' });
    vi.mocked(prepareImage).mockResolvedValue(prepared);
    vi.mocked(patientApi.uploadPhoto).mockResolvedValue({ photoVersion: 'v9' });
    await render(false);
    await pick(container.querySelectorAll('input[type=file]')[1] as HTMLInputElement, original);
    expect(prepareImage).toHaveBeenCalledWith(original, 'profile');
    expect(patientApi.uploadPhoto).toHaveBeenCalledWith('p1', prepared);
    expect(onChanged).toHaveBeenCalledWith('v9');
  });

  it('ImagePrepareError mesaji gosterilir, yukleme yapilmaz', async () => {
    vi.mocked(prepareImage).mockRejectedValue(new ImagePrepareError());
    await render(false);
    await pick(container.querySelectorAll('input[type=file]')[0] as HTMLInputElement, new File(['a'], 'a.jpg'));
    expect(container.textContent).toContain('Bu fotoğraf biçimi desteklenmiyor.');
    expect(patientApi.uploadPhoto).not.toHaveBeenCalled();
    expect(onChanged).not.toHaveBeenCalled();
  });

  it('Fotografi Kaldir yalniz hasPhoto iken gorunur ve calisir', async () => {
    await render(false);
    expect(container.textContent).not.toContain('Fotoğrafı Kaldır');
    await render(true);
    vi.mocked(patientApi.removePhoto).mockResolvedValue(undefined);
    const btn = Array.from(container.querySelectorAll('button')).find((b) => b.textContent === 'Fotoğrafı Kaldır')!;
    await act(async () => {
      btn.click();
    });
    expect(patientApi.removePhoto).toHaveBeenCalledWith('p1');
    expect(onChanged).toHaveBeenCalledWith(null);
  });

  it('Escape menuyu kapatir', async () => {
    await render(false);
    await act(async () => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('arka plana tiklayinca kapanir', async () => {
    await render(false);
    const backdrop = container.querySelector('[data-testid=photo-menu-backdrop]') as HTMLElement;
    await act(async () => {
      backdrop.click();
    });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('yukleme surerken Escape ve arka plan kapatmaz', async () => {
    vi.mocked(prepareImage).mockReturnValue(new Promise(() => {}));
    await render(false);
    await pick(container.querySelectorAll('input[type=file]')[0] as HTMLInputElement, new File(['a'], 'a.jpg'));
    expect(container.textContent).toContain('Yükleniyor...');
    await act(async () => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      (container.querySelector('[data-testid=photo-menu-backdrop]') as HTMLElement).click();
    });
    expect(onClose).not.toHaveBeenCalled();
  });
});
