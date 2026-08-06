import { EncounterDetail } from '../../api/encounterApi';
import { PatientProfile } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { colorFor, initialsOf } from '../dashboard/avatarColor';
import { PatientStatusBadge } from '../patients/statusBadge';
import { ageLabelFrom, formatDate } from './ageUtils';
import { EncounterStatusBadge } from './encounterStatus';
import styles from './PatientHeaderBar.module.css';

const SEX_LABELS: Record<string, string> = { MALE: 'Erkek', FEMALE: 'Dişi', UNKNOWN: 'Cinsiyet Bilinmiyor' };

interface PatientHeaderBarProps {
  profile: PatientProfile;
  encounter: EncounterDetail;
  onBack: () => void;
}

/**
 * Referans: Shepherd SOAP ekranindaki ust "hasta bilgi cubugu" -- proje
 * tasarim dilimize (mor/altin, pill kart) uyarlandi. Muayene boyunca
 * sayfanin en ustunde sabit kalir (position: sticky).
 */
export function PatientHeaderBar({ profile, encounter, onBack }: PatientHeaderBarProps) {
  const age = ageLabelFrom(profile.birthDate);
  const dob = formatDate(profile.birthDate);

  return (
    <div className={styles.wrap}>
      <div className={styles.bar}>
        <div className={styles.avatar} style={{ background: colorFor(profile.id) }}>
          {initialsOf(profile.name)}
        </div>

        <div className={styles.identity}>
          <div className={styles.nameRow}>
            <span className={styles.name}>{profile.name}</span>
            <PatientStatusBadge status={profile.status} />
          </div>
          <span className={styles.owner}>{profile.ownerFullName}</span>
        </div>

        <div className={styles.metaStrip}>
          {profile.speciesName && <span>{profile.speciesName}</span>}
          {profile.breedName && <span>{profile.breedName}</span>}
          <span>{SEX_LABELS[profile.sex ?? 'UNKNOWN']}</span>
          {age && <span>{age}</span>}
          {dob && <span>{dob}</span>}
        </div>

        <div className={styles.spacer} />

        <div className={styles.rightActions}>
          <EncounterStatusBadge status={encounter.status} />
          <Button variant="secondary" onClick={onBack}>
            Hastalara Dön
          </Button>
        </div>
      </div>
      <div className={styles.subline}>
        Muayene başlangıcı: {new Date(encounter.encounterDate).toLocaleString('tr-TR')} · {encounter.staffName}
      </div>
    </div>
  );
}
