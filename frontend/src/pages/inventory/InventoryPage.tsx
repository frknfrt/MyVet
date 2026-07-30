import { useEffect, useMemo, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { ItemDetailModal } from './ItemDetailModal';
import { NewInventoryItemModal } from './NewInventoryItemModal';
import styles from './InventoryPage.module.css';

export function InventoryPage() {
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<InventoryItem | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  function load() {
    setLoading(true);
    inventoryApi
      .list()
      .then(setItems)
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((i) => i.name.toLowerCase().includes(q) || i.category?.toLowerCase().includes(q));
  }, [items, query]);

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Stok</h1>
          <div className={styles.sub}>Şube stok kalemleri ve hareketleri</div>
        </div>
        <div>
          <input className={styles.search} placeholder="Ara..." value={query} onChange={(e) => setQuery(e.target.value)} />
          <Button variant="primary" onClick={() => setCreateOpen(true)}>
            Yeni Ürün
          </Button>
        </div>
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Ürün</div>
          <div>Kategori</div>
          <div>Miktar</div>
          <div>Kritik Eşik</div>
          <div>Durum</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className={styles.empty}>Stok kalemi bulunamadı</div>
        ) : (
          filtered.map((item) => (
            <div key={item.id} className={styles.row} onClick={() => setSelected(item)}>
              <div>{item.name}</div>
              <div className={styles.muted}>{item.category ?? '—'}</div>
              <div>{item.quantityOnHand}</div>
              <div className={styles.muted}>{item.reorderThreshold}</div>
              <div>
                {item.belowReorderThreshold ? <Badge tone="warning">Düşük Stok</Badge> : <Badge tone="success">Yeterli</Badge>}
              </div>
            </div>
          ))
        )}
      </div>

      <ItemDetailModal item={selected} onClose={() => setSelected(null)} onChanged={load} />
      <NewInventoryItemModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={load} />
    </AppShell>
  );
}
