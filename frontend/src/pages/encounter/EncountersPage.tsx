import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { encounterApi, EncounterDetail } from '../../api/encounterApi';
import { useAuth } from '../../auth/AuthContext';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { NewEncounterModal } from '../dashboard/NewEncounterModal';
import { ageLabel, daysOpen } from './encounterAge';
import styles from './EncountersPage.module.css';

/** Bu kadar gün açık kalan taslak "unutulmuş olabilir" diye işaretlenir. */
const STALE_DAYS = 2;

/** Muayeneler: klinikteki açık (taslak) muayeneler, en eskisi üstte; buradan yeni muayene de başlatılır. */
export function EncountersPage() {
  const navigate = useNavigate();
  const { session } = useAuth();
  const canStart = session?.role === 'VET' || session?.role === 'ADMIN';
  const [open, setOpen] = useState<EncounterDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    encounterApi
      .listOpen()
      .then(setOpen)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Muayeneler yüklenemedi'))
      .finally(() => setLoading(false));
  }, []);

  const staleCount = open.filter((e) => daysOpen(e.encounterDate) >= STALE_DAYS).length;

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Muayeneler</h1>
          <div className={styles.sub}>Tamamlanmamış muayeneler. Tamamlananlar hastanın sayfasındaki Muayene Geçmişi'nde.</div>
        </div>
        {canStart && (
          <Button variant="primary" onClick={() => setModalOpen(true)}>
            Yeni muayene
          </Button>
        )}
      </div>

      {staleCount > 0 && (
        <div className={styles.staleNote}>
          {staleCount} muayene {STALE_DAYS} günden uzun süredir açık. Tamamlayın ya da gerekmiyorsa iptal edin.
        </div>
      )}

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Hasta</div>
          <div>Hekim</div>
          <div>Açıldı</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : error ? (
          <div className={styles.empty}>{error}</div>
        ) : open.length === 0 ? (
          <div className={styles.empty}>Açık muayene yok.</div>
        ) : (
          open.map((e) => {
            const days = daysOpen(e.encounterDate);
            return (
              <button key={e.id} type="button" className={styles.row} onClick={() => navigate(`/muayene/${e.id}`)}>
                <span className={styles.patient}>{e.patientName}</span>
                <span className={styles.muted}>{e.staffName}</span>
                <span className={days >= STALE_DAYS ? styles.stale : styles.muted}>
                  {new Date(e.encounterDate).toLocaleDateString('tr-TR')} · {ageLabel(days)}
                </span>
              </button>
            );
          })
        )}
      </div>

      <NewEncounterModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </AppShell>
  );
}
