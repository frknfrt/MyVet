// Telefon fotograflarini yuklemeden once JPEG'e cevirir ve kucultur.
export type ImagePreset = 'profile' | 'document';

const PRESETS: Record<ImagePreset, { maxEdge: number; quality: number }> = {
  profile: { maxEdge: 800, quality: 0.85 },
  document: { maxEdge: 2000, quality: 0.9 },
};

export class ImagePrepareError extends Error {
  constructor() {
    super(
      'Bu fotoğraf biçimi desteklenmiyor. Telefonun kamera ayarlarından "En Uyumlu" biçimi seçin veya ekran görüntüsü alıp yükleyin.',
    );
    this.name = 'ImagePrepareError';
  }
}

/** Oranı korur, asla büyütmez, tam sayıya yuvarlar. */
export function fitWithin(width: number, height: number, maxEdge: number): { width: number; height: number } {
  const longest = Math.max(width, height);
  if (longest <= maxEdge) return { width, height };
  const scale = maxEdge / longest;
  return { width: Math.round(width * scale), height: Math.round(height * scale) };
}

export async function prepareImage(file: File, preset: ImagePreset): Promise<File> {
  if (!file.type.startsWith('image/') || file.type === 'image/gif') return file;
  const { maxEdge, quality } = PRESETS[preset];

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    throw new ImagePrepareError();
  }

  try {
    const { width, height } = fitWithin(bitmap.width, bitmap.height, maxEdge);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new ImagePrepareError();
    // Seffaf PNG JPEG'e cevrilirken siyah olmasin diye beyaz zemin (gorsel piksel verisi, UI stili degil).
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(bitmap, 0, 0, width, height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
    if (!blob) throw new ImagePrepareError();
    const base = file.name.replace(/\.[^.]+$/, '') || 'foto';
    return new File([blob], `${base}.jpg`, { type: 'image/jpeg', lastModified: Date.now() });
  } finally {
    bitmap.close();
  }
}
