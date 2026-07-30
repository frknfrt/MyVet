import { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { Sidebar } from './Sidebar';
import styles from './AppShell.module.css';

const ROLE_LABELS: Record<string, string> = {
  VET: 'Veteriner Hekim',
  TECHNICIAN: 'Teknisyen',
  RECEPTIONIST: 'Resepsiyonist',
  ADMIN: 'Klinik Yöneticisi',
  OWNER_ACCOUNT: 'Sahip Hesabı',
};

function initialsOf(fullName: string) {
  return fullName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

export function AppShell({ children }: { children: ReactNode }) {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className={styles.app}>
      <Sidebar
        userName={session?.fullName ?? ''}
        userRole={session ? ROLE_LABELS[session.role] ?? session.role : ''}
        userInitials={session ? initialsOf(session.fullName) : ''}
        onLogout={handleLogout}
      />
      <main className={styles.main}>{children}</main>
    </div>
  );
}
