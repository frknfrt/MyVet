import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { appointmentApi, AppointmentItem } from '../../api/appointmentApi';
import { AppointmentActionsModal } from './AppointmentActionsModal';
import { AppointmentStatusBadge } from './appointmentStatus';
import { ScheduleAppointmentModal } from './ScheduleAppointmentModal';
import { addDays, formatDayLabel, formatTime, formatWeekRange, isoDate, mondayOf } from './weekUtils';
import styles from './AppointmentsPage.module.css';

export function AppointmentsPage() {
  const [monday, setMonday] = useState(() => mondayOf(new Date()));
  const [appointments, setAppointments] = useState<AppointmentItem[]>([]);
  const [selected, setSelected] = useState<AppointmentItem | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [loading, setLoading] = useState(true);

  const weekStartIso = isoDate(monday);

  const load = useCallback(() => {
    setLoading(true);
    appointmentApi
      .weeklyCalendar(weekStartIso)
      .then(setAppointments)
      .finally(() => setLoading(false));
  }, [weekStartIso]);

  useEffect(() => {
    load();
  }, [load]);

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(monday, i)), [monday]);
  const todayIso = isoDate(new Date());

  function appointmentsForDay(day: Date) {
    const dayIso = isoDate(day);
    return appointments
      .filter((a) => a.scheduledStart.slice(0, 10) === dayIso)
      .sort((a, b) => a.scheduledStart.localeCompare(b.scheduledStart));
  }

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Randevu Takvimi</h1>
          <div className={styles.weekNav}>
            <button className={styles.navBtn} onClick={() => setMonday(addDays(monday, -7))} aria-label="Önceki hafta">
              ‹
            </button>
            <span className={styles.weekLabel}>{formatWeekRange(monday)}</span>
            <button className={styles.navBtn} onClick={() => setMonday(addDays(monday, 7))} aria-label="Sonraki hafta">
              ›
            </button>
            <Button variant="secondary" onClick={() => setMonday(mondayOf(new Date()))}>
              Bugün
            </Button>
          </div>
        </div>
        <Button variant="primary" onClick={() => setCreateOpen(true)}>
          Yeni Randevu
        </Button>
      </div>

      <div className={styles.grid}>
        {days.map((day) => {
          const dayIso = isoDate(day);
          const dayAppointments = appointmentsForDay(day);
          return (
            <div key={dayIso} className={styles.dayColumn}>
              <div className={`${styles.dayHeader} ${dayIso === todayIso ? styles.dayHeaderToday : ''}`}>
                {formatDayLabel(day)}
              </div>
              <div className={styles.dayBody}>
                {loading ? (
                  <div className={styles.dayEmpty}>...</div>
                ) : dayAppointments.length === 0 ? (
                  <div className={styles.dayEmpty}>Randevu yok</div>
                ) : (
                  dayAppointments.map((a) => (
                    <div key={a.id} className={styles.card} onClick={() => setSelected(a)}>
                      <div className={styles.cardTime}>
                        {formatTime(a.scheduledStart)}–{formatTime(a.scheduledEnd)}
                      </div>
                      <div className={styles.cardPatient}>{a.patientName}</div>
                      <div className={styles.cardMeta}>
                        {a.serviceName} · {a.staffName}
                      </div>
                      <AppointmentStatusBadge status={a.status} />
                    </div>
                  ))
                )}
              </div>
            </div>
          );
        })}
      </div>

      <AppointmentActionsModal appointment={selected} onClose={() => setSelected(null)} onChanged={load} />
      <ScheduleAppointmentModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onScheduled={load}
        defaultDate={weekStartIso}
      />
    </AppShell>
  );
}
