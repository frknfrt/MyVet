import { ChangeEvent, useEffect, useRef, useState } from "react";
import { ApiError } from "../../api/client";
import { patientApi } from "../../api/patientApi";
import { prepareImage } from "../../lib/prepareImage";
import styles from "./PatientPhotoMenu.module.css";

interface PatientPhotoMenuProps {
  patientId: string;
  hasPhoto: boolean;
  onChanged: (photoVersion: string | null) => void;
  onClose: () => void;
}

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError
    ? err.message
    : err instanceof Error
      ? err.message
      : "Beklenmeyen bir hata oluştu";
}

/** Hasta fotografi menusu: kamera / galeri / kaldir. Mobilde alttan liste, masaustunde acilir menu (CSS). */
export function PatientPhotoMenu({
  patientId,
  hasPhoto,
  onChanged,
  onClose,
}: PatientPhotoMenuProps) {
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Escape ile kapanir; istek surerken kapanmaz (hata mesaji kaybolmasin).
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !busy) onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [busy, onClose]);

  async function handleFile(e: ChangeEvent<HTMLInputElement>) {
    const input = e.target;
    const file = input.files?.[0];
    input.value = ""; // ayni dosya tekrar secilebilsin
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const prepared = await prepareImage(file, "profile");
      const res = await patientApi.uploadPhoto(patientId, prepared);
      onChanged(res.photoVersion);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleRemove() {
    setBusy(true);
    setError(null);
    try {
      await patientApi.removePhoto(patientId);
      onChanged(null);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div
        className={styles.backdrop}
        data-testid="photo-menu-backdrop"
        onClick={() => !busy && onClose()}
      />
      <div className={styles.menu} role="menu">
        <input
          ref={cameraRef}
          type="file"
          accept="image/*"
          capture="environment"
          hidden
          onChange={handleFile}
        />
        <input
          ref={galleryRef}
          type="file"
          accept="image/*"
          hidden
          onChange={handleFile}
        />
        {busy && <div className={styles.busy}>Yükleniyor...</div>}
        {error && <div className={styles.error}>{error}</div>}
        <button
          type="button"
          className={styles.option}
          disabled={busy}
          onClick={() => cameraRef.current?.click()}
        >
          📷 Fotoğraf Çek
        </button>
        <button
          type="button"
          className={styles.option}
          disabled={busy}
          onClick={() => galleryRef.current?.click()}
        >
          🖼 Galeriden Seç
        </button>
        {hasPhoto && (
          <button
            type="button"
            className={`${styles.option} ${styles.danger}`}
            disabled={busy}
            onClick={handleRemove}
          >
            Fotoğrafı Kaldır
          </button>
        )}
      </div>
    </>
  );
}
