import { useCallback, useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { patientApi, PatientSearchResult } from '../../api/patientApi';
import { NewPatientModal } from './NewPatientModal';
import { PatientProfileModal } from './PatientProfileModal';
import { PatientStatusBadge } from './statusBadge';
import styles from './PatientsPage.module.css';

export function PatientsPage() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PatientSearchResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const runSearch = useCallback((q: string) => {
    setLoading(true);
    patientApi
      .search(q)
      .then(setResults)
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    runSearch('');
  }, [runSearch]);

  useEffect(() => {
    const handle = setTimeout(() => runSearch(query), 300);
    return () => clearTimeout(handle);
  }, [query, runSearch]);

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Hastalar &amp; Sahipler</h1>
          <div className={styles.sub}>Hasta adı, sahip adı veya telefon numarasıyla arayın</div>
        </div>
        <div className={styles.actions}>
          <input
            className={styles.search}
            type="text"
            placeholder="Ara..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <Button variant="primary" onClick={() => setCreateOpen(true)}>
            Yeni Kayıt
          </Button>
        </div>
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Hasta</div>
          <div>Tür / Irk</div>
          <div>Sahip</div>
          <div>Telefon</div>
          <div>Durum</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : results.length === 0 ? (
          <div className={styles.empty}>Kayıt bulunamadı</div>
        ) : (
          results.map((r) => (
            <div key={r.patientId} className={styles.row} onClick={() => setSelectedPatientId(r.patientId)}>
              <div className={styles.patientName}>{r.patientName}</div>
              <div className={styles.muted}>
                {r.speciesName}
                {r.breedName ? ` · ${r.breedName}` : ''}
              </div>
              <div>{r.ownerFullName}</div>
              <div className={styles.muted}>{r.ownerPhone}</div>
              <div>
                <PatientStatusBadge status={r.status} />
              </div>
            </div>
          ))
        )}
      </div>

      <PatientProfileModal
        patientId={selectedPatientId}
        onClose={() => setSelectedPatientId(null)}
        onChanged={() => runSearch(query)}
      />
      <NewPatientModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => runSearch(query)}
      />
    </AppShell>
  );
}
