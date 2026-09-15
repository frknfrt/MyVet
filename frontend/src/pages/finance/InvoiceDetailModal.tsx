import { FormEvent, useEffect, useState } from 'react';
import { billingApi, InvoiceDetail, PaymentMethod } from '../../api/billingApi';
import { appointmentApi, ServiceTypeItem } from '../../api/appointmentApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { InvoiceStatusBadge } from './invoiceStatus';
import styles from './InvoiceDetailModal.module.css';

const PAYMENT_LABELS: Record<PaymentMethod, string> = {
  CARD: 'Kart',
  CASH: 'Nakit',
  TEXT_TO_PAY: 'Text-to-Pay',
  INSTALLMENT: 'Taksit',
};

interface InvoiceDetailModalProps {
  invoiceId: string | null;
  onClose: () => void;
  onChanged: () => void;
}

export function InvoiceDetailModal({ invoiceId, onClose, onChanged }: InvoiceDetailModalProps) {
  const [invoice, setInvoice] = useState<InvoiceDetail | null>(null);
  const [serviceTypes, setServiceTypes] = useState<ServiceTypeItem[]>([]);
  const [busy, setBusy] = useState(false);
  const [lineServiceTypeId, setLineServiceTypeId] = useState('');
  const [lineDesc, setLineDesc] = useState('');
  const [lineQty, setLineQty] = useState('1');
  const [linePrice, setLinePrice] = useState('');
  const [lineDiscount, setLineDiscount] = useState('0');
  const [lineVatRate, setLineVatRate] = useState('20');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [paymentAmount, setPaymentAmount] = useState('');

  function reload() {
    if (!invoiceId) return;
    billingApi.getInvoice(invoiceId).then(setInvoice);
  }

  useEffect(() => {
    appointmentApi.listServiceTypes().then(setServiceTypes);
  }, []);

  useEffect(() => {
    setInvoice(null);
    reload();
    setLineServiceTypeId('');
    setLineDesc('');
    setLineQty('1');
    setLinePrice('');
    setLineDiscount('0');
    setLineVatRate('20');
    setPaymentAmount('');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [invoiceId]);

  function handleServiceTypeSelect(id: string) {
    setLineServiceTypeId(id);
    const svc = serviceTypes.find((s) => s.id === id);
    if (svc) {
      setLineDesc(svc.name);
      setLinePrice(String(svc.defaultPrice));
    }
  }

  async function handleAddLine(e: FormEvent) {
    e.preventDefault();
    if (!invoiceId || busy) return;
    setBusy(true);
    try {
      await billingApi.addLine(invoiceId, {
        description: lineDesc,
        quantity: Number(lineQty),
        unitPrice: Number(linePrice),
        discountAmount: Number(lineDiscount) || 0,
        vatRate: Number(lineVatRate) || 0,
        serviceTypeId: lineServiceTypeId || undefined,
      });
      setLineServiceTypeId('');
      setLineDesc('');
      setLineQty('1');
      setLinePrice('');
      setLineDiscount('0');
      setLineVatRate('20');
      reload();
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  async function handleIssue() {
    if (!invoiceId) return;
    setBusy(true);
    try {
      await billingApi.issueInvoice(invoiceId);
      reload();
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  async function handleVoid() {
    if (!invoiceId) return;
    if (!window.confirm('Faturayi iptal etmek istediginizden emin misiniz?')) return;
    setBusy(true);
    try {
      await billingApi.voidInvoice(invoiceId);
      reload();
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  async function handleRecordPayment(e: FormEvent) {
    e.preventDefault();
    if (!invoiceId || busy) return;
    setBusy(true);
    try {
      await billingApi.recordPayment(invoiceId, { method: paymentMethod, amount: Number(paymentAmount) });
      setPaymentAmount('');
      reload();
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  const dirty =
    lineServiceTypeId !== '' ||
    lineDesc !== '' ||
    linePrice !== '' ||
    lineDiscount !== '0' ||
    lineVatRate !== '20' ||
    paymentAmount !== '';

  return (
    <Modal open={invoiceId !== null} onClose={onClose} width={700} dirty={dirty}>
      {!invoice ? (
        <div>Yükleniyor...</div>
      ) : (
        <>
          <div className={styles.header}>
            <div>
              <div className={styles.ownerName}>{invoice.ownerName}</div>
              <div className={styles.subline}>{invoice.issuedAt ? new Date(invoice.issuedAt).toLocaleDateString('tr-TR') : 'Henüz kesilmedi'}</div>
              {invoice.status !== 'DRAFT' && (
                <div className={styles.subline}>
                  {invoice.eInvoiceRef ? `e-Fatura No: ${invoice.eInvoiceRef}` : 'e-Fatura: gönderiliyor...'}
                </div>
              )}
            </div>
            <InvoiceStatusBadge status={invoice.status} />
          </div>

          <div className={styles.totalsRow}>
            <div className={styles.totalItem}>
              <div className={styles.totalLabel}>Toplam</div>
              <div className={styles.totalValue}>{invoice.totalAmount.toFixed(2)} ₺</div>
            </div>
            <div className={styles.totalItem}>
              <div className={styles.totalLabel}>Ödenen</div>
              <div className={styles.totalValue}>{invoice.paidAmount.toFixed(2)} ₺</div>
            </div>
            <div className={styles.totalItem}>
              <div className={styles.totalLabel}>Kalan</div>
              <div className={styles.totalValue}>{(invoice.totalAmount - invoice.paidAmount).toFixed(2)} ₺</div>
            </div>
          </div>

          <div className={styles.sectionLabel}>Kalemler</div>
          {invoice.lines.length === 0 ? (
            <div className={styles.emptyNote}>Henüz kalem eklenmedi</div>
          ) : (
            <>
              <div className={styles.lineHeaderRow}>
                <span>Açıklama</span>
                <span>Adet</span>
                <span>Birim Fiyat</span>
                <span>İndirim</span>
                <span>KDV</span>
                <span>Tutar</span>
                <span>Kaynak</span>
              </div>
              {invoice.lines.map((l) => (
                <div key={l.id} className={styles.lineRow}>
                  <span>{l.description}</span>
                  <span>{l.quantity}x</span>
                  <span>{l.unitPrice.toFixed(2)} ₺</span>
                  <span>{l.discountAmount > 0 ? `-${l.discountAmount.toFixed(2)} ₺` : '—'}</span>
                  <span>{l.vatRate > 0 ? `%${l.vatRate} (${l.vatAmount.toFixed(2)} ₺)` : '—'}</span>
                  <span>{l.lineTotal.toFixed(2)} ₺</span>
                  <span>{l.source === 'AUTO_CHARGE_CAPTURE' ? 'Otomatik' : 'Manuel'}</span>
                </div>
              ))}
            </>
          )}

          {invoice.status === 'DRAFT' && (
            <form className={styles.addLineForm} onSubmit={handleAddLine}>
              <FieldWrap label="Hizmet/Ürün (opsiyonel)">
                <Select value={lineServiceTypeId} onChange={(e) => handleServiceTypeSelect(e.target.value)}>
                  <option value="">Serbest kalem</option>
                  {serviceTypes.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </Select>
              </FieldWrap>
              <FieldWrap label="Açıklama">
                <Input value={lineDesc} onChange={(e) => setLineDesc(e.target.value)} required />
              </FieldWrap>
              <FieldWrap label="Adet">
                <Input type="number" min={1} value={lineQty} onChange={(e) => setLineQty(e.target.value)} required />
              </FieldWrap>
              <FieldWrap label="Birim Fiyat">
                <Input type="number" step="0.01" value={linePrice} onChange={(e) => setLinePrice(e.target.value)} required />
              </FieldWrap>
              <FieldWrap label="İndirim">
                <Input type="number" step="0.01" min={0} value={lineDiscount} onChange={(e) => setLineDiscount(e.target.value)} />
              </FieldWrap>
              <FieldWrap label="KDV %">
                <Input type="number" step="1" min={0} value={lineVatRate} onChange={(e) => setLineVatRate(e.target.value)} />
              </FieldWrap>
              <Button type="submit" variant="secondary" disabled={busy}>
                Ekle
              </Button>
            </form>
          )}

          <div className={styles.sectionLabel}>Ödemeler</div>
          {invoice.payments.length === 0 ? (
            <div className={styles.emptyNote}>Henüz ödeme alınmadı</div>
          ) : (
            invoice.payments.map((p) => (
              <div key={p.id} className={styles.paymentRow}>
                <span>{PAYMENT_LABELS[p.method]}</span>
                <span>{new Date(p.paidAt).toLocaleString('tr-TR')}</span>
                <span>{p.amount.toFixed(2)} ₺</span>
              </div>
            ))
          )}

          {(invoice.status === 'ISSUED' || invoice.status === 'PARTIALLY_PAID') && (
            <form className={styles.paymentForm} onSubmit={handleRecordPayment}>
              <FieldWrap label="Yöntem">
                <Select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}>
                  <option value="CASH">Nakit</option>
                  <option value="CARD">Kart</option>
                  <option value="TEXT_TO_PAY">Text-to-Pay</option>
                  <option value="INSTALLMENT">Taksit</option>
                </Select>
              </FieldWrap>
              <FieldWrap label="Tutar">
                <Input type="number" step="0.01" value={paymentAmount} onChange={(e) => setPaymentAmount(e.target.value)} required />
              </FieldWrap>
              <Button type="submit" variant="secondary" disabled={busy}>
                Ödeme Al
              </Button>
            </form>
          )}

          <div className={styles.actions}>
            {invoice.status === 'DRAFT' && (
              <>
                <Button variant="danger" onClick={handleVoid} disabled={busy}>
                  İptal Et
                </Button>
                <Button variant="primary" onClick={handleIssue} disabled={busy || invoice.lines.length === 0}>
                  Faturayı Kes
                </Button>
              </>
            )}
          </div>
        </>
      )}
    </Modal>
  );
}
