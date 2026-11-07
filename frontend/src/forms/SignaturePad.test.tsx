// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SignaturePad } from './SignaturePad';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  const ctx = {
    scale: vi.fn(), beginPath: vi.fn(), moveTo: vi.fn(), lineTo: vi.fn(), quadraticCurveTo: vi.fn(),
    stroke: vi.fn(), clearRect: vi.fn(), save: vi.fn(), restore: vi.fn(), setTransform: vi.fn(),
  };
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as never);
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((cb: BlobCallback) =>
    cb(new Blob(['png'], { type: 'image/png' })),
  );
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(400);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
});

async function render(onChange: (b: Blob | null) => void) {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () => root.render(<SignaturePad onChange={onChange} />));
}

function fire(el: Element, type: string, x: number, y: number) {
  act(() => {
    el.dispatchEvent(new MouseEvent(type, { bubbles: true, clientX: x, clientY: y }));
  });
}

describe('SignaturePad', () => {
  it('cizim yokken onChange cagrilmaz, placeholder gorunur', async () => {
    const onChange = vi.fn();
    await render(onChange);
    expect(onChange).not.toHaveBeenCalled();
    expect(container.textContent).toContain('Buraya imzalayın');
  });

  it('cizgi bitince PNG blob verir, Temizle sonrasi null verir', async () => {
    const onChange = vi.fn();
    await render(onChange);
    const canvas = container.querySelector('canvas')!;
    fire(canvas, 'pointerdown', 10, 10);
    fire(canvas, 'pointermove', 40, 30);
    fire(canvas, 'pointerup', 40, 30);
    expect(onChange).toHaveBeenCalledTimes(1);
    const blob = onChange.mock.calls[0][0] as Blob;
    expect(blob.type).toBe('image/png');
    expect(container.textContent).not.toContain('Buraya imzalayın');

    const clear = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Temizle')!;
    act(() => clear.click());
    expect(onChange).toHaveBeenLastCalledWith(null);
    expect(container.textContent).toContain('Buraya imzalayın');
  });

  it('genislik degisince cizim silinmez, yeniden cizilir ve null gonderilmez', async () => {
    const onChange = vi.fn();
    await render(onChange);
    const canvas = container.querySelector('canvas')!;
    fire(canvas, 'pointerdown', 10, 10);
    fire(canvas, 'pointermove', 40, 30);
    fire(canvas, 'pointerup', 40, 30);
    const ctx = canvas.getContext('2d') as unknown as { moveTo: ReturnType<typeof vi.fn> };
    const drawsBefore = ctx.moveTo.mock.calls.length;
    const blobsBefore = onChange.mock.calls.length;

    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(385); // kaydirma cubugu kadar daralma
    act(() => {
      window.dispatchEvent(new Event('resize'));
    });

    expect(ctx.moveTo.mock.calls.length).toBeGreaterThan(drawsBefore);
    expect(onChange.mock.calls.length).toBe(blobsBefore + 1);
    expect(onChange).not.toHaveBeenCalledWith(null);
    expect(container.textContent).not.toContain('Buraya imzalayın');
  });

  it('Temizle toBlob geri cagrisindan once olursa son deger null kalir', async () => {
    const pending: BlobCallback[] = [];
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((cb: BlobCallback) => {
      pending.push(cb);
    });
    const onChange = vi.fn();
    await render(onChange);
    const canvas = container.querySelector('canvas')!;
    fire(canvas, 'pointerdown', 10, 10);
    fire(canvas, 'pointerup', 10, 10);
    const clear = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Temizle')!;
    act(() => clear.click());
    act(() => pending.forEach((cb) => cb(new Blob(['png'], { type: 'image/png' }))));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenLastCalledWith(null);
  });
});
