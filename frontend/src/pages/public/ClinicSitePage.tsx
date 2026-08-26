import { FormEvent, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { publicApi, PublicClinicInfo, PublicServiceType, PublicSpecies } from '../../api/publicApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import styles from './ClinicSitePage.module.css';

export function ClinicSitePage() {
  const { branchId } = useParams<{ branchId: string }>();
  const [clinic, setClinic] = useState<PublicClinicInfo | null>(null);
  const [services, setServices] = useState<PublicServiceType[]>([]);
  const [species, setSpecies] = useState<PublicSpecies[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  const [ownerFullName, setOwnerFullName] = useState('');
  const [ownerPhone, setOwnerPhone] = useState('');
  const [ownerEmail, setOwnerEmail] = useState('');
  const [petName, setPetName] = useState('');
  const [speciesId, setSpeciesId] = useState('');
  const [serviceTypeId, setServiceTypeId] = useState('');
  const [date, setDate] = useState('');
  const [time, setTime] = useState('10:00');
  const [notes, setNotes] = useState('');

  useEffect(() => {
    if (!branchId) return;
    Promise.all([publicApi.getClinic(branchId), publicApi.listSpecies()])
      .then(([clinicInfo, speciesList]) => {
        setClinic(clinicInfo);
        setSpecies(speciesList);
        if (speciesList.length > 0) setSpeciesId(speciesList[0].id);
        return publicApi.listServiceTypes(clinicInfo.tenantId);
      })
      .then((serviceList) => {
        setServices(serviceList);
        if (serviceList.length > 0) setServiceTypeId(serviceList[0].id);
      })
      .finally(() => setLoading(false));
  }, [branchId]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!clinic || !branchId || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const owner = await publicApi.registerOwner({
        tenantId: clinic.tenantId,
        fullName: ownerFullName,
        phone: ownerPhone,
        email: ownerEmail || undefined,
      });
      const patient = await publicApi.registerPatient({ ownerId: owner.id, speciesId, name: petName });
      const scheduledStart = new Date(`${date}T${time}:00Z`);
      const service = services.find((s) => s.id === serviceTypeId);
      const scheduledEnd = new Date(scheduledStart.getTime() + (service?.defaultDurationMin ?? 30) * 60 * 1000);

      await publicApi.requestAppointment({
        tenantId: clinic.tenantId,
        branchId,
        patientId: patient.id,
        ownerId: owner.id,
        serviceTypeId,
        scheduledStart: scheduledStart.toISOString(),
        scheduledEnd: scheduledEnd.toISOString(),
        notes: notes || undefined,
      });
      setSuccess(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Randevu talebi gönderilemedi, tekrar deneyin');
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <div className={styles.page} />;
  }

  if (!clinic) {
    return (
      <div className={styles.page}>
        <div className={styles.content}>
          <div className={styles.card}>Klinik bulunamadı.</div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.hero}>
        <div className={styles.brandMark}>
          <div className={styles.glyph}>
            <svg viewBox="0 0 24 24" fill="none" stroke="#180F24" strokeWidth={2.2} strokeLinecap="round">
              <path d="M12 3v6M12 15v6M4.2 7.5l5.2 3M14.6 13.5l5.2 3M4.2 16.5l5.2-3M14.6 10.5l5.2-3" />
            </svg>
          </div>
        </div>
        <h1 className={styles.clinicName}>{clinic.tenantName}</h1>
        <p className={styles.clinicAddress}>
          {clinic.branchName}
          {clinic.address ? ` · ${clinic.address}` : ''}
          {clinic.city ? `, ${clinic.city}` : ''}
        </p>
      </div>

      <div className={styles.content}>
        {services.length > 0 && (
          <div className={styles.card}>
            <h2 className={styles.sectionTitle}>Hizmetlerimiz</h2>
            <div className={styles.servicesGrid}>
              {services.map((s) => (
                <div key={s.id} className={styles.serviceItem}>
                  <div className={styles.serviceName}>{s.name}</div>
                  <div className={styles.serviceMeta}>
                    {s.defaultDurationMin} dk · {s.defaultPrice.toFixed(2)} ₺
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className={styles.card}>
          <h2 className={styles.sectionTitle}>Online Randevu Al</h2>

          {success ? (
            <div className={styles.successBox}>
              <div className={styles.successTitle}>Randevu talebiniz alındı</div>
              <p className={styles.successCopy}>
                Kliniğimiz en kısa sürede sizinle iletişime geçerek randevunuzu onaylayacaktır.
              </p>
            </div>
          ) : (
            <form onSubmit={handleSubmit}>
              {error && <div className={styles.errorBanner}>{error}</div>}

              <div className={styles.formGrid}>
                <FieldWrap label="Adınız soyadınız">
                  <Input value={ownerFullName} onChange={(e) => setOwnerFullName(e.target.value)} required />
                </FieldWrap>
                <FieldWrap label="Telefon">
                  <Input value={ownerPhone} onChange={(e) => setOwnerPhone(e.target.value)} required />
                </FieldWrap>
              </div>
              <FieldWrap label="E-posta (opsiyonel)">
                <Input type="email" value={ownerEmail} onChange={(e) => setOwnerEmail(e.target.value)} />
              </FieldWrap>

              <div className={styles.formGrid}>
                <FieldWrap label="Hayvanınızın adı">
                  <Input value={petName} onChange={(e) => setPetName(e.target.value)} required />
                </FieldWrap>
                <FieldWrap label="Tür">
                  <Select value={speciesId} onChange={(e) => setSpeciesId(e.target.value)} required>
                    {species.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </Select>
                </FieldWrap>
              </div>

              <FieldWrap label="Hizmet">
                <Select value={serviceTypeId} onChange={(e) => setServiceTypeId(e.target.value)} required>
                  {services.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </Select>
              </FieldWrap>

              <div className={styles.formGrid}>
                <FieldWrap label="Tarih">
                  <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
                </FieldWrap>
                <FieldWrap label="Saat">
                  <Input type="time" value={time} onChange={(e) => setTime(e.target.value)} required />
                </FieldWrap>
              </div>

              <FieldWrap label="Not (opsiyonel)">
                <Input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Şikayetiniz veya isteğiniz" />
              </FieldWrap>

              <div className={styles.submitRow}>
                <Button type="submit" variant="primary" className={styles.submitBtn} disabled={submitting}>
                  {submitting ? 'Gönderiliyor...' : 'Randevu Talep Et'}
                </Button>
              </div>
            </form>
          )}
        </div>

        <div className={styles.footer}>Vetly altyapısıyla oluşturulmuştur</div>
      </div>
    </div>
  );
}
