import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
const LoginPage = lazy(() => import('./pages/LoginPage').then(m => ({ default: m.LoginPage })));
const AcceptInvitePage = lazy(() => import('./pages/auth/AcceptInvitePage').then(m => ({ default: m.AcceptInvitePage })));
const ForgotPasswordPage = lazy(() => import('./pages/auth/ForgotPasswordPage').then(m => ({ default: m.ForgotPasswordPage })));
const ResetPasswordPage = lazy(() => import('./pages/auth/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })));
const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage').then(m => ({ default: m.DashboardPage })));
const PatientsPage = lazy(() => import('./pages/patients/PatientsPage').then(m => ({ default: m.PatientsPage })));
const PatientDetailPage = lazy(() => import('./pages/patients/PatientDetailPage').then(m => ({ default: m.PatientDetailPage })));
const NewPatientPage = lazy(() => import('./pages/patients/NewPatientPage').then(m => ({ default: m.NewPatientPage })));
const OwnerDetailPage = lazy(() => import('./pages/owners/OwnerDetailPage').then(m => ({ default: m.OwnerDetailPage })));
const OwnersListPage = lazy(() => import('./pages/owners/OwnersListPage').then(m => ({ default: m.OwnersListPage })));
const NewOwnerPage = lazy(() => import('./pages/owners/NewOwnerPage').then(m => ({ default: m.NewOwnerPage })));
const AppointmentsPage = lazy(() => import('./pages/appointments/AppointmentsPage').then(m => ({ default: m.AppointmentsPage })));
const EncounterPage = lazy(() => import('./pages/encounter/EncounterPage').then(m => ({ default: m.EncounterPage })));
const EncountersPage = lazy(() => import('./pages/encounter/EncountersPage').then(m => ({ default: m.EncountersPage })));
const LaboratoryPage = lazy(() => import('./pages/laboratory/LaboratoryPage').then(m => ({ default: m.LaboratoryPage })));
const ImagingPage = lazy(() => import('./pages/imaging/ImagingPage').then(m => ({ default: m.ImagingPage })));
const VaccinationsPage = lazy(() => import('./pages/vaccinations/VaccinationsPage').then(m => ({ default: m.VaccinationsPage })));
const NewVaccinationPage = lazy(() => import('./pages/vaccinations/NewVaccinationPage').then(m => ({ default: m.NewVaccinationPage })));
const FinancePage = lazy(() => import('./pages/finance/FinancePage').then(m => ({ default: m.FinancePage })));
const KonaklamaPage = lazy(() => import('./pages/boarding/KonaklamaPage').then(m => ({ default: m.KonaklamaPage })));
const NewBoardingStayPage = lazy(() => import('./pages/boarding/NewBoardingStayPage').then(m => ({ default: m.NewBoardingStayPage })));
const InventoryPage = lazy(() => import('./pages/inventory/InventoryPage').then(m => ({ default: m.InventoryPage })));
const ReportsPage = lazy(() => import('./pages/reports/ReportsPage').then(m => ({ default: m.ReportsPage })));
const SettingsPage = lazy(() => import('./pages/settings/SettingsPage').then(m => ({ default: m.SettingsPage })));
const ClinicSitePage = lazy(() => import('./pages/public/ClinicSitePage').then(m => ({ default: m.ClinicSitePage })));
const FillFormPage = lazy(() => import('./pages/forms/FillFormPage').then(m => ({ default: m.FillFormPage })));
const PlatformAdminApp = lazy(() => import('./platformAdmin/PlatformAdminApp').then(m => ({ default: m.PlatformAdminApp })));

export function App() {
  return (
    <Suspense fallback={<p role="status">Yükleniyor…</p>}>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/davet/:token" element={<AcceptInvitePage />} />
      <Route path="/sifremi-unuttum" element={<ForgotPasswordPage />} />
      <Route path="/sifre-sifirla/:token" element={<ResetPasswordPage />} />
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
        path="/hastalar/yeni"
        element={
          <RequireAuth roles={['VET', 'RECEPTIONIST', 'ADMIN']}>
            <NewPatientPage />
          </RequireAuth>
        }
      />
      <Route
        path="/hastalar/:patientId"
        element={
          <RequireAuth>
            <PatientDetailPage />
          </RequireAuth>
        }
      />
      <Route
        path="/musteriler"
        element={
          <RequireAuth>
            <OwnersListPage />
          </RequireAuth>
        }
      />
      <Route
        path="/musteriler/yeni"
        element={
          <RequireAuth roles={['VET', 'RECEPTIONIST', 'ADMIN']}>
            <NewOwnerPage />
          </RequireAuth>
        }
      />
      <Route
        path="/musteriler/:ownerId"
        element={
          <RequireAuth>
            <OwnerDetailPage />
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
        path="/muayeneler"
        element={
          <RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']}>
            <EncountersPage />
          </RequireAuth>
        }
      />
      <Route
        path="/muayene/:encounterId"
        element={
          <RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']}>
            <EncounterPage />
          </RequireAuth>
        }
      />
      <Route
        path="/asi-takvimi"
        element={
          <RequireAuth>
            <VaccinationsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/asi-takvimi/yeni"
        element={
          <RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']}>
            <NewVaccinationPage />
          </RequireAuth>
        }
      />
      <Route
        path="/laboratuvar"
        element={
          <RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']} feature="LAB_INTEGRATION">
            <LaboratoryPage />
          </RequireAuth>
        }
      />
      <Route
        path="/goruntuleme"
        element={
          <RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']} feature="IMAGING">
            <ImagingPage />
          </RequireAuth>
        }
      />
      <Route
        path="/finans"
        element={
          <RequireAuth roles={['RECEPTIONIST', 'ADMIN']}>
            <FinancePage />
          </RequireAuth>
        }
      />
      <Route
        path="/konaklama"
        element={
          <RequireAuth feature="BOARDING">
            <KonaklamaPage />
          </RequireAuth>
        }
      />
      <Route
        path="/konaklama/yeni"
        element={
          <RequireAuth feature="BOARDING">
            <NewBoardingStayPage />
          </RequireAuth>
        }
      />
      <Route
        path="/stok"
        element={
          <RequireAuth feature="INVENTORY">
            <InventoryPage />
          </RequireAuth>
        }
      />
      <Route
        path="/raporlar"
        element={
          <RequireAuth roles={['RECEPTIONIST', 'ADMIN']}>
            <ReportsPage />
          </RequireAuth>
        }
      />
      <Route path="/sms-whatsapp" element={<Navigate to="/ayarlar/sms-whatsapp" replace />} />
      <Route
        path="/ayarlar/*"
        element={
          <RequireAuth>
            <SettingsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/formlar/doldur"
        element={
          <RequireAuth roles={['VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN']}>
            <FillFormPage />
          </RequireAuth>
        }
      />
      <Route path="/site/:branchId" element={<ClinicSitePage />} />
      <Route path="/platform-admin/*" element={<PlatformAdminApp />} />
      <Route path="/" element={<Navigate to="/panel" replace />} />
    </Routes>
    </Suspense>
  );
}
