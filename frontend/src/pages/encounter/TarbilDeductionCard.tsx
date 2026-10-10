import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { inventoryApi } from '../../api/inventoryApi';
import { EncounterDeduction, tarbilApi } from '../../api/tarbilApi';
import { Button } from '../../components/ui/Button';
import encounterStyles from './EncounterPage.module.css';
import styles from './TarbilDeductionCard.module.css';
import { TarbilDiseaseSelect } from './TarbilDiseaseSelect';

/**
 * Muayene tamamlanınca TARBİL'e bağlı ilaçların düşümü (spec 2026-10-09 S5.1): stok TARBİL'de onaylanınca düşer.
 * "Yalnız Vetly'den düş" TARBİL'e gitmeden düşer ve bekleyen aktarımı reddeder (iki adımlı onay).
 */
export function TarbilDeductionCard({ encounterId, canAct }: { encounterId: string; canAct: boolean }) {
  const [data, setData] = useState<EncounterDeduction | null>(null);
  const [disease, setDisease] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmVetlyOnly, setConfirmVetlyOnly] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function load() {
    return tarbilApi
      .getEncounterDeduction(encounterId)
      .then(setData)
      .catch(() => setData(null));
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [encounterId]);

  if (!data || data.lines.length === 0) return null;
  const pending = data.lines.filter((l) => l.status === 'PENDING');
  const deducted = data.lines.filter((l) => l.status === 'DEDUCTED');
  const shown = pending.length > 0 ? pending : deducted;

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      setConfirmVetlyOnly(false);
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'İşlem yapılamadı');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={encounterStyles.card}>
      <div className={encounterStyles.cardTitle}>TARBİL stok düşümü</div>
      {error && <div className={encounterStyles.aiError}>{error}</div>}
      {pending.length === 0 ? (
        <div className={styles.done}>TARBİL'den düşüldü ✓</div>
      ) : data.submissionStatus === 'PENDING' ? (
        <div className={styles.note}>TARBİL'e aktarılmayı bekliyor — eklentiden "TARBİL'e aktar" deyin.</div>
      ) : (
        <div className={styles.note}>
          Bu muayenede TARBİL'e bağlı {pending.length} ilaç kullanıldı — TARBİL düşümü bekliyor.
        </div>
      )}
      {shown.length > 0 && (
        <ul className={styles.lines}>
          {shown.map((l, i) => (
            <li key={i}>
              {l.itemName} · Seri {l.lotNumber ?? '—'} · {l.settledQuantity ?? l.quantity} adet
            </li>
          ))}
        </ul>
      )}
      {canAct && pending.length > 0 && (
        <div className={styles.actions}>
          {data.submissionStatus !== 'PENDING' && (
            <>
              <TarbilDiseaseSelect value={disease} onChange={setDisease} />
              <Button
                variant="primary"
                disabled={!disease || busy}
                onClick={() => run(() => tarbilApi.queueEncounterDeduction(encounterId, disease))}
              >
                TARBİL'den stok düş
              </Button>
            </>
          )}
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() =>
              confirmVetlyOnly ? run(() => inventoryApi.settleVetlyOnly('ENCOUNTER', encounterId)) : setConfirmVetlyOnly(true)
            }
          >
            {confirmVetlyOnly ? "Emin misiniz? Yalnız Vetly'den düş" : "Yalnız Vetly'den düş"}
          </Button>
        </div>
      )}
    </div>
  );
}
