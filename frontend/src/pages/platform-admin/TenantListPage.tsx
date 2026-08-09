import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { platformAdminApi, TenantAdminOverview } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { FieldWrap, Input } from '../../components/ui/Field';
import styles from './PlatformAdminPages.module.css';
import { BILLING_STATUS_LABELS, BILLING_STATUS_TONES, TENANT_STATUS_LABELS, TENANT_STATUS_TONES } from './tenantBadges';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function TenantListPage() {
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<TenantAdminOverview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  useEffect(() => {
    platformAdminApi
      .listTenants()
      .then(setTenants)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }, []);

  const filtered = tenants.filter((t) => t.name.toLowerCase().includes(query.trim().toLowerCase()));

  return (
    <div>
      <div className={styles.title}>Kiracılar</div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.searchRow}>
        <FieldWrap label="Ara">
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Klinik adı..." />
        </FieldWrap>
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Klinik Adı</div>
          <div>Plan</div>
          <div>Faturalama</div>
          <div>Durum</div>
          <div>Şube</div>
          <div>Personel</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className={styles.empty}>Kiracı bulunamadı</div>
        ) : (
          filtered.map((t) => (
            <div key={t.tenantId} className={styles.row} onClick={() => navigate(`/platform-admin/tenants/${t.tenantId}`)}>
              <div>{t.name}</div>
              <div className={styles.muted}>{t.planCode}</div>
              <div>
                <Badge tone={BILLING_STATUS_TONES[t.billingStatus]}>{BILLING_STATUS_LABELS[t.billingStatus]}</Badge>
              </div>
              <div>
                <Badge tone={TENANT_STATUS_TONES[t.status]}>{TENANT_STATUS_LABELS[t.status]}</Badge>
              </div>
              <div className={styles.muted}>{t.branchCount}</div>
              <div className={styles.muted}>{t.staffUserCount}</div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
