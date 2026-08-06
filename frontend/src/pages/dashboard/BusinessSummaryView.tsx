import { useEffect, useState } from 'react';
import { appointmentApi, AppointmentActivitySummary } from '../../api/appointmentApi';
import { billingApi, RevenueSummary } from '../../api/billingApi';
import { patientApi, PatientGrowthSummary } from '../../api/patientApi';
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

const MONTH_LABELS = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];

function monthLabel(yearMonth: string): string {
  const [, month] = yearMonth.split('-');
  const idx = Number(month) - 1;
  return MONTH_LABELS[idx] ?? yearMonth;
}

function formatCurrency(v: number): string {
  return `₺${v.toLocaleString('tr-TR', { maximumFractionDigits: 0 })}`;
}

function deltaLabel(current: number, previous: number): { text: string; tone: 'up' | 'down' | 'neutral' } {
  if (previous === 0) {
    if (current === 0) return { text: 'Geçen ayla aynı', tone: 'neutral' };
    return { text: 'Geçen ay veri yok', tone: 'neutral' };
  }
  const pct = ((current - previous) / previous) * 100;
  if (Math.abs(pct) < 0.5) return { text: 'Geçen ayla aynı', tone: 'neutral' };
  const rounded = Math.abs(pct).toFixed(0);
  return pct > 0
    ? { text: `↑ %${rounded} geçen aya göre`, tone: 'up' }
    : { text: `↓ %${rounded} geçen aya göre`, tone: 'down' };
}

function deltaClass(tone: 'up' | 'down' | 'neutral'): string {
  if (tone === 'up') return styles.kpiDeltaUp;
  if (tone === 'down') return styles.kpiDeltaDown;
  return styles.kpiDeltaNeutral;
}

/** No-show orani gibi "dusmesi iyi" metrikler icin up/down anlamini ters cevirir. */
function invertTone(tone: 'up' | 'down' | 'neutral'): 'up' | 'down' | 'neutral' {
  if (tone === 'up') return 'down';
  if (tone === 'down') return 'up';
  return 'neutral';
}

function buildInsight(
  revenue: RevenueSummary | null,
  activity: AppointmentActivitySummary | null
): string | null {
  if (!activity) return null;
  const noShowPct = activity.currentMonthNoShowRate * 100;
  const prevNoShowPct = activity.previousMonthNoShowRate * 100;
  if (noShowPct >= 15) {
    return `Bu ay no-show oranı %${noShowPct.toFixed(0)} — hatırlatma sıklığını artırmayı değerlendirin.`;
  }
  if (prevNoShowPct > 0 && noShowPct > prevNoShowPct * 1.5) {
    return `No-show oranı geçen aya göre belirgin arttı (%${prevNoShowPct.toFixed(0)} → %${noShowPct.toFixed(0)}).`;
  }
  if (revenue && revenue.previousMonthRevenue > 0) {
    const pct = ((revenue.currentMonthRevenue - revenue.previousMonthRevenue) / revenue.previousMonthRevenue) * 100;
    if (pct >= 10) return `Bu ay ciro geçen aya göre %${pct.toFixed(0)} arttı — güzel gidiyor.`;
    if (pct <= -10) return `Bu ay ciro geçen aya göre %${Math.abs(pct).toFixed(0)} azaldı — göz atmakta fayda var.`;
  }
  return null;
}

export function BusinessSummaryView() {
  const [revenue, setRevenue] = useState<RevenueSummary | null>(null);
  const [activity, setActivity] = useState<AppointmentActivitySummary | null>(null);
  const [growth, setGrowth] = useState<PatientGrowthSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      billingApi.revenueSummary().catch(() => null),
      appointmentApi.activitySummary().catch(() => null),
      patientApi.growthSummary().catch(() => null),
    ])
      .then(([r, a, g]) => {
        setRevenue(r);
        setActivity(a);
        setGrowth(g);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <div className={styles.loading}>Yükleniyor...</div>;
  }

  const revenueDelta = revenue ? deltaLabel(revenue.currentMonthRevenue, revenue.previousMonthRevenue) : null;
  const appointmentDelta = activity ? deltaLabel(activity.currentMonthCount, activity.previousMonthCount) : null;
  const patientDelta = growth ? deltaLabel(growth.newPatientsThisMonth, growth.newPatientsLastMonth) : null;
  const noShowDelta = activity
    ? deltaLabel(activity.currentMonthNoShowRate * 100, activity.previousMonthNoShowRate * 100)
    : null;

  const insight = buildInsight(revenue, activity);

  return (
    <div>
      <div className={styles.kpiStrip}>
        <Card>
          <div className={styles.kpiLabel}>Aylık ciro</div>
          <div className={styles.kpiValue}>{revenue ? formatCurrency(revenue.currentMonthRevenue) : '—'}</div>
          {revenueDelta && <div className={deltaClass(revenueDelta.tone)}>{revenueDelta.text}</div>}
        </Card>
        <Card>
          <div className={styles.kpiLabel}>Randevu sayısı</div>
          <div className={styles.kpiValue}>{activity ? activity.currentMonthCount : '—'}</div>
          {appointmentDelta && <div className={deltaClass(appointmentDelta.tone)}>{appointmentDelta.text}</div>}
        </Card>
        <Card>
          <div className={styles.kpiLabel}>Yeni hasta</div>
          <div className={styles.kpiValue}>{growth ? growth.newPatientsThisMonth : '—'}</div>
          {patientDelta && <div className={deltaClass(patientDelta.tone)}>{patientDelta.text}</div>}
        </Card>
        <Card>
          <div className={styles.kpiLabel}>No-show oranı</div>
          <div className={styles.kpiValue}>{activity ? `%${(activity.currentMonthNoShowRate * 100).toFixed(1)}` : '—'}</div>
          {noShowDelta && <div className={deltaClass(invertTone(noShowDelta.tone))}>{noShowDelta.text}</div>}
        </Card>
      </div>

      {insight && (
        <div className={styles.aiBanner}>
          <Badge tone="ai" icon={AI_ICON}>
            Otomatik özet
          </Badge>
          <span className={styles.aiText}>{insight}</span>
        </div>
      )}

      <div className={styles.chartsGrid}>
        <Card>
          <h3 className={styles.chartTitle}>Ciro trendi (son 6 ay)</h3>
          {revenue && revenue.monthlyTrend.length > 0 ? (
            <LineChart labels={revenue.monthlyTrend.map((m) => monthLabel(m.month))} values={revenue.monthlyTrend.map((m) => m.revenue)} />
          ) : (
            <div className={styles.emptyChart}>Henüz veri yok</div>
          )}
        </Card>
        <Card>
          <h3 className={styles.chartTitle}>Şube bazlı ciro (bu ay)</h3>
          {revenue && revenue.branchBreakdown.length > 0 ? (
            <BarChart
              data={revenue.branchBreakdown.map((b) => ({ label: b.branchName, value: b.revenue }))}
              formatValue={formatCurrency}
            />
          ) : (
            <div className={styles.emptyChart}>Bu ay henüz fatura kesilmedi</div>
          )}
        </Card>
      </div>
    </div>
  );
}
