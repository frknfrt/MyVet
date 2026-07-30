import { useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { IntegrationsPanel } from './IntegrationsPanel';
import styles from './SettingsPage.module.css';

type Tab = 'integrations';

export function SettingsPage() {
  const [tab] = useState<Tab>('integrations');

  return (
    <AppShell>
      <div className={styles.topbar}>
        <h1 className={styles.title}>Ayarlar</h1>
      </div>

      <div className={styles.tabs}>
        <div className={`${styles.tab} ${tab === 'integrations' ? styles.tabActive : ''}`}>Entegrasyonlar</div>
      </div>

      {tab === 'integrations' && <IntegrationsPanel />}
    </AppShell>
  );
}
