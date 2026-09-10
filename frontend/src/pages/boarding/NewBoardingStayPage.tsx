import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { OwnerSearchResult, patientApi } from '../../api/patientApi';
import { boardingApi, BoardingRoom } from '../../api/boardingApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';
import { InvoiceDetailModal } from '../finance/InvoiceDetailModal';
import styles from './NewBoardingStayPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

const DATE_SHORTCUTS: { label: string; days: number }[] = [
  { label: 'Bugün', days: 0 },
  { label: 'Yarın', days: 1 },
  { label: '1 hafta sonra', days: 7 },
];

interface OwnerPatientOption {
  id: string;
  name: string;
}

export function NewBoardingStayPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const presetRoomId = searchParams.get('roomId');

  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerResults, setOwnerResults] = useState<OwnerSearchResult[]>([]);
  const [ownerSearchOpen, setOwnerSearchOpen] = useState(false);
  const [ownerPatients, setOwnerPatients] = useState<OwnerPatientOption[]>([]);
  const [patientId, setPatientId] = useState('');

  const [rooms, setRooms] = useState<BoardingRoom[]>([]);
  const [roomId, setRoomId] = useState(presetRoomId ?? '');
  const [checkInDate, setCheckInDate] = useState(isoDate(new Date()));
  const [expectedCheckOutDate, setExpectedCheckOutDate] = useState('');
  const [notesOpen, setNotesOpen] = useState(false);
  const [notes, setNotes] = useState('');

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createdInvoiceId, setCreatedInvoiceId] = useState<string | null>(null);

  useEffect(() => {
    boardingApi.listRooms().then(setRooms);
  }, []);

  useEffect(() => {
    if (!ownerSearchOpen) return;
    const handle = setTimeout(() => {
      patientApi.searchOwners(ownerQuery).then(setOwnerResults);
    }, 250);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ownerQuery, ownerSearchOpen]);

  useEffect(() => {
    if (!ownerId) {
      setOwnerPatients([]);
      setPatientId('');
      return;
    }
    patientApi.getOwnerProfile(ownerId).then((profile) => {
      const options = profile.patients.map((p) => ({ id: p.id, name: p.name }));
      setOwnerPatients(options);
      setPatientId(options.length === 1 ? options[0].id : '');
    });
  }, [ownerId]);

  function selectOwner(o: OwnerSearchResult) {
    setOwnerId(o.id);
    setOwnerLabel(o.fullName);
    setOwnerSearchOpen(false);
    setOwnerQuery('');
  }

  function applyCheckOutShortcut(days: number) {
    const base = checkInDate ? new Date(checkInDate + 'T00:00:00') : new Date();
    const d = new Date(base);
    d.setDate(d.getDate() + days);
    setExpectedCheckOutDate(isoDate(d));
  }

  const groupedRooms = rooms.reduce<Record<string, BoardingRoom[]>>((acc, r) => {
    if (!r.active) return acc;
    (acc[r.groupName] ??= []).push(r);
    return acc;
  }, {});

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || !patientId || !roomId || !checkInDate) return;
    setBusy(true);
    setError(null);
    try {
      const stayId = await boardingApi.createStay({
        roomId,
        patientId,
        checkInDate,
        expectedCheckOutDate: expectedCheckOutDate || undefined,
        notes: notes || undefined,
      });
      const invoiceId = await boardingApi.getInvoiceIdForStay(stayId);
      setCreatedInvoiceId(invoiceId);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AppShell>
      <button className={styles.backLink} onClick={() => navigate('/konaklama')}>
        ← Konaklama
      </button>

      <div className={styles.card}>
        <h1 className={styles.title}>Yeni Konaklama Kaydı</h1>

        <form onSubmit={handleSubmit}>
          {error && <div className={styles.errorBanner}>{error}</div>}

          <div className={styles.row2}>
            <FieldWrap label="Müşteri*">
              {ownerId ? (
                <div className={styles.selectedChip}>
                  <span>{ownerLabel}</span>
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
                    <div className={styles.ownerDropdown} onMouseDown={(e) => e.preventDefault()}>
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

          <FieldWrap label="Oda / Kafes*">
            <Select value={roomId} onChange={(e) => setRoomId(e.target.value)} required>
              <option value="">Oda seçiniz</option>
              {Object.entries(groupedRooms).map(([groupName, groupRooms]) => (
                <optgroup key={groupName} label={groupName}>
                  {groupRooms.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name} {r.dailyRate ? `— ${r.dailyRate.toFixed(2)} ₺/gün` : ''}
                    </option>
                  ))}
                </optgroup>
              ))}
            </Select>
          </FieldWrap>

          <div className={styles.row2}>
            <FieldWrap label="Giriş Tarihi*">
              <Input type="date" value={checkInDate} onChange={(e) => setCheckInDate(e.target.value)} required />
            </FieldWrap>

            <FieldWrap label="Planlanan Çıkış Tarihi">
              <Input type="date" value={expectedCheckOutDate} onChange={(e) => setExpectedCheckOutDate(e.target.value)} />
              <div className={styles.shortcutRow}>
                {DATE_SHORTCUTS.map((s) => (
                  <button key={s.label} type="button" className={styles.shortcutBtn} onClick={() => applyCheckOutShortcut(s.days)}>
                    {s.label}
                  </button>
                ))}
              </div>
            </FieldWrap>
          </div>

          {notesOpen ? (
            <FieldWrap label="Notlar">
              <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} autoFocus />
            </FieldWrap>
          ) : (
            <button type="button" className={styles.notesToggle} onClick={() => setNotesOpen(true)}>
              + Not eklemek için tıklayın
            </button>
          )}

          <div className={styles.actions}>
            <Button type="submit" variant="primary" disabled={busy || !patientId || !roomId || !checkInDate}>
              {busy ? 'Kaydediliyor...' : 'Konaklama Kaydı Oluştur'}
            </Button>
          </div>
        </form>
      </div>

      <InvoiceDetailModal
        invoiceId={createdInvoiceId}
        onClose={() => navigate('/konaklama')}
        onChanged={() => {}}
      />
    </AppShell>
  );
}
