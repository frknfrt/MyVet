import { ChangeEvent, FormEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { imagingApi, ImagingRecordDetail } from '../../api/imagingApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { ImagingStatusBadge, MODALITY_LABELS } from './imagingStatus';
import styles from './ImagingRecordDetailModal.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function isImage(contentType: string | null): boolean {
  return !!contentType && contentType.startsWith('image/');
}

interface ImagingRecordDetailModalProps {
  recordId: string | null;
  onClose: () => void;
  onChanged: () => void;
}

export function ImagingRecordDetailModal({ recordId, onClose, onChanged }: ImagingRecordDetailModalProps) {
  const [detail, setDetail] = useState<ImagingRecordDetail | null>(null);
  const [findings, setFindings] = useState('');
  const [previewUrls, setPreviewUrls] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  function load() {
    if (!recordId) return;
    imagingApi.get(recordId).then((d) => {
      setDetail(d);
      setFindings(d.findings ?? '');
    });
  }

  useEffect(() => {
    setError(null);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recordId]);

  useEffect(() => {
    if (!detail) return;
    const imageFiles = detail.files.filter((f) => isImage(f.contentType));
    let cancelled = false;
    const urls: Record<string, string> = {};

    Promise.all(
      imageFiles.map(async (f) => {
        const res = await fetch(imagingApi.fileUrl(f.id), { headers: imagingApi.authHeader() });
        if (!res.ok) return;
        const blob = await res.blob();
        urls[f.id] = URL.createObjectURL(blob);
      })
    ).then(() => {
      if (!cancelled) setPreviewUrls(urls);
    });

    return () => {
      cancelled = true;
      Object.values(urls).forEach((u) => URL.revokeObjectURL(u));
    };
  }, [detail]);

  async function handleComplete(e: FormEvent) {
    e.preventDefault();
    if (busy || !recordId) return;
    setBusy(true);
    setError(null);
    try {
      await imagingApi.complete(recordId, findings);
      onChanged();
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleCancel() {
    if (busy || !recordId) return;
    setBusy(true);
    setError(null);
    try {
      await imagingApi.cancel(recordId);
      onChanged();
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !recordId) return;
    setUploading(true);
    setError(null);
    try {
      await imagingApi.uploadFile(recordId, file);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  async function handleDownload(fileId: string, fileName: string) {
    try {
      const res = await fetch(imagingApi.fileUrl(fileId), { headers: imagingApi.authHeader() });
      if (!res.ok) throw new Error('Dosya indirilemedi');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(errorMessageOf(err));
    }
  }

  if (!detail) {
    return (
      <Modal open={recordId !== null} onClose={onClose} width={680}>
        <div className={styles.loading}>Yükleniyor...</div>
      </Modal>
    );
  }

  const isPending = detail.status === 'PENDING';
  const dirty = isPending && findings !== (detail.findings ?? '');

  return (
    <Modal open={recordId !== null} onClose={onClose} width={680} dirty={dirty}>
      <div className={styles.header}>
        <div>
          <div className={styles.title}>
            {MODALITY_LABELS[detail.modality]}
            {detail.bodyRegion ? ` · ${detail.bodyRegion}` : ''}
          </div>
          <div className={styles.subline}>
            {detail.patientName} · {detail.ownerFullName}
          </div>
        </div>
        <ImagingStatusBadge status={detail.status} />
      </div>

      <div className={styles.metaGrid}>
        <div className={styles.metaItem}>
          <div className={styles.metaLabel}>İstek Tarihi</div>
          <div className={styles.metaValue}>{new Date(detail.requestedAt).toLocaleString('tr-TR')}</div>
        </div>
        <div className={styles.metaItem}>
          <div className={styles.metaLabel}>İsteyen</div>
          <div className={styles.metaValue}>{detail.orderingStaffName ?? '—'}</div>
        </div>
        <div className={styles.metaItem}>
          <div className={styles.metaLabel}>Sonuç Tarihi</div>
          <div className={styles.metaValue}>{detail.resultedAt ? new Date(detail.resultedAt).toLocaleString('tr-TR') : '—'}</div>
        </div>
      </div>

      {detail.notes && (
        <div className={styles.notesBlock}>
          <div className={styles.metaLabel}>Not</div>
          <div className={styles.notesText}>{detail.notes}</div>
        </div>
      )}

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.sectionLabel}>Görseller</div>
      {detail.files.length === 0 ? (
        <div className={styles.filesEmpty}>Henüz görsel eklenmedi</div>
      ) : (
        <div className={styles.gallery}>
          {detail.files.map((f) =>
            isImage(f.contentType) ? (
              <div key={f.id} className={styles.thumb} onClick={() => handleDownload(f.id, f.fileName)}>
                {previewUrls[f.id] ? (
                  <img src={previewUrls[f.id]} alt={f.fileName} />
                ) : (
                  <div className={styles.thumbLoading}>...</div>
                )}
                <div className={styles.thumbCaption}>{f.fileName}</div>
              </div>
            ) : (
              <div key={f.id} className={styles.fileChip} onClick={() => handleDownload(f.id, f.fileName)}>
                <span className={styles.fileChipName}>{f.fileName}</span>
                <span className={styles.muted}>{formatBytes(f.fileSize)}</span>
              </div>
            )
          )}
        </div>
      )}
      <input ref={fileInputRef} type="file" className={styles.fileInput} onChange={handleFileChange} disabled={uploading} />
      <Button type="button" variant="secondary" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
        {uploading ? 'Yükleniyor...' : '+ Görsel Ekle'}
      </Button>

      {isPending ? (
        <form onSubmit={handleComplete}>
          <div className={styles.sectionLabel}>Sonucu Tamamla</div>
          <FieldWrap label="Radyolojik bulgular / yorum">
            <Textarea rows={4} value={findings} onChange={(e) => setFindings(e.target.value)} required />
          </FieldWrap>
          <div className={styles.actionsSplit}>
            <Button type="button" variant="danger" onClick={handleCancel} disabled={busy}>
              İsteği İptal Et
            </Button>
            <Button type="submit" variant="primary" disabled={busy}>
              {busy ? 'Kaydediliyor...' : 'Sonucu Kaydet'}
            </Button>
          </div>
        </form>
      ) : detail.findings ? (
        <div className={styles.notesBlock}>
          <div className={styles.sectionLabel}>Radyolojik Bulgular</div>
          <div className={styles.notesText}>{detail.findings}</div>
        </div>
      ) : null}
    </Modal>
  );
}
