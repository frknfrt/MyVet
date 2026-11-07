import { useLocation } from 'react-router-dom';
import styles from './PhotoUploadFailedNotice.module.css';

/** Yeni hasta kaydedilip profil fotografi yuklenemediyse (NewPatientPage navigate state'i) kartta uyari. */
export function PhotoUploadFailedNotice() {
  const state = useLocation().state as { photoUploadFailed?: boolean } | null;
  if (!state?.photoUploadFailed) return null;
  return (
    <div className={styles.notice} role="status">
      Hasta kaydedildi ama fotoğraf yüklenemedi. Fotoğrafın üzerindeki 📷 ile tekrar deneyebilirsiniz.
    </div>
  );
}
