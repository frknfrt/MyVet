import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { OwnerProfile, patientApi } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './OwnerEditModal.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

interface OwnerEditModalProps {
  open: boolean;
  profile: OwnerProfile | null;
  onClose: () => void;
  onSaved: () => void;
}

export function OwnerEditModal({ open, profile, onClose, onSaved }: OwnerEditModalProps) {
  const [fullName, setFullName] = useState('');
  const [middleName, setMiddleName] = useState('');
  const [phone, setPhone] = useState('');
  const [secondaryPhone, setSecondaryPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [city, setCity] = useState('');
  const [district, setDistrict] = useState('');
  const [occupation, setOccupation] = useState('');
  const [referralSource, setReferralSource] = useState('');
  const [clientDiscount, setClientDiscount] = useState('0');
  const [protocolNumber, setProtocolNumber] = useState('');
  const [smsConsent, setSmsConsent] = useState(true);
  const [whatsappConsent, setWhatsappConsent] = useState(true);
  const [notificationConsent, setNotificationConsent] = useState(true);
  const [criticalAlert, setCriticalAlert] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !profile) return;
    setFullName(profile.fullName);
    setMiddleName(profile.middleName ?? '');
    setPhone(profile.phone);
    setSecondaryPhone(profile.secondaryPhone ?? '');
    setEmail(profile.email ?? '');
    setAddress(profile.address ?? '');
    setCity(profile.city ?? '');
    setDistrict(profile.district ?? '');
    setOccupation(profile.occupation ?? '');
    setReferralSource(profile.referralSource ?? '');
    setClientDiscount(String(profile.clientDiscount ?? 0));
    setProtocolNumber(profile.protocolNumber ?? '');
    setSmsConsent(profile.smsConsent);
    setWhatsappConsent(profile.whatsappConsent);
    setNotificationConsent(profile.notificationConsent);
    setCriticalAlert(profile.criticalAlert ?? '');
    setNotes(profile.notes ?? '');
    setError(null);
  }, [open, profile]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || !profile) return;
    setBusy(true);
    setError(null);
    try {
      await patientApi.updateOwner(profile.id, {
        fullName,
        phone,
        email: email || undefined,
        address: address || undefined,
        middleName: middleName || undefined,
        secondaryPhone: secondaryPhone || undefined,
        city: city || undefined,
        district: district || undefined,
        occupation: occupation || undefined,
        referralSource: referralSource || undefined,
        clientDiscount: clientDiscount ? Number(clientDiscount) : undefined,
        criticalAlert: criticalAlert || undefined,
        notes: notes || undefined,
        smsConsent,
        whatsappConsent,
        notificationConsent,
        protocolNumber: protocolNumber || undefined,
      });
      onSaved();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  if (!profile) return null;

  return (
    <Modal open={open} onClose={onClose} width={620}>
      <form onSubmit={handleSubmit}>
        <h2 className={styles.title}>{profile.fullName} — Müşteri bilgilerini düzenle</h2>

        {error && <div className={styles.errorBanner}>{error}</div>}

        <div className={styles.sectionLabel}>İletişim</div>
        <FieldWrap label="Ad Soyad">
          <Input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
        </FieldWrap>
        <div className={styles.row2}>
          <FieldWrap label="İkinci ad (opsiyonel)">
            <Input value={middleName} onChange={(e) => setMiddleName(e.target.value)} />
          </FieldWrap>
          <FieldWrap label="Meslek">
            <Input value={occupation} onChange={(e) => setOccupation(e.target.value)} placeholder="Örn. Avukat, polis" />
          </FieldWrap>
        </div>
        <div className={styles.row2}>
          <FieldWrap label="Telefon">
            <Input value={phone} onChange={(e) => setPhone(e.target.value)} required />
          </FieldWrap>
          <FieldWrap label="İkincil telefon (opsiyonel)">
            <Input value={secondaryPhone} onChange={(e) => setSecondaryPhone(e.target.value)} />
          </FieldWrap>
        </div>
        <FieldWrap label="E-posta">
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </FieldWrap>

        <div className={styles.sectionLabel}>Adres</div>
        <div className={styles.row3}>
          <FieldWrap label="İl">
            <Input value={city} onChange={(e) => setCity(e.target.value)} />
          </FieldWrap>
          <FieldWrap label="İlçe">
            <Input value={district} onChange={(e) => setDistrict(e.target.value)} />
          </FieldWrap>
          <FieldWrap label="Protokol No">
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
          <FieldWrap label="Müşteri indirimi (%)">
            <Input type="number" min={0} max={100} value={clientDiscount} onChange={(e) => setClientDiscount(e.target.value)} />
          </FieldWrap>
        </div>
        <label className={styles.checkboxRow}>
          <input type="checkbox" checked={smsConsent} onChange={(e) => setSmsConsent(e.target.checked)} />
          SMS gönderim izni
        </label>
        <label className={styles.checkboxRow}>
          <input type="checkbox" checked={whatsappConsent} onChange={(e) => setWhatsappConsent(e.target.checked)} />
          WhatsApp gönderim izni
        </label>
        <label className={styles.checkboxRow}>
          <input type="checkbox" checked={notificationConsent} onChange={(e) => setNotificationConsent(e.target.checked)} />
          Uygulama bildirimi izni
        </label>

        <div className={styles.sectionLabel}>Notlar</div>
        <FieldWrap label="Kritik Uyarı (kırmızı banner ile gösterilir)">
          <Textarea rows={2} value={criticalAlert} onChange={(e) => setCriticalAlert(e.target.value)} />
        </FieldWrap>
        <FieldWrap label="Genel Notlar">
          <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </FieldWrap>

        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            Vazgeç
          </Button>
          <Button type="submit" variant="primary" disabled={busy}>
            {busy ? 'Kaydediliyor...' : 'Kaydet'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
