import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { FeatureNotInPlanPage } from '../components/FeatureNotInPlanPage';
import { useAuth } from './AuthContext';
import { FeatureCode, useFeatures } from './FeatureContext';
import { StaffRole } from './session';

interface RequireAuthProps {
  children: ReactNode;
  /** Belirtilmezse tüm oturum açmış roller erişebilir. */
  roles?: StaffRole[];
  /** Belirtilirse kliniğin paketinde bu modül açık olmalı; değilse "paketinizde yok" sayfası. */
  feature?: FeatureCode;
}

export function RequireAuth({ children, roles, feature }: RequireAuthProps) {
  const { session } = useAuth();
  const { hasFeature } = useFeatures();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  if (roles && !roles.includes(session.role)) {
    return <Navigate to="/panel" replace />;
  }
  if (feature && !hasFeature(feature)) {
    return <FeatureNotInPlanPage />;
  }
  return <>{children}</>;
}
