import { useEffect, useState } from 'react';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { tarbilApi, TarbilStockSync, TarbilStockSyncStatus, TarbilStockSystem } from '../../api/tarbilApi';
import styles from './InventoryPage.module.css';

const STATUS: Record<TarbilStockSyncStatus, { label: string; tone: 'success' | 'warning' | 'neutral' | 'gold' }> = {
  NEW: { label: "Vetly'de yok", tone: 'gold' },
  QUANTITY_DIFFERS: { label: 'Miktar farklı', tone: 'warning' },
  MATCHED: { label: 'Eşleşti', tone: 'success' },
  APPLIED: { label: 'İşlendi', tone: 'neutral' },
};

/**
 * TARBIL stogu (eklentinin gonderdigi son goruntu) ile Vetly stogunu karsilastirir; secilen satirlari Vetly stoguna isler.
 * Spec 2026-10-04 S13. Yalniz ADMIN ve VET (/tarbil/** kurali).
 */
export function TarbilStockSyncPanel({ onApplied }: { onApplied: () => void }) {
  const [system, setSystem] = useState<TarbilStockSystem>('VETILAC_MEDICINE');
  const [data, setData] = useState<TarbilStockSync | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  function load(s: TarbilStockSystem) {
    setData(null);
    setSelected(new Set());
    tarbilApi.stockSync(s).then(setData).catch((e: Error) => setMessage(e.message));
  }

  useEffect(() => {
    load(system);
  }, [system]);

  const selectable = (data?.lines ?? []).filter((l) => l.status === 'NEW' || l.status === 'QUANTITY_DIFFERS');

  async function apply() {
    if (!data?.snapshotId || selected.size === 0) return;
    setBusy(true);
    setMessage(null);
    try {
      const res = await tarbilApi.applyStockSync(data.snapshotId, Array.from(selected));
      setMessage(`${res.applied} satır Vetly stoğuna işlendi.`);
      load(system);
      onApplied();
    } catch (e) {
      setMessage((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.tableCard} style={{ marginTop: 24, padding: 16 }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <strong>TARBİL Eşitleme</strong>
        <Button variant={system === 'VETILAC_MEDICINE' ? 'primary' : 'secondary'} onClick={() => setSystem('VETILAC_MEDICINE')}>İlaç</Button>
        <Button variant={system === 'HBSAPP_VACCINE' ? 'primary' : 'secondary'} onClick={() => setSystem('HBSAPP_VACCINE')}>Aşı</Button>
        <span className={styles.muted}>
          {data?.takenAt ? `TARBİL'den alındı: ${new Date(data.takenAt).toLocaleString('tr-TR')}` : ''}
        </span>
      </div>
      {!data ? (
        <div className={styles.empty}>Yükleniyor...</div>
      ) : !data.snapshotId ? (
        <div className={styles.empty}>
          Henüz TARBİL stoğu gönderilmedi. TARBİL'de {system === 'VETILAC_MEDICINE' ? 'İlaç Takip Sistemi > Stok Ara' : 'Aşı > Stok > Ara'} sayfasında Vetly kartındaki "TARBİL stoğunu Vetly'ye gönder" butonuna basın.
        </div>
      ) : (
        <>
          <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 12 }}>
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label="Hepsini seç"
                    checked={selectable.length > 0 && selected.size === selectable.length}
                    onChange={(e) => setSelected(e.target.checked ? new Set(selectable.map((l) => l.lineId)) : new Set())}
                  />
                </th>
                <th style={{ textAlign: 'left' }}>Ürün</th>
                <th style={{ textAlign: 'left' }}>Seri No</th>
                <th style={{ textAlign: 'left' }}>Son Kullanma</th>
                <th>TARBİL</th>
                <th>Vetly</th>
                <th>Durum</th>
              </tr>
            </thead>
            <tbody>
              {data.lines.map((l) => {
                const canSelect = l.status === 'NEW' || l.status === 'QUANTITY_DIFFERS';
                return (
                  <tr key={l.lineId}>
                    <td>
                      <input
                        type="checkbox"
                        disabled={!canSelect}
                        checked={selected.has(l.lineId)}
                        onChange={(e) => {
                          const next = new Set(selected);
                          if (e.target.checked) next.add(l.lineId);
                          else next.delete(l.lineId);
                          setSelected(next);
                        }}
                      />
                    </td>
                    <td>
                      {l.productName}
                      {l.presentation && <span className={styles.muted}> · {l.presentation}</span>}
                    </td>
                    <td>{l.lotNumber ?? '—'}</td>
                    <td>{l.expiryDate ? new Date(l.expiryDate).toLocaleDateString('tr-TR') : '—'}</td>
                    <td style={{ textAlign: 'center' }}>{l.tarbilQuantity}</td>
                    <td style={{ textAlign: 'center' }}>{l.vetlyQuantity ?? '—'}</td>
                    <td><Badge tone={STATUS[l.status].tone}>{STATUS[l.status].label}</Badge></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 12 }}>
            <Button variant="primary" disabled={busy || selected.size === 0} onClick={apply}>
              Seçilenleri Vetly stoğuna işle ({selected.size})
            </Button>
          </div>
        </>
      )}
      {message && <div className={styles.muted} style={{ marginTop: 8 }}>{message}</div>}
    </div>
  );
}
