import { FormEvent, useState } from 'react';
import { ApiError } from '../../api/client';
import { boardingApi } from '../../api/boardingApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { COMMON_ROOM_GROUPS } from './boardingStatus';
import styles from './NewRoomModal.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

interface NewRoomModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

export function NewRoomModal({ open, onClose, onCreated }: NewRoomModalProps) {
  const [groupName, setGroupName] = useState('');
  const [name, setName] = useState('');
  const [capacity, setCapacity] = useState('1');
  const [dailyRate, setDailyRate] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setGroupName('');
    setName('');
    setCapacity('1');
    setDailyRate('');
    setNotes('');
    setError(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await boardingApi.createRoom({
        groupName: groupName.trim(),
        name: name.trim(),
        capacity: Number(capacity) || 1,
        dailyRate: dailyRate ? Number(dailyRate) : undefined,
        notes: notes || undefined,
      });
      reset();
      onCreated();
      onClose();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  const dirty = groupName !== '' || name !== '' || capacity !== '1' || dailyRate !== '' || notes !== '';

  return (
    <Modal open={open} onClose={onClose} width={460} dirty={dirty}>
      <h2 className={styles.title}>Yeni Oda / Alan Ekle</h2>
      <form onSubmit={handleSubmit}>
        {error && <div className={styles.errorBanner}>{error}</div>}

        <FieldWrap label="Kategori (grup)*">
          <Input list="room-groups" value={groupName} onChange={(e) => setGroupName(e.target.value)} placeholder="Örn. Köpek Pansiyonu" required />
          <datalist id="room-groups">
            {COMMON_ROOM_GROUPS.map((g) => (
              <option key={g} value={g} />
            ))}
          </datalist>
        </FieldWrap>

        <FieldWrap label="Oda / Kafes Adı*">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Örn. Oda 1" required />
        </FieldWrap>

        <div className={styles.row2}>
          <FieldWrap label="Kapasite*">
            <Input type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} required />
          </FieldWrap>
          <FieldWrap label="Günlük Ücret (₺)">
            <Input type="number" step="0.01" min={0} value={dailyRate} onChange={(e) => setDailyRate(e.target.value)} />
          </FieldWrap>
        </div>

        <FieldWrap label="Notlar">
          <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </FieldWrap>

        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            Vazgeç
          </Button>
          <Button type="submit" variant="primary" disabled={busy || !groupName.trim() || !name.trim()}>
            {busy ? 'Kaydediliyor...' : 'Oda Ekle'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
