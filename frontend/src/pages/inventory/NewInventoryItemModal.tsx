import { FormEvent, useState } from 'react';
import { inventoryApi } from '../../api/inventoryApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './InventoryPage.module.css';

interface NewInventoryItemModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

export function NewInventoryItemModal({ open, onClose, onCreated }: NewInventoryItemModalProps) {
  const [name, setName] = useState('');
  const [category, setCategory] = useState('');
  const [initialQuantity, setInitialQuantity] = useState('0');
  const [reorderThreshold, setReorderThreshold] = useState('5');
  const [unitCost, setUnitCost] = useState('');
  const [busy, setBusy] = useState(false);

  function reset() {
    setName('');
    setCategory('');
    setInitialQuantity('0');
    setReorderThreshold('5');
    setUnitCost('');
    onClose();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await inventoryApi.create({
        name,
        category: category || undefined,
        initialQuantity: Number(initialQuantity),
        reorderThreshold: Number(reorderThreshold),
        unitCost: unitCost ? Number(unitCost) : undefined,
      });
      onCreated();
      reset();
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={reset} width={420}>
      <form onSubmit={handleSubmit}>
        <h2 className={styles.modalTitle}>Yeni stok kalemi</h2>
        <FieldWrap label="Ürün adı">
          <Input value={name} onChange={(e) => setName(e.target.value)} required />
        </FieldWrap>
        <FieldWrap label="Kategori (opsiyonel)">
          <Input value={category} onChange={(e) => setCategory(e.target.value)} />
        </FieldWrap>
        <div className={styles.modalRow}>
          <FieldWrap label="Başlangıç miktarı">
            <Input type="number" min={0} value={initialQuantity} onChange={(e) => setInitialQuantity(e.target.value)} required />
          </FieldWrap>
          <FieldWrap label="Kritik stok eşiği">
            <Input type="number" min={0} value={reorderThreshold} onChange={(e) => setReorderThreshold(e.target.value)} required />
          </FieldWrap>
        </div>
        <FieldWrap label="Birim maliyet (opsiyonel)">
          <Input type="number" step="0.01" value={unitCost} onChange={(e) => setUnitCost(e.target.value)} />
        </FieldWrap>
        <div className={styles.modalActions}>
          <Button type="button" variant="secondary" onClick={reset}>
            Vazgeç
          </Button>
          <Button type="submit" variant="primary" disabled={busy}>
            {busy ? 'Kaydediliyor...' : 'Kaydet'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
