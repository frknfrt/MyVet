import { Navigate, Route, Routes } from 'react-router-dom';
import { PlanManagementPage } from '../pages/platform-admin/PlanManagementPage';
import { PlatformAdminLoginPage } from '../pages/platform-admin/PlatformAdminLoginPage';
import { PlatformBillingPage } from '../pages/platform-admin/PlatformBillingPage';
import { TenantDetailPage } from '../pages/platform-admin/TenantDetailPage';
import { TenantListPage } from '../pages/platform-admin/TenantListPage';
import { PlatformAdminAuthProvider } from './PlatformAdminAuthContext';
import { PlatformAdminShell } from './PlatformAdminShell';
import { RequirePlatformAdminAuth } from './RequirePlatformAdminAuth';

export function PlatformAdminApp() {
  return (
    <PlatformAdminAuthProvider>
      <Routes>
        <Route path="login" element={<PlatformAdminLoginPage />} />
        <Route
          path="*"
          element={
            <RequirePlatformAdminAuth>
              <PlatformAdminShell>
                <Routes>
                  <Route index element={<Navigate to="tenants" replace />} />
                  <Route path="tenants" element={<TenantListPage />} />
                  <Route path="tenants/:tenantId" element={<TenantDetailPage />} />
                  <Route path="plans" element={<PlanManagementPage />} />
                  <Route path="billing" element={<PlatformBillingPage />} />
                </Routes>
              </PlatformAdminShell>
            </RequirePlatformAdminAuth>
          }
        />
      </Routes>
    </PlatformAdminAuthProvider>
  );
}
