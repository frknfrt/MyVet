import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { encounterApi } from '../../api/encounterApi';
import { patientApi, PatientSearchResult } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { formatDate } from '../encounter/ageUtils';
import { ageLabel, daysOpen } from '../encounter/encounterAge';
import styles from './NewEncounterModal.module.css';

interface NewEncounterModalProps {
  open: boolean;
  onClose: () => void;
}

const MIN_QUERY_LENGTH = 2;

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

/**
 * Kapıdan gelen hastayı randevu açmadan muayeneye almak için. Hasta kartındaki
 * "Muayene Başlat" ile aynı kural: hastanın açık taslağı varsa ona devam edilir,
 * ikinci bir taslak açılmaz.
 */
export function NewEncounterModal({ open, onClose }: NewEncounterModalProps) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PatientSearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<PatientSearchResult | null>(null);
  const [draft, setDraft] = useState<{ id: string; date: string; days: number } | null>(null);
  const [confirmReplace, setConfirmReplace] = useState(false);
  const [checkingDraft, setCheckingDraft] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const searchSeq = useRef(0);

  useEffect(() => {
    if (!open) {
      setQuery('');
      setResults([]);
      setSelected(null);
      setDraft(null);
      setConfirmReplace(false);
      setError(null);
    }
  }, [open]);

  useEffect(() => {
    const q = query.trim();
    if (q.length < MIN_QUERY_LENGTH) {
      setResults([]);
      return;
    }
    const seq = ++searchSeq.current;
    setSearching(true);
    patientApi
      .search(q)
      .then((list) => {
        if (seq === searchSeq.current) setResults(list);
      })
      .catch((err) => {
        if (seq === searchSeq.current) setError(errorMessageOf(err));
      })
      .finally(() => {
        if (seq === searchSeq.current) setSearching(false);
      });
  }, [query]);

  async function selectPatient(p: PatientSearchResult) {
    setSelected(p);
    setDraft(null);
    setConfirmReplace(false);
    setError(null);
    setCheckingDraft(true);
    try {
      const encounters = await encounterApi.listByPatient(p.patientId);
      const openDraft = encounters.find((e) => e.status === 'DRAFT');
      setDraft(
        openDraft
          ? { id: openDraft.id, date: formatDate(openDraft.encounterDate) ?? '', days: daysOpen(openDraft.encounterDate) }
          : null,
      );
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setCheckingDraft(false);
    }
  }

  /** Eski taslağı "İptal edildi" yapıp (silinmez) yeni muayene açar; ilk tıklama onay ister. */
  async function handleReplaceDraft() {
    if (!selected || !draft) return;
    if (!confirmReplace) {
      setConfirmReplace(true);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await encounterApi.cancel(draft.id);
      const encounterId = await encounterApi.start({ patientId: selected.patientId });
      onClose();
      navigate(`/muayene/${encounterId}`);
    } catch (err) {
      setError(errorMessageOf(err));
      setConfirmReplace(false);
    } finally {
      setBusy(false);
    }
  }

  async function handleStart() {
    if (!selected) return;
    setBusy(true);
    setError(null);
    try {
      const encounterId = draft?.id ?? (await encounterApi.start({ patientId: selected.patientId }));
      onClose();
      navigate(`/muayene/${encounterId}`);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  const showEmpty = !searching && !selected && query.trim().length >= MIN_QUERY_LENGTH && results.length === 0;

  return (
    <Modal open={open} onClose={onClose} width={560}>
      <div className={styles.title}>Yeni Muayene</div>
      <div className={styles.sub}>Muayeneye alınacak hastayı adı, sahibinin adı veya telefonuyla arayın.</div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      {selected ? (
        <div className={styles.selectedBox}>
          <div className={styles.selectedRow}>
            <div>
              <div className={styles.patientName}>{selected.patientName}</div>
              <div className={styles.patientMeta}>
                {[selected.speciesName, selected.breedName].filter(Boolean).join(' · ')} — {selected.ownerFullName} ·{' '}
                {selected.ownerPhone}
              </div>
            </div>
            <button type="button" className={styles.changeBtn} onClick={() => setSelected(null)}>
              Değiştir
            </button>
          </div>
          {draft && (
            <div className={styles.draftNote}>
              Bu hastanın {draft.date} tarihli ({ageLabel(draft.days).toLocaleLowerCase('tr-TR')}) açık bir taslak
              muayenesi var. Devam edebilir ya da eski taslağı iptal edip yeni muayene başlatabilirsiniz.
              <button type="button" className={styles.replaceBtn} onClick={handleReplaceDraft} disabled={busy}>
                {confirmReplace
                  ? 'Emin misiniz? Eski taslak "İptal edildi" olarak saklanır — onaylamak için tekrar tıklayın'
                  : 'Eski taslağı iptal et, yeni muayene başlat'}
              </button>
            </div>
          )}
        </div>
      ) : (
        <>
          <Input
            autoFocus
            placeholder="Örn. Pamuk, Şeyda Fırat veya 0505…"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setError(null);
            }}
          />
          <div className={styles.results}>
            {searching && <div className={styles.hint}>Aranıyor…</div>}
            {!searching &&
              results.map((p) => {
                const deceased = p.status === 'DECEASED';
                return (
                  <button
                    key={p.patientId}
                    type="button"
                    className={styles.resultRow}
                    disabled={deceased}
                    onClick={() => selectPatient(p)}
                  >
                    <span className={styles.patientName}>{p.patientName}</span>
                    <span className={styles.patientMeta}>
                      {deceased ? 'Vefat etti' : p.speciesName ?? ''} — {p.ownerFullName} · {p.ownerPhone}
                    </span>
                  </button>
                );
              })}
            {showEmpty && <div className={styles.hint}>Bu aramayla eşleşen hasta bulunamadı.</div>}
          </div>
        </>
      )}

      <div className={styles.footer}>
        <button
          type="button"
          className={styles.linkBtn}
          onClick={() => {
            onClose();
            navigate('/hastalar/yeni');
          }}
        >
          Hasta kayıtlı değil mi? Yeni hasta kaydı aç
        </button>
        <div className={styles.footerActions}>
          <Button variant="secondary" onClick={onClose}>
            Vazgeç
          </Button>
          <Button variant="primary" onClick={handleStart} disabled={!selected || checkingDraft || busy}>
            {busy ? 'Açılıyor...' : draft ? `${draft.date} taslağına dön` : 'Muayeneyi Başlat'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
