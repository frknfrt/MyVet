import { AppointmentItem } from '../../api/appointmentApi';
import { AppointmentStatusBadge } from '../appointments/appointmentStatus';
import { formatTime } from '../appointments/weekUtils';
import { colorFor, initialsOf } from './avatarColor';
import styles from './WorklistRow.module.css';

const SOURCE_LABELS: Record<AppointmentItem['source'], string> = {
  PET_APP: 'Sahip Uygulaması',
  PHONE: 'Telefon',
  WALK_IN: 'Kapıdan Geldi',
  WIDGET: 'Web Sitesi',
};

interface WorklistRowProps {
  appointment: AppointmentItem;
  delayMs: number;
  onClick: () => void;
}

export function WorklistRow({ appointment, delayMs, onClick }: WorklistRowProps) {
  const initials = initialsOf(appointment.patientName);

  return (
    <div className={styles.row} style={{ animationDelay: `${delayMs}ms` }} onClick={onClick}>
      <div className={styles.patientCell}>
        <div className={styles.avatar} style={{ background: colorFor(appointment.patientId) }}>
          {initials}
        </div>
        <div>
          <div className={styles.name}>
            {appointment.patientName}
            {appointment.patientSpeciesName && <span className={styles.muted}> · {appointment.patientSpeciesName}</span>}
          </div>
          <div className={styles.owner}>{appointment.ownerName}</div>
        </div>
      </div>
      <div className={styles.muted}>{formatTime(appointment.scheduledStart)}</div>
      <div className={styles.muted}>{appointment.staffName ?? 'Atanmadı'}</div>
      <div className={styles.muted}>{appointment.serviceName ?? '—'}</div>
      <div className={styles.muted}>{SOURCE_LABELS[appointment.source]}</div>
      <div>
        <AppointmentStatusBadge status={appointment.status} />
      </div>
    </div>
  );
}
