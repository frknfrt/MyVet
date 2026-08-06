import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { OwnerSearchResult, patientApi } from '../../api/patientApi';
import { vaccinationApi, VaccinationStatus } from '../../api/vaccinationApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';
import { COMMON_VACCINE_NAMES } from './vaccinationStatus';
import styles from './NewVaccinationPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

const DATE_SHORTCUTS: { label: string; days: number }[] = [
  { label: 'Bugün', days: 0 },
  { label: 'Dün', days: -1 },
  { label: 'Yarın', days: 1 },
  { label: '1 hafta sonra', days: 7 },
  { label: '1 hafta önce', days: -7 },
];

interface OwnerPatientOption {
  id: string;
  name: string;
}

export function NewVaccinationPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const presetOwnerId = searchParams.get('ownerId');
  const presetPatientId = searchParams.get('patientId');

  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerResults, setOwnerResults] = useState<OwnerSearchResult[]>([]);
  const [ownerSearchOpen, setOwnerSearchOpen] = useState(false);
  const [ownerPatients, setOwnerPatients] = useState<OwnerPatientOption[]>([]);
  const [patientId, setPatientId] = useState('');

  const [vaccineName, setVaccineName] = useState('');
  const [vaccinationDate, setVaccinationDate] = useState(isoDate(new Date()));
  const [nextDueDate, setNextDueDate] = useState('');
  const [status, setStatus] = useState<VaccinationStatus>('SCHEDULED');
  const [notesOpen, setNotesOpen] = useState(false);
  const [notes, setNotes] = useState('');

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!presetOwnerId) return;
    patientApi.getOwnerProfile(presetOwnerId).then((profile) => {
      setOwnerId(profile.id);
      setOwnerLabel(`${profile.fullName} · ${profile.phone}`);
    });
  }, [presetOwnerId]);

  useEffect(() => {
    if (presetOwnerId || !ownerSearchOpen) return;
    const handle = setTimeout(() => {
      patientApi.searchOwners(ownerQuery).then(setOwnerResults);
    }, 250);
    return () => clearTimeout(handle);
  }, [ownerQuery, ownerSearchOpen, presetOwnerId]);

  useEffect(() => {
    if (!ownerId) {
      setOwnerPatients([]);
      setPatientId('');
      return;
    }
    patientApi.getOwnerProfile(ownerId).then((profile) => {
      const options = profile.patients.map((p) => ({ id: p.id, name: p.name }));
      setOwnerPatients(options);
      if (presetPatientId && options.some((o) => o.id === presetPatientId)) {
        setPatientId(presetPatientId);
      } else {
        setPatientId(options.length === 1 ? options[0].id : '');
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ownerId]);

  function selectOwner(o: OwnerSearchResult) {
    setOwnerId(o.id);
    setOwnerLabel(o.fullName);
    setOwnerSearchOpen(false);
    setOwnerQuery('');
  }

  function applyDateShortcut(days: number) {
    const d = new Date();
    d.setDate(d.getDate() + days);
    setVaccinationDate(isoDate(d));
  }

  function resetVaccineFields() {
    setVaccineName('');
    setNextDueDate('');
    setNotes('');
    setNotesOpen(false);
    setStatus('SCHEDULED');
    setVaccinationDate(isoDate(new Date()));
  }

  async function submit(andPlanAnother: boolean) {
    if (busy || !patientId || !vaccineName.trim()) return;
    setBusy(true);
    setError(null);
    setSuccessMsg(null);
    try {
      await vaccinationApi.record({
        patientId,
        vaccineName: vaccineName.trim(),
        administeredDate: vaccinationDate,
        nextDueDate: nextDueDate || undefined,
        status,
        notes: notes || undefined,
      });
      if (andPlanAnother) {
        resetVaccineFields();
        setSuccessMsg('Aşı kaydı oluşturuldu. Yeni bir aşı planlayabilirsiniz.');
      } else {
        navigate('/asi-takvimi');
      }
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    submit(false);
  }

  return (
    <AppShell>
      <button className={styles.backLink} onClick={() => navigate('/asi-takvimi')}>
        ← Aşı Takvimi
      </button>

      <div className={styles.card}>
        <h1 className={styles.title}>Yeni Aşı Kaydı</h1>

        <form onSubmit={handleSubmit}>
          {error && <div className={styles.errorBanner}>{error}</div>}
          {successMsg && <div className={styles.successBanner}>{successMsg}</div>}

          <div className={styles.row2}>
            <FieldWrap label="Müşteri*">
              {ownerId ? (
                <div className={styles.selectedChip}>
                  <span>{ownerLabel}</span>
                  {!presetOwnerId && (
                    <button
                      type="button"
                      className={styles.changeBtn}
                      onClick={() => {
                        setOwnerId(null);
                        setOwnerLabel(null);
                      }}
                    >
                      Değiştir
                    </button>
                  )}
                </div>
              ) : (
                <div className={styles.ownerPicker}>
                  <Input
                    placeholder="Müşteri seçiniz"
                    value={ownerQuery}
                    onFocus={() => setOwnerSearchOpen(true)}
                    onBlur={() => setTimeout(() => setOwnerSearchOpen(false), 150)}
                    onChange={(e) => {
                      setOwnerQuery(e.target.value);
                      setOwnerSearchOpen(true);
                    }}
                  />
                  {ownerSearchOpen && ownerResults.length > 0 && (
                    <div className={styles.ownerDropdown}>
                      {ownerResults.map((o) => (
                        <div key={o.id} className={styles.ownerOption} onClick={() => selectOwner(o)}>
                          <span className={styles.ownerOptionName}>{o.fullName}</span>
                          <span className={styles.ownerOptionPhone}>{o.phone}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </FieldWrap>

            <FieldWrap label="Hasta*">
              <Select
                value={patientId}
                onChange={(e) => setPatientId(e.target.value)}
                disabled={!ownerId || ownerPatients.length === 0}
                required
              >
                <option value="">{ownerId ? 'Hasta seçiniz' : 'Önce müşteri seçiniz'}</option>
                {ownerPatients.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </Select>
            </FieldWrap>
          </div>

          <div className={styles.row2}>
            <FieldWrap label="Aşı*">
              <Input
                list="vaccine-names"
                value={vaccineName}
                onChange={(e) => setVaccineName(e.target.value)}
                placeholder="Aşı seçilmedi"
                required
              />
              <datalist id="vaccine-names">
                {COMMON_VACCINE_NAMES.map((n) => (
                  <option key={n} value={n} />
                ))}
              </datalist>
            </FieldWrap>

            <FieldWrap label="Aşı Tarihi*">
              <Input type="date" value={vaccinationDate} onChange={(e) => setVaccinationDate(e.target.value)} required />
              <div className={styles.shortcutRow}>
                {DATE_SHORTCUTS.map((s) => (
                  <button key={s.label} type="button" className={styles.shortcutBtn} onClick={() => applyDateShortcut(s.days)}>
                    {s.label}
                  </button>
                ))}
              </div>
            </FieldWrap>
          </div>

          <div className={styles.radioRow}>
            <label className={styles.radioOption}>
              <input
                type="radio"
                name="vaccStatus"
                checked={status === 'SCHEDULED'}
                onChange={() => setStatus('SCHEDULED')}
              />
              İleri tarihli aşı randevusu
            </label>
            <label className={styles.radioOption}>
              <input
                type="radio"
                name="vaccStatus"
                checked={status === 'ADMINISTERED'}
                onChange={() => setStatus('ADMINISTERED')}
              />
              Yapılmış aşı tanımla
            </label>
          </div>

          <FieldWrap label="Sonraki aşı tarihi (opsiyonel)">
            <Input type="date" value={nextDueDate} onChange={(e) => setNextDueDate(e.target.value)} />
          </FieldWrap>

          {notesOpen ? (
            <FieldWrap label="İşlem notları">
              <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} autoFocus />
            </FieldWrap>
          ) : (
            <button type="button" className={styles.notesToggle} onClick={() => setNotesOpen(true)}>
              + İşlem notları eklemek için tıklayın
            </button>
          )}

          <div className={styles.actions}>
            <Button type="submit" variant="primary" disabled={busy || !patientId || !vaccineName.trim()}>
              {busy ? 'Kaydediliyor...' : 'Aşı Kaydı Oluştur'}
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={busy || !patientId || !vaccineName.trim()}
              onClick={() => submit(true)}
            >
              + Kaydet ve Yeni Aşı Planla
            </Button>
          </div>
        </form>
      </div>
    </AppShell>
  );
}
