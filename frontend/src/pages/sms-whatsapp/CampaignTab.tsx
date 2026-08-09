import { useEffect, useMemo, useRef, useState } from 'react';
import { authApi, BranchOverview } from '../../api/authApi';
import { billingApi } from '../../api/billingApi';
import {
  AppointmentCampaignCandidate,
  CampaignRecipientInput,
  CampaignSendSummary,
  MessageTemplate,
  OwnerCampaignCandidate,
  VaccinationCampaignCandidate,
  campaignApi,
  recipientCandidatesApi,
  templateApi,
} from '../../api/campaignApi';
import { ApiError } from '../../api/client';
import { NotificationChannel } from '../../api/notificationApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';
import styles from './SmsWhatsappPage.module.css';
import campaignStyles from './CampaignTab.module.css';

type Source = 'custom' | 'owners' | 'vaccinations' | 'appointments' | 'debtors';

const VARIABLE_CHIPS: { token: string; label: string }[] = [
  { token: 'musteri_adi', label: 'Müşteri adı' },
  { token: 'musteri_bakiye', label: 'Müşteri bakiye' },
  { token: 'firma_adi', label: 'Firma adı' },
  { token: 'firma_adres', label: 'Firma adresi' },
];

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

function parseCustomNumbers(raw: string): CampaignRecipientInput[] {
  return raw
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const parts = line.split(',').map((p) => p.trim());
      const label = parts.length > 1 ? parts[0] : null;
      const phone = parts.length > 1 ? parts[1] : parts[0];
      return {
        ownerId: null,
        phone,
        smsConsent: true,
        whatsappConsent: true,
        variables: { musteri_adi: label ?? phone, musteri_bakiye: '' },
        label,
      };
    });
}

