import { useEffect, useState } from 'react';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { FieldWrap, Select } from '../../components/ui/Field';
import { formatStock, hasStock } from '../inventory/stockFormat';

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Reçeteden "kliniğin stoğundan ver" (spec 2026-10-09 S5.6): TARBİL'e bağlı, stokta olan ve SKT'si geçmemiş kalemler.
 * Stok TARBİL'de reçete onaylanınca düşer.
 */
export function StockGivePicker({ value, onChange }: { value: string | null; onChange: (id: string | null, name: string | null) => void }) {
  const [items, setItems] = useState<InventoryItem[]>([]);

  useEffect(() => {
    const today = todayIso();
    inventoryApi
      .list()
      .then((all) => setItems(all.filter((i) => i.tarbilProductName && hasStock(i) && !(i.expiryDate && i.expiryDate < today))))
      .catch(() => setItems([]));
  }, []);

  return (
    <FieldWrap label="Stok ürünü">
      <Select
        aria-label="Stok ürünü"
        value={value ?? ''}
        onChange={(e) => {
          const picked = items.find((i) => i.id === e.target.value);
          onChange(picked?.id ?? null, picked?.name ?? null);
        }}
      >
        <option value="">Seçiniz</option>
        {items.map((i) => (
          <option key={i.id} value={i.id}>
            {`${i.name} · Seri ${i.lotNumber ?? '—'} · ${formatStock(i.quantityOnHand, i.openedQuantity, i.unit)}`}
          </option>
        ))}
      </Select>
    </FieldWrap>
  );
}
