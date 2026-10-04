import { useEffect, useState } from 'react';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { Select } from '../../components/ui/Field';

/**
 * Kategorisi tam olarak "Aşı" (büyük/küçük harf ve ş/ı farkı gözetmeden) olan kalemler -- sunucu da yalnız bunları
 * stoktan düşer (ApplyVaccinationStockUseCase). P1a TARBİL'den gelen kalemler "Aşı" kategorisiyle açılır.
 */
const isVaccine = (i: InventoryItem) =>
  (i.category ?? '').trim().toLocaleLowerCase('tr-TR').replace(/ş/g, 's').replace(/ı/g, 'i') === 'asi';

function trDate(iso: string | null): string {
  const m = iso ? /^(\d{4})-(\d{2})-(\d{2})/.exec(iso) : null;
  return m ? `${m[3]}.${m[2]}.${m[1]}` : '—';
}

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Yeni Asi formu (spec 2026-10-04 P2 S3.2): subenin asi stogundan seri secimi. "Stokta yok" secilirse asi adi elle
 * yazilir (stok baglantisi olmaz, eklenti urun adimini hekime birakir). SKT'si gecmis seri secilemez.
 */
export function VaccineStockPicker({ value, onChange }: { value: string | null; onChange: (item: InventoryItem | null) => void }) {
  const [items, setItems] = useState<InventoryItem[]>([]);

  useEffect(() => {
    inventoryApi
      .list()
      .then((all) => setItems(all.filter((i) => isVaccine(i) && i.quantityOnHand > 0)))
      .catch(() => setItems([]));
  }, []);

  const today = todayIso();

  return (
    <Select value={value ?? ''} onChange={(e) => onChange(items.find((i) => i.id === e.target.value) ?? null)}>
      <option value="">Stokta yok — aşı adını elle yaz</option>
      {items.map((i) => {
        const expired = !!i.expiryDate && i.expiryDate < today;
        return (
          <option key={i.id} value={i.id} disabled={expired} style={expired ? { color: 'var(--color-danger-700)' } : undefined}>
            {`${i.name} · Seri ${i.lotNumber ?? '—'} · SKT ${trDate(i.expiryDate)} · ${i.quantityOnHand} adet${expired ? ' (SKT geçmiş)' : ''}`}
          </option>
        );
      })}
    </Select>
  );
}
