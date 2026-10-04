import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { PlatformOverview, platformAdminApi } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import styles from './PlatformAdminPages.module.css';
import overviewStyles from './OverviewPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatCurrency(value: number): string {
  return `${value.toLocaleString('tr-TR', { minimumFractionDigits: 0, maximumFractionDigits: 0 })} ₺`;
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString('tr-TR');
}

export function OverviewPage() {
  const navigate = useNavigate();
  const [overview, setOverview] = useState<PlatformOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    platformAdminApi
      .getOverview()
      .then(setOverview)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <div className={styles.title}>Genel Bakış</div>
      <p className={overviewStyles.note}>
        Platform genelinde kiracı sayıları ve gelir özeti. "Aylık Yinelenen Gelir", şu an ödemesi aktif olan tüm
        aboneliklerin plan fiyatları toplamıdır (tahmini); "Bu Ay Tahsil Edilen" ise fiilen ödenmiş faturaların
        toplamıdır (gerçekleşen).
      </p>

      {error && <div className={styles.errorBanner}>{error}</div>}

      {loading || !overview ? (
        <div className={styles.empty}>Yükleniyor...</div>
      ) : (
        <>
          <div className={overviewStyles.summaryRow}>
            <div className={overviewStyles.summaryCard}>
              <div className={overviewStyles.summaryLabel}>Toplam Kiracı</div>
              <div className={overviewStyles.summaryCount}>{overview.totalTenants}</div>
            </div>
            <div className={overviewStyles.summaryCard}>
              <div className={overviewStyles.summaryLabel}>Aktif Kiracı</div>
              <div className={overviewStyles.summaryCount}>{overview.activeTenants}</div>
            </div>
            <div className={overviewStyles.summaryCard}>
              <div className={overviewStyles.summaryLabel}>Askıya Alınmış</div>
              <div className={overviewStyles.summaryCount}>{overview.suspendedTenants}</div>
            </div>
            <div className={overviewStyles.summaryCard}>
              <div className={overviewStyles.summaryLabel}>Deneme Sürümünde</div>
              <div className={overviewStyles.summaryCount}>{overview.trialBillingTenants}</div>
            </div>
            <div className={overviewStyles.summaryCard}>
              <div className={overviewStyles.summaryLabel}>Son 30 Günde Yeni</div>
              <div className={overviewStyles.summaryCount}>{overview.newTenantsLast30Days}</div>
            </div>
          </div>

          <div className={overviewStyles.summaryRow}>
            <div className={overviewStyles.summaryCard}>
              <div className={overviewStyles.summaryLabel}>Aylık Yinelenen Gelir (MRR)</div>
              <div className={overviewStyles.summaryCount}>{formatCurrency(overview.monthlyRecurringRevenue)}</div>
              <div className={overviewStyles.summarySub}>tahmini, aktif abonelikler üzerinden</div>
            </div>
            <div className={overviewStyles.summaryCard}>
              <div className={overviewStyles.summaryLabel}>Bu Ay Tahsil Edilen</div>
              <div className={overviewStyles.summaryCount}>{formatCurrency(overview.collectedThisMonth)}</div>
              <div className={overviewStyles.summarySub}>ödenmiş faturalar</div>
            </div>
            <div className={overviewStyles.summaryCard}>
              <div className={overviewStyles.summaryLabel}>
                <Badge tone={overview.overdueInvoiceCount > 0 ? 'danger' : 'neutral'}>Gecikmiş Ödeme</Badge>
              </div>
              <div className={overviewStyles.summaryCount}>{formatCurrency(overview.overdueInvoiceTotal)}</div>
              <div className={overviewStyles.summarySub}>{overview.overdueInvoiceCount} fatura</div>
            </div>
          </div>

          <div className={overviewStyles.sectionTitle}>Plan Dağılımı</div>
          <div className={styles.tableCard}>
            <div className={[styles.tableHead, overviewStyles.planRow].join(' ')}>
              <div>Plan</div>
              <div>Kiracı Sayısı</div>
              <div></div>
              <div>Aylık Gelir</div>
            </div>
            {overview.planBreakdown.length === 0 ? (
              <div className={styles.empty}>Henüz kiracı yok</div>
            ) : (
              overview.planBreakdown.map((p) => (
                <div key={p.planCode} className={[styles.row, overviewStyles.planRow].join(' ')} style={{ cursor: 'default' }}>
                  <div>{p.planName}</div>
                  <div className={styles.muted}>{p.tenantCount}</div>
                  <div></div>
                  <div className={styles.muted}>{formatCurrency(p.monthlyRevenue)}</div>
                </div>
              ))
            )}
          </div>

          <div className={overviewStyles.sectionTitle}>Son Kayıt Olan Kiracılar</div>
          <div className={styles.tableCard}>
            <div className={[styles.tableHead, overviewStyles.recentRow].join(' ')}>
              <div>Klinik</div>
              <div>Plan</div>
              <div>Kayıt Tarihi</div>
            </div>
            {overview.recentTenants.length === 0 ? (
              <div className={styles.empty}>Henüz kiracı yok</div>
            ) : (
              overview.recentTenants.map((t) => (
                <div
                  key={t.tenantId}
                  className={[styles.row, overviewStyles.recentRow].join(' ')}
                  onClick={() => navigate(`/platform-admin/tenants/${t.tenantId}`)}
                >
                  <div>{t.name}</div>
                  <div className={styles.muted}>{t.planCode}</div>
                  <div className={styles.muted}>{formatDate(t.createdAt)}</div>
                </div>
              ))
            )}
          </div>
        </>
      )}
    </div>
  );
}
