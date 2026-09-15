import { FormEvent, useEffect, useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { inventoryApi, InventoryItem, StockMovement, StockMovementType } from '../../api/inventoryApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './ItemDetailModal.module.css';

const MOVEMENT_LABELS: Record<StockMovementType, string> = { IN: 'Giriş', OUT: 'Çıkış', ADJUSTMENT: 'Düzeltme' };
const REFERENCE_LABELS: Record<string, string> = { ENCOUNTER: 'Muayene', PURCHASE_ORDER: 'Satın Alma', MANUAL: 'Manuel' };

interface ItemDetailModalProps {
  item: InventoryItem | null;
  onClose: () => void;
  onChanged: () => void;
}

export function ItemDetailModal({ item, onClose, onChanged }: ItemDetailModalProps) {
  const { session } = useAuth();
  // InventoryItemsController POST /movements -- sadece TECHNICIAN/ADMIN.
  const canWrite = session ? ['TECHNICIAN', 'ADMIN'].includes(session.role) : false;
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [movementType, setMovementType] = useState<StockMovementType>('IN');
  const [quantity, setQuantity] = useState('1');
  const [busy, setBusy] = useState(false);

  function reload() {
    if (!item) return;
    inventoryApi.listMovements(item.id).then(setMovements);
  }

  useEffect(() => {
    reload();
    setQuantity('1');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item?.id]);

  async function handleAdjust(e: FormEvent) {
    e.preventDefault();
    if (!item || busy) return;
    setBusy(true);
    try {
      await inventoryApi.recordMovement(item.id, { movementType, quantity: Number(quantity) });
      reload();
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  const dirty = movementType !== 'IN' || quantity !== '1';

  return (
    <Modal open={item !== null} onClose={onClose} width={480} dirty={dirty}>
      {!item ? null : (
        <>
          <div className={styles.header}>
            <div>
              <div className={styles.name}>{item.name}</div>
              <div className={styles.subline}>{item.category ?? 'Kategorisiz'}</div>
              {item.expiryDate && (
                <div className={styles.subline}>
                  Son kullanma: {new Date(item.expiryDate).toLocaleDateString('tr-TR')}
                  {new Date(item.expiryDate).getTime() < Date.now() && (
                    <>
                      {' '}
                      <Badge tone="danger">Süresi Doldu</Badge>
                    </>
                  )}
                </div>
              )}
            </div>
            <div>
              <div className={styles.qtyValue}>{item.quantityOnHand}</div>
              {item.belowReorderThreshold && <Badge tone="warning">Düşük Stok</Badge>}
            </div>
          </div>

          <div className={styles.sectionLabel}>Stok Hareketleri</div>
          {movements.length === 0 ? (
            <div className={styles.emptyNote}>Henüz hareket yok</div>
          ) : (
            movements.map((m) => (
              <div key={m.id} className={styles.movementRow}>
                <span>{MOVEMENT_LABELS[m.movementType]}</span>
                <span>{REFERENCE_LABELS[m.referenceType] ?? m.referenceType}</span>
                <span>{new Date(m.createdAt).toLocaleString('tr-TR')}</span>
                <span>{m.movementType === 'OUT' ? '-' : '+'}{m.quantity}</span>
              </div>
            ))
          )}

          {canWrite && (
            <form className={styles.adjustForm} onSubmit={handleAdjust}>
              <FieldWrap label="Hareket türü">
                <Select value={movementType} onChange={(e) => setMovementType(e.target.value as StockMovementType)}>
                  <option value="IN">Giriş</option>
                  <option value="OUT">Çıkış</option>
                  <option value="ADJUSTMENT">Düzeltme</option>
                </Select>
              </FieldWrap>
              <FieldWrap label="Miktar">
                <Input type="number" min={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
              </FieldWrap>
              <Button type="submit" variant="secondary" disabled={busy}>
                Uygula
              </Button>
            </form>
          )}
        </>
      )}
    </Modal>
  );
}
