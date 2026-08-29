import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '../components/layout/AuthLayout';
import { Button } from '../components/ui/Button';
import { FieldWrap, Input } from '../components/ui/Field';
import styles from './LoginPage.module.css';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError(null);
    try {
      await login({ email, password });
      navigate('/panel');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Giris yapilamadi, tekrar deneyin');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout
      headline={<>Kliniğinizi yönetmenin <em>en akıllı</em> yolu</>}
      subcopy="SOAP kaydından TARBİL bildirimine, muayene notundan stok takibine — tek ekranda."
    >
      <form className={styles.formCard} onSubmit={handleSubmit}>
        <h1 className={styles.cardTitle}>Tekrar hoş geldiniz</h1>
        <p className={styles.lede}>Klinik hesabınıza giriş yapın</p>

        {error && <div className={styles.errorBanner}>{error}</div>}

        <FieldWrap label="E-posta">
          <Input
            type="email"
            placeholder="ornek@klinik.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </FieldWrap>
        <FieldWrap label="Şifre">
          <Input
            type="password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </FieldWrap>

        <Button type="submit" variant="primary" className={styles.fullWidth} disabled={loading}>
          {loading ? 'Giriş yapılıyor...' : 'Giriş yap'}
        </Button>
      </form>
    </AuthLayout>
  );
}
