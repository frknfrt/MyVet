import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { AppShell } from '../../components/layout/AppShell';
import { AiCenterPanel } from './AiCenterPanel';
import { BranchManagementPanel } from './BranchManagementPanel';
import { DrugCatalogPanel } from './DrugCatalogPanel';
import { EfaturaPanel } from './EfaturaPanel';
import { IntegrationsPanel } from './IntegrationsPanel';
import { RolePermissionsPanel } from './RolePermissionsPanel';
import { ServiceTypesPanel } from './ServiceTypesPanel';
import { SmsWhatsappPanel } from './SmsWhatsappPanel';
import { SpeciesBreedsPanel } from './SpeciesBreedsPanel';
import { StaffManagementPanel } from './StaffManagementPanel';
import { SubscriptionPanel } from './SubscriptionPanel';
import { WorkingHoursPanel } from './WorkingHoursPanel';
import styles from './SettingsPage.module.css';

interface SettingsTabConfig {
  path: string;
  label: string;
  adminOnly?: boolean;
}

/**
 * Ayarlar altındaki her yeni ekran SADECE bu listeye bir satır ekleyerek
 * bağlanır — sekme çubuğu ve <Routes> burada tek kaynaktan türetilir.
 * adminOnly: true olan sekmeler sıradan rollere (VET/RECEPTIONIST/TECHNICIAN)
 * sekme çubuğunda gösterilmez; URL'ye direkt gidilirse de "yetkiniz yok"
 * mesajı görünür (backend zaten aynı endpoint'i ADMIN'e kilitliyor).
 */
const SETTINGS_TABS: SettingsTabConfig[] = [
  { path: 'kullanicilar', label: 'Kullanıcılar', adminOnly: true },
  { path: 'subeler', label: 'Şubeler' },
  { path: 'calisma-saatleri', label: 'Çalışma Saatleri' },
  { path: 'roller', label: 'Rol & Yetki' },
  { path: 'abonelik', label: 'Abonelik' },
  { path: 'entegrasyonlar', label: 'Entegrasyonlar' },
  { path: 'tur-irk', label: 'Tür & Irk' },
  { path: 'hizmetler', label: 'Hizmetler' },
  { path: 'ilac-katalogu', label: 'İlaç Kataloğu' },
  { path: 'ai-merkezi', label: 'AI Merkezi' },
  { path: 'sms-whatsapp', label: 'SMS / WhatsApp' },
  { path: 'e-fatura', label: 'e-Fatura' },
];

export function SettingsPage() {
  const { session } = useAuth();
  const isAdmin = session?.role === 'ADMIN';
  const visibleTabs = SETTINGS_TABS.filter((t) => !t.adminOnly || isAdmin);

  return (
    <AppShell>
      <div className={styles.topbar}>
        <h1 className={styles.title}>Ayarlar</h1>
      </div>

      <div className={styles.tabs}>
        {visibleTabs.map((t) => (
          <NavLink
            key={t.path}
            to={t.path}
            className={({ isActive }) => `${styles.tab} ${isActive ? styles.tabActive : ''}`}
          >
            {t.label}
          </NavLink>
        ))}
      </div>

      <Routes>
        <Route index element={<Navigate to={visibleTabs[0]?.path ?? 'entegrasyonlar'} replace />} />
        <Route
          path="kullanicilar"
          element={isAdmin ? <StaffManagementPanel /> : <div className={styles.errorBanner}>Bu bölümü görüntüleme yetkiniz yok</div>}
        />
        <Route path="subeler" element={<BranchManagementPanel />} />
        <Route path="calisma-saatleri" element={<WorkingHoursPanel />} />
        <Route path="roller" element={<RolePermissionsPanel />} />
        <Route path="abonelik" element={<SubscriptionPanel />} />
        <Route path="entegrasyonlar" element={<IntegrationsPanel />} />
        <Route path="tur-irk" element={<SpeciesBreedsPanel />} />
        <Route path="hizmetler" element={<ServiceTypesPanel />} />
        <Route path="ilac-katalogu" element={<DrugCatalogPanel />} />
        <Route path="ai-merkezi" element={<AiCenterPanel />} />
        <Route path="sms-whatsapp" element={<SmsWhatsappPanel />} />
        <Route path="e-fatura" element={<EfaturaPanel />} />
      </Routes>
    </AppShell>
  );
}
