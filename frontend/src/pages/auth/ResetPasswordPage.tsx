import { FormEvent, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { authApi } from '../../api/authApi';
import { ApiError } from '../../api/client';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import styles from './AuthForm.module.css';

const MIN_LENGTH = 8;

/** E-postadaki bağlantıdan açılır: yeni şifre belirlenir, bağlantı tek kullanımlıktır. */
export function ResetPasswordPage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!token || busy) return;
    if (password.length < MIN_LENGTH) {
      setError(`Şifre en az ${MIN_LENGTH} karakter olmalıdır.`);
      return;
    }
    if (password !== confirmPassword) {
      setError('Şifreler eşleşmiyor.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await authApi.confirmPasswordReset(token, password);
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Şifre değiştirilemedi, tekrar deneyin.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout headline={<>Yeni <em>şifre</em> belirleyin</>} subcopy="En az 8 karakterli, tahmin edilmesi zor bir şifre seçin.">
      {done ? (
        <div>
          <h1 className={styles.cardTitle}>Şifreniz değişti</h1>
          <p className={styles.lede}>Yeni şifrenizle giriş yapabilirsiniz.</p>
          <Button variant="primary" className={styles.fullWidth} onClick={() => navigate('/login')}>
            Girişe git
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit}>
          <h1 className={styles.cardTitle}>Yeni şifre</h1>
          <p className={styles.lede}>Bu bağlantı 1 saat geçerlidir ve bir kez kullanılabilir.</p>

          {error && (
            <div className={styles.errorBanner}>
              {error}
              {error.includes('geçersiz') && (
                <>
                  {' '}
                  <Link to="/sifremi-unuttum">Yeni bağlantı iste</Link>
                </>
              )}
            </div>
          )}

          <FieldWrap label="Yeni şifre">
            <Input
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={MIN_LENGTH}
              autoFocus
              required
            />
          </FieldWrap>
          <FieldWrap label="Yeni şifre (tekrar)">
            <Input
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              minLength={MIN_LENGTH}
              required
            />
          </FieldWrap>

          <Button type="submit" variant="primary" className={styles.fullWidth} disabled={busy}>
            {busy ? 'Kaydediliyor...' : 'Şifremi değiştir'}
          </Button>

          <p className={styles.footNote}>
            <Link to="/login">Girişe dön</Link>
          </p>
        </form>
      )}
    </AuthLayout>
  );
}
