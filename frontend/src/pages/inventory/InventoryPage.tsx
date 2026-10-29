import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { AppShell } from '../../components/layout/AppShell';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { ItemDetailModal } from './ItemDetailModal';
import { NewInventoryItemModal } from './NewInventoryItemModal';
import { PendingDeductionsPanel } from './PendingDeductionsPanel';
import { TarbilStockSyncPanel } from './TarbilStockSyncPanel';
import styles from './InventoryPage.module.css';
import { formatStock, openedExpiryStatus, unitLabel } from './stockFormat';

/** Kapalı kısım: "2 şişe", "1 kutu", "5 adet" (spec 2026-10-24). */
function closedLabel(item: InventoryItem): string {
  return formatStock(item.quantityOnHand, null, item.unit);
}

/** Açık kısım: "49 ml", "19 flakon"; adetle kullanılanda "—". */
function openLabel(item: InventoryItem): string {
  if (item.unit === 'ADET') return item.openedQuantity > 0 ? `${item.openedQuantity.toLocaleString('tr-TR')} açık` : '—';
  return `${item.openedQuantity.toLocaleString('tr-TR')} ${unitLabel(item.unit)}`;
}

export function InventoryPage() {
  const { session } = useAuth();
  // InventoryItemsController yazma -- sadece TECHNICIAN/ADMIN (VET/RECEPTIONIST yok).
  const canWrite = session ? ['TECHNICIAN', 'ADMIN'].includes(session.role) : false;
  // /tarbil/** uclari ADMIN ve VET'e acik (TARBIL'de stok isini hekim yapar).
  const canTarbilSync = session ? ['ADMIN', 'VET'].includes(session.role) : false;
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<InventoryItem | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  function load() {
    setLoading(true);
    inventoryApi
      .list()
      .then((data) => {
        setItems(data);
        // Modal acikken "Birimi Kaydet" gibi bir degisiklik sonrasi onChanged={load}
        // cagrilir; selected'i de taze veriyle degistirmezsek ItemDetailModal eski
        // item prop'uyla kalir (ornek: packageSize hala null gorunur, "openable"
        // yanlis hesaplanir, acik miktar alani gorunmez). Bkz. bulunan bug notu.
        setSelected((prev) => (prev ? data.find((i) => i.id === prev.id) ?? prev : prev));
      })
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

  function expiryInfo(expiryDate: string | null) {
    if (!expiryDate) return { label: '—', tone: null as 'warning' | 'danger' | null };
    const days = Math.ceil((new Date(expiryDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24));
    const label = new Date(expiryDate).toLocaleDateString('tr-TR');
    if (days < 0) return { label, tone: 'danger' as const };
    if (days <= 30) return { label, tone: 'warning' as const };
    return { label, tone: null };
  }

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Stok</h1>
          <div className={styles.sub}>Şube stok kalemleri ve hareketleri</div>
        </div>
        <div>
          <input className={styles.search} placeholder="Ara..." value={query} onChange={(e) => setQuery(e.target.value)} />
          {canWrite && (
            <Button variant="primary" onClick={() => setCreateOpen(true)}>
              Yeni Ürün
            </Button>
          )}
        </div>
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Ürün</div>
          <div>Kategori</div>
          <div>Kapalı</div>
          <div>Açık</div>
          <div>Kritik Eşik</div>
          <div>Son Kullanma</div>
          <div>Durum</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className={styles.empty}>Stok kalemi bulunamadı</div>
        ) : (
          filtered.map((item) => {
            const expiry = expiryInfo(item.expiryDate);
            return (
              <div key={item.id} className={styles.row} onClick={() => setSelected(item)}>
                <div>{item.name}</div>
                <div className={styles.muted}>{item.category ?? '—'}</div>
                <div>{closedLabel(item)}</div>
                <div className={item.unit === 'ADET' ? styles.muted : undefined}>
                  {openLabel(item)}
                  {openedExpiryStatus(item.openedExpiryDate) === 'expired' && <> <Badge tone="danger">Açık ürünün süresi doldu</Badge></>}
                  {openedExpiryStatus(item.openedExpiryDate) === 'soon' && <> <Badge tone="warning">Açık ürün bitiyor</Badge></>}
                </div>
                <div className={styles.muted}>{item.reorderThreshold}</div>
                <div>
                  {expiry.tone ? <Badge tone={expiry.tone}>{expiry.label}</Badge> : <span className={styles.muted}>{expiry.label}</span>}
                </div>
                <div>
                  {item.belowReorderThreshold ? <Badge tone="warning">Düşük Stok</Badge> : <Badge tone="success">Yeterli</Badge>}
                </div>
              </div>
            );
          })
        )}
      </div>

      <PendingDeductionsPanel canAct={canTarbilSync} onSettled={load} />
      {canTarbilSync && <TarbilStockSyncPanel onApplied={load} />}
      <ItemDetailModal item={selected} onClose={() => setSelected(null)} onChanged={load} />
      <NewInventoryItemModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={load} />
    </AppShell>
  );
}
