import { useEffect, useState } from 'react';
import { notificationApi, NotificationStatus } from '../../api/notificationApi';
import { Badge } from '../../components/ui/Badge';
import styles from './SettingsTab.module.css';

// Her kanalin (SMS: Ileti Merkezi, WhatsApp: Twilio) gercekten baglanip
// baglanmadigi birbirinden bagimsiz -- daha once burada tek bir "connected"
// bayragi vardi ve metin sabit kodlanmisti ("WhatsApp gercek, SMS mock"),
// bu yuzden SMS gercekten baglandiktan sonra bile ekran hala "simule
// ediliyor" diyordu. Artik ikisi ayri ayri gosteriliyor.
export function SettingsTab() {
  const [status, setStatus] = useState<NotificationStatus | null>(null);

  useEffect(() => {
    notificationApi.status().then(setStatus);
  }, []);

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <div>
          <div className={styles.name}>SMS / WhatsApp Sağlayıcı Durumu</div>
          <div className={styles.desc}>
            {status && (
              <>
                WhatsApp {status.whatsappConfigured ? 'Twilio üzerinden gerçek gönderim yapıyor.' : 'henüz sağlayıcıya bağlı değil, simüle ediliyor.'}{' '}
                SMS {status.smsConfigured ? 'İleti Merkezi üzerinden gerçek gönderim yapıyor.' : 'henüz sağlayıcıya bağlı değil, simüle ediliyor.'}
              </>
            )}
          </div>
        </div>
        {status ? (
          <div className={styles.badgeGroup}>
            <Badge tone={status.smsConfigured ? 'success' : 'warning'}>SMS: {status.smsConfigured ? 'İleti Merkezi (gerçek)' : 'Mock'}</Badge>
            <Badge tone={status.whatsappConfigured ? 'success' : 'warning'}>WhatsApp: {status.whatsappConfigured ? 'Twilio (gerçek)' : 'Mock'}</Badge>
          </div>
        ) : (
          <Badge tone="neutral">Yükleniyor...</Badge>
        )}
      </div>

      {status && (
        <>
          <div className={styles.statsRow}>
            <div className={styles.statCard}>
              <div className={styles.statLabel}>Bekleyen</div>
              <div className={styles.statValue}>{status.pendingCount}</div>
            </div>
            <div className={styles.statCard}>
              <div className={styles.statLabel}>Gönderildi</div>
              <div className={styles.statValue}>{status.sentCount}</div>
            </div>
            <div className={styles.statCard}>
              <div className={styles.statLabel}>Başarısız</div>
              <div className={styles.statValue}>{status.failedCount}</div>
            </div>
          </div>
          <div className={styles.lastSynced}>
            Son gönderim: {status.lastSentAt ? new Date(status.lastSentAt).toLocaleString('tr-TR') : 'Henüz yok'}
          </div>
        </>
      )}
    </div>
  );
}
