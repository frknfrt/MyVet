import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { BillingStatus, platformAdminApi, TenantAdminOverview } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import styles from './PlatformAdminPages.module.css';
import billingStyles from './PlatformBillingPage.module.css';
import { BILLING_STATUS_LABELS, BILLING_STATUS_TONES } from './tenantBadges';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const BILLING_STATUS_ORDER: BillingStatus[] = ['ACTIVE', 'TRIAL', 'PAST_DUE', 'CANCELED'];

export function PlatformBillingPage() {
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<TenantAdminOverview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    platformAdminApi
      .listTenants()
      .then(setTenants)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }, []);

  const groups: Record<BillingStatus, TenantAdminOverview[]> = { TRIAL: [], ACTIVE: [], PAST_DUE: [], CANCELED: [] };
  tenants.forEach((t) => groups[t.billingStatus].push(t));

  return (
    <div>
      <div className={styles.title}>Faturalama</div>
      <p className={billingStyles.note}>
        Gerçek bir ödeme tahsilat entegrasyonu henüz yok — bu sayfa kiracıların mevcut faturalama durumlarının
        salt-okunur bir özetidir.
      </p>

      {error && <div className={styles.errorBanner}>{error}</div>}

      {loading ? (
        <div className={styles.empty}>Yükleniyor...</div>
      ) : (
        <>
          <div className={billingStyles.summaryRow}>
            {BILLING_STATUS_ORDER.map((status) => (
              <div key={status} className={billingStyles.summaryCard}>
                <Badge tone={BILLING_STATUS_TONES[status]}>{BILLING_STATUS_LABELS[status]}</Badge>
                <div className={billingStyles.summaryCount}>{groups[status].length}</div>
              </div>
            ))}
          </div>

          {BILLING_STATUS_ORDER.map((status) =>
            groups[status].length === 0 ? null : (
              <div key={status} className={billingStyles.group}>
                <div className={billingStyles.groupTitle}>
                  <Badge tone={BILLING_STATUS_TONES[status]}>{BILLING_STATUS_LABELS[status]}</Badge>
                  <span className={styles.muted}>({groups[status].length})</span>
                </div>
                <div className={styles.tableCard}>
                  {groups[status].map((t) => (
                    <div
                      key={t.tenantId}
                      className={billingStyles.row}
                      onClick={() => navigate(`/platform-admin/tenants/${t.tenantId}`)}
                    >
                      <div>{t.name}</div>
                      <div className={styles.muted}>{t.planCode}</div>
                    </div>
                  ))}
                </div>
              </div>
            )
          )}
        </>
      )}
    </div>
  );
}
