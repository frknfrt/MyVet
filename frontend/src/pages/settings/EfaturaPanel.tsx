import { useEffect, useState } from 'react';
import { efaturaApi, EInvoiceStatus, EInvoiceSubmission } from '../../api/efaturaApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EInvoiceSubmissionStatusBadge, eInvoiceDocumentTypeLabel } from './efaturaStatus';
import styles from './SettingsPage.module.css';

export function EfaturaPanel() {
  const [status, setStatus] = useState<EInvoiceStatus | null>(null);
  const [submissions, setSubmissions] = useState<EInvoiceSubmission[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  function reload() {
    efaturaApi.status().then(setStatus);
    efaturaApi.submissions().then(setSubmissions);
  }

  useEffect(() => {
    reload();
  }, []);

  async function handleRetry(id: string) {
    setBusyId(id);
    try {
      await efaturaApi.retry(id);
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
            <div className={styles.integrationName}>e-Fatura / e-Arşiv</div>
            <div className={styles.integrationDesc}>
              Kesilen faturalar GİB'e otomatik gönderilir (şu an sadece bireysel alıcı — e-Arşiv — desteklenir)
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
                <div className={styles.statValue}>{status.submittedCount}</div>
              </div>
              <div className={styles.statCard}>
                <div className={styles.statLabel}>Başarısız</div>
                <div className={styles.statValue}>{status.failedCount}</div>
              </div>
            </div>
            <div className={styles.lastSynced}>
              Son gönderim: {status.lastSubmittedAt ? new Date(status.lastSubmittedAt).toLocaleString('tr-TR') : 'Henüz yok'}
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
        {submissions.length === 0 ? (
          <div className={styles.empty}>Kayıt bulunmuyor</div>
        ) : (
          submissions.map((s) => (
            <div key={s.id} className={styles.row}>
              <div>{s.ownerName}</div>
              <div className={styles.muted}>{eInvoiceDocumentTypeLabel(s.documentType)}</div>
              <div>
                <EInvoiceSubmissionStatusBadge status={s.status} />
              </div>
              <div className={styles.muted}>{new Date(s.attemptedAt).toLocaleString('tr-TR')}</div>
              <div>
                {s.status === 'FAILED' && (
                  <Button variant="secondary" onClick={() => handleRetry(s.id)} disabled={busyId === s.id}>
                    {busyId === s.id ? '...' : 'Tekrar Dene'}
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
