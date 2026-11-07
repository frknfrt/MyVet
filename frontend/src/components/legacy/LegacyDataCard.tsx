import { useEffect, useState } from 'react';
import { dataImportApi, LegacyData, LegacyEntityType } from '../../api/dataImportApi';
import styles from './LegacyDataCard.module.css';

interface LegacyDataCardProps {
  entityType: LegacyEntityType;
  id: string;
}

/** Eski sistemden aktarilan kaydin ek bilgilerini katlanir kart olarak gosterir; aktarilmamissa hicbir sey cizmez. */
export function LegacyDataCard({ entityType, id }: LegacyDataCardProps) {
  const [legacy, setLegacy] = useState<LegacyData | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLegacy(null);
    setOpen(false);
    dataImportApi
      .getLegacyData(entityType, id)
      .then((result) => {
        if (!cancelled) setLegacy(result);
      })
      .catch(() => {
        // Ek bilgi karti zorunlu degil; hata durumunda sessizce gizlenir.
      });
    return () => {
      cancelled = true;
    };
  }, [entityType, id]);

  if (!legacy) return null;

  const entries = Object.entries(legacy.data);

  return (
    <div className={styles.card}>
      <button
        type="button"
        className={styles.toggle}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <span>Eski sistem bilgileri ({legacy.sourceSystem})</span>
        <span aria-hidden="true">{open ? '▾' : '▸'}</span>
      </button>
      {open && (
        <div className={styles.body}>
          {entries.length === 0 ? (
            <div className={styles.muted}>Ek bilgi yok</div>
          ) : (
            <dl className={styles.list}>
              {entries.map(([key, value]) => (
                <div key={key} className={styles.row}>
                  <dt className={styles.key}>{key}</dt>
                  <dd className={styles.value}>{value}</dd>
                </div>
              ))}
            </dl>
          )}
        </div>
      )}
    </div>
  );
}
