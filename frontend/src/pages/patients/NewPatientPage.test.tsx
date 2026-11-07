// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../components/layout/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../../api/patientApi', () => ({
  patientApi: {
    listSpecies: vi.fn(),
    listBreeds: vi.fn(),
    getOwnerProfile: vi.fn(),
    searchOwners: vi.fn(),
    register: vi.fn(),
    uploadPhoto: vi.fn(),
  },
}));

vi.mock('../../lib/prepareImage', () => {
  class ImagePrepareError extends Error {
    constructor() {
      super('Bu fotoğraf biçimi desteklenmiyor.');
    }
  }
  return { prepareImage: vi.fn(), ImagePrepareError };
});

import { patientApi } from '../../api/patientApi';
import { ImagePrepareError, prepareImage } from '../../lib/prepareImage';
import { NewPatientPage } from './NewPatientPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;

function PatientCardStub() {
  const location = useLocation();
  const state = location.state as { photoUploadFailed?: boolean } | null;
  return <div data-testid="card">{state?.photoUploadFailed ? 'photo-failed' : 'ok'}</div>;
}

async function render() {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(
      <MemoryRouter
        initialEntries={['/hastalar/yeni?ownerId=o1']}
       
      >
        <Routes>
          <Route path="/hastalar/yeni" element={<NewPatientPage />} />
          <Route path="/hastalar/:id" element={<PatientCardStub />} />
        </Routes>
      </MemoryRouter>
    );
  });
}

async function pick(input: HTMLInputElement, file: File) {
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  await act(async () => {
    input.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

async function fillNameAndSubmit() {
  const nameInput = container.querySelector('input:not([type])') as HTMLInputElement;
  await act(async () => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    setter.call(nameInput, 'Pamuk');
    nameInput.dispatchEvent(new Event('input', { bubbles: true }));
  });
  const form = container.querySelector('form') as HTMLFormElement;
  await act(async () => {
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
}

const raw = new File(['raw'], 'IMG_1.HEIC', { type: 'image/heic' });
const prepared = new File(['jpg'], 'IMG_1.jpg', { type: 'image/jpeg' });

beforeEach(() => {
  vi.mocked(patientApi.listSpecies).mockResolvedValue([{ id: 's1', name: 'Köpek' }] as never);
  vi.mocked(patientApi.listBreeds).mockResolvedValue([]);
  vi.mocked(patientApi.getOwnerProfile).mockResolvedValue({ id: 'o1', fullName: 'Ayşe Yılmaz', phone: '0532' } as never);
  vi.mocked(patientApi.register).mockResolvedValue({ id: 'p9' });
  vi.mocked(prepareImage).mockResolvedValue(prepared);
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview');
  globalThis.URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  container?.remove();
  vi.clearAllMocks();
});

describe('NewPatientPage — profil fotografi', () => {
  it('offers a camera input (capture=environment) and a gallery input without capture', async () => {
    await render();
    const camera = container.querySelector('input[data-testid="new-photo-camera"]') as HTMLInputElement;
    const gallery = container.querySelector('input[data-testid="new-photo-gallery"]') as HTMLInputElement;
    expect(camera.getAttribute('accept')).toBe('image/*');
    expect(camera.getAttribute('capture')).toBe('environment');
    expect(gallery.getAttribute('accept')).toBe('image/*');
    expect(gallery.hasAttribute('capture')).toBe(false);
  });

  it('prepares the chosen photo with the profile preset and shows a preview before saving', async () => {
    await render();
    await pick(container.querySelector('input[data-testid="new-photo-camera"]') as HTMLInputElement, raw);
    expect(prepareImage).toHaveBeenCalledWith(raw, 'profile');
    expect(container.querySelector('img')?.getAttribute('src')).toBe('blob:preview');
    expect(patientApi.uploadPhoto).not.toHaveBeenCalled();
  });

  it('shows the prepare error and keeps no photo when the image cannot be converted', async () => {
    vi.mocked(prepareImage).mockRejectedValue(new ImagePrepareError());
    await render();
    await pick(container.querySelector('input[data-testid="new-photo-gallery"]') as HTMLInputElement, raw);
    expect(container.textContent).toContain('Bu fotoğraf biçimi desteklenmiyor.');
    expect(container.querySelector('img')).toBeNull();
  });

  it('removes the chosen photo with "Kaldır"', async () => {
    await render();
    await pick(container.querySelector('input[data-testid="new-photo-camera"]') as HTMLInputElement, raw);
    const remove = Array.from(container.querySelectorAll('button')).find((b) => b.textContent === 'Kaldır')!;
    await act(async () => remove.click());
    expect(container.querySelector('img')).toBeNull();
  });

  it('registers the patient, then uploads the prepared photo, then opens the card', async () => {
    vi.mocked(patientApi.uploadPhoto).mockResolvedValue({ photoVersion: 'v1' });
    await render();
    await pick(container.querySelector('input[data-testid="new-photo-camera"]') as HTMLInputElement, raw);
    await fillNameAndSubmit();
    expect(patientApi.register).toHaveBeenCalledTimes(1);
    expect(patientApi.uploadPhoto).toHaveBeenCalledWith('p9', prepared);
    expect(vi.mocked(patientApi.register).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(patientApi.uploadPhoto).mock.invocationCallOrder[0]
    );
    expect(container.querySelector('[data-testid="card"]')?.textContent).toBe('ok');
  });

  it('still opens the new patient card, flagged, when the photo upload fails', async () => {
    vi.mocked(patientApi.uploadPhoto).mockRejectedValue(new Error('network'));
    await render();
    await pick(container.querySelector('input[data-testid="new-photo-camera"]') as HTMLInputElement, raw);
    await fillNameAndSubmit();
    expect(container.querySelector('[data-testid="card"]')?.textContent).toBe('photo-failed');
  });

  it('does not upload anything when no photo was chosen', async () => {
    await render();
    await fillNameAndSubmit();
    expect(patientApi.register).toHaveBeenCalledTimes(1);
    expect(patientApi.uploadPhoto).not.toHaveBeenCalled();
    expect(container.querySelector('[data-testid="card"]')?.textContent).toBe('ok');
  });
});
