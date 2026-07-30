import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { LoginPage } from './pages/LoginPage';
import { RegisterClinicPage } from './pages/auth/RegisterClinicPage';
import { SetupWizardPage } from './pages/auth/SetupWizardPage';
import { DashboardPage } from './pages/dashboard/DashboardPage';
import { PatientsPage } from './pages/patients/PatientsPage';
import { AppointmentsPage } from './pages/appointments/AppointmentsPage';
import { EncounterPage } from './pages/encounter/EncounterPage';
import { FinancePage } from './pages/finance/FinancePage';
import { InventoryPage } from './pages/inventory/InventoryPage';
import { SettingsPage } from './pages/settings/SettingsPage';
import { ClinicSitePage } from './pages/public/ClinicSitePage';

/**
 * NOT: Bu bir başlangıç route seti. Kalan sayfalar (Randevu, Hastalar & Sahipler,
 * Laboratuvar, Görüntüleme, İletişim, Stok, Finans, Ayarlar) aynı desenle
 * (AppShell + kendi klasörü altında bileşenler) sırayla eklenecek.
 * Sidebar zaten bunları /src/components/layout/navConfig.tsx üzerinden biliyor.
 */
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/kayit" element={<RegisterClinicPage />} />
      <Route
        path="/kurulum"
        element={
          <RequireAuth>
            <SetupWizardPage />
          </RequireAuth>
        }
      />
      <Route
        path="/panel"
        element={
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        }
      />
      <Route
        path="/hastalar"
        element={
          <RequireAuth>
            <PatientsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/randevu"
        element={
          <RequireAuth>
            <AppointmentsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/muayene/:encounterId"
        element={
          <RequireAuth>
            <EncounterPage />
          </RequireAuth>
        }
      />
      <Route
        path="/finans"
        element={
          <RequireAuth>
            <FinancePage />
          </RequireAuth>
        }
      />
      <Route
        path="/stok"
        element={
          <RequireAuth>
            <InventoryPage />
          </RequireAuth>
        }
      />
      <Route
        path="/ayarlar"
        element={
          <RequireAuth>
            <SettingsPage />
          </RequireAuth>
        }
      />
      <Route path="/site/:branchId" element={<ClinicSitePage />} />
      <Route path="/" element={<Navigate to="/panel" replace />} />
    </Routes>
  );
}
