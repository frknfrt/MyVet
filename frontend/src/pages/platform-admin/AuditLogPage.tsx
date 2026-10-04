import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { AuditLogEntry, platformAdminApi } from '../../api/platformAdminApi';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import styles from './PlatformAdminPages.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('tr-TR');
}

const ACTION_LABELS: Record<string, string> = {
  TENANT_IMPERSONATED: 'Kiracı Olarak Giriş',
  TENANT_CREATED: 'Kiracı Oluşturuldu',
  TENANT_SUSPENDED: 'Kiracı Askıya Alındı',
  TENANT_ACTIVATED: 'Kiracı Aktif Edildi',
  TENANT_SUBSCRIPTION_UPDATED: 'Abonelik Güncellendi',
  PLAN_CREATED: 'Plan Oluşturuldu',
  PLAN_UPDATED: 'Plan Güncellendi',
  PLAN_DELETED: 'Plan Silindi',
  COUPON_CREATED: 'Kupon Oluşturuldu',
  COUPON_ACTIVATED: 'Kupon Aktif Edildi',
  COUPON_DEACTIVATED: 'Kupon Pasife Alındı',
  INVOICE_VOIDED: 'Fatura İptal Edildi',
  PAYMENT_RECORDED: 'Ödeme Kaydedildi',
};

const ACTION_TONES: Record<string, BadgeTone> = {
  TENANT_IMPERSONATED: 'warning',
  TENANT_SUSPENDED: 'danger',
  PLAN_DELETED: 'danger',
  INVOICE_VOIDED: 'danger',
  COUPON_DEACTIVATED: 'neutral',
  TENANT_ACTIVATED: 'success',
  COUPON_ACTIVATED: 'success',
  PAYMENT_RECORDED: 'success',
};

const TARGET_TYPE_LABELS: Record<string, string> = {
  TENANT: 'Kiracı',
  PLAN: 'Plan',
  COUPON: 'Kupon',
  INVOICE: 'Fatura',
};

export function AuditLogPage() {
  const [entries, setEntries] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    platformAdminApi
      .listAuditLog()
      .then(setEntries)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  return (
    <div>
      <div className={styles.title}>Denetim Kaydı</div>
      <p className={styles.note}>
        Platform adminlerin yaptığı hassas işlemlerin kalıcı kaydı — kiracı olarak giriş (impersonate), askıya
        alma/aktif etme, plan ve kupon değişiklikleri, manuel ödeme ve fatura iptali. En son 200 işlem, en yeniden
        eskiye sıralı olarak gösterilir. Otomatik/sistem işlemleri (ör. gecikmeden kaynaklı otomatik askıya alma)
        burada görünmez — sadece bir platform adminin bilerek tetikledikleri.
      </p>

      <div className={styles.actionsRow}>
        <Button variant="secondary" onClick={load} disabled={loading}>
          {loading ? 'Yenileniyor...' : 'Yenile'}
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.tableCard}>
        <div className={[styles.tableHead, styles.auditRow].join(' ')}>
          <div>Tarih</div>
          <div>Platform Admin</div>
          <div>İşlem</div>
          <div>Hedef</div>
          <div>Detay</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : entries.length === 0 ? (
          <div className={styles.empty}>Henüz bir denetim kaydı yok</div>
        ) : (
          entries.map((entry) => (
            <div key={entry.id} className={[styles.row, styles.auditRow].join(' ')} style={{ cursor: 'default' }}>
              <div className={styles.muted}>{formatDateTime(entry.createdAt)}</div>
              <div className={styles.muted}>{entry.platformAdminEmail}</div>
              <div>
                <Badge tone={ACTION_TONES[entry.action] ?? 'neutral'}>{ACTION_LABELS[entry.action] ?? entry.action}</Badge>
              </div>
              <div className={styles.muted}>
                {TARGET_TYPE_LABELS[entry.targetType] ?? entry.targetType}
                {entry.targetId && (
                  <span title={entry.targetId}> ({entry.targetId.slice(0, 8)}…)</span>
                )}
              </div>
              <div className={styles.muted} title={entry.details ?? ''}>
                {entry.details ?? '—'}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
