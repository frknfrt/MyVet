import { useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { HistoryTab } from './HistoryTab';
import { CampaignTab } from './CampaignTab';
import { TemplatesTab } from './TemplatesTab';
import { SettingsTab } from './SettingsTab';
import styles from './SmsWhatsappPage.module.css';

type Tab = 'history' | 'campaign' | 'templates' | 'settings';

export function SmsWhatsappPage() {
  const [tab, setTab] = useState<Tab>('history');

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Sms &amp; Whatsapp</h1>
          <div className={styles.sub}>Gönderim geçmişi, toplu kampanya ve mesaj şablonları</div>
        </div>
      </div>

      <div className={styles.tabs}>
        <div className={`${styles.tab} ${tab === 'history' ? styles.tabActive : ''}`} onClick={() => setTab('history')}>
          Gönderim Geçmişi
        </div>
        <div className={`${styles.tab} ${tab === 'campaign' ? styles.tabActive : ''}`} onClick={() => setTab('campaign')}>
          Toplu Kampanya
        </div>
        <div className={`${styles.tab} ${tab === 'templates' ? styles.tabActive : ''}`} onClick={() => setTab('templates')}>
          Şablonlar
        </div>
        <div className={`${styles.tab} ${tab === 'settings' ? styles.tabActive : ''}`} onClick={() => setTab('settings')}>
          Ayarlar
        </div>
      </div>

      {tab === 'history' && <HistoryTab />}
      {tab === 'campaign' && <CampaignTab />}
      {tab === 'templates' && <TemplatesTab />}
      {tab === 'settings' && <SettingsTab />}
    </AppShell>
  );
}
