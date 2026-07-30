import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import styles from './AuthForm.module.css';

export function RegisterClinicPage() {
  const { registerClinic } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    tenantName: '',
    taxNumber: '',
    branchName: '',
    adminFullName: '',
    adminEmail: '',
    adminPassword: '',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function update<K extends keyof typeof form>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError(null);
    try {
      await registerClinic(form);
      navigate('/kurulum');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Kayit olusturulamadi, tekrar deneyin');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout
      headline={<>Kliniğinizi <em>dakikalar içinde</em> kurun</>}
      subcopy="Klinik bilgilerinizi ve ilk yönetici hesabınızı oluşturun, hemen ardından kurulum sihirbazıyla devam edin."
    >
      <form onSubmit={handleSubmit}>
        <h1 className={styles.cardTitle}>Klinik kaydı oluştur</h1>
        <p className={styles.lede}>14 günlük ücretsiz deneme ile başlayın</p>

        {error && <div className={styles.errorBanner}>{error}</div>}

        <FieldWrap label="Klinik adı">
          <Input
            placeholder="Mutlu Pati Veteriner Kliniği"
            value={form.tenantName}
            onChange={(e) => update('tenantName', e.target.value)}
            required
          />
        </FieldWrap>
        <div className={styles.row}>
          <FieldWrap label="Vergi numarası">
            <Input value={form.taxNumber} onChange={(e) => update('taxNumber', e.target.value)} />
          </FieldWrap>
          <FieldWrap label="Şube adı">
            <Input
              placeholder="Merkez Şube"
              value={form.branchName}
              onChange={(e) => update('branchName', e.target.value)}
              required
            />
          </FieldWrap>
        </div>

        <FieldWrap label="Yetkili adı soyadı">
          <Input
            placeholder="Dr. Ayşe Yılmaz"
            value={form.adminFullName}
            onChange={(e) => update('adminFullName', e.target.value)}
            required
          />
        </FieldWrap>
        <FieldWrap label="E-posta">
          <Input
            type="email"
            placeholder="ornek@klinik.com"
            value={form.adminEmail}
            onChange={(e) => update('adminEmail', e.target.value)}
            required
          />
        </FieldWrap>
        <FieldWrap label="Şifre">
          <Input
            type="password"
            placeholder="En az 8 karakter"
            minLength={8}
            value={form.adminPassword}
            onChange={(e) => update('adminPassword', e.target.value)}
            required
          />
        </FieldWrap>

        <Button type="submit" variant="primary" className={styles.fullWidth} disabled={loading}>
          {loading ? 'Oluşturuluyor...' : 'Klinik kaydı oluştur'}
        </Button>

        <p className={styles.footNote}>
          Zaten bir hesabınız var mı? <Link to="/login">Giriş yapın</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
