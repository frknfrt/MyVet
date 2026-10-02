import { useEffect, useState } from 'react';
import { TarbilMapping, tarbilApi } from '../../api/tarbilApi';
import { Button } from '../../components/ui/Button';
import styles from './SettingsPage.module.css';

const KIND_LABELS = { VACCINE: 'Aşı', SPECIES: 'Tür' } as const;

function describeFields(fields: TarbilMapping['tarbilFields']) {
  return Object.entries(fields)
    .map(([key, v]) => `${key}: ${v.text ?? v.value ?? '?'}`)
    .join(' · ');
}

export function TarbilMappingsCard() {
  const [mappings, setMappings] = useState<TarbilMapping[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  function reload() {
    tarbilApi.mappings().then(setMappings);
  }

  useEffect(() => {
    reload();
  }, []);

  async function handleDelete(id: string) {
    setBusyId(id);
    try {
      await tarbilApi.deleteMapping(id);
      reload();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className={styles.tableCard}>
      <div className={styles.tableHead}>
        <div>Öğrenilen eşleştirme</div>
        <div>Tür</div>
        <div>TARBİL değeri</div>
        <div>Güncellendi</div>
        <div></div>
      </div>
      {mappings.length === 0 ? (
        <div className={styles.empty}>
          Henüz eşleştirme yok. Bir aşıyı ilk kez TARBİL'e aktardığınızda seçtiğiniz değerler burada görünür.
        </div>
      ) : (
        mappings.map((m) => (
          <div key={m.id} className={styles.row}>
            <div>{m.vetlyKey}</div>
            <div className={styles.muted}>{KIND_LABELS[m.kind]}</div>
            <div className={styles.muted}>{describeFields(m.tarbilFields)}</div>
            <div className={styles.muted}>{new Date(m.updatedAt).toLocaleString('tr-TR')}</div>
            <div>
              <Button variant="tertiary" onClick={() => handleDelete(m.id)} disabled={busyId === m.id}>
                Sil
              </Button>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
