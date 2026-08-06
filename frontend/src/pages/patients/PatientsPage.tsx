import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { patientApi, PatientSearchResult, SpeciesItem } from '../../api/patientApi';
import { PatientStatusBadge } from './statusBadge';
import styles from './PatientsPage.module.css';

export function PatientsPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [searchResults, setSearchResults] = useState<PatientSearchResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [speciesOptions, setSpeciesOptions] = useState<SpeciesItem[]>([]);
  const [ownerFilter, setOwnerFilter] = useState('');
  const [speciesFilter, setSpeciesFilter] = useState('');

  const runSearch = useCallback((q: string) => {
    setLoading(true);
    patientApi
      .search(q)
      .then(setSearchResults)
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    patientApi.listSpecies().then(setSpeciesOptions);
  }, []);

  useEffect(() => {
    runSearch('');
  }, [runSearch]);

  useEffect(() => {
    const handle = setTimeout(() => runSearch(query), 300);
    return () => clearTimeout(handle);
  }, [query, runSearch]);

  const ownerOptions = useMemo(() => {
    const map = new Map<string, string>();
    searchResults.forEach((r) => map.set(r.ownerId, r.ownerFullName));
    return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1], 'tr'));
  }, [searchResults]);

  const selectedSpeciesName = speciesOptions.find((s) => s.id === speciesFilter)?.name;

  const results = useMemo(
    () =>
      searchResults.filter(
        (r) =>
          (!ownerFilter || r.ownerId === ownerFilter) &&
          (!selectedSpeciesName || r.speciesName === selectedSpeciesName)
      ),
    [searchResults, ownerFilter, selectedSpeciesName]
  );

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Hastalar &amp; Sahipler</h1>
          <div className={styles.sub}>Kayıtlı hastalar ve sahipleri</div>
        </div>
        <div className={styles.actions}>
          <Button variant="secondary" onClick={() => navigate('/musteriler/yeni')}>
            Yeni Müşteri
          </Button>
          <Button variant="primary" onClick={() => navigate('/hastalar/yeni')}>
            Yeni Hasta
          </Button>
        </div>
      </div>

      <div className={styles.filterBar}>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Arama</label>
          <input
            className={styles.filterInput}
            type="text"
            placeholder="Hasta adı, mikroçip, hasta no vs."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Müşteriye göre</label>
          <select className={styles.filterInput} value={ownerFilter} onChange={(e) => setOwnerFilter(e.target.value)}>
            <option value="">Müşteri Seçiniz</option>
            {ownerOptions.map(([id, name]) => (
              <option key={id} value={id}>
                {name}
              </option>
            ))}
          </select>
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Türe göre</label>
          <select className={styles.filterInput} value={speciesFilter} onChange={(e) => setSpeciesFilter(e.target.value)}>
            <option value="">Tümü</option>
            {speciesOptions.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
        <Button variant="primary" onClick={() => runSearch(query)}>
          Filtrele
        </Button>
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
            <div key={r.patientId} className={styles.row} onClick={() => navigate(`/hastalar/${r.patientId}`)}>
              <div className={styles.patientName}>{r.patientName}</div>
              <div className={styles.muted}>
                {r.speciesName}
                {r.breedName ? ` · ${r.breedName}` : ''}
              </div>
              <div
                className={styles.ownerLink}
                onClick={(e) => {
                  e.stopPropagation();
                  navigate(`/musteriler/${r.ownerId}`);
                }}
              >
                {r.ownerFullName}
              </div>
              <div className={styles.muted}>{r.ownerPhone}</div>
              <div>
                <PatientStatusBadge status={r.status} />
              </div>
            </div>
          ))
        )}
      </div>
    </AppShell>
  );
}
