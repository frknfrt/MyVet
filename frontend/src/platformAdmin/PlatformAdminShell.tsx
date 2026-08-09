import { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { usePlatformAdminAuth } from './PlatformAdminAuthContext';
import styles from './PlatformAdminShell.module.css';

const NAV_ITEMS = [
  { path: 'tenants', label: 'Kiracılar' },
  { path: 'plans', label: 'Planlar' },
  { path: 'billing', label: 'Faturalama' },
];

export function PlatformAdminShell({ children }: { children: ReactNode }) {
  const { session, logout } = usePlatformAdminAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/platform-admin/login');
  }

  return (
    <div className={styles.shell}>
      <div className={styles.topbar}>
        <div className={styles.brand}>
          MyVet <span className={styles.badge}>Platform Admin</span>
        </div>
        <nav className={styles.nav}>
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) => `${styles.navLink} ${isActive ? styles.navLinkActive : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className={styles.userArea}>
          <span className={styles.userName}>{session?.fullName}</span>
          <button type="button" className={styles.logoutBtn} onClick={handleLogout}>
            Çıkış Yap
          </button>
        </div>
      </div>
      <div className={styles.content}>{children}</div>
    </div>
  );
}
