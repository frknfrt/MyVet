import { useEffect, useState } from 'react';
import { notificationApi, NotificationLog, NotificationStatus } from '../../api/notificationApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { NotificationStatusBadge, notificationTypeLabel } from './notificationStatus';
import styles from './SettingsPage.module.css';

export function NotificationsPanel() {
  const [status, setStatus] = useState<NotificationStatus | null>(null);
  const [logs, setLogs] = useState<NotificationLog[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  function reload() {
    notificationApi.status().then(setStatus);
    notificationApi.logs().then(setLogs);
  }

  useEffect(() => {
    reload();
  }, []);

  async function handleRetry(id: string) {
    setBusyId(id);
    try {
      await notificationApi.retry(id);
      reload();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <div className={styles.integrationCard}>
        <div className={styles.integrationHeader}>
          <div>
            <div className={styles.integrationName}>SMS / WhatsApp Bildirimleri</div>
            <div className={styles.integrationDesc}>
              Randevu onayı ve hatırlatma mesajları (her gün 09:00'da yarının randevuları için otomatik gönderilir)
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

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Müşteri</div>
          <div>Tür</div>
          <div>Durum</div>
          <div>Zaman</div>
          <div></div>
        </div>
        {logs.length === 0 ? (
          <div className={styles.empty}>Kayıt bulunmuyor</div>
        ) : (
          logs.map((log) => (
            <div key={log.id} className={styles.row}>
              <div>{log.ownerName}</div>
              <div className={styles.muted}>{notificationTypeLabel(log.notificationType)}</div>
              <div>
                <NotificationStatusBadge status={log.status} />
              </div>
              <div className={styles.muted}>{new Date(log.attemptedAt).toLocaleString('tr-TR')}</div>
              <div>
                {log.status === 'FAILED' && (
                  <Button variant="secondary" onClick={() => handleRetry(log.id)} disabled={busyId === log.id}>
                    {busyId === log.id ? '...' : 'Tekrar Dene'}
                  </Button>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
