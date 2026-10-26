import { FormEvent, useEffect, useState } from 'react';
import { encounterApi, InventoryUsage } from '../../api/encounterApi';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import encounterStyles from './EncounterPage.module.css';
import styles from './MaterialsUsedCard.module.css';
import { formatAmount, formatStock, inputModeFor } from '../inventory/stockFormat';

interface MaterialsUsedCardProps {
  encounterId: string;
  readOnly: boolean;
}

export function MaterialsUsedCard({ encounterId, readOnly }: MaterialsUsedCardProps) {
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [usage, setUsage] = useState<InventoryUsage[]>([]);
  const [itemId, setItemId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [busy, setBusy] = useState(false);

  function reloadUsage() {
    encounterApi.listMaterials(encounterId).then(setUsage);
  }

  useEffect(() => {
    inventoryApi.list().then((list) => {
      setItems(list);
      if (list.length > 0) setItemId(list[0].id);
    });
    reloadUsage();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [encounterId]);

  const selected = items.find((i) => i.id === itemId);
  const mode = inputModeFor(selected?.unit, selected?.pieceSize, selected?.pieceLabel);

  async function handleAdd(e: FormEvent) {
    e.preventDefault();
    if (busy || !itemId) return;
    setBusy(true);
    try {
      await encounterApi.addMaterial(encounterId, { inventoryItemId: itemId, quantity: mode.toBase(Number(quantity)) });
      setQuantity('1');
      reloadUsage();
    } finally {
      setBusy(false);
    }
  }

  function itemName(id: string) {
    return items.find((i) => i.id === id)?.name ?? id;
  }


  return (
    <div className={encounterStyles.card}>
      <div className={encounterStyles.cardTitle}>Kullanılan Malzeme</div>
      {usage.length === 0 ? (
        <div className={styles.emptyNote}>Henüz malzeme eklenmedi. Muayene tamamlandığında burada listelenen malzemeler stoktan otomatik düşülür.</div>
      ) : (
        usage.map((u) => (
          <div key={u.id} className={styles.row}>
            <span>{itemName(u.inventoryItemId)}</span>
            <span>{(() => { const it = items.find((i) => i.id === u.inventoryItemId); return formatAmount(u.quantity, it?.unit, it?.pieceSize, it?.pieceLabel); })()}</span>
          </div>
        ))
      )}
      {!readOnly && (
        <form className={styles.form} onSubmit={handleAdd}>
          <FieldWrap label="Malzeme">
            <Select value={itemId} onChange={(e) => setItemId(e.target.value)} disabled={items.length === 0}>
              {items.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.name} ({formatStock(i.quantityOnHand, i.openedQuantity, i.unit)} mevcut)
                </option>
              ))}
            </Select>
          </FieldWrap>
          <FieldWrap label={mode.label}>
            <Input type="number" min={mode.min} step={mode.step} value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
          </FieldWrap>
          <Button type="submit" variant="secondary" disabled={busy || items.length === 0}>
            Ekle
          </Button>
        </form>
      )}
    </div>
  );
}
