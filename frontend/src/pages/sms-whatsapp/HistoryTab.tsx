import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  NotificationChannel,
  NotificationLog,
  NotificationLogStatus,
  NotificationType,
  notificationApi,
} from '../../api/notificationApi';
import { Button } from '../../components/ui/Button';
import { NotificationStatusBadge, notificationChannelLabel, notificationTypeLabel } from '../settings/notificationStatus';
import styles from './SmsWhatsappPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const CHANNEL_OPTIONS: NotificationChannel[] = ['SMS', 'WHATSAPP'];
const STATUS_OPTIONS: NotificationLogStatus[] = ['PENDING', 'SENT', 'FAILED'];
const TYPE_OPTIONS: NotificationType[] = ['APPOINTMENT_CONFIRMATION', 'APPOINTMENT_REMINDER', 'CAMPAIGN_MESSAGE'];

export function HistoryTab() {
  const [logs, setLogs] = useState<NotificationLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [channel, setChannel] = useState<NotificationChannel | ''>('');
  const [status, setStatus] = useState<NotificationLogStatus | ''>('');
  const [notificationType, setNotificationType] = useState<NotificationType | ''>('');
  const [search, setSearch] = useState('');

  function load() {
    setLoading(true);
    setError(null);
    notificationApi
      .logs({
        channel: channel || undefined,
        status: status || undefined,
        notificationType: notificationType || undefined,
        search: search || undefined,
      })
      .then(setLogs)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleRetry(id: string) {
    setBusyId(id);
    try {
      await notificationApi.retry(id);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <div className={styles.filterBar}>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Kanal</label>
          <select className={styles.filterInput} value={channel} onChange={(e) => setChannel(e.target.value as NotificationChannel | '')}>
            <option value="">Tümü</option>
            {CHANNEL_OPTIONS.map((c) => (
              <option key={c} value={c}>
                {notificationChannelLabel(c)}
              </option>
            ))}
          </select>
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Durum</label>
          <select className={styles.filterInput} value={status} onChange={(e) => setStatus(e.target.value as NotificationLogStatus | '')}>
            <option value="">Tümü</option>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s === 'PENDING' ? 'Bekliyor' : s === 'SENT' ? 'Gönderildi' : 'Başarısız'}
              </option>
            ))}
          </select>
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Tür</label>
          <select
            className={styles.filterInput}
            value={notificationType}
            onChange={(e) => setNotificationType(e.target.value as NotificationType | '')}
          >
            <option value="">Tümü</option>
            {TYPE_OPTIONS.map((t) => (
              <option key={t} value={t}>
                {notificationTypeLabel(t)}
              </option>
            ))}
          </select>
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Mesaj içerisinde arama</label>
          <input className={styles.filterInput} type="text" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <Button variant="primary" onClick={load}>
          Filtrele
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Alıcı</div>
          <div>Kanal</div>
          <div>Tür</div>
          <div>Mesaj</div>
          <div>Durum / Zaman</div>
          <div></div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : logs.length === 0 ? (
          <div className={styles.empty}>Seçilen filtrelerde kayıt bulunmuyor</div>
        ) : (
          logs.map((log) => (
            <div key={log.id} className={styles.row}>
              <div>{log.ownerName}</div>
              <div className={styles.muted}>{notificationChannelLabel(log.channel)}</div>
              <div className={styles.muted}>{notificationTypeLabel(log.notificationType)}</div>
              <div className={styles.muted}>{log.message}</div>
              <div>
                <NotificationStatusBadge status={log.status} />
                <div className={styles.muted}>{new Date(log.attemptedAt).toLocaleString('tr-TR')}</div>
              </div>
              <div>
                {log.status === 'FAILED' && (
                  <Button variant="secondary" onClick={() => handleRetry(log.id)} disabled={busyId === log.id}>
                    {busyId === log.id ? '...' : 'Tekrar Dene'}
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
