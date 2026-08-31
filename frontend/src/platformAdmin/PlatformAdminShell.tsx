import { ReactNode, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePlatformAdminAuth } from './PlatformAdminAuthContext';
import { PlatformAdminSidebar } from './PlatformAdminSidebar';
import styles from './PlatformAdminShell.module.css';

function initialsOf(fullName: string) {
  return fullName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

export function PlatformAdminShell({ children }: { children: ReactNode }) {
  const { session, logout } = usePlatformAdminAuth();
  const navigate = useNavigate();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  function handleLogout() {
    logout();
    navigate('/platform-admin/login');
  }

  return (
    <div className={styles.app}>
      <div className={styles.mobileTopbar}>
        <button
          type="button"
          className={styles.hamburgerBtn}
          aria-label="Menüyü aç"
          onClick={() => setMobileNavOpen(true)}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>
        <span className={styles.mobileTopbarWord}>Vetly</span>
      </div>
      <PlatformAdminSidebar
        userName={session?.fullName ?? ''}
        userInitials={session ? initialsOf(session.fullName) : ''}
        onLogout={handleLogout}
        isOpen={mobileNavOpen}
        onClose={() => setMobileNavOpen(false)}
      />
      <main className={styles.main}>{children}</main>
    </div>
  );
}
