import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { appointmentApi, ServiceTypeItem } from '../../api/appointmentApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import styles from './SettingsPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function ServiceTypesPanel() {
  const [services, setServices] = useState<ServiceTypeItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [durationMin, setDurationMin] = useState('30');
  const [price, setPrice] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    appointmentApi
      .listServiceTypes()
      .then(setServices)
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || !name.trim() || !price) return;
    setBusy(true);
    setError(null);
    try {
      await appointmentApi.createServiceType({
        name: name.trim(),
        defaultDurationMin: Number(durationMin),
        defaultPrice: Number(price),
      });
      setName('');
      setDurationMin('30');
      setPrice('');
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.tableCard}>
        <div className={`${styles.tableHead} ${styles.serviceRow}`}>
          <div>Hizmet Adı</div>
          <div>Süre (dk)</div>
          <div>Fiyat</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : services.length === 0 ? (
          <div className={styles.empty}>Henüz hizmet tanımlanmadı</div>
        ) : (
          services.map((s) => (
            <div key={s.id} className={`${styles.row} ${styles.serviceRow}`}>
              <div>{s.name}</div>
              <div className={styles.muted}>{s.defaultDurationMin} dk</div>
              <div>{s.defaultPrice.toFixed(2)} ₺</div>
            </div>
          ))
        )}
      </div>

      <form className={styles.serviceAddForm} onSubmit={handleSubmit}>
        <FieldWrap label="Hizmet adı">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Örn. Aşı, Ameliyat, Tıraş" required />
        </FieldWrap>
        <FieldWrap label="Süre (dk)">
          <Input type="number" min={1} value={durationMin} onChange={(e) => setDurationMin(e.target.value)} required />
        </FieldWrap>
        <FieldWrap label="Fiyat (₺)">
          <Input type="number" min={0} step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} required />
        </FieldWrap>
        <Button type="submit" variant="primary" disabled={busy || !name.trim() || !price}>
          {busy ? 'Ekleniyor...' : 'Ekle'}
        </Button>
      </form>
    </div>
  );
}
