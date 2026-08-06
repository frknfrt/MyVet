import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { authApi } from '../../api/authApi';
import { Button } from '../ui/Button';
import { FieldWrap, Input } from '../ui/Field';
import { Modal } from '../ui/Modal';
import styles from './ChangePasswordModal.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

interface ChangePasswordModalProps {
  open: boolean;
  onClose: () => void;
}

export function ChangePasswordModal({ open, onClose }: ChangePasswordModalProps) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (!open) {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setError(null);
      setSuccess(false);
    }
  }, [open]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (newPassword !== confirmPassword) {
      setError('Yeni şifre ile tekrarı eşleşmiyor');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      setSuccess(true);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} width={420}>
      <form onSubmit={handleSubmit}>
        <h2 className={styles.title}>Şifre Değiştir</h2>

        {error && <div className={styles.errorBanner}>{error}</div>}
        {success && <div className={styles.successBanner}>Şifreniz başarıyla güncellendi.</div>}

        <FieldWrap label="Mevcut şifre">
          <Input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
          />
        </FieldWrap>
        <FieldWrap label="Yeni şifre (en az 8 karakter)">
          <Input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} minLength={8} required />
        </FieldWrap>
        <FieldWrap label="Yeni şifre (tekrar)">
          <Input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            minLength={8}
            required
          />
        </FieldWrap>

        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            Kapat
          </Button>
          <Button type="submit" variant="primary" disabled={busy}>
            {busy ? 'Kaydediliyor...' : 'Şifreyi Güncelle'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
