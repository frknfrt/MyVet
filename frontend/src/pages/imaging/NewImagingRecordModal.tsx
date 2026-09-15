import { DragEvent, FormEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { imagingApi, ImagingModality } from '../../api/imagingApi';
import { OwnerSearchResult, patientApi } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { MODALITY_LABELS } from './imagingStatus';
import styles from './NewImagingRecordModal.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

interface NewImagingRecordModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (id: string) => void;
}

interface OwnerPatientOption {
  id: string;
  name: string;
}

export function NewImagingRecordModal({ open, onClose, onCreated }: NewImagingRecordModalProps) {
  const [modality, setModality] = useState<ImagingModality>('XRAY');
  const [bodyRegion, setBodyRegion] = useState('');
  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerResults, setOwnerResults] = useState<OwnerSearchResult[]>([]);
  const [ownerSearchOpen, setOwnerSearchOpen] = useState(false);
  const [ownerPatients, setOwnerPatients] = useState<OwnerPatientOption[]>([]);
  const [patientId, setPatientId] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) {
      setModality('XRAY');
      setBodyRegion('');
      setOwnerId(null);
      setOwnerLabel(null);
      setOwnerQuery('');
      setOwnerPatients([]);
      setPatientId('');
      setFiles([]);
      setError(null);
    }
  }, [open]);

  useEffect(() => {
    if (!ownerSearchOpen) return;
    const handle = setTimeout(() => {
      patientApi.searchOwners(ownerQuery).then(setOwnerResults);
    }, 250);
    return () => clearTimeout(handle);
  }, [ownerQuery, ownerSearchOpen]);

  useEffect(() => {
    if (!ownerId) {
      setOwnerPatients([]);
      setPatientId('');
      return;
    }
    patientApi.getOwnerProfile(ownerId).then((profile) => {
      setOwnerPatients(profile.patients.map((p) => ({ id: p.id, name: p.name })));
      setPatientId(profile.patients.length === 1 ? profile.patients[0].id : '');
    });
  }, [ownerId]);

  function selectOwner(o: OwnerSearchResult) {
    setOwnerId(o.id);
    setOwnerLabel(o.fullName);
    setOwnerSearchOpen(false);
    setOwnerQuery('');
  }

  function addFiles(list: FileList | null) {
    if (!list) return;
    setFiles((prev) => [...prev, ...Array.from(list)]);
  }

  function removeFile(index: number) {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  }

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragOver(false);
    addFiles(e.dataTransfer.files);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || !patientId) return;
    setBusy(true);
    setError(null);
    try {
      const id = await imagingApi.request({ patientId, modality, bodyRegion: bodyRegion || undefined });
      for (const file of files) {
        await imagingApi.uploadFile(id, file);
      }
      onCreated(id);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  const dirty = bodyRegion !== '' || ownerId !== null || patientId !== '' || files.length > 0;

  return (
    <Modal open={open} onClose={onClose} width={840} dirty={dirty}>
      <div className={styles.banner}>Görüntüleme Kaydı Yükleme</div>
      <form onSubmit={handleSubmit} className={styles.body}>
        {error && <div className={styles.errorBanner}>{error}</div>}

        <div className={styles.columns}>
          <div className={styles.leftCol}>
            <div className={styles.row2}>
              <FieldWrap label="Modalite*">
                <Select value={modality} onChange={(e) => setModality(e.target.value as ImagingModality)} required>
                  {(Object.keys(MODALITY_LABELS) as ImagingModality[]).map((m) => (
                    <option key={m} value={m}>
                      {MODALITY_LABELS[m]}
                    </option>
                  ))}
                </Select>
              </FieldWrap>
              <FieldWrap label="Bölge (opsiyonel)">
                <Input value={bodyRegion} onChange={(e) => setBodyRegion(e.target.value)} placeholder="Örn. Toraks, Karın" />
              </FieldWrap>
            </div>

            <div className={styles.row2}>
              <FieldWrap label="Müşteri">
                {ownerId ? (
                  <div className={styles.selectedChip}>
                    <span>{ownerLabel}</span>
                    <button
                      type="button"
                      className={styles.changeBtn}
                      onClick={() => {
                        setOwnerId(null);
                        setOwnerLabel(null);
                      }}
                    >
                      Değiştir
                    </button>
                  </div>
                ) : (
                  <div className={styles.ownerPicker}>
                    <Input
                      placeholder="Müşteri bilinmiyor"
                      value={ownerQuery}
                      onFocus={() => setOwnerSearchOpen(true)}
                      onBlur={() => setTimeout(() => setOwnerSearchOpen(false), 150)}
                      onChange={(e) => {
                        setOwnerQuery(e.target.value);
                        setOwnerSearchOpen(true);
                      }}
                    />
                    {ownerSearchOpen && ownerResults.length > 0 && (
                      <div className={styles.ownerDropdown} onMouseDown={(e) => e.preventDefault()}>
                        {ownerResults.map((o) => (
                          <div key={o.id} className={styles.ownerOption} onClick={() => selectOwner(o)}>
                            <span className={styles.ownerOptionName}>{o.fullName}</span>
                            <span className={styles.ownerOptionPhone}>{o.phone}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </FieldWrap>

              <FieldWrap label="Hasta">
                <Select
                  value={patientId}
                  onChange={(e) => setPatientId(e.target.value)}
                  disabled={!ownerId || ownerPatients.length === 0}
                  required
                >
                  <option value="">{ownerId ? 'Hasta seçiniz' : 'Önce müşteri seçiniz'}</option>
                  {ownerPatients.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </Select>
              </FieldWrap>
            </div>

            <Button type="submit" variant="primary" className={styles.submitBtn} disabled={busy || !patientId}>
              {busy ? 'Yükleniyor...' : 'Kaydı Oluştur'}
            </Button>
          </div>

          <div className={styles.rightCol}>
            <div
              className={`${styles.dropzone} ${dragOver ? styles.dropzoneActive : ''}`}
              onClick={() => fileInputRef.current?.click()}
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={handleDrop}
            >
              Yüklemek için görsel seçin ya da buraya sürükleyin
            </div>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="image/*,.dcm,application/dicom"
              className={styles.fileInput}
              onChange={(e) => addFiles(e.target.files)}
            />
            <button type="button" className={styles.uploadTrigger} onClick={() => fileInputRef.current?.click()}>
              Görselleri Seçin
            </button>
            <p className={styles.hint}>ⓘ Aynı kayda aynı anda birden fazla görsel yükleyebilirsiniz</p>

            {files.length > 0 && (
              <div className={styles.fileList}>
                {files.map((f, i) => (
                  <div key={i} className={styles.fileChip}>
                    <span className={styles.fileChipName}>{f.name}</span>
                    <span className={styles.fileChipSize}>{formatBytes(f.size)}</span>
                    <button type="button" className={styles.fileChipRemove} onClick={() => removeFile(i)}>
                      ×
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </form>
    </Modal>
  );
}
