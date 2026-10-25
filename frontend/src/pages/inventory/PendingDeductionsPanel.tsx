import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { inventoryApi, PendingDeduction, PendingDeductionReference } from '../../api/inventoryApi';
import { Button } from '../../components/ui/Button';
import styles from './PendingDeductionsPanel.module.css';
import { unitLabel } from './stockFormat';

const SOURCE: Record<PendingDeductionReference, string> = {
  ENCOUNTER: 'Muayene',
  VACCINATION: 'Aşı',
  PRESCRIPTION: 'Reçete',
};

const ALL_OF: Record<PendingDeductionReference, string> = {
  ENCOUNTER: 'Bu muayenenin tüm kalemlerini',
  VACCINATION: 'Bu aşıyı',
  PRESCRIPTION: 'Bu reçetenin tüm kalemlerini',
};

interface Group {
  key: string;
  referenceType: PendingDeductionReference;
  referenceId: string;
  createdAt: string;
  rows: PendingDeduction[];
}

/** Aynı kaynağın (muayene/aşı/reçete) satırları birlikte düşer; düğme bu yüzden kaynak başına tektir (review 2026-10-10). */
function groupBySource(rows: PendingDeduction[]): Group[] {
  const groups = new Map<string, Group>();
  for (const r of rows) {
    const key = `${r.referenceType}:${r.referenceId}`;
    const g = groups.get(key) ?? { key, referenceType: r.referenceType, referenceId: r.referenceId, createdAt: r.createdAt, rows: [] };
    g.rows.push(r);
    groups.set(key, g);
  }
  return [...groups.values()];
}

/** TARBİL düşümü bekleyen kalemler (spec 2026-10-09 S5.5); liste boşsa çizilmez. */
export function PendingDeductionsPanel({ canAct, onSettled }: { canAct: boolean; onSettled: () => void }) {
  const [rows, setRows] = useState<PendingDeduction[]>([]);
  const [confirming, setConfirming] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function load() {
    return inventoryApi
      .listPendingDeductions()
      .then(setRows)
      .catch(() => setRows([]));
  }

  useEffect(() => {
    void load();
  }, []);

  if (rows.length === 0) return null;

  async function settle(group: Group) {
    if (confirming !== group.key) {
      setConfirming(group.key);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await inventoryApi.settleVetlyOnly(group.referenceType, group.referenceId);
      setConfirming(null);
      await load();
      onSettled();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'İşlem yapılamadı');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={styles.section}>
      <div className={styles.title}>TARBİL düşümü bekleyen {rows.length} kalem</div>
      <div className={styles.sub}>
        Bu kalemler TARBİL'de düşüm onaylanınca Vetly stoğundan düşer. TARBİL'e gitmeyecekseniz yalnız Vetly'den düşebilirsiniz.
      </div>
      {error && <div className={styles.error}>{error}</div>}
      {groupBySource(rows).map((g) => (
        <div key={g.key} className={styles.group}>
          <div className={styles.groupHead}>
            <span>
              {SOURCE[g.referenceType]} · {new Date(g.createdAt).toLocaleDateString('tr-TR')}
            </span>
            {canAct && (
              <Button variant="tertiary" disabled={busy} onClick={() => settle(g)}>
                {confirming === g.key
                  ? `Emin misiniz? ${ALL_OF[g.referenceType]} yalnız Vetly'den düş`
                  : `${ALL_OF[g.referenceType]} yalnız Vetly'den düş`}
              </Button>
            )}
          </div>
          <table className={styles.table}>
            <tbody>
              {g.rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.itemName}</td>
                  <td>Seri {r.lotNumber ?? '—'}</td>
                  <td>{r.quantity.toLocaleString('tr-TR')} {unitLabel(r.unit)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </section>
  );
}
