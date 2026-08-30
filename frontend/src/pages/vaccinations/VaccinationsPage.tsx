import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { Button } from '../../components/ui/Button';
import { vaccinationApi, VaccinationScheduleItem } from '../../api/vaccinationApi';
import { VaccinationStatusBadge } from './vaccinationStatus';
import styles from './VaccinationsPage.module.css';

type Tab = 'planlanan' | 'yapilan';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

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

export function VaccinationsPage() {
  const navigate = useNavigate();
  const { session } = useAuth();
  // VaccinationRecordsController yazma -- VET/TECHNICIAN/ADMIN (RECEPTIONIST yok).
  const canWrite = session ? ['VET', 'TECHNICIAN', 'ADMIN'].includes(session.role) : false;
  const [items, setItems] = useState<VaccinationScheduleItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<Tab>('planlanan');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [ownerFilter, setOwnerFilter] = useState('');
  const [patientFilter, setPatientFilter] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    vaccinationApi
      .list()
      .then(setItems)
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
    let overdue = 0;
    let planned = 0;
    items.forEach((v) => {
      const date = new Date(v.administeredDate);
      if (v.status === 'SCHEDULED') {
        planned += 1;
        if (date.getTime() < now.setHours(0, 0, 0, 0)) overdue += 1;
      }
      if (isSameDay(date, new Date())) today += 1;
      if (date >= weekStart) week += 1;
    });
    return { today, week, overdue, planned };
  }, [items]);

  const ownerOptions = useMemo(() => {
    const map = new Map<string, string>();
    items.forEach((v) => map.set(v.ownerId, v.ownerFullName));
    return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1], 'tr'));
  }, [items]);

  const patientOptions = useMemo(() => {
    const map = new Map<string, string>();
    items.forEach((v) => map.set(v.patientId, v.patientName));
    return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1], 'tr'));
  }, [items]);

  const filtered = useMemo(() => {
    const from = dateFrom ? new Date(dateFrom + 'T00:00:00') : null;
    const to = dateTo ? new Date(dateTo + 'T23:59:59') : null;
    return items
      .filter((v) => (tab === 'planlanan' ? v.status === 'SCHEDULED' : v.status !== 'SCHEDULED'))
      .filter((v) => !ownerFilter || v.ownerId === ownerFilter)
      .filter((v) => !patientFilter || v.patientId === patientFilter)
      .filter((v) => {
        const date = new Date(v.administeredDate);
        return (!from || date >= from) && (!to || date <= to);
      })
      .sort((a, b) => new Date(a.administeredDate).getTime() - new Date(b.administeredDate).getTime());
  }, [items, tab, ownerFilter, patientFilter, dateFrom, dateTo]);

  async function handleMarkAdministered(id: string) {
    if (busyId) return;
    setBusyId(id);
    setError(null);
    try {
      await vaccinationApi.markAdministered(id);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusyId(null);
    }
  }

  async function handleCancel(id: string) {
    if (busyId) return;
    setBusyId(id);
    setError(null);
    try {
      await vaccinationApi.cancel(id);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Aşı Takvimi</h1>
          <div className={styles.sub}>Planlanan ve yapılan aşılar</div>
        </div>
        {canWrite && (
          <Button variant="primary" onClick={() => navigate('/asi-takvimi/yeni')}>
            + Yeni Aşı Ekle
          </Button>
        )}
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
          <div className={`${styles.kpiIcon} ${styles.kpiIconOverdue}`}>!</div>
          <div>
            <div className={styles.kpiValue}>{kpis.overdue}</div>
            <div className={styles.kpiLabel}>Gecikmiş</div>
          </div>
        </div>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconPlanned}`}>⏳</div>
          <div>
            <div className={styles.kpiValue}>{kpis.planned}</div>
            <div className={styles.kpiLabel}>Planlanan</div>
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

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.tabs}>
        <div className={`${styles.tab} ${tab === 'planlanan' ? styles.tabActive : ''}`} onClick={() => setTab('planlanan')}>
          Planlanan ({kpis.planned})
        </div>
        <div className={`${styles.tab} ${tab === 'yapilan' ? styles.tabActive : ''}`} onClick={() => setTab('yapilan')}>
          Yapılanlar
        </div>
      </div>

      <div className={styles.tableCard}>
        <div className={`${styles.tableHead} ${tab === 'planlanan' && canWrite ? styles.rowWithActions : styles.rowPlain}`}>
          <div>Hasta</div>
          <div>Sahip</div>
          <div>Aşı</div>
          <div>Tarih</div>
          <div>Durum</div>
          {tab === 'planlanan' && canWrite && <div>İşlem</div>}
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className={styles.emptyBig}>
            <div className={styles.emptyIcon}>+</div>
            <div>Henüz aşı kaydı yok</div>
            <div className={styles.emptySub}>İlk aşı kaydını eklemek için buraya tıklayın</div>
            {canWrite && (
              <Button variant="secondary" onClick={() => navigate('/asi-takvimi/yeni')}>
                Yeni Aşı Ekle
              </Button>
            )}
          </div>
        ) : (
          filtered.map((v) => (
            <div
              key={v.id}
              className={`${styles.row} ${tab === 'planlanan' && canWrite ? styles.rowWithActions : styles.rowPlain}`}
              onClick={() => navigate(`/hastalar/${v.patientId}`)}
            >
              <div className={styles.patientName}>{v.patientName}</div>
              <div className={styles.muted}>{v.ownerFullName}</div>
              <div>{v.vaccineName}</div>
              <div className={styles.muted}>{new Date(v.administeredDate).toLocaleDateString('tr-TR')}</div>
              <div>
                <VaccinationStatusBadge status={v.status} />
              </div>
              {tab === 'planlanan' && canWrite && (
                <div className={styles.actionsCell} onClick={(e) => e.stopPropagation()}>
                  <button
                    type="button"
                    className={styles.actionBtn}
                    disabled={busyId === v.id}
                    onClick={() => handleMarkAdministered(v.id)}
                  >
                    Yapıldı
                  </button>
                  <button
                    type="button"
                    className={`${styles.actionBtn} ${styles.actionBtnDanger}`}
                    disabled={busyId === v.id}
                    onClick={() => handleCancel(v.id)}
                  >
                    İptal
                  </button>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </AppShell>
  );
}
