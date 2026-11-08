import { useCallback, useEffect, useRef, useState } from 'react';
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
  const generation = useRef(0);
  const [loadedDetail, setDetail] = useState<SignedFormDetail | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const signatureGeneration = useRef(0);
  const [signatureAttempt, setSignatureAttempt] = useState(0);
  const [signatureErrorFor, setSignatureErrorFor] = useState<string | null>(null);
  const [signatureImage, setSignatureSrc] = useState<{ formId: string; url: string; request: number } | null>(null);
  const detail = loadedDetail?.id === signedFormId ? loadedDetail : null;
  const signatureSrc = signatureImage?.formId === signedFormId ? signatureImage.url : null;
  const [voiding, setVoiding] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const canVoid = session?.role === 'VET' || session?.role === 'ADMIN';

  const load = useCallback(async (id: string, request: number, preserveOnFailure = false) => {
    try {
      const response = await formsApi.getSignedForm(id);
      if (generation.current !== request) return;
      setDetail(response);
      setLoadError(false);
      setRefreshFailed(false);
    } catch {
      if (generation.current === request) {
        if (preserveOnFailure) setRefreshFailed(true);
        else setLoadError(true);
      }
    }
  }, []);

  useEffect(() => {
    const request = ++generation.current;
    setDetail(null);
    setBusy(false);
    setLoadError(false);
    setRefreshFailed(false);
    setVoiding(false);
    setReason('');
    setActionError(null);
    if (signedFormId) void load(signedFormId, request);
    return () => { generation.current++; };
  }, [signedFormId, load]);

  // Imza gorseli: yetkili fetch -> object URL (kapaninca iptal)
  useEffect(() => {
    if (!signedFormId) return;
    const request = ++signatureGeneration.current;
    let cancelled = false;
    let objectUrl: string | null = null;
    setSignatureSrc(null);
    setSignatureErrorFor(null);
    formsApi
      .fetchFile(formsApi.signatureUrl(signedFormId))
      .then(async (res) => {
        if (cancelled) return;
        if (!res.ok) throw new Error("signature");
        objectUrl = URL.createObjectURL(await res.blob());
        if (cancelled) URL.revokeObjectURL(objectUrl);
        else setSignatureSrc({ formId: signedFormId, url: objectUrl, request });
      })
      .catch(() => { if (!cancelled) setSignatureErrorFor(signedFormId); });
    return () => {
      cancelled = true;
      signatureGeneration.current++;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [signedFormId, signatureAttempt]);

  async function downloadPdf() {
    if (!signedFormId || busy) return;
    const request = generation.current;
    setBusy(true);
    setActionError(null);
    try {
      const res = await formsApi.fetchFile(formsApi.signedFormPdfUrl(signedFormId));
      if (generation.current !== request) return;
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
      const blob = await res.blob();
      if (generation.current !== request) return;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      try { a.click(); }
      finally {
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10_000);
      }
    } catch {
      if (generation.current === request) setActionError('PDF indirilemedi, lütfen tekrar deneyin.');
    } finally {
      if (generation.current === request) setBusy(false);
    }
  }

  async function confirmVoid() {
    if (!signedFormId || !reason.trim() || busy) return;
    const request = generation.current;
    setBusy(true);
    setActionError(null);
    try {
      await formsApi.voidSignedForm(signedFormId, reason.trim());
      onChanged?.();
      if (generation.current !== request) return;
      setDetail(current => current?.id === signedFormId
        ? { ...current, status: 'VOIDED', voidReason: reason.trim(), voidedAt: null } : current);
      setVoiding(false);
      setReason('');
      await load(signedFormId, request, true);
    } catch {
      if (generation.current === request) setActionError('Form iptal edilemedi, lütfen tekrar deneyin.');
    } finally {
      if (generation.current === request) setBusy(false);
    }
  }

  async function retryRefresh() {
    if (!signedFormId || busy) return;
    const request = generation.current;
    setBusy(true);
    try { await load(signedFormId, request, true); }
    finally { if (generation.current === request) setBusy(false); }
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
          {refreshFailed && (
            <div className={styles.warning} role="alert">
              Form iptal edildi, ancak güncel bilgiler yüklenemedi.
              <Button variant="secondary" onClick={retryRefresh} disabled={busy}>Bilgileri yeniden yükle</Button>
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
                  signatureErrorFor === signedFormId ? (
                    <div role="alert" className={styles.error}>
                      <p>İmza görseli yüklenemedi. Lütfen tekrar deneyin.</p>
                      <Button variant="secondary" onClick={() => setSignatureAttempt(attempt => attempt + 1)}>
                        İmzayı yeniden yükle
                      </Button>
                    </div>
                  ) : signatureSrc ? (
                    <img src={signatureSrc} alt="İmza" className={styles.signature} onError={() => {
                      if (signatureGeneration.current === signatureImage?.request) setSignatureErrorFor(signedFormId);
                    }} />
                  ) : (
                    <div role="status" className={styles.metaLine}>İmza görseli yükleniyor...</div>
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
