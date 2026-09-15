import { ReactNode } from 'react';
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
  if (!open) return null;

  function handleBackdropClick() {
    if (dirty && !window.confirm(CONFIRM_CLOSE_MESSAGE)) return;
    onClose();
  }

  return (
    <div
      className={styles.overlay}
      onClick={(e) => {
        if (e.target === e.currentTarget) handleBackdropClick();
      }}
    >
      <div className={styles.card} style={{ width }}>
        {children}
      </div>
    </div>
  );
}
