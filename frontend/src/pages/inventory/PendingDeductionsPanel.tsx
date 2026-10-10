import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { inventoryApi, PendingDeduction, PendingDeductionReference } from '../../api/inventoryApi';
import { Button } from '../../components/ui/Button';
import styles from './PendingDeductionsPanel.module.css';

const SOURCE: Record<PendingDeductionReference, string> = {
  ENCOUNTER: 'Muayene',
  VACCINATION: 'Aşı',
  PRESCRIPTION: 'Reçete',
};

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

  async function settle(row: PendingDeduction) {
    const key = `${row.referenceType}:${row.referenceId}`;
    if (confirming !== key) {
      setConfirming(key);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await inventoryApi.settleVetlyOnly(row.referenceType, row.referenceId);
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
      <table className={styles.table}>
        <thead>
          <tr>
            <th>Kalem</th>
            <th>Seri</th>
            <th>Adet</th>
            <th>Kaynak</th>
            <th>Tarih</th>
            {canAct && <th />}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const key = `${r.referenceType}:${r.referenceId}`;
            return (
              <tr key={r.id}>
                <td>{r.itemName}</td>
                <td>{r.lotNumber ?? '—'}</td>
                <td>{r.quantity}</td>
                <td>{SOURCE[r.referenceType]}</td>
                <td>{new Date(r.createdAt).toLocaleDateString('tr-TR')}</td>
                {canAct && (
                  <td>
                    <Button variant="tertiary" disabled={busy} onClick={() => settle(r)}>
                      {confirming === key ? 'Emin misiniz?' : "Yalnız Vetly'den düş"}
                    </Button>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}
