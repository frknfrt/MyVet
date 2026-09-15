import { ChangeEvent, FormEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { labApi, LabResultDetail, LabResultItemInput, LabValueFlag } from '../../api/labApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { LabResultStatusBadge } from './labResultStatus';
import styles from './LabResultDetailModal.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const FLAG_LABELS: Record<LabValueFlag, string> = { NORMAL: 'Normal', LOW: 'Düşük', HIGH: 'Yüksek', ABNORMAL: 'Anormal' };

interface LabResultDetailModalProps {
  resultId: string | null;
  onClose: () => void;
  onChanged: () => void;
}

export function LabResultDetailModal({ resultId, onClose, onChanged }: LabResultDetailModalProps) {
  const [detail, setDetail] = useState<LabResultDetail | null>(null);
  const [resultSummary, setResultSummary] = useState('');
  const [items, setItems] = useState<LabResultItemInput[]>([]);
  const [busy, setBusy] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [aiEvaluated, setAiEvaluated] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const initialFormRef = useRef({ resultSummary: '', items: [] as LabResultItemInput[] });

  function load() {
    if (!resultId) return;
    labApi.get(resultId).then((d) => {
      setDetail(d);
      setAiEvaluated(false);
      const summary = d.resultSummary ?? '';
      const loadedItems =
        d.items.length > 0
          ? d.items.map((i) => ({ parameterName: i.parameterName, value: i.value, unit: i.unit ?? '', referenceRange: i.referenceRange ?? '', flag: i.flag ?? undefined }))
          : [{ parameterName: '', value: '', unit: '', referenceRange: '' }];
      setResultSummary(summary);
      setItems(loadedItems);
      initialFormRef.current = { resultSummary: summary, items: loadedItems };
    });
  }

  useEffect(() => {
    setError(null);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resultId]);

  function updateItem(index: number, patch: Partial<LabResultItemInput>) {
    setItems((prev) => prev.map((it, i) => (i === index ? { ...it, ...patch } : it)));
  }

  function addItem() {
    setItems((prev) => [...prev, { parameterName: '', value: '', unit: '', referenceRange: '' }]);
  }

  function removeItem(index: number) {
    setItems((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleComplete(e: FormEvent) {
    e.preventDefault();
    if (busy || !resultId) return;
    setBusy(true);
    setError(null);
    try {
      const cleanItems = items
        .filter((i) => i.parameterName.trim() && i.value.trim())
        .map((i) => ({ ...i, unit: i.unit || undefined, referenceRange: i.referenceRange || undefined }));
      await labApi.complete(resultId, { resultSummary, items: cleanItems });
      onChanged();
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleEvaluate() {
    if (evaluating) return;
    const cleanItems = items.filter((i) => i.parameterName.trim() && i.value.trim());
    if (cleanItems.length === 0) return;
    setEvaluating(true);
    setError(null);
    try {
      const result = await labApi.evaluate(cleanItems);
      setItems(result.items);
      setResultSummary(result.draftSummary);
      setAiEvaluated(true);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setEvaluating(false);
    }
  }

  async function handleCancel() {
    if (busy || !resultId) return;
    setBusy(true);
    setError(null);
    try {
      await labApi.cancel(resultId);
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
    if (!file || !resultId) return;
    setUploading(true);
    setError(null);
    try {
      await labApi.uploadFile(resultId, file);
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
      const res = await fetch(labApi.fileUrl(fileId), { headers: labApi.authHeader() });
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
      <Modal open={resultId !== null} onClose={onClose} width={620}>
        <div className={styles.loading}>Yükleniyor...</div>
      </Modal>
    );
  }

  const isPending = detail.status === 'PENDING';
  const dirty =
    isPending &&
    JSON.stringify({ resultSummary, items }) !== JSON.stringify(initialFormRef.current);

  return (
    <Modal open={resultId !== null} onClose={onClose} width={640} dirty={dirty}>
      <div className={styles.header}>
        <div>
          <div className={styles.testName}>{detail.testName}</div>
          <div className={styles.subline}>
            {detail.patientName} · {detail.ownerFullName}
          </div>
        </div>
        <LabResultStatusBadge status={detail.status} />
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

      {isPending ? (
        <form onSubmit={handleComplete}>
          <div className={styles.sectionLabel}>
            Sonucu Tamamla
            {aiEvaluated && <Badge tone="ai">AI Önerisi</Badge>}
          </div>
          <FieldWrap label="Genel değerlendirme">
            <Textarea rows={2} value={resultSummary} onChange={(e) => setResultSummary(e.target.value)} required />
          </FieldWrap>

          <div className={styles.itemsHead}>
            <span>Parametre</span>
            <span>Değer</span>
            <span>Birim</span>
            <span>Referans</span>
            <span>Bayrak</span>
            <span />
          </div>
          {items.map((item, i) => (
            <div key={i} className={styles.itemRow}>
              <Input
                value={item.parameterName}
                onChange={(e) => updateItem(i, { parameterName: e.target.value })}
                placeholder="Örn. WBC"
              />
              <Input value={item.value} onChange={(e) => updateItem(i, { value: e.target.value })} placeholder="Değer" />
              <Input value={item.unit ?? ''} onChange={(e) => updateItem(i, { unit: e.target.value })} placeholder="Birim" />
              <Input
                value={item.referenceRange ?? ''}
                onChange={(e) => updateItem(i, { referenceRange: e.target.value })}
                placeholder="Ref. aralık"
              />
              <Select value={item.flag ?? ''} onChange={(e) => updateItem(i, { flag: (e.target.value || undefined) as LabValueFlag | undefined })}>
                <option value="">—</option>
                <option value="NORMAL">Normal</option>
                <option value="LOW">Düşük</option>
                <option value="HIGH">Yüksek</option>
                <option value="ABNORMAL">Anormal</option>
              </Select>
              <button type="button" className={styles.removeItemBtn} onClick={() => removeItem(i)}>
                ×
              </button>
            </div>
          ))}
          <div className={styles.itemsFooter}>
            <button type="button" className={styles.addItemBtn} onClick={addItem}>
              + Parametre ekle
            </button>
            <Button
              type="button"
              variant="ai"
              onClick={handleEvaluate}
              disabled={evaluating || items.every((i) => !i.parameterName.trim() || !i.value.trim())}
            >
              {evaluating ? 'Değerlendiriliyor...' : '⚡ Otomatik Ön-Değerlendirme'}
            </Button>
          </div>
          <div className={styles.evaluateHint}>
            Değerler kural tabanlı olarak işaretlenir ve taslak bir özet oluşturulur; sonuç hekim onayına sunulur, siz kaydetmeden uygulanmaz.
          </div>

          <div className={styles.actionsSplit}>
            <Button type="button" variant="danger" onClick={handleCancel} disabled={busy}>
              İsteği İptal Et
            </Button>
            <Button type="submit" variant="primary" disabled={busy}>
              {busy ? 'Kaydediliyor...' : 'Sonucu Kaydet'}
            </Button>
          </div>
        </form>
      ) : (
        detail.items.length > 0 && (
          <>
            <div className={styles.sectionLabel}>Sonuç Değerleri</div>
            <div className={styles.resultTable}>
              <div className={styles.resultHead}>
                <span>Parametre</span>
                <span>Değer</span>
                <span>Referans</span>
                <span>Bayrak</span>
              </div>
              {detail.items.map((item) => (
                <div key={item.id} className={styles.resultRow}>
                  <span>{item.parameterName}</span>
                  <span>
                    {item.value} {item.unit ?? ''}
                  </span>
                  <span className={styles.muted}>{item.referenceRange ?? '—'}</span>
                  <span>
                    {item.flag && item.flag !== 'NORMAL' ? (
                      <Badge tone={item.flag === 'HIGH' ? 'danger' : item.flag === 'LOW' ? 'warning' : 'danger'}>
                        {FLAG_LABELS[item.flag]}
                      </Badge>
                    ) : item.flag === 'NORMAL' ? (
                      <Badge tone="success">Normal</Badge>
                    ) : (
                      '—'
                    )}
                  </span>
                </div>
              ))}
            </div>
            {detail.resultSummary && (
              <div className={styles.notesBlock}>
                <div className={styles.metaLabel}>Genel Değerlendirme</div>
                <div className={styles.notesText}>{detail.resultSummary}</div>
              </div>
            )}
          </>
        )
      )}

      <div className={styles.sectionLabel}>Dosya Ekleri</div>
      <div className={styles.filesList}>
        {detail.files.length === 0 ? (
          <div className={styles.filesEmpty}>Henüz dosya eklenmedi</div>
        ) : (
          detail.files.map((f) => (
            <div key={f.id} className={styles.fileRow} onClick={() => handleDownload(f.id, f.fileName)}>
              <span className={styles.fileName}>{f.fileName}</span>
              <span className={styles.muted}>{formatBytes(f.fileSize)}</span>
            </div>
          ))
        )}
      </div>
      <input ref={fileInputRef} type="file" className={styles.fileInput} onChange={handleFileChange} disabled={uploading} />
      <Button type="button" variant="secondary" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
        {uploading ? 'Yükleniyor...' : '+ Dosya Ekle'}
      </Button>
    </Modal>
  );
}
