import { ReactNode } from 'react';
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

  function handleLogout() {
    logout();
    navigate('/platform-admin/login');
  }

  return (
    <div className={styles.app}>
      <PlatformAdminSidebar
        userName={session?.fullName ?? ''}
        userInitials={session ? initialsOf(session.fullName) : ''}
        onLogout={handleLogout}
      />
      <main className={styles.main}>{children}</main>
    </div>
  );
}
