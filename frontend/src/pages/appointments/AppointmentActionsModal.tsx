import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppointmentItem, appointmentApi, StaffItem } from '../../api/appointmentApi';
import { ApiError } from '../../api/client';
import { encounterApi } from '../../api/encounterApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { AppointmentStatusBadge } from './appointmentStatus';
import { formatTime } from './weekUtils';
import styles from './AppointmentActionsModal.module.css';

interface AppointmentActionsModalProps {
  appointment: AppointmentItem | null;
  onClose: () => void;
  onChanged: () => void;
}

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function AppointmentActionsModal({ appointment, onClose, onChanged }: AppointmentActionsModalProps) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [staff, setStaff] = useState<StaffItem[]>([]);
  const [selectedStaffId, setSelectedStaffId] = useState('');

  useEffect(() => {
    setError(null);
    if (!appointment || appointment.assignedStaffId) return;
    appointmentApi.listStaff().then((list) => {
      setStaff(list);
      if (list.length > 0) setSelectedStaffId(list[0].id);
    });
  }, [appointment]);

  if (!appointment) return null;

  async function run(action: (id: string) => Promise<void>) {
    if (!appointment) return;
    setBusy(true);
    setError(null);
    try {
      await action(appointment.id);
      onChanged();
      onClose();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleAssignAndConfirm() {
    if (!appointment || !selectedStaffId) return;
    setBusy(true);
    setError(null);
    try {
      await appointmentApi.assignStaff(appointment.id, selectedStaffId);
      await appointmentApi.confirm(appointment.id);
      onChanged();
      onClose();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  /** Var olan muayeneyi bulur, yoksa yenisini acar -- her tiklamada duplicate olusturmaz. */
  async function goToSoap(startAppointment: boolean) {
    if (!appointment) return;
    setBusy(true);
    setError(null);
    try {
      if (startAppointment) {
        await appointmentApi.start(appointment.id);
      }
      const encounter = await encounterApi.findByAppointment(appointment.id);
      const encounterId = encounter
        ? encounter.id
        : await encounterApi.start({ patientId: appointment.patientId, appointmentId: appointment.id });
      onChanged();
      navigate(`/muayene/${encounterId}`);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  const { status, assignedStaffId } = appointment;
  const needsTriage = status === 'REQUESTED' && !assignedStaffId;

  return (
    <Modal open={appointment !== null} onClose={onClose} width={380}>
      <div className={styles.name}>{appointment.patientName}</div>
      <div className={styles.subline}>
        {appointment.ownerName} · {formatTime(appointment.scheduledStart)}–{formatTime(appointment.scheduledEnd)} ·{' '}
        {appointment.staffName ?? 'Hekim atanmadı'}
      </div>
      <AppointmentStatusBadge status={status} />
      {appointment.source === 'WIDGET' && <Badge tone="ai">Web Sitesi Talebi</Badge>}

      {error && <div className={styles.errorBanner}>{error}</div>}

      {needsTriage ? (
        <div className={styles.actions}>
          <Select value={selectedStaffId} onChange={(e) => setSelectedStaffId(e.target.value)} disabled={staff.length === 0}>
            {staff.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName}
              </option>
            ))}
          </Select>
          <Button variant="primary" disabled={busy || !selectedStaffId} onClick={handleAssignAndConfirm}>
            Hekim Ata ve Onayla
          </Button>
          <Button variant="danger" disabled={busy} onClick={() => run(appointmentApi.cancel)}>
            İptal et
          </Button>
        </div>
      ) : (
        <div className={styles.actions}>
          {status === 'REQUESTED' && (
            <Button variant="primary" disabled={busy} onClick={() => run(appointmentApi.confirm)}>
              Onayla
            </Button>
          )}
          {status === 'CONFIRMED' && (
            <Button variant="primary" disabled={busy} onClick={() => run(appointmentApi.checkIn)}>
              Check-in yap
            </Button>
          )}
          {status === 'CHECKED_IN' && (
            <Button variant="primary" disabled={busy} onClick={() => goToSoap(true)}>
              Muayeneyi Başlat (SOAP'a Git)
            </Button>
          )}
          {status === 'IN_PROGRESS' && (
            <Button variant="primary" disabled={busy} onClick={() => goToSoap(false)}>
              SOAP'a Git
            </Button>
          )}
          {status === 'IN_PROGRESS' && (
            <Button variant="secondary" disabled={busy} onClick={() => run(appointmentApi.complete)}>
              Randevuyu Tamamla
            </Button>
          )}
          {(status === 'CONFIRMED' || status === 'CHECKED_IN') && (
            <Button variant="secondary" disabled={busy} onClick={() => run(appointmentApi.markNoShow)}>
              Gelmedi olarak işaretle
            </Button>
          )}
          {status !== 'COMPLETED' && status !== 'CANCELLED' && status !== 'NO_SHOW' && (
            <Button variant="danger" disabled={busy} onClick={() => run(appointmentApi.cancel)}>
              İptal et
            </Button>
          )}
        </div>
      )}
    </Modal>
  );
}
