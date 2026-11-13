import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { FeatureNotInPlanPage } from '../components/FeatureNotInPlanPage';
import { useAuth } from './AuthContext';
import { FeatureCode, useFeatures } from './FeatureContext';
import { StaffRole } from './session';
import { useSubscriptionAccess } from './SubscriptionAccessContext';

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
  const { access } = useSubscriptionAccess();
  const location = useLocation();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  // Deneme süresi dolduysa panelin tamamı üyelik sayfasına düşer (sunucu da 402 döner).
  // Arama parametreleri korunur: ödeme dönüşündeki ?odeme=hata bilgisi kaybolmasın.
  if (access?.state === 'TRIAL_EXPIRED') {
    return <Navigate to={{ pathname: '/uyelik', search: location.search }} replace />;
  }
  if (roles && !roles.includes(session.role)) {
    return <Navigate to="/panel" replace />;
  }
  if (feature && !hasFeature(feature)) {
    return <FeatureNotInPlanPage />;
  }
  return <>{children}</>;
}
