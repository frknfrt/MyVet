import { useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { EfaturaPanel } from './EfaturaPanel';
import { IntegrationsPanel } from './IntegrationsPanel';
import { NotificationsPanel } from './NotificationsPanel';
import { ServiceTypesPanel } from './ServiceTypesPanel';
import { SpeciesBreedsPanel } from './SpeciesBreedsPanel';
import styles from './SettingsPage.module.css';

type Tab = 'integrations' | 'catalog' | 'services' | 'notifications' | 'efatura';

export function SettingsPage() {
  const [tab, setTab] = useState<Tab>('integrations');

  return (
    <AppShell>
      <div className={styles.topbar}>
        <h1 className={styles.title}>Ayarlar</h1>
      </div>

      <div className={styles.tabs}>
        <div className={`${styles.tab} ${tab === 'integrations' ? styles.tabActive : ''}`} onClick={() => setTab('integrations')}>
          Entegrasyonlar
        </div>
        <div className={`${styles.tab} ${tab === 'catalog' ? styles.tabActive : ''}`} onClick={() => setTab('catalog')}>
          Tür &amp; Irk
        </div>
        <div className={`${styles.tab} ${tab === 'services' ? styles.tabActive : ''}`} onClick={() => setTab('services')}>
          Hizmetler
        </div>
        <div className={`${styles.tab} ${tab === 'notifications' ? styles.tabActive : ''}`} onClick={() => setTab('notifications')}>
          Bildirimler
        </div>
        <div className={`${styles.tab} ${tab === 'efatura' ? styles.tabActive : ''}`} onClick={() => setTab('efatura')}>
          e-Fatura
        </div>
      </div>

      {tab === 'integrations' && <IntegrationsPanel />}
      {tab === 'catalog' && <SpeciesBreedsPanel />}
      {tab === 'services' && <ServiceTypesPanel />}
      {tab === 'notifications' && <NotificationsPanel />}
      {tab === 'efatura' && <EfaturaPanel />}
    </AppShell>
  );
}
