import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  EInvoiceDocumentType, FailedEInvoice, FailedNotification, FailedTarbilSync, NotificationChannel, NotificationType,
  platformAdminApi, TarbilSyncType,
} from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import styles from './PlatformAdminPages.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatDateTime(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleString('tr-TR');
}

const CHANNEL_LABELS: Record<NotificationChannel, string> = {
  SMS: 'SMS',
  WHATSAPP: 'WhatsApp',
};

const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  APPOINTMENT_CONFIRMATION: 'Randevu Onayı',
  APPOINTMENT_REMINDER: 'Randevu Hatırlatma',
  CAMPAIGN_MESSAGE: 'Kampanya',
  VACCINATION_REMINDER: 'Aşı Hatırlatma',
};

const DOCUMENT_TYPE_LABELS: Record<EInvoiceDocumentType, string> = {
  E_FATURA: 'e-Fatura',
  E_ARSIV: 'e-Arşiv',
};

const TARBIL_SYNC_TYPE_LABELS: Record<TarbilSyncType, string> = {
  VACCINATION: 'Aşı Kaydı',
  PRESCRIPTION: 'Reçete',
  STOCK_RECEIPT: 'Mal Kabul',
  IDENTIFICATION: 'Kimliklendirme',
  TREATMENT: 'Tedavi Kaydı',
};

