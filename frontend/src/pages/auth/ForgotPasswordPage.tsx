import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../../api/authApi';
import { ApiError } from '../../api/client';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import styles from './AuthForm.module.css';

/** "Şifremi unuttum": e-posta kayıtlı olsun olmasın aynı mesaj gösterilir. */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await authApi.requestPasswordReset(email.trim());
      setSent(true);
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 429
          ? 'Çok fazla deneme yapıldı. Birkaç dakika sonra tekrar deneyin.'
          : 'İstek gönderilemedi, tekrar deneyin.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      headline={<>Şifrenizi <em>sıfırlayın</em></>}
      subcopy="Hesabınıza kayıtlı e-posta adresine bir sıfırlama bağlantısı gönderelim."
    >
      {sent ? (
        <div>
          <h1 className={styles.cardTitle}>E-postanızı kontrol edin</h1>
          <p className={styles.lede}>
            <b>{email.trim()}</b> adresi Vetly'de kayıtlıysa şifre sıfırlama bağlantısı gönderdik. Bağlantı 1 saat
            geçerlidir. E-posta birkaç dakika içinde gelmezse gereksiz (spam) klasörüne bakın.
          </p>
          <p className={styles.footNote}>
            <Link to="/login">Girişe dön</Link>
          </p>
        </div>
      ) : (
        <form onSubmit={handleSubmit}>
          <h1 className={styles.cardTitle}>Şifremi unuttum</h1>
          <p className={styles.lede}>Giriş yaptığınız e-posta adresini yazın.</p>

          {error && <div className={styles.errorBanner}>{error}</div>}

          <FieldWrap label="E-posta">
            <Input
              type="email"
              placeholder="ornek@klinik.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoFocus
              required
            />
          </FieldWrap>

          <Button type="submit" variant="primary" className={styles.fullWidth} disabled={busy}>
            {busy ? 'Gönderiliyor...' : 'Sıfırlama bağlantısı gönder'}
          </Button>

          <p className={styles.footNote}>
            <Link to="/login">Girişe dön</Link>
          </p>
        </form>
      )}
    </AuthLayout>
  );
}
