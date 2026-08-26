import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { StaffInvitePublicInfo, staffInvitesApi } from '../../api/staffInvitesApi';
import { StaffRole } from '../../auth/session';
import { useAuth } from '../../auth/AuthContext';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import styles from './AuthForm.module.css';

const ROLE_LABELS: Record<StaffRole, string> = {
  VET: 'Veteriner Hekim',
  TECHNICIAN: 'Teknisyen',
  RECEPTIONIST: 'Resepsiyonist',
  ADMIN: 'Yönetici',
  OWNER_ACCOUNT: 'Sahip Hesabı',
};

export function AcceptInvitePage() {
  const { token } = useParams<{ token: string }>();
  const { login } = useAuth();
  const navigate = useNavigate();

  const [invite, setInvite] = useState<StaffInvitePublicInfo | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    staffInvitesApi
      .getByToken(token)
      .then(setInvite)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : 'Davet bulunamadı veya süresi dolmuş'));
  }, [token]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!token || busy) return;
    if (password !== confirmPassword) {
      setSubmitError('Şifreler eşleşmiyor');
      return;
    }
    setBusy(true);
    setSubmitError(null);
    try {
      await staffInvitesApi.accept(token, password);
      await login({ email: invite!.email, password });
      navigate('/panel');
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Davet kabul edilemedi, tekrar deneyin');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      headline={<>Ekibe <em>katılın</em></>}
      subcopy="Davetinizi kabul edip kendi şifrenizi belirleyerek hesabınızı etkinleştirin."
    >
      {loadError ? (
        <>
          <h1 className={styles.cardTitle}>Davet geçersiz</h1>
          <p className={styles.lede}>{loadError}</p>
        </>
      ) : !invite ? (
        <p className={styles.lede}>Yükleniyor...</p>
      ) : (
        <form onSubmit={handleSubmit}>
          <h1 className={styles.cardTitle}>{invite.tenantName}</h1>
          <p className={styles.lede}>
            {invite.fullName} · {ROLE_LABELS[invite.role]} · {invite.email}
          </p>

          {submitError && <div className={styles.errorBanner}>{submitError}</div>}

          <FieldWrap label="Şifre">
            <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required />
          </FieldWrap>
          <FieldWrap label="Şifre (tekrar)">
            <Input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              minLength={8}
              required
            />
          </FieldWrap>

          <Button type="submit" variant="primary" className={styles.fullWidth} disabled={busy}>
            {busy ? 'Hesap oluşturuluyor...' : 'Hesabımı Etkinleştir'}
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
