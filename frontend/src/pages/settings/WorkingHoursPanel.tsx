import { useEffect, useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { ApiError } from '../../api/client';
import { BranchItem, branchesApi } from '../../api/branchesApi';
import { StaffUserItem, staffUsersApi } from '../../api/staffUsersApi';
import { DayOfWeek, workingHoursApi } from '../../api/workingHoursApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Select } from '../../components/ui/Field';
import settingsStyles from './SettingsPage.module.css';
import styles from './WorkingHoursPanel.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Pazartesi',
  TUESDAY: 'Salı',
  WEDNESDAY: 'Çarşamba',
  THURSDAY: 'Perşembe',
  FRIDAY: 'Cuma',
  SATURDAY: 'Cumartesi',
  SUNDAY: 'Pazar',
};

function truncateTime(value: string | null): string {
  return value ? value.slice(0, 5) : '';
}

interface BranchDayState {
  closed: boolean;
  opensAt: string;
  closesAt: string;
}

type BranchHoursState = Record<DayOfWeek, BranchDayState>;

function defaultBranchHours(): BranchHoursState {
  const state = {} as BranchHoursState;
  DAYS.forEach((day) => {
    state[day] = { closed: true, opensAt: '09:00', closesAt: '18:00' };
  });
  return state;
}

interface ShiftBlock {
  startsAt: string;
  endsAt: string;
}

type ShiftsState = Record<DayOfWeek, ShiftBlock[]>;

function defaultShifts(): ShiftsState {
  const state = {} as ShiftsState;
  DAYS.forEach((day) => {
    state[day] = [];
  });
  return state;
}

function BranchWorkingHoursSection() {
  const { session } = useAuth();
  const [branches, setBranches] = useState<BranchItem[]>([]);
  const [branchId, setBranchId] = useState('');
  const [hours, setHours] = useState<BranchHoursState>(defaultBranchHours());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    branchesApi
      .list()
      .then((list) => {
        setBranches(list);
        setBranchId(session?.branchId && list.some((b) => b.branchId === session.branchId) ? session.branchId : list[0]?.branchId ?? '');
      })
      .catch((err) => setError(errorMessageOf(err)));
  }, [session?.branchId]);

  useEffect(() => {
    if (!branchId) return;
    setLoading(true);
    workingHoursApi
      .getBranchHours(branchId)
      .then((entries) => {
        const next = defaultBranchHours();
        entries.forEach((entry) => {
          next[entry.dayOfWeek] = {
            closed: entry.closed,
            opensAt: truncateTime(entry.opensAt) || '09:00',
            closesAt: truncateTime(entry.closesAt) || '18:00',
          };
        });
        setHours(next);
      })
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }, [branchId]);

  function updateDay(day: DayOfWeek, patch: Partial<BranchDayState>) {
    setHours((prev) => ({ ...prev, [day]: { ...prev[day], ...patch } }));
    setSaved(false);
  }

  async function handleSave() {
    if (!branchId) return;
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const days = DAYS.map((day) => ({
        dayOfWeek: day,
        closed: hours[day].closed,
        opensAt: hours[day].closed ? null : hours[day].opensAt,
        closesAt: hours[day].closed ? null : hours[day].closesAt,
      }));
      await workingHoursApi.setBranchHours(branchId, days);
      const fresh = await workingHoursApi.getBranchHours(branchId);
      const next = defaultBranchHours();
      fresh.forEach((entry) => {
        next[entry.dayOfWeek] = {
          closed: entry.closed,
          opensAt: truncateTime(entry.opensAt) || '09:00',
          closesAt: truncateTime(entry.closesAt) || '18:00',
        };
      });
      setHours(next);
      setSaved(true);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className={styles.pickerRow}>
        <FieldWrap label="Şube">
          <Select value={branchId} onChange={(e) => setBranchId(e.target.value)}>
            {branches.map((b) => (
              <option key={b.branchId} value={b.branchId}>
                {b.branchName}
              </option>
            ))}
          </Select>
        </FieldWrap>
      </div>

      {error && <div className={settingsStyles.errorBanner}>{error}</div>}
      {saved && !error && <div className={settingsStyles.successBanner}>Çalışma saatleri kaydedildi.</div>}

      {loading ? (
        <div className={settingsStyles.empty}>Yükleniyor...</div>
      ) : (
        <div className={styles.dayCard}>
          {DAYS.map((day) => (
            <div key={day} className={styles.dayRow}>
              <div className={styles.dayLabel}>{DAY_LABELS[day]}</div>
              <label className={styles.closedToggle}>
                <input
                  type="checkbox"
                  checked={hours[day].closed}
                  onChange={(e) => updateDay(day, { closed: e.target.checked })}
                />
                Kapalı
              </label>
              <div className={styles.timeInputs}>
                <input
                  type="time"
                  value={hours[day].opensAt}
                  disabled={hours[day].closed}
                  onChange={(e) => updateDay(day, { opensAt: e.target.value })}
                />
                <span>–</span>
                <input
                  type="time"
                  value={hours[day].closesAt}
                  disabled={hours[day].closed}
                  onChange={(e) => updateDay(day, { closesAt: e.target.value })}
                />
              </div>
            </div>
          ))}
        </div>
      )}

      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={handleSave} disabled={saving || loading || !branchId}>
          {saving ? 'Kaydediliyor...' : 'Kaydet'}
        </Button>
      </div>
    </div>
  );
}

