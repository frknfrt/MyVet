import { useEffect, useState } from 'react';
import { billingApi, PaymentMethod, QuickSaleLine } from '../../api/billingApi';
import { ApiError } from '../../api/client';
import { InventoryItem, inventoryApi } from '../../api/inventoryApi';
import { OwnerSearchResult, patientApi } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './QuickSaleModal.module.css';

const PAYMENT_LABELS: Record<PaymentMethod, string> = {
  CARD: 'Kart',
  CASH: 'Nakit',
  TEXT_TO_PAY: 'Text-to-Pay',
  INSTALLMENT: 'Taksit',
};

interface CartLine extends QuickSaleLine {
  key: string;
}

interface QuickSaleModalProps {
  open: boolean;
  onClose: () => void;
  onCompleted: (invoiceId: string) => void;
}

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function QuickSaleModal({ open, onClose, onCompleted }: QuickSaleModalProps) {
  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerResults, setOwnerResults] = useState<OwnerSearchResult[]>([]);
  const [ownerSearchOpen, setOwnerSearchOpen] = useState(false);

  const [items, setItems] = useState<InventoryItem[]>([]);
  const [selectedItemId, setSelectedItemId] = useState('');
  const [lineQty, setLineQty] = useState('1');
  const [linePrice, setLinePrice] = useState('');
  const [lineVatRate, setLineVatRate] = useState('20');
  const [cart, setCart] = useState<CartLine[]>([]);

  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setOwnerId(null);
      setOwnerLabel(null);
      setIsAnonymous(false);
      setOwnerQuery('');
      setOwnerResults([]);
      setSelectedItemId('');
      setLineQty('1');
      setLinePrice('');
      setLineVatRate('20');
      setCart([]);
      setPaymentMethod('CASH');
      setError(null);
      return;
    }
    inventoryApi.list().then(setItems);
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

  function handleItemSelect(id: string) {
    setSelectedItemId(id);
    const item = items.find((i) => i.id === id);
    setLinePrice(item?.unitCost != null ? String(item.unitCost) : '');
  }

  function addToCart() {
    const item = items.find((i) => i.id === selectedItemId);
    const qty = Number(lineQty);
    const price = Number(linePrice);
    if (!item || qty <= 0 || price < 0 || linePrice === '') return;
    setCart((prev) => [
      ...prev,
      {
        key: `${item.id}-${Date.now()}`,
        inventoryItemId: item.id,
        description: item.name,
        quantity: qty,
        unitPrice: price,
        vatRate: Number(lineVatRate) || 0,
      },
    ]);
    setSelectedItemId('');
    setLineQty('1');
    setLinePrice('');
    setLineVatRate('20');
  }

  function removeFromCart(key: string) {
    setCart((prev) => prev.filter((l) => l.key !== key));
  }

  // Backend kalem toplamini KDV dahil hesaplar (InvoiceLine.create) ve Hizli
  // Satis'te tahsilat fatura toplami uzerinden alinir -- sepetteki tutarlar da
  // KDV dahil gosterilmeli, aksi halde yazan tutar ile cekilen tutar ayrisir.
  const lineTotalOf = (l: CartLine) => l.quantity * l.unitPrice * (1 + l.vatRate / 100);
  const total = cart.reduce((sum, l) => sum + lineTotalOf(l), 0);

  async function handleComplete() {
    if (busy) return;
    if (ownerId === null && !isAnonymous) {
      setError('Lütfen bir müşteri seçin veya anonim satış olarak devam edin.');
      return;
    }
    if (cart.length === 0) {
      setError('Sepete en az bir ürün ekleyin.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const invoiceId = await billingApi.quickSale({
        ownerId: ownerId ?? undefined,
        lines: cart.map(({ key: _key, ...line }) => line),
        paymentMethod,
      });
      onCompleted(invoiceId);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} width={640}>
      <div className={styles.title}>Hızlı Satış</div>
      <div className={styles.sub}>Kayıtlı bir müşteri seçin veya anonim satış yapın, ürünleri ekleyip ödemeyi alın.</div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <FieldWrap label="Müşteri">
        {isAnonymous ? (
          <div className={styles.selectedChip}>
            <span>Anonim / Günlük Müşteri</span>
            <button type="button" className={styles.changeBtn} onClick={() => setIsAnonymous(false)}>
              Değiştir
            </button>
          </div>
        ) : ownerId ? (
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
          <>
            <button type="button" className={styles.anonymousBtn} onClick={() => setIsAnonymous(true)}>
              Anonim / Günlük Müşteri Olarak Devam Et
            </button>
            <div className={styles.ownerPicker}>
              <Input
                placeholder="veya kayıtlı müşteri ara (ad veya telefon)"
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
          </>
        )}
      </FieldWrap>

      <div className={styles.sectionLabel}>Ürün Ekle</div>
      <div className={styles.addLineForm}>
        <FieldWrap label="Ürün">
          <Select value={selectedItemId} onChange={(e) => handleItemSelect(e.target.value)}>
            <option value="">Seçiniz</option>
            {items.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name} ({i.quantityOnHand} adet stokta)
              </option>
            ))}
          </Select>
        </FieldWrap>
        <FieldWrap label="Adet">
          <Input type="number" min={1} value={lineQty} onChange={(e) => setLineQty(e.target.value)} />
        </FieldWrap>
        <FieldWrap label="Birim Fiyat">
          <Input type="number" step="0.01" min={0} value={linePrice} onChange={(e) => setLinePrice(e.target.value)} />
        </FieldWrap>
        <FieldWrap label="KDV %">
          <Input type="number" step="1" min={0} value={lineVatRate} onChange={(e) => setLineVatRate(e.target.value)} />
        </FieldWrap>
        <Button type="button" variant="secondary" onClick={addToCart} disabled={!selectedItemId || linePrice === ''}>
          Sepete Ekle
        </Button>
      </div>

      <div className={styles.sectionLabel}>Sepet</div>
      {cart.length === 0 ? (
        <div className={styles.emptyNote}>Henüz ürün eklenmedi</div>
      ) : (
        cart.map((l) => (
          <div key={l.key} className={styles.cartRow}>
            <span>{l.description}</span>
            <span>{l.quantity}x</span>
            <span>{l.unitPrice.toFixed(2)} ₺</span>
            <span>%{l.vatRate} KDV</span>
            <span>{lineTotalOf(l).toFixed(2)} ₺</span>
            <button type="button" className={styles.removeBtn} onClick={() => removeFromCart(l.key)}>
              ×
            </button>
          </div>
        ))
      )}

      <div className={styles.totalRow}>
        <span>Toplam (KDV dahil)</span>
        <span className={styles.totalValue}>{total.toFixed(2)} ₺</span>
      </div>

      <FieldWrap label="Ödeme Yöntemi">
        <Select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}>
          {Object.entries(PAYMENT_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </FieldWrap>

      <div className={styles.actions}>
        <Button variant="secondary" onClick={onClose} disabled={busy}>
          Vazgeç
        </Button>
        <Button variant="primary" onClick={handleComplete} disabled={busy}>
          {busy ? 'Tamamlanıyor...' : 'Satışı Tamamla'}
        </Button>
      </div>
    </Modal>
  );
}
