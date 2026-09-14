import { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { billingApi, InvoiceSummary, OwnerBalance } from '../../api/billingApi';
import { Button } from '../../components/ui/Button';
import { CashRegisterPanel } from './CashRegisterPanel';
import { InvoiceDetailModal } from './InvoiceDetailModal';
import { InvoiceStatusBadge } from './invoiceStatus';
import { NewInvoiceModal } from './NewInvoiceModal';
import { QuickSaleModal } from './QuickSaleModal';
import styles from './FinancePage.module.css';

type Tab = 'invoices' | 'cash-register' | 'debtors';

export function FinancePage() {
  const [tab, setTab] = useState<Tab>('invoices');
  const [invoices, setInvoices] = useState<InvoiceSummary[]>([]);
  const [debtors, setDebtors] = useState<OwnerBalance[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(null);
  const [newInvoiceOpen, setNewInvoiceOpen] = useState(false);
  const [quickSaleOpen, setQuickSaleOpen] = useState(false);

  function loadInvoices() {
    setLoading(true);
    billingApi
      .listInvoices()
      .then(setInvoices)
      .finally(() => setLoading(false));
  }

  function loadDebtors() {
    billingApi.listOwnerBalances().then(setDebtors);
  }

  useEffect(() => {
    if (tab === 'invoices') loadInvoices();
    if (tab === 'debtors') loadDebtors();
  }, [tab]);

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Finans</h1>
        </div>
        {tab === 'invoices' && (
          <div className={styles.actions}>
            <Button variant="secondary" onClick={() => setQuickSaleOpen(true)}>
              Hızlı Satış
            </Button>
            <Button variant="primary" onClick={() => setNewInvoiceOpen(true)}>
              Yeni Fatura
            </Button>
          </div>
        )}
      </div>

      <div className={styles.tabs}>
        <div className={`${styles.tab} ${tab === 'invoices' ? styles.tabActive : ''}`} onClick={() => setTab('invoices')}>
          Faturalar
        </div>
        <div className={`${styles.tab} ${tab === 'cash-register' ? styles.tabActive : ''}`} onClick={() => setTab('cash-register')}>
          Kasa
        </div>
        <div className={`${styles.tab} ${tab === 'debtors' ? styles.tabActive : ''}`} onClick={() => setTab('debtors')}>
          Borç Listesi
        </div>
      </div>

      {tab === 'invoices' && (
        <div className={styles.tableCard}>
          <div className={`${styles.tableHead} ${styles.invoicesHead}`}>
            <div>Sahip</div>
            <div>Kesim Tarihi</div>
            <div>Tutar</div>
            <div>Durum</div>
          </div>
          {loading ? (
            <div className={styles.empty}>Yükleniyor...</div>
          ) : invoices.length === 0 ? (
            <div className={styles.empty}>Fatura bulunmuyor</div>
          ) : (
            invoices.map((inv) => (
              <div
                key={inv.id}
                className={`${styles.row} ${styles.invoicesRow} ${styles.rowClickable}`}
                onClick={() => setSelectedInvoiceId(inv.id)}
              >
                <div>{inv.ownerName}</div>
                <div className={styles.muted}>{inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString('tr-TR') : 'Taslak'}</div>
                <div className={styles.amount}>{inv.totalAmount.toFixed(2)} ₺</div>
                <div>
                  <InvoiceStatusBadge status={inv.status} />
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {tab === 'cash-register' && <CashRegisterPanel />}

      {tab === 'debtors' && (
        <div className={styles.tableCard}>
          <div className={`${styles.tableHead} ${styles.debtorsHead}`}>
            <div>Sahip</div>
            <div>Telefon</div>
            <div>Açık Bakiye</div>
          </div>
          {debtors.length === 0 ? (
            <div className={styles.empty}>Açık borç bulunmuyor</div>
          ) : (
            debtors.map((d) => (
              <div key={d.ownerId} className={`${styles.row} ${styles.debtorsRow}`}>
                <div>{d.ownerName}</div>
                <div className={styles.muted}>{d.ownerPhone}</div>
                <div className={styles.amount}>{d.outstandingBalance.toFixed(2)} ₺</div>
              </div>
            ))
          )}
        </div>
      )}

      <InvoiceDetailModal
        invoiceId={selectedInvoiceId}
        onClose={() => setSelectedInvoiceId(null)}
        onChanged={loadInvoices}
      />

      <NewInvoiceModal
        open={newInvoiceOpen}
        onClose={() => setNewInvoiceOpen(false)}
        onCreated={(invoiceId) => {
          setNewInvoiceOpen(false);
          loadInvoices();
          setSelectedInvoiceId(invoiceId);
        }}
      />

      <QuickSaleModal
        open={quickSaleOpen}
        onClose={() => setQuickSaleOpen(false)}
        onCompleted={(invoiceId) => {
          setQuickSaleOpen(false);
          loadInvoices();
          setSelectedInvoiceId(invoiceId);
        }}
      />
    </AppShell>
  );
}
