import { useEffect, useState } from 'react';
import { patientApi } from '../../api/patientApi';
import { colorFor, initialsOf } from '../../pages/dashboard/avatarColor';
import styles from './PatientAvatar.module.css';

interface PatientAvatarProps {
  patientId: string;
  name: string;
  photoVersion: string | null;
  size: number;
}

// url -> blob URL; sayfalar/render'lar arasi yeniden kullanilir, bilerek revoke edilmez.
const objectUrlCache = new Map<string, string>();

/** Hasta fotografi (oturum cerezli fetch -> blob URL); yoksa veya yuklenemezse renkli bas harf dairesi. */
export function PatientAvatar({ patientId, name, photoVersion, size }: PatientAvatarProps) {
  const url = photoVersion ? patientApi.photoUrl(patientId, photoVersion) : null;
  const [loaded, setLoaded] = useState<{ url: string; src: string } | null>(null);

  useEffect(() => {
    if (!url) return;
    const cached = objectUrlCache.get(url);
    if (cached) {
      setLoaded({ url, src: cached });
      return;
    }
    let cancelled = false;
    patientApi
      .fetchFile(url)
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const objectUrl = URL.createObjectURL(await res.blob());
        objectUrlCache.set(url, objectUrl);
        if (!cancelled) setLoaded({ url, src: objectUrl });
      })
      .catch(() => {
        // baslangic harfine dus
      });
    return () => {
      cancelled = true;
    };
  }, [url]);

  // Eski surume ait gelen sonuc gosterilmez.
  const src = url && loaded?.url === url ? loaded.src : null;

  return (
    <span
      className={styles.avatar}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.4), background: colorFor(patientId) }}
    >
      {src ? <img className={styles.img} src={src} alt={name} /> : initialsOf(name)}
    </span>
  );
}
