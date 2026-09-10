import { useEffect, useState } from 'react';
import { billingApi } from '../../api/billingApi';
import { OwnerSearchResult, patientApi } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './NewInvoiceModal.module.css';

interface NewInvoiceModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (invoiceId: string) => void;
}

export function NewInvoiceModal({ open, onClose, onCreated }: NewInvoiceModalProps) {
  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerResults, setOwnerResults] = useState<OwnerSearchResult[]>([]);
  const [ownerSearchOpen, setOwnerSearchOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) {
      setOwnerId(null);
      setOwnerLabel(null);
      setOwnerQuery('');
      setOwnerResults([]);
    }
  }, [open]);

  useEffect(() => {
    if (!ownerSearchOpen) return;
    const handle = setTimeout(() => {
      patientApi.searchOwners(ownerQuery).then(setOwnerResults);
    }, 250);
    return () => clearTimeout(handle);
  }, [ownerQuery, ownerSearchOpen]);

  function selectOwner(o: OwnerSearchResult) {
    setOwnerId(o.id);
    setOwnerLabel(o.fullName);
    setOwnerSearchOpen(false);
    setOwnerQuery('');
  }

  async function handleCreate() {
    if (!ownerId || busy) return;
    setBusy(true);
    try {
      const invoiceId = await billingApi.createInvoice(ownerId);
      onCreated(invoiceId);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} width={440}>
      <div className={styles.title}>Yeni Fatura</div>
      <div className={styles.sub}>Faturayı hangi müşteri için açmak istiyorsunuz?</div>

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
              placeholder="Müşteri adı veya telefon"
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

      <div className={styles.actions}>
        <Button variant="secondary" onClick={onClose} disabled={busy}>
          Vazgeç
        </Button>
        <Button variant="primary" onClick={handleCreate} disabled={!ownerId || busy}>
          Fatura Oluştur
        </Button>
      </div>
    </Modal>
  );
}
