import { useCallback, useEffect, useRef, useState } from 'react';
import { formsApi, type SignedFormSummary } from '../../api/formsApi';
import { Badge } from '../../components/ui/Badge';
import { formatDateTime } from './formRoles';
import { SignedFormViewModal } from './SignedFormViewModal';
import styles from './SignedFormsList.module.css';

type SignedFormsListProps = { patientId: string; ownerId?: never } | { ownerId: string; patientId?: never };

export function SignedFormsList(props: SignedFormsListProps) {
  const { patientId, ownerId } = props;
  const key = patientId ? `patient:${patientId}` : `owner:${ownerId}`;
  const [loaded, setLoaded] = useState<{ key: string; items: SignedFormSummary[] } | null>(null);
  const [errorFor, setError] = useState<string | null>(null);
  const [selection, setSelectedId] = useState<{ key: string; id: string } | null>(null);
  const generation = useRef(0);
  const activeSource = useRef<string | null>(null);
  const items = loaded?.key === key ? loaded.items : null;
  const error = errorFor === key;
  const selectedId = selection?.key === key ? selection.id : null;

  const load = useCallback(() => {
    if (activeSource.current !== key) return;
    const request = ++generation.current;
    setLoaded(null);
    setError(null);
    const filter = patientId ? { patientId } : { ownerId: ownerId as string };
    formsApi
      .listSignedForms(filter)
      .then((list) => {
        if (generation.current !== request) return;
        setLoaded({ key, items: list });
        setError(null);
      })
      .catch(() => { if (generation.current === request) setError(key); });
  }, [patientId, ownerId, key]);

  useEffect(() => {
    activeSource.current = key;
    load();
    return () => { activeSource.current = null; generation.current++; };
  }, [load, key]);

  return (
    <div className={styles.card}>
      {error ? (
        <div className={styles.empty}>Formlar yüklenemedi</div>
      ) : items === null ? (
        <div className={styles.empty}>Yükleniyor...</div>
      ) : items.length === 0 ? (
        <div className={styles.empty}>Henüz imzalı form yok</div>
      ) : (
        items.map((f) => (
          <button key={f.id} type="button" className={styles.row} onClick={() => setSelectedId({ key, id: f.id })}>
            <span className={styles.title}>{f.templateTitle}</span>
            <span className={styles.muted}>{formatDateTime(f.signedAt)}</span>
            <span className={styles.muted}>
              {f.signerName ?? '—'}
              {f.signerType === 'OTHER' && ' (hasta yakını/diğer)'}
            </span>
            <span>
              <Badge tone={f.status === 'VALID' ? 'success' : 'danger'}>{f.status === 'VALID' ? 'Geçerli' : 'İptal edildi'}</Badge>
            </span>
          </button>
        ))
      )}
      <SignedFormViewModal signedFormId={selectedId} onClose={() => setSelectedId(null)} onChanged={load} />
    </div>
  );
}
