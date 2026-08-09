import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { usePlatformAdminAuth } from './PlatformAdminAuthContext';

export function RequirePlatformAdminAuth({ children }: { children: ReactNode }) {
  const { session } = usePlatformAdminAuth();
  if (!session) {
    return <Navigate to="/platform-admin/login" replace />;
  }
  return <>{children}</>;
}
