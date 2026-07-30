import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { encounterApi } from '../../api/encounterApi';
import { patientApi, PatientProfile } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { PatientStatusBadge } from './statusBadge';
import styles from './PatientProfileModal.module.css';

const SEX_LABELS: Record<string, string> = { MALE: 'Erkek', FEMALE: 'Dişi', UNKNOWN: 'Bilinmiyor' };

interface PatientProfileModalProps {
  patientId: string | null;
  onClose: () => void;
  onChanged: () => void;
}

export function PatientProfileModal({ patientId, onClose, onChanged }: PatientProfileModalProps) {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<PatientProfile | null>(null);
  const [busy, setBusy] = useState(false);
  const [startingEncounter, setStartingEncounter] = useState(false);

  useEffect(() => {
    if (!patientId) {
      setProfile(null);
      return;
    }
    patientApi.getProfile(patientId).then(setProfile);
  }, [patientId]);

  async function handleStartEncounter() {
    if (!patientId) return;
    setStartingEncounter(true);
    try {
      const encounterId = await encounterApi.start({ patientId });
      navigate(`/muayene/${encounterId}`);
    } finally {
      setStartingEncounter(false);
    }
  }

  async function handleMarkDeceased() {
    if (!patientId) return;
    setBusy(true);
    try {
      await patientApi.markDeceased(patientId);
      const updated = await patientApi.getProfile(patientId);
      setProfile(updated);
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={patientId !== null} onClose={onClose} width={520}>
      {!profile ? (
        <div className={styles.loading}>Yükleniyor...</div>
      ) : (
        <>
          <div className={styles.header}>
            <div>
              <div className={styles.name}>{profile.name}</div>
              <div className={styles.subline}>
                {profile.speciesName}
                {profile.breedName ? ` · ${profile.breedName}` : ''} · {SEX_LABELS[profile.sex ?? 'UNKNOWN']}
              </div>
            </div>
            <PatientStatusBadge status={profile.status} />
          </div>

          <div className={styles.section}>
            <div className={styles.sectionLabel}>Hasta bilgileri</div>
            <div className={styles.grid}>
              <div className={styles.item}>
                <div className={styles.itemLabel}>Doğum tarihi</div>
                <div className={styles.itemValue}>{profile.birthDate ?? '—'}</div>
              </div>
              <div className={styles.item}>
                <div className={styles.itemLabel}>Kısırlaştırma</div>
                <div className={styles.itemValue}>{profile.neutered ? 'Evet' : 'Hayır'}</div>
              </div>
              <div className={styles.item}>
                <div className={styles.itemLabel}>Mikroçip no</div>
                <div className={styles.itemValue}>{profile.microchipNumber ?? '—'}</div>
              </div>
              <div className={styles.item}>
                <div className={styles.itemLabel}>TARBİL kimlik no</div>
                <div className={styles.itemValue}>{profile.tarbilAnimalId ?? '—'}</div>
              </div>
              <div className={styles.item}>
                <div className={styles.itemLabel}>Ağırlık</div>
                <div className={styles.itemValue}>{profile.weightKg != null ? `${profile.weightKg} kg` : '—'}</div>
              </div>
            </div>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionLabel}>Sahip</div>
            <div className={styles.grid}>
              <div className={styles.item}>
                <div className={styles.itemLabel}>Adı soyadı</div>
                <div className={styles.itemValue}>{profile.ownerFullName}</div>
              </div>
              <div className={styles.item}>
                <div className={styles.itemLabel}>Telefon</div>
                <div className={styles.itemValue}>{profile.ownerPhone}</div>
              </div>
            </div>
          </div>

          {profile.status === 'ACTIVE' && (
            <div className={styles.actions}>
              <Button variant="danger" onClick={handleMarkDeceased} disabled={busy}>
                {busy ? 'İşleniyor...' : 'Vefat etti olarak işaretle'}
              </Button>
              <Button variant="primary" onClick={handleStartEncounter} disabled={startingEncounter}>
                {startingEncounter ? 'Başlatılıyor...' : 'Muayene Başlat'}
              </Button>
            </div>
          )}
        </>
      )}
    </Modal>
  );
}
