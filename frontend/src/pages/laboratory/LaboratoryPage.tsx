import { useEffect, useMemo, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { labApi, LabResultSummary } from '../../api/labApi';
import { LabResultDetailModal } from './LabResultDetailModal';
import { LabResultStatusBadge } from './labResultStatus';
import { NewLabResultModal } from './NewLabResultModal';
import styles from './LaboratoryPage.module.css';

type Tab = 'son-islemler' | 'bekleyen';

function startOfWeek(d: Date) {
  const date = new Date(d);
  const day = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - day);
  date.setHours(0, 0, 0, 0);
  return date;
}

function isSameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

export function LaboratoryPage() {
  const [results, setResults] = useState<LabResultSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<Tab>('son-islemler');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [ownerFilter, setOwnerFilter] = useState('');
  const [patientFilter, setPatientFilter] = useState('');
  const [newResultOpen, setNewResultOpen] = useState(false);
  const [selectedResultId, setSelectedResultId] = useState<string | null>(null);

  function load() {
    setLoading(true);
    labApi
      .list()
      .then(setResults)
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
  }, []);

  const kpis = useMemo(() => {
    const now = new Date();
    const weekStart = startOfWeek(now);
    let today = 0;
    let week = 0;
    let month = 0;
    let pending = 0;
    results.forEach((r) => {
      const requested = new Date(r.requestedAt);
      if (isSameDay(requested, now)) today += 1;
      if (requested >= weekStart) week += 1;
      if (requested.getFullYear() === now.getFullYear() && requested.getMonth() === now.getMonth()) month += 1;
      if (r.status === 'PENDING') pending += 1;
    });
    return { today, week, month, pending };
  }, [results]);

  const ownerOptions = useMemo(() => {
    const map = new Map<string, string>();
    results.forEach((r) => map.set(r.ownerId, r.ownerFullName));
    return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1], 'tr'));
  }, [results]);

  const patientOptions = useMemo(() => {
    const map = new Map<string, string>();
    results.forEach((r) => map.set(r.patientId, r.patientName));
    return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1], 'tr'));
  }, [results]);

  const filtered = useMemo(() => {
    const from = dateFrom ? new Date(dateFrom + 'T00:00:00') : null;
    const to = dateTo ? new Date(dateTo + 'T23:59:59') : null;
    return results
      .filter((r) => (tab === 'bekleyen' ? r.status === 'PENDING' : r.status !== 'PENDING'))
      .filter((r) => !ownerFilter || r.ownerId === ownerFilter)
      .filter((r) => !patientFilter || r.patientId === patientFilter)
      .filter((r) => {
        const requested = new Date(r.requestedAt);
        return (!from || requested >= from) && (!to || requested <= to);
      })
      .sort((a, b) => new Date(b.requestedAt).getTime() - new Date(a.requestedAt).getTime());
  }, [results, tab, ownerFilter, patientFilter, dateFrom, dateTo]);

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Laboratuvar</h1>
          <div className={styles.sub}>Laboratuvar sonuçları ve bekleyen istekler</div>
        </div>
        <Button variant="primary" onClick={() => setNewResultOpen(true)}>
          + Yeni Sonuç Yükle
        </Button>
      </div>

      <div className={styles.kpiRow}>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconToday}`}>✓</div>
          <div>
            <div className={styles.kpiValue}>{kpis.today}</div>
            <div className={styles.kpiLabel}>Bugün</div>
          </div>
        </div>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconWeek}`}>✓</div>
          <div>
            <div className={styles.kpiValue}>{kpis.week}</div>
            <div className={styles.kpiLabel}>Bu Hafta</div>
          </div>
        </div>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconMonth}`}>✓</div>
          <div>
            <div className={styles.kpiValue}>{kpis.month}</div>
            <div className={styles.kpiLabel}>Bu Ay</div>
          </div>
        </div>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconPending}`}>⏳</div>
          <div>
            <div className={styles.kpiValue}>{kpis.pending}</div>
            <div className={styles.kpiLabel}>Bekleyen İstekler</div>
          </div>
        </div>
      </div>

      <div className={styles.filterBar}>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Tarih aralığı (başlangıç)</label>
          <input type="date" className={styles.filterInput} value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Tarih aralığı (bitiş)</label>
          <input type="date" className={styles.filterInput} value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Müşteriye göre</label>
          <select className={styles.filterInput} value={ownerFilter} onChange={(e) => setOwnerFilter(e.target.value)}>
            <option value="">Tüm müşteriler</option>
            {ownerOptions.map(([id, name]) => (
              <option key={id} value={id}>
                {name}
              </option>
            ))}
          </select>
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Hastaya göre</label>
          <select className={styles.filterInput} value={patientFilter} onChange={(e) => setPatientFilter(e.target.value)}>
            <option value="">Tümü</option>
            {patientOptions.map(([id, name]) => (
              <option key={id} value={id}>
                {name}
              </option>
            ))}
          </select>
        </div>
        <Button variant="primary" onClick={load}>
          Filtrele
        </Button>
      </div>

      <div className={styles.tabs}>
        <div
          className={`${styles.tab} ${tab === 'son-islemler' ? styles.tabActive : ''}`}
          onClick={() => setTab('son-islemler')}
        >
          Son İşlemler
        </div>
        <div className={`${styles.tab} ${tab === 'bekleyen' ? styles.tabActive : ''}`} onClick={() => setTab('bekleyen')}>
          Bekleyen İstekler ({kpis.pending})
        </div>
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Hasta</div>
          <div>Sahip</div>
          <div>Test</div>
          <div>İstek Tarihi</div>
          <div>Durum</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className={styles.emptyBig}>
            <div className={styles.emptyIcon}>+</div>
            <div>Henüz sonuç yüklenmemiş</div>
            <div className={styles.emptySub}>İlk laboratuvar sonucunu yüklemek için buraya tıklayın</div>
            <Button variant="secondary" onClick={() => setNewResultOpen(true)}>
              Yeni Sonuç Yükle
            </Button>
          </div>
        ) : (
          filtered.map((r) => (
            <div key={r.id} className={styles.row} onClick={() => setSelectedResultId(r.id)}>
              <div className={styles.patientName}>{r.patientName}</div>
              <div className={styles.muted}>{r.ownerFullName}</div>
              <div>{r.testName}</div>
              <div className={styles.muted}>{new Date(r.requestedAt).toLocaleDateString('tr-TR')}</div>
              <div>
                <LabResultStatusBadge status={r.status} />
              </div>
            </div>
          ))
        )}
      </div>

      <NewLabResultModal
        open={newResultOpen}
        onClose={() => setNewResultOpen(false)}
        onCreated={(id) => {
          setNewResultOpen(false);
          load();
          setSelectedResultId(id);
        }}
      />
      <LabResultDetailModal resultId={selectedResultId} onClose={() => setSelectedResultId(null)} onChanged={load} />
    </AppShell>
  );
}
