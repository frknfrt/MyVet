import { useEffect, useState } from 'react';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { tarbilApi, TarbilStockSync, TarbilStockSyncStatus, TarbilStockSystem } from '../../api/tarbilApi';
import styles from './TarbilStockSyncPanel.module.css';

const STATUS: Record<TarbilStockSyncStatus, { label: string; tone: 'success' | 'warning' | 'neutral' | 'gold' }> = {
  NEW: { label: "Vetly'de yok", tone: 'gold' },
  QUANTITY_DIFFERS: { label: 'Miktar farklı', tone: 'warning' },
  MATCHED: { label: 'Eşleşti', tone: 'success' },
  APPLIED: { label: 'İşlendi', tone: 'neutral' },
};

const TABS: { system: TarbilStockSystem; label: string; where: string }[] = [
  { system: 'VETILAC_MEDICINE', label: 'İlaç', where: 'İlaç Takip Sistemi > Stok Ara' },
  { system: 'HBSAPP_VACCINE', label: 'Aşı', where: 'Aşı > Stok > Ara' },
];

const canSelect = (s: TarbilStockSyncStatus) => s === 'NEW' || s === 'QUANTITY_DIFFERS';

/** "2027-01-31" -> "31.01.2027"; Date ile ayristirilmaz (UTC kaymasi olmasin). */
function trDate(iso: string | null): string {
  const m = iso ? /^(\d{4})-(\d{2})-(\d{2})/.exec(iso) : null;
  return m ? `${m[3]}.${m[2]}.${m[1]}` : '—';
}

/**
 * TARBIL stogu (eklentinin gonderdigi son goruntu) ile Vetly stogunu karsilastirir; secilen satirlari Vetly stoguna isler.
 * Spec 2026-10-04 S13. Yalniz ADMIN ve VET (/tarbil/** kurali).
 */
export function TarbilStockSyncPanel({ onApplied }: { onApplied: () => void }) {
  const [system, setSystem] = useState<TarbilStockSystem>('VETILAC_MEDICINE');
  const [data, setData] = useState<TarbilStockSync | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);

  function load(s: TarbilStockSystem) {
    setData(null);
    setLoadError(null);
    setSelected(new Set());
    tarbilApi.stockSync(s).then(setData).catch((e: Error) => setLoadError(e.message));
  }

  useEffect(() => {
    setMessage(null);
    load(system);
  }, [system]);

  const selectable = (data?.lines ?? []).filter((l) => canSelect(l.status));
  const allSelected = selectable.length > 0 && selected.size === selectable.length;

  function toggle(lineId: string) {
    const next = new Set(selected);
    if (next.has(lineId)) next.delete(lineId);
    else next.add(lineId);
    setSelected(next);
  }

  async function apply() {
    if (!data?.snapshotId || selected.size === 0) return;
    setBusy(true);
    setMessage(null);
    try {
      const res = await tarbilApi.applyStockSync(data.snapshotId, Array.from(selected));
      setMessage({ text: `${res.applied} satır Vetly stoğuna işlendi.`, error: false });
      load(system);
      onApplied();
    } catch (e) {
      setMessage({ text: (e as Error).message, error: true });
    } finally {
      setBusy(false);
    }
  }

  const where = TABS.find((t) => t.system === system)!.where;

  return (
    <section className={styles.section}>
      <div className={styles.header}>
        <div>
          <h2 className={styles.title}>TARBİL Eşitleme</h2>
          <div className={styles.sub}>TARBİL'deki stoğu Vetly stoğuyla karşılaştırın, farklı olanları işleyin</div>
        </div>
        {data?.takenAt && (
          <div className={styles.takenAt}>TARBİL'den alındı: {new Date(data.takenAt).toLocaleString('tr-TR')}</div>
        )}
      </div>

      <div className={styles.tabs}>
        {TABS.map((t) => (
          <button
            key={t.system}
            type="button"
            className={`${styles.tab} ${system === t.system ? styles.tabActive : ''}`}
            onClick={() => setSystem(t.system)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>
            <input
              type="checkbox"
              className={styles.check}
              aria-label="Hepsini seç"
              disabled={selectable.length === 0}
              checked={allSelected}
              onChange={() => setSelected(allSelected ? new Set() : new Set(selectable.map((l) => l.lineId)))}
            />
          </div>
          <div>Ürün</div>
          <div>Seri No</div>
          <div>Son Kullanma</div>
          <div className={styles.num}>TARBİL</div>
          <div className={styles.num}>Vetly</div>
          <div>Durum</div>
        </div>

        {loadError ? (
          <div className={`${styles.empty} ${styles.error}`}>{loadError}</div>
        ) : !data ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : !data.snapshotId ? (
          <div className={styles.empty}>
            Henüz TARBİL stoğu gönderilmedi.
            <br />
            TARBİL'de <strong>{where}</strong> sayfasında Vetly kartındaki "TARBİL stoğunu Vetly'ye gönder" butonuna basın.
          </div>
        ) : data.lines.length === 0 ? (
          <div className={styles.empty}>TARBİL stoğunda satır yok.</div>
        ) : (
          data.lines.map((l) => {
            const selectableRow = canSelect(l.status);
            return (
              <div
                key={l.lineId}
                className={`${styles.row} ${selectableRow ? styles.rowSelectable : ''}`}
                onClick={selectableRow ? () => toggle(l.lineId) : undefined}
              >
                <div>
                  <input
                    type="checkbox"
                    className={styles.check}
                    disabled={!selectableRow}
                    checked={selected.has(l.lineId)}
                    onClick={(e) => e.stopPropagation()}
                    onChange={() => toggle(l.lineId)}
                  />
                </div>
                <div className={styles.product}>
                  <div className={styles.productName} title={l.productName}>{l.productName}</div>
                  {l.presentation && <div className={`${styles.muted} ${styles.small}`}>{l.presentation}</div>}
                </div>
                <div className={l.lotNumber ? undefined : styles.muted}>{l.lotNumber ?? '—'}</div>
                <div className={styles.muted}>{trDate(l.expiryDate)}</div>
                <div className={styles.num}>{l.tarbilQuantity}</div>
                <div className={`${styles.num} ${l.vetlyQuantity === null ? styles.muted : ''}`}>{l.vetlyQuantity ?? '—'}</div>
                <div>
                  <Badge tone={STATUS[l.status].tone}>{STATUS[l.status].label}</Badge>
                </div>
              </div>
            );
          })
        )}

        {data?.snapshotId && data.lines.length > 0 && (
          <div className={styles.footer}>
            <span className={message?.error ? styles.error : styles.muted}>
              {message?.text ?? (selectable.length === 0 ? 'Vetly stoğu TARBİL ile aynı.' : `${selected.size} / ${selectable.length} satır seçili`)}
            </span>
            <Button variant="primary" disabled={busy || selected.size === 0} onClick={apply}>
              Seçilenleri Vetly stoğuna işle
            </Button>
          </div>
        )}
      </div>
    </section>
  );
}
