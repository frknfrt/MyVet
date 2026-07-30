import { Badge } from '../../components/ui/Badge';
import { WorklistPatient } from '../../data/worklist';
import styles from './WorklistRow.module.css';

const CHECK_ICON = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={3} strokeLinecap="round">
    <path d="M4 12l6 6L20 6" />
  </svg>
);

interface WorklistRowProps {
  patient: WorklistPatient;
  delayMs: number;
}

export function WorklistRow({ patient, delayMs }: WorklistRowProps) {
  return (
    <div className={styles.row} style={{ animationDelay: `${delayMs}ms` }}>
      <div className={styles.patientCell}>
        <div className={styles.avatar} style={{ background: patient.color }}>
          {patient.initials}
        </div>
        <div>
          <div className={styles.name}>
            {patient.name} <span className={styles.muted}>· {patient.species}</span>
            {patient.directive === 'cpr' && <span className={`${styles.directive} ${styles.cpr}`}>CPR</span>}
            {patient.directive === 'dnr' && <span className={`${styles.directive} ${styles.dnr}`}>DNR</span>}
            {patient.flag && (
              <span className={styles.flagIcon} title={patient.flag}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={3}>
                  <path d="M12 8v4M12 16h.01" />
                </svg>
              </span>
            )}
          </div>
          <div className={styles.owner}>{patient.owner}</div>
        </div>
      </div>
      <div className={styles.muted}>{patient.time}</div>
      <div className={styles.muted}>{patient.location}</div>
      <div className={styles.miniAvatar}>{patient.vetInitials}</div>
      <div className={styles.assignedStack}>
        {patient.assignedInitials.length ? (
          patient.assignedInitials.map((a, i) => (
            <div key={i} className={styles.miniAvatar}>
              {a}
            </div>
          ))
        ) : (
          <span className={styles.muted}>—</span>
        )}
      </div>
      <div className={styles.muted}>{patient.reason}</div>
      <div>
        <Badge tone={patient.badgeTone} icon={patient.badgeTone === 'success' ? CHECK_ICON : undefined}>
          {patient.badgeLabel}
        </Badge>
      </div>
    </div>
  );
}
