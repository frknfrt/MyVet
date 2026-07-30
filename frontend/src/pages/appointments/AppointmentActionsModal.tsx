import { useEffect, useState } from 'react';
import { AppointmentItem, appointmentApi, StaffItem } from '../../api/appointmentApi';
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

export function AppointmentActionsModal({ appointment, onClose, onChanged }: AppointmentActionsModalProps) {
  const [busy, setBusy] = useState(false);
  const [staff, setStaff] = useState<StaffItem[]>([]);
  const [selectedStaffId, setSelectedStaffId] = useState('');

  useEffect(() => {
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
    try {
      await action(appointment.id);
      onChanged();
      onClose();
    } finally {
      setBusy(false);
    }
  }

  async function handleAssignAndConfirm() {
    if (!appointment || !selectedStaffId) return;
    setBusy(true);
    try {
      await appointmentApi.assignStaff(appointment.id, selectedStaffId);
      await appointmentApi.confirm(appointment.id);
      onChanged();
      onClose();
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
            <Button variant="primary" disabled={busy} onClick={() => run(appointmentApi.start)}>
              Muayeneyi başlat
            </Button>
          )}
          {status === 'IN_PROGRESS' && (
            <Button variant="primary" disabled={busy} onClick={() => run(appointmentApi.complete)}>
              Tamamla
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
