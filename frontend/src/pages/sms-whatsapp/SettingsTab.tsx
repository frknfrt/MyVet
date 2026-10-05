import { useEffect, useState } from 'react';
import { notificationApi, NotificationChannel, NotificationStatus, NotificationTemplate, NotificationType } from '../../api/notificationApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Select, Textarea } from '../../components/ui/Field';
import { notificationTypeLabel } from '../settings/notificationStatus';
import styles from './SettingsTab.module.css';

// Onizlemede placeholder'lari gercege yakin gorunsun diye kullanilan ornek
// degerler -- sadece ekranda gosterilir, hicbir yere kaydedilmez.
const SAMPLE_VALUES: Record<string, string> = {
  sahipAdi: 'Ayşe Yılmaz',
  hastaAdi: 'Boncuk',
  tarihSaat: '02.10.2026 11:00',
  tarih: '02.10.2026',
  asiAdi: 'Kuduz',
};

function preview(template: string, placeholders: string[]): string {
  let result = template;
  for (const key of placeholders) {
    result = result.split(`{${key}}`).join(SAMPLE_VALUES[key] ?? `{${key}}`);
  }
  return result;
}

// Her kanalin (SMS: Ileti Merkezi, WhatsApp: Meta WhatsApp Cloud API) gercekten baglanip
// baglanmadigi birbirinden bagimsiz -- daha once burada tek bir "connected"
// bayragi vardi ve metin sabit kodlanmisti ("WhatsApp gercek, SMS mock"),
// bu yuzden SMS gercekten baglandiktan sonra bile ekran hala "simule
// ediliyor" diyordu. Artik ikisi ayri ayri gosteriliyor.
export function SettingsTab() {
  const [status, setStatus] = useState<NotificationStatus | null>(null);
  const [appointmentChannel, setAppointmentChannel] = useState<NotificationChannel | null>(null);
  const [savingChannel, setSavingChannel] = useState(false);

  const [templates, setTemplates] = useState<NotificationTemplate[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [savingType, setSavingType] = useState<NotificationType | null>(null);

  useEffect(() => {
    notificationApi.status().then(setStatus);
    notificationApi.settings().then((s) => setAppointmentChannel(s.appointmentChannel));
    loadTemplates();
  }, []);

  function loadTemplates() {
    notificationApi.templates().then((list) => {
      setTemplates(list);
      setDrafts(Object.fromEntries(list.map((t) => [t.notificationType, t.templateText])));
    });
  }

  async function handleChannelChange(next: NotificationChannel) {
    setAppointmentChannel(next);
    setSavingChannel(true);
    try {
      const saved = await notificationApi.updateSettings(next);
      setAppointmentChannel(saved.appointmentChannel);
    } finally {
      setSavingChannel(false);
    }
  }

  async function handleSaveTemplate(type: NotificationType) {
    setSavingType(type);
    try {
      const updated = await notificationApi.updateTemplate(type, drafts[type] ?? '');
      setTemplates(updated);
      setDrafts(Object.fromEntries(updated.map((t) => [t.notificationType, t.templateText])));
    } finally {
      setSavingType(null);
    }
  }

  async function handleResetTemplate(type: NotificationType) {
    setSavingType(type);
    try {
      const updated = await notificationApi.updateTemplate(type, '');
      setTemplates(updated);
      setDrafts(Object.fromEntries(updated.map((t) => [t.notificationType, t.templateText])));
    } finally {
      setSavingType(null);
    }
  }

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <div>
          <div className={styles.name}>SMS / WhatsApp Sağlayıcı Durumu</div>
          <div className={styles.desc}>
            {status && (
              <>
                WhatsApp {status.whatsappConfigured ? 'Meta WhatsApp Cloud API üzerinden gerçek gönderim yapıyor.' : 'henüz sağlayıcıya bağlı değil, simüle ediliyor.'}{' '}
                SMS {status.smsConfigured ? 'İleti Merkezi üzerinden gerçek gönderim yapıyor.' : 'henüz sağlayıcıya bağlı değil, simüle ediliyor.'}
              </>
            )}
          </div>
        </div>
        {status ? (
          <div className={styles.badgeGroup}>
            <Badge tone={status.smsConfigured ? 'success' : 'warning'}>SMS: {status.smsConfigured ? 'İleti Merkezi (gerçek)' : 'Mock'}</Badge>
            <Badge tone={status.whatsappConfigured ? 'success' : 'warning'}>WhatsApp: {status.whatsappConfigured ? 'Meta (gerçek)' : 'Mock'}</Badge>
          </div>
        ) : (
          <Badge tone="neutral">Yükleniyor...</Badge>
        )}
      </div>

      {status && (
        <>
          <div className={styles.statsRow}>
            <div className={styles.statCard}>
              <div className={styles.statLabel}>Bekleyen</div>
              <div className={styles.statValue}>{status.pendingCount}</div>
            </div>
            <div className={styles.statCard}>
              <div className={styles.statLabel}>Gönderildi</div>
              <div className={styles.statValue}>{status.sentCount}</div>
            </div>
            <div className={styles.statCard}>
              <div className={styles.statLabel}>Başarısız</div>
              <div className={styles.statValue}>{status.failedCount}</div>
            </div>
          </div>
          <div className={styles.lastSynced}>
            Son gönderim: {status.lastSentAt ? new Date(status.lastSentAt).toLocaleString('tr-TR') : 'Henüz yok'}
          </div>
        </>
      )}

      <div className={styles.channelPreference}>
        <FieldWrap label="Randevu onayı ve hatırlatma mesajları hangi kanaldan gönderilsin?">
          <Select
            value={appointmentChannel ?? 'SMS'}
            disabled={appointmentChannel === null || savingChannel}
            onChange={(e) => handleChannelChange(e.target.value as NotificationChannel)}
          >
            <option value="SMS">SMS</option>
            <option value="WHATSAPP">WhatsApp</option>
          </Select>
        </FieldWrap>
        <div className={styles.desc}>
          WhatsApp seçilse bile, WhatsApp iznini (izinler/onay sekmesinden) vermemiş sahiplere yine SMS gönderilir. WhatsApp'ta
          mesajınızın önüne/sonuna Meta'nın onayladığı "Merhaba, ... Bilgilerinize sunarız." kalıbı otomatik eklenir, bu
          aşağıdaki metne dahil değildir.
        </div>
      </div>

      <div className={styles.templatesSection}>
        <div className={styles.sectionTitle}>
          Otomatik Mesaj Şablonları
        </div>
        <div className={styles.sectionDesc}>
          Randevu ve aşı hatırlatmalarında otomatik gönderilen mesajın metnini buradan düzenleyebilirsiniz. Süslü parantez
          içindeki değişkenler ({'{sahipAdi}'} gibi) gönderim anında gerçek bilgiyle değiştirilir.
        </div>
        {templates.length === 0 ? (
          <div className={styles.desc}>Yükleniyor...</div>
        ) : (
          templates.map((t) => (
            <div key={t.notificationType} className={styles.templateRow}>
              <div className={styles.templateRowHeader}>
                <span className={styles.templateRowTitle}>{notificationTypeLabel(t.notificationType)}</span>
                <Badge tone={t.customized ? 'success' : 'neutral'}>{t.customized ? 'Özelleştirildi' : 'Varsayılan'}</Badge>
              </div>
              <Textarea
                rows={3}
                value={drafts[t.notificationType] ?? ''}
                onChange={(e) => setDrafts((d) => ({ ...d, [t.notificationType]: e.target.value }))}
              />
              <div className={styles.templatePlaceholders}>
                Değişkenler: {t.placeholders.map((p) => `{${p}}`).join(', ')}
              </div>
              <div className={styles.templatePreview}>
                Örnek: {preview(drafts[t.notificationType] ?? t.templateText, t.placeholders)}
              </div>
              <div className={styles.templateActions}>
                <Button variant="secondary" onClick={() => handleResetTemplate(t.notificationType)} disabled={savingType === t.notificationType}>
                  Varsayılana Dön
                </Button>
                <Button variant="primary" onClick={() => handleSaveTemplate(t.notificationType)} disabled={savingType === t.notificationType}>
                  {savingType === t.notificationType ? 'Kaydediliyor...' : 'Kaydet'}
                </Button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
