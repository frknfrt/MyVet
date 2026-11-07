// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fitWithin, ImagePrepareError, prepareImage } from './prepareImage';

afterEach(() => {
  vi.restoreAllMocks();
  delete (globalThis as { createImageBitmap?: unknown }).createImageBitmap;
});

describe('fitWithin', () => {
  it('buyuk kenari sinira indirir, oranı korur', () => {
    expect(fitWithin(4000, 3000, 2000)).toEqual({ width: 2000, height: 1500 });
    expect(fitWithin(3000, 4000, 800)).toEqual({ width: 600, height: 800 });
  });

  it('asla buyutmez', () => {
    expect(fitWithin(500, 300, 800)).toEqual({ width: 500, height: 300 });
  });
});

describe('prepareImage', () => {
  it('goruntu olmayan dosyayi aynen dondurur', async () => {
    const pdf = new File(['x'], 'rapor.pdf', { type: 'application/pdf' });
    expect(await prepareImage(pdf, 'document')).toBe(pdf);
  });

  it('gif dosyasini aynen dondurur', async () => {
    const gif = new File(['x'], 'a.gif', { type: 'image/gif' });
    expect(await prepareImage(gif, 'profile')).toBe(gif);
  });

  it('cozulemeyen goruntude ImagePrepareError firlatir', async () => {
    globalThis.createImageBitmap = vi.fn().mockRejectedValue(new Error('decode'));
    const heic = new File(['x'], 'IMG_1.HEIC', { type: 'image/heic' });
    await expect(prepareImage(heic, 'profile')).rejects.toBeInstanceOf(ImagePrepareError);
  });

  it('goruntuyu kucultup JPEG olarak kodlar', async () => {
    const close = vi.fn();
    globalThis.createImageBitmap = vi.fn().mockResolvedValue({ width: 4000, height: 3000, close });
    const drawImage = vi.fn();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage, fillRect: vi.fn() } as never);
    let canvas: HTMLCanvasElement | undefined;
    const toBlob = vi
      .spyOn(HTMLCanvasElement.prototype, 'toBlob')
      .mockImplementation(function (this: HTMLCanvasElement, cb: BlobCallback) {
        canvas = this;
        cb(new Blob(['jpg'], { type: 'image/jpeg' }));
      });

    const out = await prepareImage(new File(['x'], 'IMG_1.HEIC', { type: 'image/heic' }), 'document');

    expect(out.type).toBe('image/jpeg');
    expect(out.name).toBe('IMG_1.jpg');
    expect(canvas?.width).toBe(2000);
    expect(canvas?.height).toBe(1500);
    expect(toBlob.mock.calls[0][2]).toBe(0.9);
    expect(drawImage).toHaveBeenCalled();
    expect(close).toHaveBeenCalled();
  });

  it('seffaf PNG siyah olmasin diye cizimden once beyaz zemin doldurur', async () => {
    globalThis.createImageBitmap = vi.fn().mockResolvedValue({ width: 4000, height: 3000, close: vi.fn() });
    const calls: string[] = [];
    const ctx = {
      fillStyle: '',
      fillRect: vi.fn(() => calls.push('fillRect')),
      drawImage: vi.fn(() => calls.push('drawImage')),
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as never);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((cb: BlobCallback) => cb(new Blob(['jpg'])));

    await prepareImage(new File(['x'], 'logo.png', { type: 'image/png' }), 'document');

    expect(ctx.fillStyle).toBe('#fff');
    expect(ctx.fillRect).toHaveBeenCalledWith(0, 0, 2000, 1500);
    expect(calls).toEqual(['fillRect', 'drawImage']);
  });
});
