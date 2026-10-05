import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { platformAdminApi, TenantSignupRequest } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import styles from './PlatformAdminPages.module.css';
import leadsStyles from './LeadsPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('tr-TR');
}

function daysSince(value: string): number {
  return Math.floor((Date.now() - new Date(value).getTime()) / (1000 * 60 * 60 * 24));
}

export function LeadsPage() {
  const [requests, setRequests] = useState<TenantSignupRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    platformAdminApi
      .listSignupRequests()
      .then(setRequests)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  const pendingCount = requests.filter((r) => r.status === 'PENDING').length;

  return (
    <div>
      <div className={styles.title}>Potansiyel Müşteriler</div>
      <p className={leadsStyles.note}>
        vetly.com'da ödeme başlatıp klinik hesabı henüz tamamlanmamış ("Bekliyor") ya da başarıyla tamamlanmış
        ("Tamamlandı") kayıtlar. "Bekliyor" olanlar ödemesi yarıda kalmış ya da başarısız olmuş potansiyel
        müşterilerdir — telefon ya da e-posta ile aranıp yardımcı olunabilir. 2 günden eski "Bekliyor" kayıtları
        kırmızı ile işaretlenir.
      </p>

      <div className={styles.actionsRow}>
        <Button variant="secondary" onClick={load} disabled={loading}>
          {loading ? 'Yenileniyor...' : 'Yenile'}
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.modalTitle} style={{ marginTop: 8 }}>
        Tüm Kayıtlar {requests.length > 0 && `(${requests.length})`}
        {pendingCount > 0 && <span className={styles.muted}> — {pendingCount} bekliyor</span>}
      </div>
      <div className={styles.tableCard}>
        <div className={[styles.tableHead, leadsStyles.leadRow].join(' ')}>
          <div>Klinik</div>
          <div>Yetkili</div>
          <div>İletişim</div>
          <div>Plan</div>
          <div>Durum</div>
          <div>Kayıt Tarihi</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : requests.length === 0 ? (
          <div className={styles.empty}>Henüz kayıt yok</div>
        ) : (
          requests.map((r) => {
            const stale = r.status === 'PENDING' && daysSince(r.createdAt) >= 2;
            return (
              <div key={r.id} className={[styles.row, leadsStyles.leadRow].join(' ')} style={{ cursor: 'default' }}>
                <div>{r.clinicName}</div>
                <div className={styles.muted}>{r.adminFullName}</div>
                <div className={leadsStyles.contact}>
                  <span>{r.adminEmail}</span>
                  {r.phone && <span className={leadsStyles.contactPhone}>{r.phone}</span>}
                </div>
                <div className={styles.muted}>{r.planCode}</div>
                <div>
                  <Badge tone={r.status === 'COMPLETED' ? 'success' : stale ? 'danger' : 'warning'}>
                    {r.status === 'COMPLETED' ? 'Tamamlandı' : 'Bekliyor'}
                  </Badge>
                </div>
                <div className={styles.muted}>{formatDateTime(r.createdAt)}</div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
