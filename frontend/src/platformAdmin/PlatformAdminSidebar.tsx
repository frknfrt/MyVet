import { ReactNode, useEffect, useRef, useState } from 'react';
import { NavLink } from 'react-router-dom';
import vetlyIcon from '../assets/vetly-icon.png';
import styles from './PlatformAdminSidebar.module.css';

interface PlatformAdminSidebarProps {
  userName: string;
  userInitials: string;
  onLogout: () => void;
  isOpen: boolean;
  onClose: () => void;
}

interface NavItem {
  path: string;
  label: string;
  icon: ReactNode;
}

const NAV_ITEMS: NavItem[] = [
  {
    path: 'overview',
    label: 'Genel Bakış',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="9" rx="1" />
        <rect x="14" y="3" width="7" height="5" rx="1" />
        <rect x="14" y="12" width="7" height="9" rx="1" />
        <rect x="3" y="16" width="7" height="5" rx="1" />
      </svg>
    ),
  },
  {
    path: 'leads',
    label: 'Potansiyel Müşteriler',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21c0-4 3.5-7 8-7s8 3 8 7" />
        <path d="M19 8h3M20.5 6.5v3" />
      </svg>
    ),
  },
  {
    path: 'tenants',
    label: 'Kiracılar',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 21V5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v16" />
        <path d="M14 10h5a1 1 0 0 1 1 1v10" />
        <path d="M9 8h.01M9 12h.01M9 16h.01" />
      </svg>
    ),
  },
  {
    path: 'plans',
    label: 'Planlar',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M9 2h6l1 4H8l1-4z" />
        <path d="M6 6h12l1 15a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1L6 6z" />
        <path d="M9 12h6M9 16h6" />
      </svg>
    ),
  },
  {
    path: 'coupons',
    label: 'Kuponlar',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v1a2 2 0 0 0 0 4v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1a2 2 0 0 0 0-4V9z" />
        <path d="M9 7v10" strokeDasharray="2 2" />
      </svg>
    ),
  },
  {
    path: 'billing',
    label: 'Faturalama',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <rect x="2" y="6" width="20" height="12" rx="2" />
        <circle cx="12" cy="12" r="2.5" />
      </svg>
    ),
  },
  {
    path: 'system-health',
    label: 'Sistem Sağlığı',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
      </svg>
    ),
  },
  {
    path: 'audit-log',
    label: 'Denetim Kaydı',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z" />
        <path d="M9 8h6M9 12h6M9 16h3" />
      </svg>
    ),
  },
];

/**
 * AppShell/Sidebar (klinik paneli) ile AYNI görsel dil VE etkileşim --
 * platform admin kendi izole auth/UI yığınına sahip olduğu için (bkz.
 * architecture.md SS6.1) bileşen BİREBİR paylaşılmıyor, ama alt kısımdaki
 * "tıkla-aç" profil menüsü davranışı Sidebar.tsx ile birebir eşleniyor --
 * sadece "Hesap Ayarları"/"Şifre Değiştir" yok (platform admin'de karşılığı
 * yok), menüde tek öğe olarak "Çıkış Yap" kalıyor.
 */
export function PlatformAdminSidebar({ userName, userInitials, onLogout, isOpen, onClose }: PlatformAdminSidebarProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const footRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    function handleClickOutside(e: MouseEvent) {
      if (footRef.current && !footRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [menuOpen]);

  return (
    <>
      <div
        className={[styles.backdrop, isOpen && styles.backdropOpen].filter(Boolean).join(' ')}
        onClick={onClose}
        aria-hidden="true"
      />
      <aside className={[styles.sidebar, isOpen && styles.open].filter(Boolean).join(' ')}>
      <div className={styles.brandMark}>
        <img className={styles.glyph} src={vetlyIcon} alt="" />
        <span className={styles.word}>Vetly</span>
        <span className={styles.badge}>Platform Admin</span>
      </div>

      <nav>
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            onClick={onClose}
            className={({ isActive }) => [styles.navItem, isActive && styles.active].filter(Boolean).join(' ')}
          >
            {item.icon}
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className={styles.sidebarFootWrap} ref={footRef}>
        {menuOpen && (
          <div className={styles.footMenu}>
            <button
              type="button"
              className={`${styles.footMenuItem} ${styles.footMenuItemDanger}`}
              onClick={onLogout}
            >
              Çıkış Yap
            </button>
          </div>
        )}
        <button type="button" className={styles.sidebarFoot} onClick={() => setMenuOpen((v) => !v)}>
          <div className={styles.avatar}>{userInitials}</div>
          <div>
            <div className={styles.who}>{userName}</div>
            <div className={styles.role}>Platform Yöneticisi</div>
          </div>
        </button>
      </div>
      </aside>
    </>
  );
}
