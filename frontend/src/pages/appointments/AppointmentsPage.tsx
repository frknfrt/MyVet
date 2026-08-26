import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { appointmentApi, AppointmentItem, StaffItem } from '../../api/appointmentApi';
import { AppointmentActionsModal } from './AppointmentActionsModal';
import { AppointmentStatusBadge } from './appointmentStatus';
import { ScheduleAppointmentModal } from './ScheduleAppointmentModal';
import { addDays, formatDayLabel, formatTime, formatWeekRange, isoDate, mondayOf } from './weekUtils';
import styles from './AppointmentsPage.module.css';

type Mode = 'week' | 'day';

function sortByStart(list: AppointmentItem[]): AppointmentItem[] {
  return list.slice().sort((a, b) => a.scheduledStart.localeCompare(b.scheduledStart));
}

export function AppointmentsPage() {
  const [mode, setMode] = useState<Mode>('week');
  const [cursor, setCursor] = useState(() => new Date());
  const [appointments, setAppointments] = useState<AppointmentItem[]>([]);
  const [doctors, setDoctors] = useState<StaffItem[]>([]);
  const [doctorFilter, setDoctorFilter] = useState('');
  const [selected, setSelected] = useState<AppointmentItem | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [loading, setLoading] = useState(true);

  const monday = useMemo(() => mondayOf(cursor), [cursor]);
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

  useEffect(() => {
    appointmentApi.listStaff().then((list) => setDoctors(list.filter((s) => s.role === 'VET')));
  }, []);

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(monday, i)), [monday]);
  const todayIso = isoDate(new Date());
  const cursorIso = isoDate(cursor);

  const filteredAppointments = useMemo(
    () => (doctorFilter ? appointments.filter((a) => a.assignedStaffId === doctorFilter) : appointments),
    [appointments, doctorFilter]
  );

  function appointmentsForDay(day: Date) {
    const dayIso = isoDate(day);
    return sortByStart(filteredAppointments.filter((a) => a.scheduledStart.slice(0, 10) === dayIso));
  }

  function appointmentsForDoctor(doctorId: string) {
    return sortByStart(
      appointments.filter((a) => a.assignedStaffId === doctorId && a.scheduledStart.slice(0, 10) === cursorIso)
    );
  }

  function navPrev() {
    setCursor((c) => addDays(c, mode === 'week' ? -7 : -1));
  }
  function navNext() {
    setCursor((c) => addDays(c, mode === 'week' ? 7 : 1));
  }
  function goToday() {
    setCursor(new Date());
  }

  const visibleDoctors = doctorFilter ? doctors.filter((d) => d.id === doctorFilter) : doctors;

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Randevu Takvimi</h1>
          <div className={styles.weekNav}>
            <button className={styles.navBtn} onClick={navPrev} aria-label="Önceki">
              ‹
            </button>
            <span className={styles.weekLabel}>
              {mode === 'week' ? formatWeekRange(monday) : formatDayLabel(cursor)}
            </span>
            <button className={styles.navBtn} onClick={navNext} aria-label="Sonraki">
              ›
            </button>
            <Button variant="secondary" onClick={goToday}>
              Bugün
            </Button>
          </div>
        </div>

        <div className={styles.modeTabs}>
          <div className={`${styles.modeTab} ${mode === 'week' ? styles.modeTabActive : ''}`} onClick={() => setMode('week')}>
            Haftalık
          </div>
          <div className={`${styles.modeTab} ${mode === 'day' ? styles.modeTabActive : ''}`} onClick={() => setMode('day')}>
            Günlük / Hekim
          </div>
        </div>

        <div className={styles.actions}>
          <select className={styles.doctorFilter} value={doctorFilter} onChange={(e) => setDoctorFilter(e.target.value)}>
            <option value="">Tüm Hekimler</option>
            {doctors.map((d) => (
              <option key={d.id} value={d.id}>
                {d.fullName}
              </option>
            ))}
          </select>
          <Button variant="primary" onClick={() => setCreateOpen(true)}>
            Yeni Randevu
          </Button>
        </div>
      </div>

      {mode === 'week' ? (
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
      ) : visibleDoctors.length === 0 ? (
        <div className={styles.dayEmpty}>Kayıtlı hekim bulunmuyor</div>
      ) : (
        <div className={styles.gridDaily}>
          {visibleDoctors.map((doctor) => {
            const doctorAppointments = appointmentsForDoctor(doctor.id);
            return (
              <div key={doctor.id} className={styles.dayColumn}>
                <div className={styles.dayHeader}>{doctor.fullName}</div>
                <div className={styles.dayBody}>
                  {loading ? (
                    <div className={styles.dayEmpty}>...</div>
                  ) : doctorAppointments.length === 0 ? (
                    <div className={styles.dayEmpty}>Randevu yok</div>
                  ) : (
                    doctorAppointments.map((a) => (
                      <div key={a.id} className={styles.card} onClick={() => setSelected(a)}>
                        <div className={styles.cardTime}>
                          {formatTime(a.scheduledStart)}–{formatTime(a.scheduledEnd)}
                        </div>
                        <div className={styles.cardPatient}>{a.patientName}</div>
                        <div className={styles.cardMeta}>{a.serviceName}</div>
                        <AppointmentStatusBadge status={a.status} />
                      </div>
                    ))
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

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
