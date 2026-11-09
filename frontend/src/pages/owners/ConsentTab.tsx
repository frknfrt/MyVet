import { useEffect, useState } from 'react';
import { formsApi, type NoticeDelivery } from '../../api/formsApi';
import { PrivacyNoticeLink } from '../../forms/PrivacyNoticeLink';
import { useAuth } from '../../auth/AuthContext';
import { ApiError } from '../../api/client';
import { ConsentRecordItem, ConsentType, patientApi } from '../../api/patientApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import settingsStyles from '../settings/SettingsPage.module.css';
import styles from './ConsentTab.module.css';

const CONSENT_TYPES: { type: ConsentType; label: string; description: string }[] = [
  {
    type: 'KVKK_ACIK_RIZA',
    label: 'Geçmiş KVKK Açık Rıza',
    description: 'Önceki akıştan kalan kayıtlar. Yeni bilgilendirme beyanı imzalı formda saklanır; bu kayıtlar kendiliğinden dönüştürülmez.',
  },
  {
    type: 'PAZARLAMA',
    label: 'Pazarlama',
    description: 'Kampanya/tanıtım amaçlı iletişim için ayrı onay.',
  },
  {
    type: 'VERI_AKTARIMI',
    label: 'Veri Aktarımı',
    description: 'Kişisel verilerin üçüncü taraflara (örn. laboratuvar) aktarılması için onay.',
  },
];

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function latestFor(records: ConsentRecordItem[], type: ConsentType): ConsentRecordItem | null {
  const matching = records.filter((r) => r.consentType === type);
  if (matching.length === 0) return null;
  return matching.reduce((latest, r) => (r.grantedAt > latest.grantedAt ? r : latest));
}

function isCurrentlyGranted(record: ConsentRecordItem | null): boolean {
  return !!record && record.granted && !record.revokedAt;
}

interface ConsentTabProps {
  ownerId: string;
}

export function ConsentTab({ ownerId }: ConsentTabProps) {
  const { session } = useAuth();
  // OwnersController /owners/{id}/consents yazma -- VET/RECEPTIONIST/ADMIN.
  const canWrite = session ? ['VET', 'RECEPTIONIST', 'ADMIN'].includes(session.role) : false;
  const [records, setRecords] = useState<ConsentRecordItem[] | null>(null);
  const [busyType, setBusyType] = useState<ConsentType | null>(null);
  const [deliveries,setDeliveries] = useState<NoticeDelivery[]>([]);
  const [deliveryError,setDeliveryError] = useState<string|null>(null);
  useEffect(() => {let active=true;formsApi.noticeDeliveries(ownerId).then(d => {if(active)setDeliveries(d);})
    .catch(() => {if(active)setDeliveryError('Aydınlatma sunum kayıtları yüklenemedi.');});return () => {active=false;};},[ownerId]);
  const [error, setError] = useState<string | null>(null);

  function load() {
    patientApi
      .listConsents(ownerId)
      .then(setRecords)
      .catch((err) => setError(errorMessageOf(err)));
  }

  useEffect(load, [ownerId]);

  async function handleGrant(type: ConsentType) {
    setBusyType(type);
    setError(null);
    try {
      await patientApi.recordConsent(ownerId, type);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusyType(null);
    }
  }

  async function handleRevoke(record: ConsentRecordItem) {
    setBusyType(record.consentType);
    setError(null);
    try {
      await patientApi.revokeConsent(ownerId, record.id);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusyType(null);
    }
  }

  if (records === null) {
    return <div className={styles.empty}>Yükleniyor...</div>;
  }

  const history = [...records].sort((a, b) => (a.grantedAt < b.grantedAt ? 1 : -1));

  return (
    <div>
      {error && <div className={settingsStyles.errorBanner}>{error}</div>}

      <section>
        <h3>Aydınlatma metni sunum kayıtları</h3>
        <p>Bu kayıtlar klinik çalışanının metni sunduğuna dair beyanıdır; hasta sahibinin imzası veya açık rızası değildir. İmzalı beyanlar Formlar sekmesindedir.</p>
        {deliveryError && <p role="alert">{deliveryError}</p>}
        {deliveries.map(d => <div key={d.id}>
          <p>{new Date(d.recordedAt).toLocaleString('tr-TR')} · Kaydeden kullanıcı: {d.recordedBy}</p>
          <PrivacyNoticeLink text={d.text} version={d.version}/>
        </div>)}
      </section>
      <div className={styles.grid}>
        {CONSENT_TYPES.map(({ type, label, description }) => {
          const latest = latestFor(records, type);
          const granted = isCurrentlyGranted(latest);
          return (
            <div key={type} className={styles.card}>
              <div className={styles.cardTitle}>{label}</div>
              <Badge tone={granted ? 'success' : latest ? 'warning' : 'neutral'}>
                {granted ? 'Verildi' : latest ? 'Geri Çekildi' : 'Verilmedi'}
              </Badge>
              <div className={styles.cardMeta}>
                {description}
                {latest && (
                  <>
                    <br />
                    {granted
                      ? `Verildi: ${new Date(latest.grantedAt).toLocaleString('tr-TR')}`
                      : `Geri çekildi: ${new Date(latest.revokedAt!).toLocaleString('tr-TR')}`}
                  </>
                )}
              </div>
              {canWrite &&
                (granted ? (
                  <Button variant="danger" onClick={() => handleRevoke(latest!)} disabled={busyType === type}>
                    Geri Çek
                  </Button>
                ) : type !== 'KVKK_ACIK_RIZA' ? (
                  <Button variant="secondary" onClick={() => handleGrant(type)} disabled={busyType === type}>
                    Onay Al
                  </Button>
                ) : null)}
            </div>
          );
        })}
      </div>

      <div className={styles.historyCard}>
        <div className={styles.historyHead}>
          <div>Onay Türü</div>
          <div>Durum</div>
          <div>Verildiği Tarih</div>
          <div>Geri Çekildiği Tarih</div>
          <div>IP</div>
        </div>
        {history.length === 0 ? (
          <div className={styles.empty}>Henüz bir KVKK kaydı yok</div>
        ) : (
          history.map((r) => (
            <div key={r.id} className={styles.historyRow}>
              <div>{CONSENT_TYPES.find((c) => c.type === r.consentType)?.label ?? r.consentType}</div>
              <div>
                <Badge tone={r.granted && !r.revokedAt ? 'success' : 'neutral'}>
                  {r.granted && !r.revokedAt ? 'Aktif' : 'Geri Çekildi'}
                </Badge>
              </div>
              <div className={styles.muted}>{new Date(r.grantedAt).toLocaleString('tr-TR')}</div>
              <div className={styles.muted}>{r.revokedAt ? new Date(r.revokedAt).toLocaleString('tr-TR') : '—'}</div>
              <div className={styles.muted}>{r.ipAddress ?? '—'}</div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
