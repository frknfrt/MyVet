import { ChangeEvent, useEffect, useRef, useState } from 'react';
import { prepareImage } from '../../lib/prepareImage';
import { Button } from '../../components/ui/Button';
import styles from './NewPatientPhotoPicker.module.css';

interface NewPatientPhotoPickerProps {
  value: File | null;
  onChange: (file: File | null) => void;
  disabled?: boolean;
}

/**
 * Yeni hasta formunda profil fotografi: secilir, kucultulur ve onizlenir ama YUKLENMEZ --
 * hasta henuz yok. Yukleme, hasta kaydedildikten sonra NewPatientPage'de yapilir.
 */
export function NewPatientPhotoPicker({ value, onChange, disabled }: NewPatientPhotoPickerProps) {
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [preparing, setPreparing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!value) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(value);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [value]);

  async function handleFile(e: ChangeEvent<HTMLInputElement>) {
    const input = e.target;
    const file = input.files?.[0];
    input.value = ''; // ayni dosya tekrar secilebilsin
    if (!file) return;
    setPreparing(true);
    setError(null);
    try {
      onChange(await prepareImage(file, 'profile'));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Fotoğraf hazırlanamadı');
    } finally {
      setPreparing(false);
    }
  }

  const busy = disabled || preparing;

  return (
    <div className={styles.picker}>
      <div className={styles.circle}>
        {previewUrl ? <img src={previewUrl} alt="Hasta fotoğrafı önizleme" /> : <span aria-hidden="true">📷</span>}
      </div>
      <div className={styles.side}>
        <div className={styles.label}>Profil fotoğrafı (opsiyonel)</div>
        <div className={styles.buttons}>
          <Button type="button" variant="secondary" disabled={busy} onClick={() => cameraRef.current?.click()}>
            📷 Fotoğraf Çek
          </Button>
          <Button type="button" variant="secondary" disabled={busy} onClick={() => galleryRef.current?.click()}>
            🖼 Galeriden Seç
          </Button>
          {value && (
            <Button type="button" variant="tertiary" disabled={busy} onClick={() => onChange(null)}>
              Kaldır
            </Button>
          )}
        </div>
        {preparing && <div className={styles.hint}>Hazırlanıyor...</div>}
        {error && <div className={styles.error}>{error}</div>}
      </div>
      <input
        ref={cameraRef}
        data-testid="new-photo-camera"
        type="file"
        accept="image/*"
        capture="environment"
        hidden
        onChange={handleFile}
      />
      <input ref={galleryRef} data-testid="new-photo-gallery" type="file" accept="image/*" hidden onChange={handleFile} />
    </div>
  );
}
