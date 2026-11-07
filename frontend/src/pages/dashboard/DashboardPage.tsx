import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { appointmentApi, AppointmentItem, AppointmentStatus } from '../../api/appointmentApi';
import { billingApi, TodaySalesSummary } from '../../api/billingApi';
import { useAuth } from '../../auth/AuthContext';
import { isoDate } from '../appointments/weekUtils';
import { QuickSaleModal } from '../finance/QuickSaleModal';
import { BusinessSummaryView } from './BusinessSummaryView';
import { NewEncounterModal } from './NewEncounterModal';
import { QuickAddMenu } from './QuickAddMenu';
import { WorklistRow } from './WorklistRow';
import styles from './DashboardPage.module.css';

type ViewMode = 'operational' | 'summary';
type Tab = 'all' | Extract<AppointmentStatus, 'REQUESTED' | 'CHECKED_IN' | 'IN_PROGRESS' | 'COMPLETED'>;

const TABS: { key: Tab; label: string }[] = [
  { key: 'all', label: 'Bugün Tümü' },
  { key: 'REQUESTED', label: 'Onay Bekleyen' },
  { key: 'CHECKED_IN', label: 'Bekleme Salonu' },
  { key: 'IN_PROGRESS', label: 'Muayenede' },
  { key: 'COMPLETED', label: 'Tamamlanan' },
];

const NO_SHOW_RISK_THRESHOLD = 0.3;

/**
 * ADMIN ve RECEPTIONIST disindaki roller /invoices/** uc noktalarina erisemez
 * (api-conventions.md rol matrisi) -- BusinessSummaryView bu uc noktalara
 * bagimli oldugu icin "Isletme ozeti" sekmesi sadece bu iki role gosterilir.
 */
function canSeeBusinessSummary(role: string | undefined): boolean {
  return role === 'ADMIN' || role === 'RECEPTIONIST';
}

