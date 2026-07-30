import { NavLink } from 'react-router-dom';
import { NAV_ITEMS } from './navConfig';
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
  return (
    <aside className={styles.sidebar}>
      <div className={styles.brandMark}>
        <div className={styles.glyph}>
          <svg viewBox="0 0 24 24" fill="none" stroke="#180F24" strokeWidth={2.2} strokeLinecap="round">
            <path d="M12 3v6M12 15v6M4.2 7.5l5.2 3M14.6 13.5l5.2 3M4.2 16.5l5.2-3M14.6 10.5l5.2-3" />
          </svg>
        </div>
        <span className={styles.word}>MyVet</span>
      </div>

      <nav>
        {NAV_ITEMS.map((item) => (
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

      <button type="button" className={styles.sidebarFoot} onClick={onLogout} title="Çıkış yap">
        <div className={styles.avatar}>{userInitials}</div>
        <div>
          <div className={styles.who}>{userName}</div>
          <div className={styles.role}>{userRole}</div>
        </div>
      </button>
    </aside>
  );
}
