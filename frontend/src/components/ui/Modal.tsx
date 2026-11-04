import { ReactNode, useEffect } from 'react';
import styles from './Modal.module.css';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  width?: number;
  /** true when the modal holds unsaved input -- backdrop click asks for confirmation instead of closing directly. */
  dirty?: boolean;
}

const CONFIRM_CLOSE_MESSAGE = 'Kaydedilmemiş değişiklikleriniz var. Çıkmak istediğinize emin misiniz?';

export function Modal({ open, onClose, children, width = 480, dirty = false }: ModalProps) {
  function handleBackdropClick() {
    if (dirty && !window.confirm(CONFIRM_CLOSE_MESSAGE)) return;
    onClose();
  }

  // REVIZE (2026-11-01, canli QA bulgusu): modal'in kapatma (X) butonu YOKTU ve
  // Escape tusu da calismiyordu -- tek cikis yolu kenardaki birkac pikselllik
  // backdrop seridine tiklamakti, bu da kesfedilemiyordu. Escape dinleyicisi
  // ve gorunur bir X butonu eklendi; ikisi de ayni dirty-onay kuralina uyar.
  useEffect(() => {
    if (!open) return;
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') handleBackdropClick();
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, dirty]);

  if (!open) return null;

  return (
    <div
      className={styles.overlay}
      onClick={(e) => {
        if (e.target === e.currentTarget) handleBackdropClick();
      }}
    >
      <div className={styles.card} style={{ width }}>
        <button type="button" className={styles.closeBtn} onClick={handleBackdropClick} aria-label="Kapat" title="Kapat">
          ×
        </button>
        <div className={styles.content}>{children}</div>
      </div>
    </div>
  );
}
