import { useEffect, useState } from 'react';
import { tarbilApi, TarbilStatus, TarbilSyncLog } from '../../api/tarbilApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { TarbilSyncStatusBadge, tarbilTypeLabel } from './tarbilStatus';
import styles from './SettingsPage.module.css';

export function IntegrationsPanel() {
  const [status, setStatus] = useState<TarbilStatus | null>(null);
  const [logs, setLogs] = useState<TarbilSyncLog[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  function reload() {
    tarbilApi.status().then(setStatus);
    tarbilApi.syncLogs().then(setLogs);
  }

  useEffect(() => {
    reload();
  }, []);

  async function handleRetry(id: string) {
    setBusyId(id);
    try {
      await tarbilApi.retry(id);
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
            <div className={styles.integrationName}>TARBİL</div>
            <div className={styles.integrationDesc}>
              T.C. Tarım ve Orman Bakanlığı — aşı ve kimliklendirme bildirimleri
            </div>
          </div>
          {status?.connected ? <Badge tone="success">Bağlı</Badge> : <Badge tone="neutral">Yükleniyor...</Badge>}
        </div>

        {status && (
          <>
            <div className={styles.statsRow}>
              <div className={styles.statCard}>
                <div className={styles.statLabel}>Bekleyen</div>
                <div className={styles.statValue}>{status.pendingCount}</div>
              </div>
              <div className={styles.statCard}>
                <div className={styles.statLabel}>Senkronize</div>
                <div className={styles.statValue}>{status.syncedCount}</div>
              </div>
              <div className={styles.statCard}>
                <div className={styles.statLabel}>Başarısız</div>
                <div className={styles.statValue}>{status.failedCount}</div>
              </div>
            </div>
            <div className={styles.lastSynced}>
              Son senkronizasyon:{' '}
              {status.lastSyncedAt ? new Date(status.lastSyncedAt).toLocaleString('tr-TR') : 'Henüz yok'}
            </div>
          </>
        )}
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Hasta</div>
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
              <div>{log.patientName}</div>
              <div className={styles.muted}>{tarbilTypeLabel(log.syncType)}</div>
              <div>
                <TarbilSyncStatusBadge status={log.status} />
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