export function CampaignTab() {
  const [source, setSource] = useState<Source>('owners');
  const [channel, setChannel] = useState<NotificationChannel>('SMS');

  const [ownerName, setOwnerName] = useState('');
  const [ownerFrom, setOwnerFrom] = useState('');
  const [ownerTo, setOwnerTo] = useState('');
  const [dueFrom, setDueFrom] = useState(isoDate(new Date()));
  const [dueTo, setDueTo] = useState(isoDate(new Date(Date.now() + 14 * 86400000)));
  const [apptFrom, setApptFrom] = useState(isoDate(new Date()));
  const [apptTo, setApptTo] = useState(isoDate(new Date(Date.now() + 7 * 86400000)));
  const [customNumbers, setCustomNumbers] = useState('');

  const [recipients, setRecipients] = useState<CampaignRecipientInput[] | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);

  const [templates, setTemplates] = useState<MessageTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [messageBody, setMessageBody] = useState('');
  const [branch, setBranch] = useState<BranchOverview | null>(null);

  const [sending, setSending] = useState(false);
  const [sendResult, setSendResult] = useState<CampaignSendSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    templateApi.list().then(setTemplates);
    authApi.getCurrentBranch().then(setBranch);
  }, []);

  const relevantTemplates = useMemo(
    () => templates.filter((t) => t.channel === channel || t.channel === 'BOTH'),
    [templates, channel]
  );

  function handleTemplateSelect(id: string) {
    setSelectedTemplateId(id);
    const template = templates.find((t) => t.id === id);
    if (template) setMessageBody(template.body);
  }

  function insertToken(token: string) {
    const el = textareaRef.current;
    const insertion = `{${token}}`;
    if (!el) {
      setMessageBody((prev) => prev + insertion);
      return;
    }
    const start = el.selectionStart ?? messageBody.length;
    const end = el.selectionEnd ?? messageBody.length;
    const next = messageBody.slice(0, start) + insertion + messageBody.slice(end);
    setMessageBody(next);
    requestAnimationFrame(() => {
      el.focus();
      const pos = start + insertion.length;
      el.setSelectionRange(pos, pos);
    });
  }

  async function handlePreview() {
    setLoadingPreview(true);
    setError(null);
    setSendResult(null);
    try {
      let result: CampaignRecipientInput[] = [];
      if (source === 'custom') {
        result = parseCustomNumbers(customNumbers);
      } else if (source === 'owners') {
        const candidates = await recipientCandidatesApi.owners({
          name: ownerName || undefined,
          registeredFrom: ownerFrom || undefined,
          registeredTo: ownerTo || undefined,
        });
        result = candidates.map((c: OwnerCampaignCandidate) => ({
          ownerId: c.id,
          phone: c.phone,
          smsConsent: c.smsConsent,
          whatsappConsent: c.whatsappConsent,
          variables: { musteri_adi: c.fullName, musteri_bakiye: '' },
          label: c.fullName,
        }));
      } else if (source === 'vaccinations') {
        const candidates = await recipientCandidatesApi.vaccinations({ dueFrom: dueFrom || undefined, dueTo: dueTo || undefined });
        result = candidates.map((c: VaccinationCampaignCandidate) => ({
          ownerId: c.ownerId,
          phone: c.ownerPhone,
          smsConsent: c.smsConsent,
          whatsappConsent: c.whatsappConsent,
          variables: {
            musteri_adi: c.ownerFullName,
            musteri_bakiye: '',
            hasta_adi: c.patientName,
            asi_adi: c.vaccineName,
            asi_tarihi: c.nextDueDate,
          },
          label: c.ownerFullName,
        }));
      } else if (source === 'appointments') {
        const candidates = await recipientCandidatesApi.appointments({ from: apptFrom, to: apptTo });
        result = candidates.map((c: AppointmentCampaignCandidate) => ({
          ownerId: c.ownerId,
          phone: c.ownerPhone,
          smsConsent: c.smsConsent,
          whatsappConsent: c.whatsappConsent,
          variables: {
            musteri_adi: c.ownerFullName,
            musteri_bakiye: '',
            hasta_adi: c.patientName,
            randevu_tarihi: new Date(c.scheduledStart).toLocaleString('tr-TR'),
          },
          label: c.ownerFullName,
        }));
      } else if (source === 'debtors') {
        const balances = await billingApi.listOwnerBalances();
        result = balances.map((b) => ({
          ownerId: b.ownerId,
          phone: b.ownerPhone,
          smsConsent: b.smsConsent,
          whatsappConsent: b.whatsappConsent,
          variables: {
            musteri_adi: b.ownerName,
            musteri_bakiye: `${b.outstandingBalance.toLocaleString('tr-TR', { minimumFractionDigits: 2 })} ₺`,
          },
          label: b.ownerName,
        }));
      }

      const withCompanyVars = result.map((r) => ({
        ...r,
        variables: {
          ...r.variables,
          firma_adi: branch?.branchName ?? '',
          firma_adres: [branch?.address, branch?.city].filter(Boolean).join(', '),
        },
      }));
      setRecipients(withCompanyVars);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setLoadingPreview(false);
    }
  }

  async function handleSend() {
    if (!recipients || recipients.length === 0 || !messageBody.trim()) return;
    setSending(true);
    setError(null);
    try {
      const result = await campaignApi.sendCampaign(channel, messageBody, recipients);
      setSendResult(result);
      setRecipients(null);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSending(false);
    }
  }

  const consentedCount = recipients?.filter((r) => (channel === 'SMS' ? r.smsConsent : r.whatsappConsent) && r.phone).length ?? 0;

  return (
    <div>
      <div className={campaignStyles.sourceTabs}>
        {(['owners', 'vaccinations', 'appointments', 'debtors', 'custom'] as Source[]).map((s) => (
          <div
            key={s}
            className={`${campaignStyles.sourceTab} ${source === s ? campaignStyles.sourceTabActive : ''}`}
            onClick={() => {
              setSource(s);
              setRecipients(null);
              setSendResult(null);
            }}
          >
            {s === 'owners' && 'Müşteriler'}
            {s === 'vaccinations' && 'Aşı Takvimi'}
            {s === 'appointments' && 'Randevular'}
            {s === 'debtors' && 'Borçlu Müşteriler'}
            {s === 'custom' && 'Özel Numaralar'}
          </div>
        ))}
      </div>

      <div className={campaignStyles.composerGrid}>
        <div className={campaignStyles.filterPanel}>
          {source === 'owners' && (
            <>
              <FieldWrap label="Müşteri adı">
                <Input value={ownerName} onChange={(e) => setOwnerName(e.target.value)} placeholder="İçinde geçiyorsa" />
              </FieldWrap>
              <FieldWrap label="Kayıt tarihi (başlangıç)">
                <Input type="date" value={ownerFrom} onChange={(e) => setOwnerFrom(e.target.value)} />
              </FieldWrap>
              <FieldWrap label="Kayıt tarihi (bitiş)">
                <Input type="date" value={ownerTo} onChange={(e) => setOwnerTo(e.target.value)} />
              </FieldWrap>
            </>
          )}
          {source === 'vaccinations' && (
            <>
              <FieldWrap label="Aşı tarihi (başlangıç)">
                <Input type="date" value={dueFrom} onChange={(e) => setDueFrom(e.target.value)} />
              </FieldWrap>
              <FieldWrap label="Aşı tarihi (bitiş)">
                <Input type="date" value={dueTo} onChange={(e) => setDueTo(e.target.value)} />
              </FieldWrap>
            </>
          )}
          {source === 'appointments' && (
            <>
              <FieldWrap label="Randevu tarihi (başlangıç)">
                <Input type="date" value={apptFrom} onChange={(e) => setApptFrom(e.target.value)} />
              </FieldWrap>
              <FieldWrap label="Randevu tarihi (bitiş)">
                <Input type="date" value={apptTo} onChange={(e) => setApptTo(e.target.value)} />
              </FieldWrap>
            </>
          )}
          {source === 'debtors' && <div className={styles.muted}>Açık bakiyesi olan tüm müşteriler listelenecek.</div>}
          {source === 'custom' && (
            <FieldWrap label="Numaralar (bir satıra bir tane, opsiyonel: İsim,Numara)">
              <Textarea rows={6} value={customNumbers} onChange={(e) => setCustomNumbers(e.target.value)} placeholder={'Ahmet Yılmaz,05551234567\n05559876543'} />
            </FieldWrap>
          )}

          <Button variant="primary" onClick={handlePreview} disabled={loadingPreview}>
            {loadingPreview ? 'Yükleniyor...' : 'Alıcıları Listele ve Önizle'}
          </Button>
        </div>

        <div className={campaignStyles.composerPanel}>
          <FieldWrap label="Kanal">
            <Select value={channel} onChange={(e) => setChannel(e.target.value as NotificationChannel)}>
              <option value="SMS">SMS</option>
              <option value="WHATSAPP">WhatsApp</option>
            </Select>
          </FieldWrap>
          <FieldWrap label="Hazır şablon">
            <Select value={selectedTemplateId} onChange={(e) => handleTemplateSelect(e.target.value)}>
              <option value="">Şablon seçin</option>
              {relevantTemplates.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </Select>
          </FieldWrap>
          <div className={campaignStyles.chipRow}>
            {VARIABLE_CHIPS.map((chip) => (
              <button key={chip.token} type="button" className={campaignStyles.chip} onClick={() => insertToken(chip.token)}>
                {chip.label}
              </button>
            ))}
          </div>
          <FieldWrap label="Mesaj metni">
            <Textarea
              ref={textareaRef}
              rows={8}
              value={messageBody}
              onChange={(e) => setMessageBody(e.target.value)}
              placeholder="Sayın {musteri_adi}, ..."
            />
          </FieldWrap>
        </div>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      {sendResult && (
        <div className={campaignStyles.resultBanner}>
          <Badge tone="success">{sendResult.queued} mesaj kuyruklandı</Badge>
          {sendResult.skippedNoPhone > 0 && <Badge tone="warning">{sendResult.skippedNoPhone} telefonsuz atlandı</Badge>}
          {sendResult.skippedNoConsent > 0 && <Badge tone="warning">{sendResult.skippedNoConsent} onaysız atlandı</Badge>}
        </div>
      )}

      {recipients && (
        <div className={campaignStyles.previewBox}>
          <div className={campaignStyles.previewHeader}>
            <div>
              Alıcıları Listele ve Önizleme ({recipients.length} alıcı — {consentedCount} tanesi{' '}
              {channel === 'SMS' ? 'SMS' : 'WhatsApp'} onaylı ve telefonlu)
            </div>
            <Button variant="primary" onClick={handleSend} disabled={sending || recipients.length === 0 || !messageBody.trim()}>
              {sending ? 'Gönderiliyor...' : 'Gönder'}
            </Button>
          </div>
          <div className={styles.tableCard}>
            <div className={campaignStyles.previewTableHead}>
              <div>Ad / Etiket</div>
              <div>Telefon</div>
              <div>Onay</div>
            </div>
            {recipients.slice(0, 50).map((r, i) => (
              <div key={i} className={campaignStyles.previewTableRow}>
                <div>{r.label ?? '—'}</div>
                <div className={styles.muted}>{r.phone || '—'}</div>
                <div>
                  {(channel === 'SMS' ? r.smsConsent : r.whatsappConsent) && r.phone ? (
                    <Badge tone="success">Gönderilecek</Badge>
                  ) : (
                    <Badge tone="neutral">Atlanacak</Badge>
                  )}
                </div>
              </div>
            ))}
            {recipients.length > 50 && <div className={styles.muted} style={{ padding: '10px 20px' }}>...ve {recipients.length - 50} alıcı daha</div>}
          </div>
        </div>
      )}
    </div>
  );
}
