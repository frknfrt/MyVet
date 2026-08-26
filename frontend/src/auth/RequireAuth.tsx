import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { StaffRole } from './session';

interface RequireAuthProps {
  children: ReactNode;
  /** Belirtilmezse tüm oturum açmış roller erişebilir. */
  roles?: StaffRole[];
}

export function RequireAuth({ children, roles }: RequireAuthProps) {
  const { session } = useAuth();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  if (roles && !roles.includes(session.role)) {
    return <Navigate to="/panel" replace />;
  }
  return <>{children}</>;
}
