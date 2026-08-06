import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './QuickAddMenu.module.css';

interface QuickAddOption {
  label: string;
  icon: JSX.Element;
  onSelect: () => void;
}

const PLUS_ICON = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round">
    <path d="M12 5v14M5 12h14" />
  </svg>
);
const CHEVRON_ICON = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round">
    <path d="M6 9l6 6 6-6" />
  </svg>
);

const ICON_APPT = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="5" width="18" height="16" rx="2" />
    <path d="M8 3v4M16 3v4M3 10h18" />
  </svg>
);
const ICON_PATIENT = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <circle cx="9" cy="8" r="3.5" />
    <path d="M2.5 20v-1a6.5 6.5 0 0 1 13 0v1" />
  </svg>
);
/**
 * Tasarım sistemi kuralı korunuyor: sayfada tek bir primary buton.
 * Kolayvet'teki 5-6 ayrı "Hızlı X" butonu yerine, tek buton + açılır menü.
 */
export function QuickAddMenu() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  // NOT: "Yeni satış" (retail POS) ve "Tahsilat al" (bağımsız tahsilat) Faz 2
  // kapsamında (@docs/requirements.md 4.14) -- henüz backend'i yok, bu yüzden
  // menüden çıkarıldı. Eklendiklerinde buraya geri eklenecekler.
  const options: QuickAddOption[] = [
    { label: 'Yeni randevu', icon: ICON_APPT, onSelect: () => navigate('/randevu') },
    { label: 'Yeni hasta', icon: ICON_PATIENT, onSelect: () => navigate('/hastalar') },
  ];

  return (
    <div className={styles.wrap} ref={ref}>
      <button className={styles.trigger} onClick={() => setOpen((v) => !v)}>
        {PLUS_ICON}
        Hızlı ekle
        <span className={`${styles.chevron} ${open ? styles.chevronOpen : ''}`}>{CHEVRON_ICON}</span>
      </button>
      {open && (
        <div className={styles.menu}>
          {options.map((opt) => (
            <div
              key={opt.label}
              className={styles.menuItem}
              onClick={() => {
                opt.onSelect();
                setOpen(false);
              }}
            >
              {opt.icon}
              {opt.label}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
