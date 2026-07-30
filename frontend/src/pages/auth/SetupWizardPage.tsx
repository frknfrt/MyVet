import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { authApi } from '../../api/authApi';
import { useAuth } from '../../auth/AuthContext';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import styles from './AuthForm.module.css';

export function SetupWizardPage() {
  const { session } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ address: '', city: '', timezone: 'Europe/Istanbul', tarbilBranchCode: '' });
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authApi
      .getCurrentBranch()
      .then((overview) => {
        setForm({
          address: overview.address ?? '',
          city: overview.city ?? '',
          timezone: overview.timezone ?? 'Europe/Istanbul',
          tarbilBranchCode: overview.tarbilBranchCode ?? '',
        });
      })
      .finally(() => setLoaded(true));
  }, []);

  function update<K extends keyof typeof form>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError(null);
    try {
      await authApi.updateCurrentBranch(form);
      navigate('/panel');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Kaydedilemedi, tekrar deneyin');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout
      headline={<>Son bir adım <em>kaldı</em></>}
      subcopy="Şubenizin adres ve zaman dilimi bilgilerini tamamlayın — randevu takvimi ve TARBİL bildirimleri bu bilgiyi kullanır."
    >
      <form onSubmit={handleSubmit}>
        <div className={styles.stepIndicator}>Kurulum Sihirbazı</div>
        <h1 className={styles.cardTitle}>{session?.fullName ? `Hoş geldiniz, ${session.fullName}` : 'Şube bilgilerini tamamlayın'}</h1>
        <p className={styles.lede}>Bu bilgileri daha sonra Ayarlar sayfasından da güncelleyebilirsiniz</p>

        {error && <div className={styles.errorBanner}>{error}</div>}

        <FieldWrap label="Adres">
          <Input
            placeholder="Bağdat Cad. No: 1"
            value={form.address}
            onChange={(e) => update('address', e.target.value)}
            required
            disabled={!loaded}
          />
        </FieldWrap>
        <div className={styles.row}>
          <FieldWrap label="Şehir">
            <Input
              placeholder="İstanbul"
              value={form.city}
              onChange={(e) => update('city', e.target.value)}
              required
              disabled={!loaded}
            />
          </FieldWrap>
          <FieldWrap label="Zaman dilimi">
            <Input
              value={form.timezone}
              onChange={(e) => update('timezone', e.target.value)}
              required
              disabled={!loaded}
            />
          </FieldWrap>
        </div>
        <FieldWrap label="TARBİL şube kodu (opsiyonel)">
          <Input
            value={form.tarbilBranchCode}
            onChange={(e) => update('tarbilBranchCode', e.target.value)}
            disabled={!loaded}
          />
        </FieldWrap>

        <Button type="submit" variant="primary" className={styles.fullWidth} disabled={loading || !loaded}>
          {loading ? 'Kaydediliyor...' : 'Kurulumu tamamla'}
        </Button>
      </form>
    </AuthLayout>
  );
}
