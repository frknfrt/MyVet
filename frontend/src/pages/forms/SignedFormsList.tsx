import { useCallback, useEffect, useState } from 'react';
import { formsApi, type SignedFormSummary } from '../../api/formsApi';
import { Badge } from '../../components/ui/Badge';
import { formatDateTime } from './formRoles';
import { SignedFormViewModal } from './SignedFormViewModal';
import styles from './SignedFormsList.module.css';

type SignedFormsListProps = { patientId: string; ownerId?: never } | { ownerId: string; patientId?: never };

export function SignedFormsList(props: SignedFormsListProps) {
  const { patientId, ownerId } = props;
  const [items, setItems] = useState<SignedFormSummary[] | null>(null);
  const [error, setError] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const load = useCallback(() => {
    const filter = patientId ? { patientId } : { ownerId: ownerId as string };
    formsApi
      .listSignedForms(filter)
      .then((list) => {
        setItems(list);
        setError(false);
      })
      .catch(() => setError(true));
  }, [patientId, ownerId]);

  useEffect(() => {
    load();
  }, [load]);

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
          <button key={f.id} type="button" className={styles.row} onClick={() => setSelectedId(f.id)}>
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
