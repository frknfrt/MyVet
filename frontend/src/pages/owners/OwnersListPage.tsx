import { useCallback, useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { OwnerSearchResult, patientApi } from '../../api/patientApi';
import styles from './OwnersListPage.module.css';

/** api-conventions.md rol matrisi: /owners yazma -- VET/RECEPTIONIST/ADMIN. */
const CAN_WRITE_ROLES = ['VET', 'RECEPTIONIST', 'ADMIN'];

export function OwnersListPage() {
  const navigate = useNavigate();
  const { session } = useAuth();
  const canWrite = session ? CAN_WRITE_ROLES.includes(session.role) : false;
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<OwnerSearchResult[]>([]);
  const [loading, setLoading] = useState(true);

  const runSearch = useCallback((q: string) => {
    setLoading(true);
    patientApi
      .searchOwners(q)
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
          <div className={styles.sub}>Kayıtlı müşteriler (sahipler)</div>
        </div>
        {canWrite && (
          <Button variant="primary" onClick={() => navigate('/musteriler/yeni')}>
            Yeni Müşteri
          </Button>
        )}
      </div>

      <div className={styles.tabs}>
        <NavLink to="/hastalar" className={styles.tab}>
          Hastalar
        </NavLink>
        <NavLink to="/musteriler" className={`${styles.tab} ${styles.tabActive}`}>
          Sahipler
        </NavLink>
      </div>

      <div className={styles.filterBar}>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Arama</label>
          <input
            className={styles.filterInput}
            type="text"
            placeholder="Ad soyad veya telefon"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Ad Soyad</div>
          <div>Telefon</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : results.length === 0 ? (
          <div className={styles.empty}>Kayıt bulunamadı</div>
        ) : (
          results.map((o) => (
            <div key={o.id} className={styles.row} onClick={() => navigate(`/musteriler/${o.id}`)}>
              <div className={styles.ownerName}>{o.fullName}</div>
              <div className={styles.muted}>{o.phone}</div>
            </div>
          ))
        )}
      </div>
    </AppShell>
  );
}
