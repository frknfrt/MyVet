import { useState } from 'react';
import { CampaignTab } from '../sms-whatsapp/CampaignTab';
import { HistoryTab } from '../sms-whatsapp/HistoryTab';
import { SettingsTab } from '../sms-whatsapp/SettingsTab';
import { TemplatesTab } from '../sms-whatsapp/TemplatesTab';
import styles from './SmsWhatsappPanel.module.css';

type Tab = 'history' | 'campaign' | 'templates' | 'settings';

const TABS: { key: Tab; label: string }[] = [
  { key: 'history', label: 'Gönderim Geçmişi' },
  { key: 'campaign', label: 'Toplu Kampanya' },
  { key: 'templates', label: 'Şablonlar' },
  { key: 'settings', label: 'Ayarlar' },
];

export function SmsWhatsappPanel() {
  const [tab, setTab] = useState<Tab>('history');

  return (
    <div>
      <div className={styles.subTabs}>
        {TABS.map((t) => (
          <div
            key={t.key}
            className={`${styles.subTab} ${tab === t.key ? styles.subTabActive : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </div>
        ))}
      </div>

      {tab === 'history' && <HistoryTab />}
      {tab === 'campaign' && <CampaignTab />}
      {tab === 'templates' && <TemplatesTab />}
      {tab === 'settings' && <SettingsTab />}
    </div>
  );
}
