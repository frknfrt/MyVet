import { useMemo, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { MOCK_PATIENTS, PatientStatus, STATUS_TABS } from '../../data/worklist';
import { WorklistRow } from './WorklistRow';
import { QuickAddMenu } from './QuickAddMenu';
import { BusinessSummaryView } from './BusinessSummaryView';
import styles from './DashboardPage.module.css';

type ViewMode = 'operational' | 'summary';

export function DashboardPage() {
  const [viewMode, setViewMode] = useState<ViewMode>('operational');
  const [activeTab, setActiveTab] = useState<PatientStatus>('active');
  const [onlyMine, setOnlyMine] = useState(false);

  const pool = useMemo(
    () => (onlyMine ? MOCK_PATIENTS.filter((p) => p.mine) : MOCK_PATIENTS),
    [onlyMine]
  );
  const rows = useMemo(() => pool.filter((p) => p.status === activeTab), [pool, activeTab]);

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>{viewMode === 'operational' ? 'Hasta kuyruğu' : 'İşletme paneli'}</h1>
          <div className={styles.sub}>27 Temmuz 2026 · Merkez Şube</div>
        </div>

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

        {viewMode === 'operational' && (
          <div className={styles.actions}>
            <input className={styles.search} type="text" placeholder="Hasta veya sahip ara" />
            <Button variant={onlyMine ? 'primary' : 'secondary'} onClick={() => setOnlyMine((v) => !v)}>
              Bana atanan
            </Button>
            <QuickAddMenu />
          </div>
        )}
      </div>

      {viewMode === 'summary' ? (
        <BusinessSummaryView />
      ) : (
        <>
          <div className={styles.kpiStrip}>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Bugünkü randevu</div>
              <div className={styles.kpiValue}>18</div>
            </div>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Bekleme salonu</div>
              <div className={styles.kpiValue}>3</div>
            </div>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Yatan hasta</div>
              <div className={styles.kpiValue}>4</div>
            </div>
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>No-show riski yüksek</div>
              <div className={`${styles.kpiValue} ${styles.warn}`}>2</div>
            </div>
          </div>

          <div className={styles.tabs}>
            {STATUS_TABS.map((tab) => {
              const count = pool.filter((p) => p.status === tab.key).length;
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
              <div>Giriş</div>
              <div>Konum</div>
              <div>Hekim</div>
              <div>Atanan</div>
              <div>Geliş sebebi</div>
              <div>Durum</div>
            </div>
            {rows.length === 0 ? (
              <div className={styles.empty}>Bu durumda hasta bulunmuyor</div>
            ) : (
              rows.map((p, i) => <WorklistRow key={p.id} patient={p} delayMs={i * 40} />)
            )}
          </div>
        </>
      )}
    </AppShell>
  );
}
