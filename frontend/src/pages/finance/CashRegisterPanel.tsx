import { FormEvent, useEffect, useState } from 'react';
import { billingApi, CashRegisterSession } from '../../api/billingApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import styles from './FinancePage.module.css';

export function CashRegisterPanel() {
  const [current, setCurrent] = useState<CashRegisterSession | null | undefined>(undefined);
  const [history, setHistory] = useState<CashRegisterSession[]>([]);
  const [openingBalance, setOpeningBalance] = useState('');
  const [closingBalance, setClosingBalance] = useState('');
  const [busy, setBusy] = useState(false);

  function reload() {
    billingApi.getCurrentCashRegister().then(setCurrent);
    billingApi.cashRegisterHistory().then(setHistory);
  }

  useEffect(() => {
    reload();
  }, []);

  async function handleOpen(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await billingApi.openCashRegister({ openingBalance: Number(openingBalance) });
      setOpeningBalance('');
      reload();
    } finally {
      setBusy(false);
    }
  }

  async function handleClose(e: FormEvent) {
    e.preventDefault();
    if (busy || !current) return;
    setBusy(true);
    try {
      await billingApi.closeCashRegister(current.id, { closingBalance: Number(closingBalance) });
      setClosingBalance('');
      reload();
    } finally {
      setBusy(false);
    }
  }

  if (current === undefined) {
    return <div>Yükleniyor...</div>;
  }

  return (
    <div>
      <div className={styles.cashCard}>
        <div className={styles.cashStatusLine}>
          {current ? <Badge tone="success">Kasa Açık</Badge> : <Badge tone="neutral">Kasa Kapalı</Badge>}
        </div>

        {current ? (
          <>
            <div className={styles.cashRow}>
              <span>Açılış bakiyesi</span>
              <span>{current.openingBalance.toFixed(2)} ₺</span>
            </div>
            <div className={styles.cashRow}>
              <span>Açılış zamanı</span>
              <span>{new Date(current.openedAt).toLocaleString('tr-TR')}</span>
            </div>
            <form className={styles.cashForm} onSubmit={handleClose}>
              <FieldWrap label="Kapanış bakiyesi">
                <Input type="number" step="0.01" value={closingBalance} onChange={(e) => setClosingBalance(e.target.value)} required />
              </FieldWrap>
              <div className={styles.cashActions}>
                <Button type="submit" variant="primary" disabled={busy}>
                  Kasayı Kapat
                </Button>
              </div>
            </form>
          </>
        ) : (
          <form className={styles.cashForm} onSubmit={handleOpen}>
            <FieldWrap label="Açılış bakiyesi">
              <Input type="number" step="0.01" value={openingBalance} onChange={(e) => setOpeningBalance(e.target.value)} required />
            </FieldWrap>
            <div className={styles.cashActions}>
              <Button type="submit" variant="primary" disabled={busy}>
                Kasayı Aç
              </Button>
            </div>
          </form>
        )}
      </div>

      <div className={styles.historyTitle}>Geçmiş Oturumlar</div>
      <div className={styles.tableCard}>
        <div className={`${styles.tableHead} ${styles.debtorsHead}`}>
          <div>Açılış</div>
          <div>Kapanış</div>
          <div>Bakiye (açılış → kapanış)</div>
        </div>
        {history.length === 0 ? (
          <div className={styles.empty}>Kayıt yok</div>
        ) : (
          history.map((s) => (
            <div key={s.id} className={`${styles.row} ${styles.debtorsRow}`}>
              <div>{new Date(s.openedAt).toLocaleString('tr-TR')}</div>
              <div className={styles.muted}>{s.closedAt ? new Date(s.closedAt).toLocaleString('tr-TR') : '—'}</div>
              <div>
                {s.openingBalance.toFixed(2)} ₺ → {s.closingBalance != null ? `${s.closingBalance.toFixed(2)} ₺` : '—'}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
