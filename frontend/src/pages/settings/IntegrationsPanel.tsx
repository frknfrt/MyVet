import { useEffect, useState } from 'react';
import { tarbilApi, TarbilStatus, TarbilSyncLog } from '../../api/tarbilApi';
import { Button } from '../../components/ui/Button';
import { confirmationMethodLabel, TarbilSyncStatusBadge } from './tarbilStatus';
import { TarbilExtensionCard } from './TarbilExtensionCard';
import { TarbilMappingsCard } from './TarbilMappingsCard';
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

  async function run(id: string, action: () => Promise<void>) {
    setBusyId(id);
    try {
      await action();
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
              Aşı bildirimleri TARBİL'e hekim tarafından girilir; Vetly yalnızca gerçekten bildirilenleri "Gönderildi"
              gösterir.
            </div>
          </div>
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
                <div className={styles.statValue}>{status.submittedCount}</div>
              </div>
              <div className={styles.statCard}>
                <div className={styles.statLabel}>Bildirilmeyecek</div>
                <div className={styles.statValue}>{status.dismissedCount}</div>
              </div>
            </div>
            <div className={styles.lastSynced}>
              Son bildirim:{' '}
              {status.lastSubmittedAt ? new Date(status.lastSubmittedAt).toLocaleString('tr-TR') : 'Henüz yok'}
            </div>
          </>
        )}
      </div>

      <TarbilExtensionCard />

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Hasta</div>
          <div>Aşı</div>
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
              <div className={styles.muted}>
                {log.vaccineName}
                {log.administeredDate && ` · ${new Date(log.administeredDate).toLocaleDateString('tr-TR')}`}
              </div>
              <div>
                <TarbilSyncStatusBadge status={log.status} />
                {log.status === 'SUBMITTED' && (
                  <div className={styles.muted}>
                    {confirmationMethodLabel(log.confirmationMethod)}
                    {log.tarbilReference && ` · No: ${log.tarbilReference}`}
                  </div>
                )}
                {log.status === 'DISMISSED' && log.dismissedReason && (
                  <div className={styles.muted}>{log.dismissedReason}</div>
                )}
              </div>
              <div className={styles.muted}>{new Date(log.submittedAt ?? log.queuedAt).toLocaleString('tr-TR')}</div>
              <div>
                {log.status === 'PENDING' && (
                  <Button
                    variant="tertiary"
                    disabled={busyId === log.id}
                    onClick={() => run(log.id, () => tarbilApi.dismiss(log.id, 'Bildirim gerekmiyor'))}
                  >
                    Bildirilmeyecek
                  </Button>
                )}
                {log.status === 'DISMISSED' && (
                  <Button
                    variant="tertiary"
                    disabled={busyId === log.id}
                    onClick={() => run(log.id, () => tarbilApi.restore(log.id))}
                  >
                    Geri al
                  </Button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      <TarbilMappingsCard />
    </div>
  );
}
