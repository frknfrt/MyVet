import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { LoginPage } from './pages/LoginPage';
import { AcceptInvitePage } from './pages/auth/AcceptInvitePage';
import { DashboardPage } from './pages/dashboard/DashboardPage';
import { PatientsPage } from './pages/patients/PatientsPage';
import { PatientDetailPage } from './pages/patients/PatientDetailPage';
import { NewPatientPage } from './pages/patients/NewPatientPage';
import { OwnerDetailPage } from './pages/owners/OwnerDetailPage';
import { OwnersListPage } from './pages/owners/OwnersListPage';
import { NewOwnerPage } from './pages/owners/NewOwnerPage';
import { AppointmentsPage } from './pages/appointments/AppointmentsPage';
import { EncounterPage } from './pages/encounter/EncounterPage';
import { LaboratoryPage } from './pages/laboratory/LaboratoryPage';
import { ImagingPage } from './pages/imaging/ImagingPage';
import { VaccinationsPage } from './pages/vaccinations/VaccinationsPage';
import { NewVaccinationPage } from './pages/vaccinations/NewVaccinationPage';
import { FinancePage } from './pages/finance/FinancePage';
import { KonaklamaPage } from './pages/boarding/KonaklamaPage';
import { NewBoardingStayPage } from './pages/boarding/NewBoardingStayPage';
import { InventoryPage } from './pages/inventory/InventoryPage';
import { ReportsPage } from './pages/reports/ReportsPage';
import { SettingsPage } from './pages/settings/SettingsPage';
import { ClinicSitePage } from './pages/public/ClinicSitePage';
import { PlatformAdminApp } from './platformAdmin/PlatformAdminApp';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/davet/:token" element={<AcceptInvitePage />} />
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
          <RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']}>
            <LaboratoryPage />
          </RequireAuth>
        }
      />
      <Route
        path="/goruntuleme"
        element={
          <RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']}>
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
          <RequireAuth>
            <KonaklamaPage />
          </RequireAuth>
        }
      />
      <Route
        path="/konaklama/yeni"
        element={
          <RequireAuth>
            <NewBoardingStayPage />
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
      <Route path="/site/:branchId" element={<ClinicSitePage />} />
      <Route path="/platform-admin/*" element={<PlatformAdminApp />} />
      <Route path="/" element={<Navigate to="/panel" replace />} />
    </Routes>
  );
}