export function SystemHealthPage() {
  const [notifications, setNotifications] = useState<FailedNotification[]>([]);
  const [eInvoices, setEInvoices] = useState<FailedEInvoice[]>([]);
  const [tarbilSyncs, setTarbilSyncs] = useState<FailedTarbilSync[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryingIds, setRetryingIds] = useState<Set<string>>(new Set());

  function load() {
    setLoading(true);
    setError(null);
    Promise.all([
      platformAdminApi.listFailedNotifications(), platformAdminApi.listFailedEInvoices(), platformAdminApi.listFailedTarbilSyncs(),
    ])
      .then(([n, e, t]) => {
        setNotifications(n);
        setEInvoices(e);
        setTarbilSyncs(t);
      })
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function withRetrying(id: string, run: () => Promise<void>) {
    setError(null);
    setRetryingIds((prev) => new Set(prev).add(id));
    run()
      .then(load)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => {
        setRetryingIds((prev) => {
          const next = new Set(prev);
          next.delete(id);
          return next;
        });
      });
  }

  function handleRetryNotification(id: string) {
    withRetrying(id, () => platformAdminApi.retryFailedNotification(id));
  }

  function handleRetryEInvoice(id: string) {
    withRetrying(id, () => platformAdminApi.retryFailedEInvoice(id));
  }

  return (
    <div>
      <div className={styles.title}>Sistem Sağlığı</div>
      <p className={styles.note}>
        Tüm kiracılardaki, otomatik yeniden deneme hakları tükenmiş veya henüz yeniden denenmeyi bekleyen başarısız
        SMS/WhatsApp, e-Fatura ve TARBIL senkronizasyon gönderimleri. "Son Deneme Tarihi" sütunu, sorunun ne zamandır
        sürdüğünü anlamak için önemlidir. "Tekrar Dene" sorunu (örn. yanlış numara) düzelttikten sonra kullanılmalı —
        aksi halde aynı hata tekrar oluşur.
      </p>

      <div className={styles.actionsRow}>
        <Button variant="secondary" onClick={load} disabled={loading}>
          {loading ? 'Yenileniyor...' : 'Yenile'}
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.modalTitle} style={{ marginTop: 24 }}>
        Başarısız Bildirimler (SMS / WhatsApp) {notifications.length > 0 && `(${notifications.length})`}
      </div>
      <div className={styles.tableCard}>
        <div className={[styles.tableHead, styles.healthNotifRow].join(' ')}>
          <div>Klinik</div>
          <div>Kanal</div>
          <div>Tür</div>
          <div>Alıcı</div>
          <div>Durum</div>
          <div></div>
          <div>Kuyruğa Giriş</div>
          <div></div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : notifications.length === 0 ? (
          <div className={styles.empty}>Başarısız bildirim yok 🎉</div>
        ) : (
          notifications.map((n) => (
            <div key={n.notificationLogId} className={[styles.row, styles.healthNotifRow].join(' ')}>
              <div>{n.tenantName}</div>
              <div>
                <Badge tone={n.channel === 'WHATSAPP' ? 'success' : 'neutral'}>{CHANNEL_LABELS[n.channel]}</Badge>
              </div>
              <div className={styles.muted}>{NOTIFICATION_TYPE_LABELS[n.notificationType]}</div>
              <div className={styles.muted}>{n.recipientLabel ?? n.recipientContact}</div>
              <div className={styles.muted} title={n.failureReason ?? ''}>
                {n.failureReason ? (n.failureReason.length > 60 ? `${n.failureReason.slice(0, 60)}…` : n.failureReason) : '—'}
              </div>
              <div>
                <Badge tone={n.nextRetryAt ? 'warning' : 'danger'}>{n.attemptCount}</Badge>
              </div>
              <div className={styles.muted}>{formatDateTime(n.attemptedAt)}</div>
              <div>
                <Button
                  variant="secondary"
                  onClick={() => handleRetryNotification(n.notificationLogId)}
                  disabled={retryingIds.has(n.notificationLogId)}
                >
                  {retryingIds.has(n.notificationLogId) ? 'Deneniyor...' : 'Tekrar Dene'}
                </Button>
              </div>
            </div>
          ))
        )}
      </div>

      <div className={styles.modalTitle} style={{ marginTop: 24 }}>
        Başarısız e-Fatura Gönderimleri {eInvoices.length > 0 && `(${eInvoices.length})`}
      </div>
      <div className={styles.tableCard}>
        <div className={[styles.tableHead, styles.healthInvoiceRow].join(' ')}>
          <div>Klinik</div>
          <div>Belge Türü</div>
          <div>Tutar</div>
          <div>Hata</div>
          <div>Deneme</div>
          <div>Son Deneme</div>
          <div></div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : eInvoices.length === 0 ? (
          <div className={styles.empty}>Başarısız e-Fatura gönderimi yok 🎉</div>
        ) : (
          eInvoices.map((inv) => (
            <div key={inv.submissionId} className={[styles.row, styles.healthInvoiceRow].join(' ')}>
              <div>{inv.tenantName}</div>
              <div className={styles.muted}>{DOCUMENT_TYPE_LABELS[inv.documentType]}</div>
              <div className={styles.muted}>{inv.totalAmount.toLocaleString('tr-TR')} ₺</div>
              <div className={styles.muted} title={inv.failureReason ?? ''}>
                {inv.failureReason ? (inv.failureReason.length > 60 ? `${inv.failureReason.slice(0, 60)}…` : inv.failureReason) : '—'}
              </div>
              <div>
                <Badge tone="danger">{inv.attemptCount}</Badge>
              </div>
              <div className={styles.muted}>{formatDateTime(inv.attemptedAt)}</div>
              <div>
                <Button
                  variant="secondary"
                  onClick={() => handleRetryEInvoice(inv.submissionId)}
                  disabled={retryingIds.has(inv.submissionId)}
                >
                  {retryingIds.has(inv.submissionId) ? 'Deneniyor...' : 'Tekrar Dene'}
                </Button>
              </div>
            </div>
          ))
        )}
      </div>

      <div className={styles.modalTitle} style={{ marginTop: 24 }}>
        Takılmış TARBİL Aktarımları {tarbilSyncs.length > 0 && `(${tarbilSyncs.length})`}
      </div>
      <div className={styles.tableCard}>
        <div className={[styles.tableHead, styles.healthInvoiceRow].join(' ')}>
          <div>Klinik</div>
          <div>Hasta</div>
          <div>Kayıt Türü</div>
          <div>Hata</div>
          <div>Deneme</div>
          <div>Son Deneme</div>
          <div></div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : tarbilSyncs.length === 0 ? (
          <div className={styles.empty}>3 günden uzun süredir bekleyen TARBİL aktarımı yok 🎉</div>
        ) : (
          tarbilSyncs.map((sync) => (
            <div key={sync.syncLogId} className={[styles.row, styles.healthInvoiceRow].join(' ')}>
              <div>{sync.tenantName}</div>
              <div className={styles.muted}>{sync.patientName}</div>
              <div className={styles.muted}>{TARBIL_SYNC_TYPE_LABELS[sync.syncType]}</div>
              <div className={styles.muted} title={sync.failureReason ?? ''}>
                {sync.failureReason
                  ? (sync.failureReason.length > 60 ? `${sync.failureReason.slice(0, 60)}…` : sync.failureReason)
                  : '—'}
              </div>
              <div></div>
              <div className={styles.muted}>{formatDateTime(sync.attemptedAt)}</div>
              {/* Eklenti modeli: TARBIL'e gonderimi klinikteki eklenti hekimle yapar; sunucudan yeniden deneme yok. */}
              <div></div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
