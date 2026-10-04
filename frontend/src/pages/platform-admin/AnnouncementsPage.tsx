import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { Announcement, platformAdminApi } from '../../api/platformAdminApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Textarea } from '../../components/ui/Field';
import styles from './PlatformAdminPages.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('tr-TR');
}

export function AnnouncementsPage() {
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [sending, setSending] = useState(false);

  function load() {
    setLoading(true);
    platformAdminApi
      .listAnnouncements()
      .then(setAnnouncements)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!window.confirm('Bu duyuru, askıya alınmamış TÜM kiracıların faturalama iletişim e-postasına gönderilecek. Onaylıyor musun?')) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      await platformAdminApi.sendAnnouncement({ title, body });
      setTitle('');
      setBody('');
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSending(false);
    }
  }

  return (
    <div>
      <div className={styles.title}>Duyurular</div>
      <p className={styles.note}>
        Burada yazdığın duyuru, askıya alınmamış tüm kiracıların faturalama iletişim e-postasına gönderilir (bakım,
        yeni özellik, fiyat değişikliği gibi). Faturalama iletişim e-postası olmayan kiracılar atlanır. Gönderim bu
        ortamda e-posta sağlayıcısı henüz bağlı olmadığı için şimdilik sunucu loglarına yazılıyor (mock) — alt
        yapıda e-posta servisi bağlandığında otomatik olarak gerçek gönderime geçecek.
      </p>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.card} style={{ maxWidth: 560, marginBottom: 24 }}>
        <form onSubmit={handleSubmit}>
          <FieldWrap label="Başlık">
            <Input value={title} onChange={(e) => setTitle(e.target.value)} required />
          </FieldWrap>
          <FieldWrap label="İçerik">
            <Textarea value={body} onChange={(e) => setBody(e.target.value)} rows={5} required />
          </FieldWrap>
          <div className={styles.modalActions}>
            <Button type="submit" variant="primary" disabled={sending}>
              {sending ? 'Gönderiliyor...' : 'Duyuruyu Gönder'}
            </Button>
          </div>
        </form>
      </div>

      <div className={styles.tableCard}>
        <div className={[styles.tableHead, styles.announcementRow].join(' ')}>
          <div>Tarih</div>
          <div>Başlık</div>
          <div>Gönderen</div>
          <div>Alıcı Sayısı</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : announcements.length === 0 ? (
          <div className={styles.empty}>Henüz duyuru gönderilmedi</div>
        ) : (
          announcements.map((a) => (
            <div key={a.id} className={[styles.row, styles.announcementRow].join(' ')} style={{ cursor: 'default' }} title={a.body}>
              <div className={styles.muted}>{formatDateTime(a.createdAt)}</div>
              <div>{a.title}</div>
              <div className={styles.muted}>{a.createdByAdminEmail}</div>
              <div className={styles.muted}>{a.recipientCount}</div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
