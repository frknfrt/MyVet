import { useEffect, useMemo, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { InvoiceStatus } from '../../api/billingApi';
import { ProductSalesLine, reportingApi, ReportFilters, RevenueReportLine } from '../../api/reportingApi';
import { Button } from '../../components/ui/Button';
import { InvoiceStatusBadge } from '../finance/invoiceStatus';
import styles from './ReportsPage.module.css';

type Tab = 'revenue' | 'products';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

function firstDayOfMonth(): string {
  const d = new Date();
  return isoDate(new Date(d.getFullYear(), d.getMonth(), 1));
}

function formatCurrency(v: number): string {
  return `${v.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₺`;
}

const STATUS_OPTIONS: InvoiceStatus[] = ['ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID'];
const STATUS_LABELS: Record<InvoiceStatus, string> = {
  DRAFT: 'Taslak',
  ISSUED: 'Kesildi',
  PARTIALLY_PAID: 'Kısmi Ödendi',
  PAID: 'Ödendi',
  VOID: 'İptal',
};

export function ReportsPage() {
  const [tab, setTab] = useState<Tab>('revenue');
  const [from, setFrom] = useState(firstDayOfMonth());
  const [to, setTo] = useState(isoDate(new Date()));
  const [status, setStatus] = useState<InvoiceStatus | ''>('');

  const [revenueLines, setRevenueLines] = useState<RevenueReportLine[]>([]);
  const [productLines, setProductLines] = useState<ProductSalesLine[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);

  const filters: ReportFilters = useMemo(
    () => ({ from: from || undefined, to: to || undefined, status: status || undefined }),
    [from, to, status]
  );

  function load() {
    setLoading(true);
    setError(null);
    const request = tab === 'revenue' ? reportingApi.revenueReport(filters) : reportingApi.productSalesReport(filters);
    request
      .then((data) => {
        if (tab === 'revenue') setRevenueLines(data as RevenueReportLine[]);
        else setProductLines(data as ProductSalesLine[]);
      })
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  async function handleDownload() {
    if (downloading) return;
    setDownloading(true);
    setError(null);
    try {
      const url = tab === 'revenue' ? reportingApi.revenueExportUrl(filters) : reportingApi.productSalesExportUrl(filters);
      const res = await fetch(url, { headers: reportingApi.authHeader() });
      if (!res.ok) throw new Error('Rapor indirilemedi');
      const blob = await res.blob();
      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = objectUrl;
      a.download = tab === 'revenue' ? 'ciro-raporu.csv' : 'urun-hizmet-satis-raporu.csv';
      a.click();
      URL.revokeObjectURL(objectUrl);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setDownloading(false);
    }
  }

  const revenueTotals = useMemo(
    () =>
      revenueLines.reduce(
        (acc, l) => ({ total: acc.total + l.totalAmount, paid: acc.paid + l.paidAmount }),
        { total: 0, paid: 0 }
      ),
    [revenueLines]
  );
  const productTotals = useMemo(
    () =>
      productLines.reduce(
        (acc, l) => ({ quantity: acc.quantity + l.totalQuantity, revenue: acc.revenue + l.totalRevenue }),
        { quantity: 0, revenue: 0 }
      ),
    [productLines]
  );

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Raporlar</h1>
          <div className={styles.sub}>Filtrelenebilir, dışa aktarılabilir detaylı raporlar</div>
        </div>
      </div>

      <div className={styles.tabs}>
        <div className={`${styles.tab} ${tab === 'revenue' ? styles.tabActive : ''}`} onClick={() => setTab('revenue')}>
          Ciro Raporu
        </div>
        <div className={`${styles.tab} ${tab === 'products' ? styles.tabActive : ''}`} onClick={() => setTab('products')}>
          Ürün / Hizmet Satış Raporu
        </div>
      </div>

      <div className={styles.filterBar}>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Başlangıç</label>
          <input type="date" className={styles.filterInput} value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className={styles.filterField}>
          <label className={styles.filterLabel}>Bitiş</label>
          <input type="date" className={styles.filterInput} value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        {tab === 'revenue' && (
          <div className={styles.filterField}>
            <label className={styles.filterLabel}>Durum</label>
            <select
              className={styles.filterInput}
              value={status}
              onChange={(e) => setStatus(e.target.value as InvoiceStatus | '')}
            >
              <option value="">Tümü</option>
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </select>
          </div>
        )}
        <Button variant="primary" onClick={load}>
          Filtrele
        </Button>
        <Button variant="secondary" onClick={handleDownload} disabled={downloading}>
          {downloading ? 'İndiriliyor...' : '⭳ CSV İndir'}
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      {tab === 'revenue' ? (
        <div className={styles.tableCard}>
          <div className={styles.revenueHead}>
            <div>Fatura Tarihi</div>
            <div>Müşteri</div>
            <div>Şube</div>
            <div>Durum</div>
            <div>Tutar</div>
            <div>Tahsil Edilen</div>
          </div>
          {loading ? (
            <div className={styles.empty}>Yükleniyor...</div>
          ) : revenueLines.length === 0 ? (
            <div className={styles.empty}>Seçilen aralıkta kesilmiş fatura bulunmuyor</div>
          ) : (
            <>
              {revenueLines.map((l) => (
                <div key={l.invoiceId} className={styles.revenueRow}>
                  <div>{new Date(l.issuedAt).toLocaleDateString('tr-TR')}</div>
                  <div>{l.ownerName}</div>
                  <div className={styles.muted}>{l.branchName}</div>
                  <div>
                    <InvoiceStatusBadge status={l.status} />
                  </div>
                  <div>{formatCurrency(l.totalAmount)}</div>
                  <div className={styles.muted}>{formatCurrency(l.paidAmount)}</div>
                </div>
              ))}
              <div className={styles.totalsRow}>
                <div>Toplam ({revenueLines.length} fatura)</div>
                <div />
                <div />
                <div />
                <div>{formatCurrency(revenueTotals.total)}</div>
                <div>{formatCurrency(revenueTotals.paid)}</div>
              </div>
            </>
          )}
        </div>
      ) : (
        <div className={styles.tableCard}>
          <div className={styles.productHead}>
            <div>Ürün / Hizmet</div>
            <div>Adet</div>
            <div>Toplam Ciro</div>
          </div>
          {loading ? (
            <div className={styles.empty}>Yükleniyor...</div>
          ) : productLines.length === 0 ? (
            <div className={styles.empty}>Seçilen aralıkta satış bulunmuyor</div>
          ) : (
            <>
              {productLines.map((l) => (
                <div key={l.description} className={styles.productRow}>
                  <div>{l.description}</div>
                  <div>{l.totalQuantity}</div>
                  <div>{formatCurrency(l.totalRevenue)}</div>
                </div>
              ))}
              <div className={styles.totalsRowProducts}>
                <div>Toplam ({productLines.length} kalem)</div>
                <div>{productTotals.quantity}</div>
                <div>{formatCurrency(productTotals.revenue)}</div>
              </div>
            </>
          )}
        </div>
      )}
    </AppShell>
  );
}
