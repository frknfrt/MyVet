import { Navigate, Route, Routes } from 'react-router-dom';
import { AiUsagePage } from '../pages/platform-admin/AiUsagePage';
import { AnnouncementsPage } from '../pages/platform-admin/AnnouncementsPage';
import { AuditLogPage } from '../pages/platform-admin/AuditLogPage';
import { CouponsPage } from '../pages/platform-admin/CouponsPage';
import { LeadsPage } from '../pages/platform-admin/LeadsPage';
import { OverviewPage } from '../pages/platform-admin/OverviewPage';
import { PlanManagementPage } from '../pages/platform-admin/PlanManagementPage';
import { PlatformAdminLoginPage } from '../pages/platform-admin/PlatformAdminLoginPage';
import { PlatformBillingPage } from '../pages/platform-admin/PlatformBillingPage';
import { SystemHealthPage } from '../pages/platform-admin/SystemHealthPage';
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
                  <Route index element={<Navigate to="overview" replace />} />
                  <Route path="overview" element={<OverviewPage />} />
                  <Route path="leads" element={<LeadsPage />} />
                  <Route path="tenants" element={<TenantListPage />} />
                  <Route path="tenants/:tenantId" element={<TenantDetailPage />} />
                  <Route path="plans" element={<PlanManagementPage />} />
                  <Route path="coupons" element={<CouponsPage />} />
                  <Route path="billing" element={<PlatformBillingPage />} />
                  <Route path="ai-usage" element={<AiUsagePage />} />
                  <Route path="announcements" element={<AnnouncementsPage />} />
                  <Route path="system-health" element={<SystemHealthPage />} />
                  <Route path="audit-log" element={<AuditLogPage />} />
                </Routes>
              </PlatformAdminShell>
            </RequirePlatformAdminAuth>
          }
        />
      </Routes>
    </PlatformAdminAuthProvider>
  );
}
