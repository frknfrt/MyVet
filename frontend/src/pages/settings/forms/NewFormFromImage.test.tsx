// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../api/formsApi', () => ({
  formsApi: {
    uploadSourceImage: vi.fn().mockResolvedValue('img1'),
    extractDraft: vi.fn(),
    createTemplate: vi.fn(),
    fetchFile: vi.fn().mockRejectedValue(new Error('no image')),
    sourceImageUrl: (id: string) => `/img/${id}`,
    templatePdfUrl: (id: string) => `/pdf/${id}`,
  },
}));
vi.mock('../../../lib/prepareImage', () => ({ prepareImage: vi.fn(async (f: File) => f) }));

import { formsApi } from '../../../api/formsApi';
import { NewFormFromImage } from './NewFormFromImage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(formsApi.uploadSourceImage).mockResolvedValue('img1');
  vi.mocked(formsApi.fetchFile).mockRejectedValue(new Error('no image'));
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

async function pickFile() {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () => root.render(<NewFormFromImage onSaved={() => {}} onCancel={() => {}} />));
  const input = container.querySelector<HTMLInputElement>('input[type=file]:not([capture])')!;
  Object.defineProperty(input, 'files', { value: [new File(['x'], 'form.jpg', { type: 'image/jpeg' })] });
  await act(async () => {
    input.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await act(async () => {});
}

describe('NewFormFromImage', () => {
  it('model bagli degilse bos sablon + uyari acilir, mavi AI cercevesi yok', async () => {
    vi.mocked(formsApi.extractDraft).mockResolvedValue({ aiJobId: 'j', modelConnected: false, content: null, warnings: ['Ek uyarı'] });
    await pickFile();
    const text = container.textContent ?? '';
    expect(formsApi.extractDraft).toHaveBeenCalledWith('img1');
    expect(text).toContain('Yapay zekâ modeli bağlı değil');
    expect(text).toContain('Ek uyarı');
    expect(text).not.toContain('AI taslağı');
    const codes = [...container.querySelectorAll('[data-code]')].map((e) => e.textContent);
    expect(codes).toEqual(['imza']);
  });

  it('gercek AI icerigi gelince mavi cerceve gorunur', async () => {
    vi.mocked(formsApi.extractDraft).mockResolvedValue({
      aiJobId: 'j',
      modelConnected: true,
      warnings: [],
      content: {
        title: 'Onam',
        sections: [{ title: null, items: [{ type: 'SIGNATURE', code: 'imza', label: 'İmza', text: null, required: false, binding: null, unit: null, options: null, multiple: false }] }],
      },
    });
    await pickFile();
    expect(container.textContent).toContain('AI taslağı — onayınızı bekliyor');
  });
});
