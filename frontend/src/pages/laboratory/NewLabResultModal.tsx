import { DragEvent, FormEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { labApi } from '../../api/labApi';
import { OwnerSearchResult, patientApi } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { prepareImage } from '../../lib/prepareImage';
import styles from './NewLabResultModal.module.css';

const COMMON_TEST_NAMES = [
  'Hemogram',
  'Biyokimya Paneli',
  'İdrar Tahlili',
  'Dışkı Tahlili',
  'Serolojik Test',
  'Kan Gazı',
  'Elektrolit Paneli',
  'Sitoloji',
  'Histopatoloji',
  'Radyografi',
  'Ultrasonografi',
];

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// Kamerayla cekilen fotograf + kucuk onizleme (2026-11-07).
function CameraThumb({ file }: { file: File }) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    const u = URL.createObjectURL(file);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [file]);
  return url ? <img className={styles.fileThumb} src={url} alt="" /> : null;
}

interface PendingFile {
  file: File;
  /** Kamerayla cekildi: kayit acilinca otomatik AI okumaya girer. */
  fromCamera: boolean;
}

interface NewLabResultModalProps {
  open: boolean;
  onClose: () => void;
  /** autoOcrFileIds: yalnizca kamerayla cekilen dosyalarin yuklenen id'leri. */
  onCreated: (id: string, opts: { autoOcrFileIds: string[] }) => void;
}

interface OwnerPatientOption {
  id: string;
  name: string;
}

export function NewLabResultModal({ open, onClose, onCreated }: NewLabResultModalProps) {
  const [testName, setTestName] = useState('');
  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerResults, setOwnerResults] = useState<OwnerSearchResult[]>([]);
  const [ownerSearchOpen, setOwnerSearchOpen] = useState(false);
  const [ownerPatients, setOwnerPatients] = useState<OwnerPatientOption[]>([]);
  const [patientId, setPatientId] = useState('');
  const [files, setFiles] = useState<PendingFile[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const [busy, setBusy] = useState(false);
  const [preparing, setPreparing] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);
  // Modal kapanip acilinca artar: kapanmadan once baslayan foto hazirlama sonucu yeni forma sizmasin.
  const generationRef = useRef(0);

  useEffect(() => {
    generationRef.current += 1;
    if (!open) {
      setTestName('');
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

  // Gorseller yuklenmeden once kucultulup JPEG'e cevrilir; PDF vb. aynen kalir.
  async function addFiles(list: FileList | null, fromCamera = false) {
    if (!list) return;
    const picked = Array.from(list);
    const generation = generationRef.current;
    setPreparing((n) => n + 1);
    setError(null);
    try {
      for (const original of picked) {
        const file = original.type.startsWith('image/') ? await prepareImage(original, 'document') : original;
        if (generation !== generationRef.current) return;
        setFiles((prev) => [...prev, { file, fromCamera }]);
      }
    } catch (err) {
      if (generation === generationRef.current) setError(errorMessageOf(err));
    } finally {
      setPreparing((n) => n - 1);
    }
  }

  function removeFile(index: number) {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  }

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragOver(false);
    void addFiles(e.dataTransfer.files);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || !patientId) return;
    setBusy(true);
    setError(null);
    try {
      const id = await labApi.request({ patientId, testName });
      const autoOcrFileIds: string[] = [];
      for (const { file, fromCamera } of files) {
        const fileId = await labApi.uploadFile(id, file);
        if (fromCamera) autoOcrFileIds.push(fileId);
      }
      onCreated(id, { autoOcrFileIds });
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  const hasCameraFile = files.some((f) => f.fromCamera);
  const dirty = testName !== '' || ownerId !== null || patientId !== '' || files.length > 0;

  return (
    <Modal open={open} onClose={onClose} width={840} dirty={dirty}>
      <div className={styles.banner}>Laboratuvar Sonucu Yükleme</div>
      <form onSubmit={handleSubmit} className={styles.body}>
        {error && <div className={styles.errorBanner}>{error}</div>}

        <div className={styles.columns}>
          <div className={styles.leftCol}>
            <FieldWrap label="Sonuç türü*">
              <div className={styles.clearableInput}>
                <Input
                  list="lab-test-names"
                  value={testName}
                  onChange={(e) => setTestName(e.target.value)}
                  placeholder="Örn. Hemogram"
                  required
                />
                {testName && (
                  <button type="button" className={styles.clearBtn} onClick={() => setTestName('')}>
                    ×
                  </button>
                )}
              </div>
              <datalist id="lab-test-names">
                {COMMON_TEST_NAMES.map((n) => (
                  <option key={n} value={n} />
                ))}
              </datalist>
            </FieldWrap>

            <div className={styles.row2}>
              <FieldWrap label="Hasta Sahibi">
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
                      placeholder="Hasta sahibi bilinmiyor"
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
                  <option value="">{ownerId ? 'Hasta seçiniz' : 'Önce hasta sahibi seçiniz'}</option>
                  {ownerPatients.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </Select>
              </FieldWrap>
            </div>

            <Button type="submit" variant="primary" className={styles.submitBtn} disabled={busy || preparing > 0 || !patientId}>
              {busy ? 'Yükleniyor...' : 'Sonucu Oluştur'}
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
              Yüklemek için dosya seçin ya da buraya sürükleyin
            </div>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className={styles.fileInput}
              onChange={(e) => void addFiles(e.target.files)}
            />
            <button type="button" className={styles.uploadTrigger} onClick={() => fileInputRef.current?.click()}>
              Dosyaları Seçin
            </button>
            <input
              ref={cameraInputRef}
              type="file"
              accept="image/*"
              capture="environment"
              className={styles.fileInput}
              onChange={(e) => {
                void addFiles(e.target.files, true);
                e.target.value = '';
              }}
            />
            <Button
              type="button"
              variant="secondary"
              className={styles.cameraBtn}
              onClick={() => cameraInputRef.current?.click()}
              disabled={preparing > 0}
            >
              {preparing > 0 ? 'Hazırlanıyor...' : hasCameraFile ? '📷 Sayfa Ekle' : '📷 Fotoğraf Çek'}
            </Button>
            <p className={styles.hint}>ⓘ Aynı sonuca aynı anda birden fazla dosya ekleyebilirsiniz</p>

            {files.length > 0 && (
              <div className={styles.fileList}>
                {files.map(({ file: f, fromCamera }, i) => (
                  <div key={i} className={styles.fileChip}>
                    {fromCamera && <CameraThumb file={f} />}
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
