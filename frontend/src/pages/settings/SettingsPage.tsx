import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { StaffRole } from '../../auth/session';
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
  /** Belirtilmezse tüm roller görür. Belirtilirse hem sekme çubuğunda hem URL erişiminde uygulanır. */
  roles?: StaffRole[];
}

/**
 * Ayarlar altındaki her yeni ekran SADECE bu listeye bir satır ekleyerek
 * bağlanır — sekme çubuğu ve <Routes> burada tek kaynaktan türetilir.
 * roles kısıtlı olan sekmeler diğer rollere sekme çubuğunda gösterilmez;
 * URL'ye direkt gidilirse de "yetkiniz yok" mesajı görünür (backend zaten
 * aynı endpoint'i aynı role kilitliyor).
 */
const SETTINGS_TABS: SettingsTabConfig[] = [
  { path: 'kullanicilar', label: 'Kullanıcılar', roles: ['ADMIN'] },
  // Şubeler/Çalışma Saatleri (BranchesController) -- yazma ADMIN-only.
  { path: 'subeler', label: 'Şubeler', roles: ['ADMIN'] },
  { path: 'calisma-saatleri', label: 'Çalışma Saatleri', roles: ['ADMIN'] },
  { path: 'roller', label: 'Rol & Yetki' },
  // SubscriptionsController: GET /subscriptions/current ADMIN-only.
  { path: 'abonelik', label: 'Abonelik', roles: ['ADMIN'] },
  // TarbilController tamamı ADMIN-only.
  { path: 'entegrasyonlar', label: 'Entegrasyonlar', roles: ['ADMIN'] },
  // SpeciesController: POST (tür/ırk ekleme) ADMIN-only.
  { path: 'tur-irk', label: 'Tür & Irk', roles: ['ADMIN'] },
  // ServiceTypesController: POST (hizmet ekleme) ADMIN-only.
  { path: 'hizmetler', label: 'Hizmetler', roles: ['ADMIN'] },
  // DrugsController: POST/PUT (ilaç ekleme/düzenleme) ADMIN-only.
  { path: 'ilac-katalogu', label: 'İlaç Kataloğu', roles: ['ADMIN'] },
  { path: 'ai-merkezi', label: 'AI Merkezi' },
  { path: 'sms-whatsapp', label: 'SMS / WhatsApp', roles: ['ADMIN', 'RECEPTIONIST'] },
  // EInvoiceController tamamı ADMIN-only.
  { path: 'e-fatura', label: 'e-Fatura', roles: ['ADMIN'] },
];

export function SettingsPage() {
  const { session } = useAuth();
  const isAdmin = session?.role === 'ADMIN';
  const hasRoles = (roles?: StaffRole[]) => !roles || (session != null && roles.includes(session.role));
  const hasAccess = (t: SettingsTabConfig) => hasRoles(t.roles);
  const visibleTabs = SETTINGS_TABS.filter(hasAccess);
  const canViewSmsWhatsapp = hasRoles(['ADMIN', 'RECEPTIONIST']);
  const forbidden = <div className={styles.errorBanner}>Bu bölümü görüntüleme yetkiniz yok</div>;

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
        <Route path="kullanicilar" element={isAdmin ? <StaffManagementPanel /> : forbidden} />
        <Route path="subeler" element={isAdmin ? <BranchManagementPanel /> : forbidden} />
        <Route path="calisma-saatleri" element={isAdmin ? <WorkingHoursPanel /> : forbidden} />
        <Route path="roller" element={<RolePermissionsPanel />} />
        <Route path="abonelik" element={isAdmin ? <SubscriptionPanel /> : forbidden} />
        <Route path="entegrasyonlar" element={isAdmin ? <IntegrationsPanel /> : forbidden} />
        <Route path="tur-irk" element={isAdmin ? <SpeciesBreedsPanel /> : forbidden} />
        <Route path="hizmetler" element={isAdmin ? <ServiceTypesPanel /> : forbidden} />
        <Route path="ilac-katalogu" element={isAdmin ? <DrugCatalogPanel /> : forbidden} />
        <Route path="ai-merkezi" element={<AiCenterPanel />} />
        <Route path="sms-whatsapp" element={canViewSmsWhatsapp ? <SmsWhatsappPanel /> : forbidden} />
        <Route path="e-fatura" element={isAdmin ? <EfaturaPanel /> : forbidden} />
      </Routes>
    </AppShell>
  );
}
