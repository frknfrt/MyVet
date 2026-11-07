// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/patientApi', () => ({
  patientApi: {
    photoUrl: (id: string, v: string) => `http://x.test/${id}/photo?v=${v}`,
    fetchFile: vi.fn(),
  },
}));

import { patientApi } from '../../api/patientApi';
import { PatientAvatar } from './PatientAvatar';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const fetchFile = vi.mocked(patientApi.fetchFile);
const okResponse = () => ({ ok: true, blob: async () => new Blob(['x']) }) as unknown as Response;

let container: HTMLDivElement;
let root: Root;

async function render(photoVersion: string | null) {
  await act(async () => {
    root.render(<PatientAvatar patientId="p1" name="Pamuk Ali" photoVersion={photoVersion} size={40} />);
  });
}

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  fetchFile.mockReset();
  URL.createObjectURL = vi.fn((b: Blob) => `blob:${b.size}-${Math.random()}`);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe('PatientAvatar', () => {
  it('foto yoksa bas harfleri gosterir, fetch yapmaz', async () => {
    await render(null);
    expect(container.querySelector('img')).toBeNull();
    expect(container.textContent).toBe('PA');
    expect(fetchFile).not.toHaveBeenCalled();
  });

  it('foto varsa fetchFile ile cekip img gosterir', async () => {
    fetchFile.mockResolvedValue(okResponse());
    await render('v1');
    expect(fetchFile).toHaveBeenCalledWith('http://x.test/p1/photo?v=v1');
    expect(container.querySelector('img')?.getAttribute('alt')).toBe('Pamuk Ali');
  });

  it('surum degisince yeni adresle yeniden ceker', async () => {
    fetchFile.mockResolvedValue(okResponse());
    await render('a1');
    await render('a2');
    expect(fetchFile).toHaveBeenLastCalledWith('http://x.test/p1/photo?v=a2');
    expect(fetchFile).toHaveBeenCalledTimes(2);
  });

  it('fetch hata verirse bas harflere duser', async () => {
    fetchFile.mockRejectedValue(new Error('ag'));
    await render('v-err');
    expect(container.querySelector('img')).toBeNull();
    expect(container.textContent).toBe('PA');
  });
});
