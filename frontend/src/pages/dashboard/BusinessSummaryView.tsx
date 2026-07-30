import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LineChart } from '../../components/ui/LineChart';
import { BarChart } from '../../components/ui/BarChart';
import styles from './BusinessSummaryView.module.css';

const AI_ICON = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
    <path d="M12 3v3M12 18v3M4.2 7.5l2.6 1.5M17.2 15l2.6 1.5M4.2 16.5l2.6-1.5M17.2 9l2.6-1.5" />
  </svg>
);

const REVENUE_LABELS = ['Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem'];
const REVENUE_VALUES = [682, 705, 748, 771, 803, 843];
const BRANCH_DATA = [
  { label: 'Merkez', value: 412 },
  { label: 'Kadıköy', value: 268 },
  { label: 'Ataşehir', value: 162 },
];

export function BusinessSummaryView() {
  return (
    <div>
      <div className={styles.kpiStrip}>
        <Card>
          <div className={styles.kpiLabel}>Aylık ciro</div>
          <div className={styles.kpiValue}>₺842.500</div>
          <div className={styles.kpiDeltaUp}>↑ %12 geçen aya göre</div>
        </Card>
        <Card>
          <div className={styles.kpiLabel}>Randevu sayısı</div>
          <div className={styles.kpiValue}>486</div>
          <div className={styles.kpiDeltaUp}>↑ %6 geçen aya göre</div>
        </Card>
        <Card>
          <div className={styles.kpiLabel}>Yeni hasta</div>
          <div className={styles.kpiValue}>63</div>
          <div className={styles.kpiDeltaNeutral}>Geçen ayla aynı</div>
        </Card>
        <Card>
          <div className={styles.kpiLabel}>No-show oranı</div>
          <div className={styles.kpiValue}>%7.2</div>
          <div className={styles.kpiDeltaDown}>↑ %1.1 geçen aya göre</div>
        </Card>
      </div>

      <div className={styles.aiBanner}>
        <Badge tone="ai" icon={AI_ICON}>
          AI özeti
        </Badge>
        <span className={styles.aiText}>
          Kadıköy şubesinde no-show oranı diğerlerinin iki katı. Hatırlatma sıklığını artırmayı öner.
        </span>
      </div>

      <div className={styles.chartsGrid}>
        <Card>
          <h3 className={styles.chartTitle}>Ciro trendi</h3>
          <LineChart labels={REVENUE_LABELS} values={REVENUE_VALUES} />
        </Card>
        <Card>
          <h3 className={styles.chartTitle}>Şube bazlı ciro</h3>
          <BarChart data={BRANCH_DATA} formatValue={(v) => `₺${v}B`} />
        </Card>
      </div>
    </div>
  );
}
