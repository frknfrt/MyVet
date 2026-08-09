import { useEffect, useState } from 'react';
import { notificationApi, NotificationStatus } from '../../api/notificationApi';
import { Badge } from '../../components/ui/Badge';
import styles from './SettingsTab.module.css';

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
            Gerçek bir sağlayıcı hesabı (Twilio/Netgsm/WhatsApp Business API) bağlandığında burada gösterilecek.
          </div>
        </div>
        {status ? (
          status.connected ? (
            <Badge tone="success">Bağlı</Badge>
          ) : (
            <Badge tone="warning">Mock modu — sağlayıcı bağlı değil</Badge>
          )
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