export function DashboardPage() {
  const { session } = useAuth();
  const navigate = useNavigate();
  const role = session?.role;
  // Panel herkes icin hasta kuyruguyla acilir; isletme ozeti istege bagli sekmedir.
  const [viewMode, setViewMode] = useState<ViewMode>('operational');
  const [activeTab, setActiveTab] = useState<Tab>(role === 'TECHNICIAN' ? 'CHECKED_IN' : 'all');
  const [onlyMine, setOnlyMine] = useState(role === 'VET');
  const [query, setQuery] = useState('');
  const [appointments, setAppointments] = useState<AppointmentItem[]>([]);
  const generation = useRef(0);
  const [loadError, setLoadError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [quickSaleOpen, setQuickSaleOpen] = useState(false);
  const [newEncounterOpen, setNewEncounterOpen] = useState(false);
  const [todaySales, setTodaySales] = useState<TodaySalesSummary | null>(null);

  const todayIso = isoDate(new Date());

  function load() {
    const request = ++generation.current;
    setLoading(true);
    setLoadError(false);
    appointmentApi.dailyCalendar(todayIso)
      .then(list => { if (request === generation.current) setAppointments(list); })
      .catch(() => { if (request === generation.current) setLoadError(true); })
      .finally(() => { if (request === generation.current) setLoading(false); });
    if (canSeeBusinessSummary(role)) {
      billingApi.todaySalesSummary().then(setTodaySales).catch(() => setTodaySales(null));
    }
  }

  useEffect(() => {
    load();
    return () => { generation.current++; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const pool = useMemo(() => {
    let list = appointments;
    if (onlyMine && session) {
      list = list.filter((a) => a.assignedStaffId === session.staffUserId);
    }
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      list = list.filter((a) => a.patientName.toLowerCase().includes(q) || a.ownerName.toLowerCase().includes(q));
    }
    return list;
  }, [appointments, onlyMine, query, session]);

  const rows = useMemo(() => {
    const filtered = activeTab === 'all' ? pool : pool.filter((a) => a.status === activeTab);
    return filtered.slice().sort((a, b) => a.scheduledStart.localeCompare(b.scheduledStart));
  }, [pool, activeTab]);

  const requestedCount = pool.filter((a) => a.status === 'REQUESTED').length;
  const checkedInCount = pool.filter((a) => a.status === 'CHECKED_IN').length;
  const highRiskCount = pool.filter((a) => (a.noShowRiskScore ?? 0) >= NO_SHOW_RISK_THRESHOLD).length;

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>{viewMode === 'operational' ? 'Hasta kuyruğu' : 'İşletme paneli'}</h1>
          <div className={styles.sub}>
            {new Date().toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric' })} · Merkez Şube
          </div>
        </div>

        {canSeeBusinessSummary(role) && (
          <div className={styles.modeTabs}>
            <div
              className={`${styles.modeTab} ${viewMode === 'operational' ? styles.modeTabActive : ''}`}
              onClick={() => setViewMode('operational')}
            >
              Operasyonel
            </div>
            <div
              className={`${styles.modeTab} ${viewMode === 'summary' ? styles.modeTabActive : ''}`}
              onClick={() => setViewMode('summary')}
            >
              İşletme özeti
            </div>
          </div>
        )}

        {viewMode === 'operational' && (
          <div className={styles.actions}>
            <input
              className={styles.search}
              type="text"
              placeholder="Hasta veya sahip ara"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <Button variant={onlyMine ? 'primary' : 'secondary'} onClick={() => setOnlyMine((v) => !v)}>
              Bana atanan
            </Button>
            <QuickAddMenu
              role={role}
              onQuickSale={() => setQuickSaleOpen(true)}
              onNewEncounter={() => setNewEncounterOpen(true)}
            />
          </div>
        )}
      </div>

      {viewMode === 'summary' && canSeeBusinessSummary(role) ? (
        <BusinessSummaryView />
      ) : (
        <>
          <div className={styles.kpiStrip}>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Bugünkü randevu</div>
              <div className={styles.kpiValue}>{pool.length}</div>
            </div>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Onay bekleyen</div>
              <div className={styles.kpiValue}>{requestedCount}</div>
            </div>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Bekleme salonu</div>
              <div className={styles.kpiValue}>{checkedInCount}</div>
            </div>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Gelmeme riski yüksek</div>
              <div className={`${styles.kpiValue} ${styles.warn}`}>{highRiskCount}</div>
            </div>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Bugünkü satış</div>
              <div className={styles.kpiValue}>
                {todaySales ? `${todaySales.totalAmount.toFixed(0)} ₺` : '—'}
              </div>
            </div>
          </div>

          <div className={styles.tabs}>
            {TABS.map((tab) => {
              const count = tab.key === 'all' ? pool.length : pool.filter((a) => a.status === tab.key).length;
              const isActive = tab.key === activeTab;
              return (
                <div
                  key={tab.key}
                  className={`${styles.tab} ${isActive ? styles.tabActive : ''}`}
                  onClick={() => setActiveTab(tab.key)}
                >
                  {tab.label} <span className={styles.tabBadge}>{count}</span>
                </div>
              );
            })}
          </div>

          <div className={styles.tableCard}>
            <div className={styles.tableHead}>
              <div>Hasta</div>
              <div>Saat</div>
              <div>Hekim</div>
              <div>Hizmet</div>
              <div>Kaynak</div>
              <div>Durum</div>
            </div>
            {loading ? (
              <div className={styles.empty}>Yükleniyor...</div>
            ) : loadError ? (
              <div role="alert">Randevular yüklenemedi. <button onClick={load}>Tekrar dene</button></div>
            ) : rows.length === 0 ? (
              <div className={styles.empty}>Bu durumda randevu bulunmuyor</div>
            ) : (
              rows.map((a, i) => (
                <WorklistRow key={a.id} appointment={a} delayMs={i * 40} onClick={() => navigate(`/hastalar/${a.patientId}`)} />
              ))
            )}
          </div>
        </>
      )}

      <QuickSaleModal
        open={quickSaleOpen}
        onClose={() => setQuickSaleOpen(false)}
        onCompleted={() => {
          setQuickSaleOpen(false);
          load();
        }}
      />
      <NewEncounterModal open={newEncounterOpen} onClose={() => setNewEncounterOpen(false)} />
    </AppShell>
  );
}