function StaffShiftsSection() {
  const [staff, setStaff] = useState<StaffUserItem[]>([]);
  const [staffUserId, setStaffUserId] = useState('');
  const [shifts, setShifts] = useState<ShiftsState>(defaultShifts());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    staffUsersApi
      .list()
      .then((list) => {
        setStaff(list);
        setStaffUserId(list[0]?.id ?? '');
      })
      .catch((err) => setError(errorMessageOf(err)));
  }, []);

  useEffect(() => {
    if (!staffUserId) return;
    setLoading(true);
    workingHoursApi
      .getStaffShifts(staffUserId)
      .then((entries) => {
        const next = defaultShifts();
        entries.forEach((entry) => {
          next[entry.dayOfWeek] = [...next[entry.dayOfWeek], { startsAt: truncateTime(entry.startsAt), endsAt: truncateTime(entry.endsAt) }];
        });
        setShifts(next);
      })
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }, [staffUserId]);

  function addBlock(day: DayOfWeek) {
    setShifts((prev) => ({ ...prev, [day]: [...prev[day], { startsAt: '09:00', endsAt: '17:00' }] }));
    setSaved(false);
  }

  function updateBlock(day: DayOfWeek, index: number, patch: Partial<ShiftBlock>) {
    setShifts((prev) => ({
      ...prev,
      [day]: prev[day].map((block, i) => (i === index ? { ...block, ...patch } : block)),
    }));
    setSaved(false);
  }

  function removeBlock(day: DayOfWeek, index: number) {
    setShifts((prev) => ({ ...prev, [day]: prev[day].filter((_, i) => i !== index) }));
    setSaved(false);
  }

  async function handleSave() {
    if (!staffUserId) return;
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const entries = DAYS.flatMap((day) =>
        shifts[day].map((block) => ({ dayOfWeek: day, startsAt: block.startsAt, endsAt: block.endsAt }))
      );
      await workingHoursApi.setStaffShifts(staffUserId, entries);
      const fresh = await workingHoursApi.getStaffShifts(staffUserId);
      const next = defaultShifts();
      fresh.forEach((entry) => {
        next[entry.dayOfWeek] = [...next[entry.dayOfWeek], { startsAt: truncateTime(entry.startsAt), endsAt: truncateTime(entry.endsAt) }];
      });
      setShifts(next);
      setSaved(true);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className={styles.pickerRow}>
        <FieldWrap label="Personel">
          <Select value={staffUserId} onChange={(e) => setStaffUserId(e.target.value)}>
            {staff.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName}
              </option>
            ))}
          </Select>
        </FieldWrap>
      </div>

      {error && <div className={settingsStyles.errorBanner}>{error}</div>}
      {saved && !error && <div className={settingsStyles.successBanner}>Vardiyalar kaydedildi.</div>}

      {loading ? (
        <div className={settingsStyles.empty}>Yükleniyor...</div>
      ) : (
        DAYS.map((day) => (
          <div key={day} className={styles.shiftDayCard}>
            <div className={styles.shiftDayHeader}>
              <div className={styles.dayLabel}>{DAY_LABELS[day]}</div>
              <Button variant="secondary" onClick={() => addBlock(day)}>
                + Vardiya Ekle
              </Button>
            </div>
            {shifts[day].length === 0 ? (
              <div className={styles.shiftEmpty}>Bu gün için vardiya tanımlanmadı</div>
            ) : (
              shifts[day].map((block, index) => (
                <div key={index} className={styles.shiftBlockRow}>
                  <div className={styles.timeInputs}>
                    <input
                      type="time"
                      value={block.startsAt}
                      onChange={(e) => updateBlock(day, index, { startsAt: e.target.value })}
                    />
                    <span>–</span>
                    <input type="time" value={block.endsAt} onChange={(e) => updateBlock(day, index, { endsAt: e.target.value })} />
                  </div>
                  <button type="button" className={styles.removeBtn} onClick={() => removeBlock(day, index)}>
                    Kaldır
                  </button>
                </div>
              ))
            )}
          </div>
        ))
      )}

      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={handleSave} disabled={saving || loading || !staffUserId}>
          {saving ? 'Kaydediliyor...' : 'Kaydet'}
        </Button>
      </div>
    </div>
  );
}

export function WorkingHoursPanel() {
  const [tab, setTab] = useState<'branch' | 'staff'>('branch');

  return (
    <div>
      <div className={styles.subTabs}>
        <button
          type="button"
          className={`${styles.subTab} ${tab === 'branch' ? styles.subTabActive : ''}`}
          onClick={() => setTab('branch')}
        >
          Şube Saatleri
        </button>
        <button
          type="button"
          className={`${styles.subTab} ${tab === 'staff' ? styles.subTabActive : ''}`}
          onClick={() => setTab('staff')}
        >
          Personel Vardiyaları
        </button>
      </div>

      {tab === 'branch' ? <BranchWorkingHoursSection /> : <StaffShiftsSection />}
    </div>
  );
}
