import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { LoginPage } from './pages/LoginPage';
import { RegisterClinicPage } from './pages/auth/RegisterClinicPage';
import { SetupWizardPage } from './pages/auth/SetupWizardPage';
import { DashboardPage } from './pages/dashboard/DashboardPage';
import { PatientsPage } from './pages/patients/PatientsPage';
import { PatientDetailPage } from './pages/patients/PatientDetailPage';
import { NewPatientPage } from './pages/patients/NewPatientPage';
import { OwnerDetailPage } from './pages/owners/OwnerDetailPage';
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
        path="/hastalar/yeni"
        element={
          <RequireAuth>
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
        path="/musteriler/yeni"
        element={
          <RequireAuth>
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
          <RequireAuth>
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
          <RequireAuth>
            <NewVaccinationPage />
          </RequireAuth>
        }
      />
      <Route
        path="/laboratuvar"
        element={
          <RequireAuth>
            <LaboratoryPage />
          </RequireAuth>
        }
      />
      <Route
        path="/goruntuleme"
        element={
          <RequireAuth>
            <ImagingPage />
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
          <RequireAuth>
            <ReportsPage />
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
