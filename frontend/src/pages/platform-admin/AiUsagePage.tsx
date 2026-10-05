import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { AiUsageByTenant, platformAdminApi } from '../../api/platformAdminApi';
import { Button } from '../../components/ui/Button';
import styles from './PlatformAdminPages.module.css';
import usageStyles from './AiUsagePage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatDateTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('tr-TR') : '—';
}

function formatPercent(numerator: number, denominator: number): string {
  if (denominator === 0) return '—';
  return `%${Math.round((numerator / denominator) * 100)}`;
}

export function AiUsagePage() {
  const [rows, setRows] = useState<AiUsageByTenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    platformAdminApi
      .listAiUsage()
      .then(setRows)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  const totalJobs = rows.reduce((sum, r) => sum + r.totalJobs, 0);
  const totalDiagnosis = rows.reduce((sum, r) => sum + r.diagnosisJobs, 0);
  const totalTreatment = rows.reduce((sum, r) => sum + r.treatmentJobs, 0);
  const totalDecided = rows.reduce((sum, r) => sum + r.acceptedAsIs + r.acceptedWithEdits + r.rejected, 0);
  const totalAccepted = rows.reduce((sum, r) => sum + r.acceptedAsIs + r.acceptedWithEdits, 0);
  const totalFeedback = rows.reduce((sum, r) => sum + r.accurateFeedback + r.inaccurateFeedback, 0);
  const totalAccurate = rows.reduce((sum, r) => sum + r.accurateFeedback, 0);

  return (
    <div>
      <div className={styles.title}>AI Kullanımı</div>
      <p className={styles.note}>
        Kiracıların tanı/tedavi önerisi AI özelliğini ne kadar kullandığı, hekimlerin önerileri ne oranda kabul
        ettiği ve doğruluk geri bildirimi. "Karar Verilmemiş", öneri üretildi ama hekimin henüz kabul/düzenleme/red
        kararı vermediği işlerdir. Sadece tanı/tedavi önerisi bu şekilde kayıt altına alınıyor (SOAP taslağı AI
        özelliği için ayrı bir kullanım kaydı yok).
      </p>

      <div className={styles.actionsRow}>
        <Button variant="secondary" onClick={load} disabled={loading}>
          {loading ? 'Yenileniyor...' : 'Yenile'}
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      {!loading && (
        <div className={usageStyles.summaryRow}>
          <div className={usageStyles.summaryCard}>
            <div className={usageStyles.summaryLabel}>Toplam AI İşi</div>
            <div className={usageStyles.summaryCount}>{totalJobs}</div>
          </div>
          <div className={usageStyles.summaryCard}>
            <div className={usageStyles.summaryLabel}>Tanı Önerisi</div>
            <div className={usageStyles.summaryCount}>{totalDiagnosis}</div>
          </div>
          <div className={usageStyles.summaryCard}>
            <div className={usageStyles.summaryLabel}>Tedavi Önerisi</div>
            <div className={usageStyles.summaryCount}>{totalTreatment}</div>
          </div>
          <div className={usageStyles.summaryCard}>
            <div className={usageStyles.summaryLabel}>Kabul Oranı</div>
            <div className={usageStyles.summaryCount}>{formatPercent(totalAccepted, totalDecided)}</div>
            <div className={usageStyles.summarySub}>karara bağlanmış işler üzerinden</div>
          </div>
          <div className={usageStyles.summaryCard}>
            <div className={usageStyles.summaryLabel}>Doğruluk Oranı</div>
            <div className={usageStyles.summaryCount}>{formatPercent(totalAccurate, totalFeedback)}</div>
            <div className={usageStyles.summarySub}>geri bildirim verilmiş işler üzerinden</div>
          </div>
        </div>
      )}

      <div className={styles.tableCard}>
        <div className={[styles.tableHead, styles.aiUsageRow].join(' ')}>
          <div>Kiracı</div>
          <div>Toplam İş</div>
          <div>Tanı / Tedavi</div>
          <div>Kabul / Düzenleyerek / Red</div>
          <div>Karar Verilmemiş</div>
          <div>Doğruluk (✓/✗)</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : rows.length === 0 ? (
          <div className={styles.empty}>Henüz AI özelliğini kullanan bir kiracı yok</div>
        ) : (
          rows.map((row) => (
            <div key={row.tenantId} className={[styles.row, styles.aiUsageRow].join(' ')} style={{ cursor: 'default' }}>
              <div>
                {row.tenantName}
                <div className={styles.muted} style={{ fontSize: 11 }}>Son kullanım: {formatDateTime(row.lastUsedAt)}</div>
              </div>
              <div>{row.totalJobs}</div>
              <div className={styles.muted}>{row.diagnosisJobs} / {row.treatmentJobs}</div>
              <div className={styles.muted}>{row.acceptedAsIs} / {row.acceptedWithEdits} / {row.rejected}</div>
              <div className={styles.muted}>{row.noDecisionYet}</div>
              <div className={styles.muted}>{row.accurateFeedback} / {row.inaccurateFeedback}</div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
