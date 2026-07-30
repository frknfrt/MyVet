import { FormEvent, useEffect, useState } from 'react';
import { appointmentApi, AppointmentSource, ServiceTypeItem, StaffItem } from '../../api/appointmentApi';
import { ApiError } from '../../api/client';
import { patientApi, PatientSearchResult } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './ScheduleAppointmentModal.module.css';

interface ScheduleAppointmentModalProps {
  open: boolean;
  onClose: () => void;
  onScheduled: () => void;
  defaultDate: string;
}

export function ScheduleAppointmentModal({ open, onClose, onScheduled, defaultDate }: ScheduleAppointmentModalProps) {
  const [staff, setStaff] = useState<StaffItem[]>([]);
  const [serviceTypes, setServiceTypes] = useState<ServiceTypeItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [patientQuery, setPatientQuery] = useState('');
  const [patientResults, setPatientResults] = useState<PatientSearchResult[]>([]);
  const [selectedPatient, setSelectedPatient] = useState<PatientSearchResult | null>(null);
  const [staffId, setStaffId] = useState('');
  const [serviceTypeId, setServiceTypeId] = useState('');
  const [date, setDate] = useState(defaultDate);
  const [startTime, setStartTime] = useState('09:00');
  const [source, setSource] = useState<AppointmentSource>('WALK_IN');
  const [notes, setNotes] = useState('');

  useEffect(() => {
    if (!open) return;
    setDate(defaultDate);
    appointmentApi.listStaff().then((list) => {
      setStaff(list);
      if (list.length > 0) setStaffId(list[0].id);
    });
    appointmentApi.listServiceTypes().then((list) => {
      setServiceTypes(list);
      if (list.length > 0) setServiceTypeId(list[0].id);
    });
  }, [open, defaultDate]);

  useEffect(() => {
    if (!patientQuery || selectedPatient) {
      setPatientResults([]);
      return;
    }
    const handle = setTimeout(() => {
      patientApi.search(patientQuery).then(setPatientResults);
    }, 250);
    return () => clearTimeout(handle);
  }, [patientQuery, selectedPatient]);

  function reset() {
    setPatientQuery('');
    setPatientResults([]);
    setSelectedPatient(null);
    setNotes('');
    setError(null);
    onClose();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (loading || !selectedPatient) return;
    setLoading(true);
    setError(null);
    try {
      const service = serviceTypes.find((s) => s.id === serviceTypeId);
      const durationMin = service?.defaultDurationMin ?? 30;
      const scheduledStart = new Date(`${date}T${startTime}:00Z`);
      const scheduledEnd = new Date(scheduledStart.getTime() + durationMin * 60 * 1000);

      await appointmentApi.schedule({
        patientId: selectedPatient.patientId,
        ownerId: selectedPatient.ownerId,
        assignedStaffId: staffId,
        serviceTypeId,
        scheduledStart: scheduledStart.toISOString(),
        scheduledEnd: scheduledEnd.toISOString(),
        source,
        notes: notes || undefined,
      });
      onScheduled();
      reset();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Randevu olusturulamadi, tekrar deneyin');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={reset} width={460}>
      <form onSubmit={handleSubmit}>
        <h2 className={styles.title}>Yeni randevu</h2>
        <p className={styles.lede}>Hasta arayın ve randevu bilgilerini girin</p>

        {error && <div className={styles.errorBanner}>{error}</div>}

        {selectedPatient ? (
          <div className={styles.selectedPatient}>
            <span>
              {selectedPatient.patientName} · {selectedPatient.ownerFullName}
            </span>
            <button type="button" onClick={() => setSelectedPatient(null)}>
              Değiştir
            </button>
          </div>
        ) : (
          <FieldWrap label="Hasta veya sahip ara">
            <Input value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} placeholder="Örn. Pamuk veya Mehmet Demir" />
          </FieldWrap>
        )}
        {!selectedPatient && patientResults.length > 0 && (
          <div className={styles.patientResults}>
            {patientResults.map((r) => (
              <div key={r.patientId} className={styles.patientResultItem} onClick={() => setSelectedPatient(r)}>
                {r.patientName} · {r.ownerFullName} ({r.ownerPhone})
              </div>
            ))}
          </div>
        )}

        <div className={styles.row}>
          <FieldWrap label="Hekim">
            <Select value={staffId} onChange={(e) => setStaffId(e.target.value)} required>
              {staff.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.fullName}
                </option>
              ))}
            </Select>
          </FieldWrap>
          <FieldWrap label="Hizmet">
            <Select value={serviceTypeId} onChange={(e) => setServiceTypeId(e.target.value)} required>
              {serviceTypes.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.defaultDurationMin} dk)
                </option>
              ))}
            </Select>
          </FieldWrap>
        </div>
        <div className={styles.row}>
          <FieldWrap label="Tarih">
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
          </FieldWrap>
          <FieldWrap label="Saat">
            <Input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
          </FieldWrap>
        </div>
        <FieldWrap label="Kaynak">
          <Select value={source} onChange={(e) => setSource(e.target.value as AppointmentSource)}>
            <option value="WALK_IN">Kapıdan Geldi</option>
            <option value="PHONE">Telefon</option>
            <option value="PET_APP">Sahip Uygulaması</option>
            <option value="WIDGET">Web Sitesi</option>
          </Select>
        </FieldWrap>
        <FieldWrap label="Not (opsiyonel)">
          <Input value={notes} onChange={(e) => setNotes(e.target.value)} />
        </FieldWrap>

        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={reset}>
            Vazgeç
          </Button>
          <Button type="submit" variant="primary" disabled={loading || !selectedPatient}>
            {loading ? 'Oluşturuluyor...' : 'Randevu Oluştur'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
