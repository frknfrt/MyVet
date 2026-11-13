import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { platformAdminApi, TrialSignupRequest } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import styles from './PlatformAdminPages.module.css';
import leadsStyles from './LeadsPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('tr-TR');
}

/**
 * Vitrin sitesinden self-servis açılan 7 günlük deneme kayıtları. Oda sicil numarası
 * doğrulanmaz (sorgulanabilir açık bir kaynak yok) — şüpheli hesap, klinik detayından
 * askıya alınır.
 */
export function TrialSignupsSection() {
  const [requests, setRequests] = useState<TrialSignupRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    platformAdminApi
      .listTrialSignupRequests()
      .then((list) => {
        if (!cancelled) setRequests(list);
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessageOf(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const completedCount = requests.filter((r) => r.status === 'COMPLETED').length;

  return (
    <section>
      <div className={styles.modalTitle} style={{ marginTop: 32 }}>
        Deneme Kayıtları {requests.length > 0 && `(${requests.length})`}
        {completedCount > 0 && <span className={styles.muted}> — {completedCount} hesap açıldı</span>}
      </div>
      <p className={leadsStyles.note}>
        Siteden 7 günlük ücretsiz deneme başlatanlar. Telefon SMS koduyla doğrulanır; oda sicil numarası
        kullanıcının beyanıdır ve doğrulanmaz.
      </p>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.tableCard}>
        <div className={[styles.tableHead, leadsStyles.trialRow].join(' ')}>
          <div>Klinik</div>
          <div>Yetkili</div>
          <div>İletişim</div>
          <div>İl</div>
          <div>Oda Sicil No</div>
          <div>Durum</div>
          <div>Kayıt Tarihi</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : requests.length === 0 ? (
          <div className={styles.empty}>Henüz deneme kaydı yok</div>
        ) : (
          requests.map((r) => (
            <div key={r.id} className={[styles.row, leadsStyles.trialRow].join(' ')} style={{ cursor: 'default' }}>
              <div>
                {r.tenantId ? <Link to={`/platform-admin/tenants/${r.tenantId}`}>{r.clinicName}</Link> : r.clinicName}
              </div>
              <div className={styles.muted}>{r.fullName}</div>
              <div className={leadsStyles.contact}>
                <span>{r.email}</span>
                <span className={leadsStyles.contactPhone}>{r.phone}</span>
              </div>
              <div className={styles.muted}>{r.city}</div>
              <div>{r.chamberRegistrationNo}</div>
              <div>
                <Badge tone={r.status === 'COMPLETED' ? 'success' : 'neutral'}>
                  {r.status === 'COMPLETED' ? 'Hesap Açıldı' : 'Kod Bekliyor'}
                </Badge>
              </div>
              <div className={styles.muted}>{formatDateTime(r.createdAt)}</div>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
