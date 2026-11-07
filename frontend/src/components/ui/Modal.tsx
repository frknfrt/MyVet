import { ReactNode, useEffect, useId, useRef } from 'react';
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
  const card = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const close = useRef<() => void>(() => {});
  function handleBackdropClick() {
    if (dirty && !window.confirm(CONFIRM_CLOSE_MESSAGE)) return;
    onClose();
  }

  close.current = handleBackdropClick;
  useEffect(() => {
    const dialog = card.current;
    if (!open || !dialog) return;
    const previous = document.activeElement;
    const heading = dialog.querySelector('h1,h2,h3');
    if (heading) {
      if (!heading.id) heading.id = titleId;
      dialog.setAttribute('aria-labelledby', heading.id);
    }
    dialog.focus();
    function handleKeyDown(e: KeyboardEvent) {
      if (!dialog || !dialog.contains(document.activeElement)) return;
      if (document.activeElement?.closest('[aria-modal="true"]') !== dialog) return;
      if (e.key === 'Escape') { e.preventDefault(); close.current(); return; }
      if (e.key !== 'Tab') return;
      const items = Array.from(dialog.querySelectorAll<HTMLElement>(
        'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]'
      )).filter(el => el.getClientRects().length > 0);
      const first = items[0], last = items[items.length - 1];
      if (!first) { e.preventDefault(); dialog.focus(); }
      else if (e.shiftKey && (document.activeElement === first || document.activeElement === dialog)) {
        e.preventDefault(); last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault(); first.focus();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      if (previous instanceof HTMLElement && previous.isConnected) previous.focus();
    };
  }, [open, titleId]);

  if (!open) return null;

  return (
    <div
      className={styles.overlay}
      onClick={(e) => {
        if (e.target === e.currentTarget) handleBackdropClick();
      }}
    >
      <div ref={card} role="dialog" aria-modal="true" aria-label="İşlem penceresi" tabIndex={-1} className={styles.card} style={{ width }}>
        <button type="button" className={styles.closeBtn} onClick={handleBackdropClick} aria-label="Kapat" title="Kapat">
          ×
        </button>
        <div className={styles.content}>{children}</div>
      </div>
    </div>
  );
}
