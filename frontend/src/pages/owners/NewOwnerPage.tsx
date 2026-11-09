import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { formsApi, type PrivacyNotice } from '../../api/formsApi';
import { PrivacyNoticeLink } from '../../forms/PrivacyNoticeLink';
import { patientApi } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Textarea } from '../../components/ui/Field';
import { isValidTcKimlik } from './tcKimlik';
import styles from './NewOwnerPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function NewOwnerPage() {
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [middleName, setMiddleName] = useState('');
  const [phone, setPhone] = useState('');
  const [secondaryPhone, setSecondaryPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [city, setCity] = useState('');
  const [district, setDistrict] = useState('');
  const [occupation, setOccupation] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [nationalId, setNationalId] = useState('');
  const [referralSource, setReferralSource] = useState('');
  const [clientDiscount, setClientDiscount] = useState('0');
  const [protocolNumber, setProtocolNumber] = useState('');
  const [noticePresented, setNoticePresented] = useState(false);
  const [notice, setNotice] = useState<PrivacyNotice|null>(null);
  const [noticeLoadError,setNoticeLoadError] = useState<string|null>(null);
  const [createdOwnerId,setCreatedOwnerId] = useState<string|null>(null);
  useEffect(() => { let active=true;formsApi.getPrivacyNotice().then(n => {if(active)setNotice(n);})
    .catch(() => {if(active)setNoticeLoadError('Aydınlatma metni yüklenemedi. Metin sunum kaydı oluşturulmayacak.');});
    return () => {active=false;}; },[]);
  const [smsConsent] = useState(false);
  const [whatsappConsent] = useState(false);
  const [notificationConsent, setNotificationConsent] = useState(true);
  const [marketingConsent] = useState(false);
  const [criticalAlert, setCriticalAlert] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const nationalIdError = nationalId && !isValidTcKimlik(nationalId) ? 'Geçersiz TC kimlik numarası' : null;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || createdOwnerId || !!nationalIdError) return;
    setBusy(true);
    setError(null);
    try {
      const owner = await patientApi.registerOwner({
        fullName,
        middleName: middleName || undefined,
        phone,
        secondaryPhone: secondaryPhone || undefined,
        email: email || undefined,
        address: address || undefined,
        city: city || undefined,
        district: district || undefined,
        occupation: occupation || undefined,
        birthDate,
        nationalId: nationalId || undefined,
        referralSource: referralSource || undefined,
        clientDiscount: clientDiscount ? Number(clientDiscount) : undefined,
        criticalAlert: criticalAlert || undefined,
        notes: notes || undefined,
        marketingConsent,
        smsConsent,
        whatsappConsent,
        notificationConsent,
        protocolNumber: protocolNumber || undefined,
      });
      setCreatedOwnerId(owner.id);
      if (noticePresented && notice) {
        try { await formsApi.recordNoticeDelivery(owner.id, notice.hash); }
        catch (e) {setError(`Hasta sahibi oluşturuldu; aydınlatma sunum kaydı oluşturulamadı: ${errorMessageOf(e)}`);return;}
      }
      navigate(`/musteriler/${owner.id}`);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AppShell>
      <button className={styles.backLink} onClick={() => navigate('/hastalar')}>
        ← Hastalar &amp; Sahipler
      </button>

      <div className={styles.card}>
        <h1 className={styles.title}>Yeni Hasta Sahibi</h1>
        {createdOwnerId && <Button type="button" onClick={() => navigate(`/musteriler/${createdOwnerId}`)}>Oluşturulan hasta sahibine git</Button>}
        <p className={styles.lede}>Yeni bir hasta sahibi kaydı oluşturun — hayvanlarını daha sonra bu kişinin sayfasından ekleyebilirsiniz.</p>

        <form onSubmit={handleSubmit}>
          {error && <div className={styles.errorBanner}>{error}</div>}

          <div className={styles.sectionLabel}>Kimlik &amp; İletişim</div>
          <div className={styles.row3}>
            <FieldWrap label="Ad Soyad">
              <Input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
            </FieldWrap>
            <FieldWrap label="İkinci ad (opsiyonel)">
              <Input value={middleName} onChange={(e) => setMiddleName(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="Meslek (opsiyonel)">
              <Input value={occupation} onChange={(e) => setOccupation(e.target.value)} placeholder="Örn. Avukat, polis" />
            </FieldWrap>
          </div>
          <div className={styles.row3}>
            <FieldWrap label="Telefon">
              <Input value={phone} onChange={(e) => setPhone(e.target.value)} required />
            </FieldWrap>
            <FieldWrap label="İkincil telefon (opsiyonel)">
              <Input value={secondaryPhone} onChange={(e) => setSecondaryPhone(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="E-posta (opsiyonel)">
              <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </FieldWrap>
          </div>
          <div className={styles.row2}>
            <FieldWrap label="Doğum Tarihi">
              <Input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} required />
            </FieldWrap>
            <FieldWrap label="TC Kimlik No (opsiyonel)">
              <Input
                value={nationalId}
                onChange={(e) => setNationalId(e.target.value.replace(/\D/g, '').slice(0, 11))}
                maxLength={11}
                inputMode="numeric"
                placeholder="11 haneli TC kimlik no"
              />
              {nationalIdError && <div className={styles.fieldError}>{nationalIdError}</div>}
            </FieldWrap>
          </div>

          <div className={styles.sectionLabel}>Adres</div>
          <div className={styles.row3}>
            <FieldWrap label="İl">
              <Input value={city} onChange={(e) => setCity(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="İlçe">
              <Input value={district} onChange={(e) => setDistrict(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="Protokol No (opsiyonel)">
              <Input value={protocolNumber} onChange={(e) => setProtocolNumber(e.target.value)} />
            </FieldWrap>
          </div>
          <FieldWrap label="Açık adres">
            <Input value={address} onChange={(e) => setAddress(e.target.value)} />
          </FieldWrap>

          <div className={styles.sectionLabel}>Pazarlama &amp; İzinler</div>
          <div className={styles.row2}>
            <FieldWrap label="Referans kaynağı">
              <Input value={referralSource} onChange={(e) => setReferralSource(e.target.value)} placeholder="Örn. Tavsiye, Instagram" />
            </FieldWrap>
            <FieldWrap label="İndirim (%)">
              <Input type="number" min={0} max={100} value={clientDiscount} onChange={(e) => setClientDiscount(e.target.value)} />
            </FieldWrap>
          </div>
          <div className={styles.kvkkBox}>
            {notice && <PrivacyNoticeLink text={notice.text} version={notice.version}/>}
            <label><input type="checkbox" checked={noticePresented} disabled={!notice || !!createdOwnerId}
              onChange={e => setNoticePresented(e.target.checked)} />
              KVKK aydınlatma metnini ilgili kişiye sundum (klinik çalışanının beyanı).
            </label>
            <p>Hasta sahibinin bilgilendirme beyanı ve imzası, bu bölümün etkin olduğu formun tablet ekranında alınır. Bu kayıt açık rıza değildir.</p>
            {noticeLoadError && <p role="alert">{noticeLoadError}</p>}
          </div>

          <div className={styles.consentRow}>
            <p>SMS, WhatsApp ve e-posta hizmet tercihlerini hasta sahibi tablet formunda kendisi seçer.</p>
            <label className={styles.checkboxRow}>
              <input type="checkbox" checked={notificationConsent} onChange={(e) => setNotificationConsent(e.target.checked)} />
              Uygulama bildirimi izni
            </label>

          </div>

          <div className={styles.sectionLabel}>Notlar</div>
          <div className={styles.row2}>
            <FieldWrap label="Kritik Uyarı (kırmızı banner ile gösterilir)">
              <Textarea rows={2} value={criticalAlert} onChange={(e) => setCriticalAlert(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="Genel Notlar">
              <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </FieldWrap>
          </div>

          <div className={styles.actions}>
            <Button type="button" variant="secondary" onClick={() => navigate('/hastalar')}>
              Vazgeç
            </Button>
            <Button type="submit" variant="primary" disabled={busy || !!createdOwnerId || !!nationalIdError}>
              {busy ? 'Kaydediliyor...' : 'Hasta Sahibini Kaydet'}
            </Button>
          </div>
        </form>
      </div>
    </AppShell>
  );
}
