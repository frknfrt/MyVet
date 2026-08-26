import { useEffect, useRef, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import vetlyIcon from '../../assets/vetly-icon.png';
import { useAuth } from '../../auth/AuthContext';
import { NAV_ITEMS } from './navConfig';
import { ChangePasswordModal } from './ChangePasswordModal';
import styles from './Sidebar.module.css';

interface SidebarProps {
  userName: string;
  userRole: string;
  userInitials: string;
  onLogout: () => void;
}

/**
 * Aktif menü öğesi artık elle senkronize edilmiyor — react-router-dom'un
 * NavLink bileşeni, mevcut URL'ye göre "active" class'ını otomatik uyguluyor.
 * Bu bileşen tüm sayfalarda BİREBİR AYNI import edilir, kopyalanmaz.
 */
export function Sidebar({ userName, userRole, userInitials, onLogout }: SidebarProps) {
  const { session } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const footRef = useRef<HTMLDivElement>(null);
  const visibleNavItems = NAV_ITEMS.filter((item) => !item.roles || (session && item.roles.includes(session.role)));

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
    <aside className={styles.sidebar}>
      <div className={styles.brandMark}>
        <img className={styles.glyph} src={vetlyIcon} alt="" />
        <span className={styles.word}>Vetly</span>
      </div>

      <nav>
        {visibleNavItems.map((item) => (
          <NavLink
            key={item.key}
            to={item.path}
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
            <Link to="/ayarlar" className={styles.footMenuItem} onClick={() => setMenuOpen(false)}>
              Hesap Ayarları
            </Link>
            <button
              type="button"
              className={styles.footMenuItem}
              onClick={() => {
                setMenuOpen(false);
                setPasswordModalOpen(true);
              }}
            >
              Şifre Değiştir
            </button>
            <div className={styles.footMenuDivider} />
            <button type="button" className={`${styles.footMenuItem} ${styles.footMenuItemDanger}`} onClick={onLogout}>
              Çıkış Yap
            </button>
          </div>
        )}
        <button type="button" className={styles.sidebarFoot} onClick={() => setMenuOpen((v) => !v)}>
          <div className={styles.avatar}>{userInitials}</div>
          <div>
            <div className={styles.who}>{userName}</div>
            <div className={styles.role}>{userRole}</div>
          </div>
        </button>
      </div>

      <ChangePasswordModal open={passwordModalOpen} onClose={() => setPasswordModalOpen(false)} />
    </aside>
  );
}
