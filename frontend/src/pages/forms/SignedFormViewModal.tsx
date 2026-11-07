import { useCallback, useEffect, useState } from 'react';
import { formsApi, type SignedFormDetail } from '../../api/formsApi';
import { useAuth } from '../../auth/AuthContext';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { FormRenderer } from '../../forms/FormRenderer';
import { formatDateTime } from './formRoles';
import styles from './SignedFormViewModal.module.css';

interface Props {
  signedFormId: string | null;
  onClose: () => void;
  /** Iptal sonrasi ust listeyi yenilemek icin. */
  onChanged?: () => void;
}

export function SignedFormViewModal({ signedFormId, onClose, onChanged }: Props) {
  const { session } = useAuth();
  const [detail, setDetail] = useState<SignedFormDetail | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [signatureSrc, setSignatureSrc] = useState<string | null>(null);
  const [voiding, setVoiding] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const canVoid = session?.role === 'VET' || session?.role === 'ADMIN';

  const load = useCallback(async (id: string) => {
    try {
      setDetail(await formsApi.getSignedForm(id));
      setLoadError(false);
    } catch {
      setLoadError(true);
    }
  }, []);

  useEffect(() => {
    setDetail(null);
    setLoadError(false);
    setVoiding(false);
    setReason('');
    setActionError(null);
    if (signedFormId) void load(signedFormId);
  }, [signedFormId, load]);

  // Imza gorseli: yetkili fetch -> object URL (kapaninca iptal)
  useEffect(() => {
    if (!signedFormId) return;
    let cancelled = false;
    let objectUrl: string | null = null;
    setSignatureSrc(null);
    formsApi
      .fetchFile(formsApi.signatureUrl(signedFormId))
      .then(async (res) => {
        if (!res.ok || cancelled) return;
        objectUrl = URL.createObjectURL(await res.blob());
        if (cancelled) URL.revokeObjectURL(objectUrl);
        else setSignatureSrc(objectUrl);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [signedFormId]);

  async function downloadPdf() {
    if (!signedFormId || busy) return;
    setBusy(true);
    setActionError(null);
    try {
      const res = await formsApi.fetchFile(formsApi.signedFormPdfUrl(signedFormId));
      if (!res.ok) throw new Error('pdf');
      const disposition = res.headers?.get?.('Content-Disposition') ?? '';
      const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
      let filename = `imzali-form-${signedFormId}.pdf`;
      if (match) {
        try {
          filename = decodeURIComponent(match[1]);
        } catch {
          filename = match[1];
        }
      }
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setActionError('PDF indirilemedi, lütfen tekrar deneyin.');
    } finally {
      setBusy(false);
    }
  }

  async function confirmVoid() {
    if (!signedFormId || !reason.trim() || busy) return;
    setBusy(true);
    setActionError(null);
    try {
      await formsApi.voidSignedForm(signedFormId, reason.trim());
      setVoiding(false);
      setReason('');
      await load(signedFormId);
      onChanged?.();
    } catch {
      setActionError('Form iptal edilemedi, lütfen tekrar deneyin.');
    } finally {
      setBusy(false);
    }
  }

  const signer = detail?.signer;

  return (
    <Modal open={signedFormId !== null} onClose={onClose} width={720}>
      {loadError ? (
        <div className={styles.note}>Form yüklenemedi</div>
      ) : !detail ? (
        <div className={styles.note}>Yükleniyor...</div>
      ) : (
        <div className={styles.body}>
          {detail.status === 'VOIDED' && (
            <div className={styles.voided} role="status">
              <strong>İPTAL EDİLDİ</strong>
              {detail.voidedAt && <span> · {formatDateTime(detail.voidedAt)}</span>}
              {detail.voidReason && <div>Gerekçe: {detail.voidReason}</div>}
            </div>
          )}
          {!detail.integrityOk && (
            <div className={styles.warning} role="alert">
              Bütünlük doğrulanamadı — bu belgenin içeriği imzadan sonra değişmiş olabilir.
            </div>
          )}

          <div className={styles.meta}>
            <h3 className={styles.heading}>{detail.templateTitle}</h3>
            <Badge tone={detail.status === 'VALID' ? 'success' : 'danger'}>{detail.status === 'VALID' ? 'Geçerli' : 'İptal edildi'}</Badge>
          </div>
          <div className={styles.metaLine}>
            İmzalayan: {signer?.name ?? '—'}
            {signer?.type === 'OTHER' ? ' (hasta yakını/diğer)' : ' (hasta sahibi)'}
          </div>
          <div className={styles.metaLine}>
            İmza tarihi: {formatDateTime(detail.signedAt)}
            {detail.signedByName ? ` · Kaydeden: ${detail.signedByName}` : ''}
          </div>

          {detail.content && (
            <div className={styles.form}>
              <FormRenderer
                content={detail.content}
                values={detail.values ?? {}}
                mode="readonly"
                signatureSlot={
                  signatureSrc ? (
                    <img src={signatureSrc} alt="İmza" className={styles.signature} />
                  ) : (
                    <div className={styles.metaLine}>İmza görseli yükleniyor...</div>
                  )
                }
              />
            </div>
          )}

          {actionError && <div className={styles.error}>{actionError}</div>}

          {voiding && (
            <div className={styles.voidBox}>
              <label htmlFor="void-reason">İptal gerekçesi</label>
              <textarea id="void-reason" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
              <div className={styles.actions}>
                <Button variant="secondary" onClick={() => setVoiding(false)} disabled={busy}>
                  Vazgeç
                </Button>
                <Button variant="danger" onClick={confirmVoid} disabled={busy || !reason.trim()}>
                  İptali onayla
                </Button>
              </div>
            </div>
          )}

          <div className={styles.actions}>
            <Button variant="secondary" onClick={downloadPdf} disabled={busy}>
              PDF olarak indir
            </Button>
            {canVoid && detail.status === 'VALID' && !voiding && (
              <Button variant="danger" onClick={() => setVoiding(true)}>
                İptal et
              </Button>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}
