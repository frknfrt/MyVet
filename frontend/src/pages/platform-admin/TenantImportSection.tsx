import { FormEvent, useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  canCommitReport, ImportBatch, ImportBatchStatus, ImportRollbackBlocker, platformAdminImportApi,
} from '../../api/dataImportApi';
import { Badge, BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { formatDate } from './tenantBadges';
import styles from './TenantImportSection.module.css';

const STATUS_LABELS: Record<ImportBatchStatus, string> = {
  PREVIEWED: 'Önizleme',
  COMMITTING: 'Aktarılıyor',
  COMMITTED: 'Aktarıldı',
  FAILED: 'Başarısız',
  ROLLED_BACK: 'Geri alındı',
  DISCARDED: 'İptal',
};

const STATUS_TONES: Record<ImportBatchStatus, BadgeTone> = {
  PREVIEWED: 'neutral',
  COMMITTING: 'warning',
  COMMITTED: 'success',
  FAILED: 'danger',
  ROLLED_BACK: 'neutral',
  DISCARDED: 'neutral',
};

const ENTITY_LABELS: Record<string, string> = { OWNER: 'Sahip', PATIENT: 'Hasta' };

const POLL_INTERVAL_MS = 2000;

type ConfirmKind = 'commit' | 'rollback' | 'discard';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function sumTotals(batch: ImportBatch, rejected: Set<string>) {
  const totals = Object.values(batch.report?.totals ?? {});
  // Baglantisi kaldirilan sahipler yeni kayit olarak eklenir; yalnizca raporda gercekten bulunan kimlikler sayilir.
  const knownIds = new Set((batch.report?.ownerMatches ?? []).map((m) => m.sourceId));
  const unlinked = Array.from(rejected).filter((id) => knownIds.has(id)).length;
  return {
    toCreate: totals.reduce((s, t) => s + (t?.toCreate ?? 0), 0) + unlinked,
    matched: totals.reduce((s, t) => s + (t?.matched ?? 0), 0) - unlinked,
    errorRows: totals.reduce((s, t) => s + (t?.errorRows ?? 0), 0),
  };
}

export function TenantImportSection({ tenantId }: { tenantId: string }) {
  const [batches, setBatches] = useState<ImportBatch[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sourceSystem, setSourceSystem] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<ConfirmKind | null>(null);
  const [rejected, setRejected] = useState<Set<string>>(new Set());
  const [blockers, setBlockers] = useState<ImportRollbackBlocker[] | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const sourceSystemListId = useId();
  // Bu klinikte daha once kullanilan yazimlar: ayni program farkli yazilip "daha once aktarilmis" kontrolu bozulmasin.
  const knownSourceSystems = useMemo(
    () => Array.from(new Set(batches.map((b) => b.sourceSystem).filter((s) => s && s.trim()))),
    [batches]
  );

  const selected = batches.find((b) => b.id === selectedId) ?? null;
  const selectedStatus = selected?.status;

  const replaceBatch = useCallback((updated: ImportBatch) => {
    setBatches((prev) => (prev.some((b) => b.id === updated.id)
      ? prev.map((b) => (b.id === updated.id ? updated : b))
      : [updated, ...prev]));
  }, []);

  const reload = useCallback(async () => {
    const list = await platformAdminImportApi.list(tenantId);
    setBatches(list);
    return list;
  }, [tenantId]);

  useEffect(() => {
    let cancelled = false;
    platformAdminImportApi.list(tenantId)
      .then((list) => {
        if (cancelled) return;
        setBatches(list);
        setSelectedId((cur) => cur ?? list[0]?.id ?? null);
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessageOf(err));
      })
      .finally(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId]);

  // Yalnizca secili parti COMMITTING iken 2 sn'de bir yenile; durum degisince/unmount'ta temizlenir.
  useEffect(() => {
    if (!selectedId || selectedStatus !== 'COMMITTING') return;
    const timer = setInterval(() => {
      platformAdminImportApi.get(tenantId, selectedId)
        .then(replaceBatch)
        .catch((err) => setError(errorMessageOf(err)));
    }, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [tenantId, selectedId, selectedStatus, replaceBatch]);

  function select(id: string) {
    if (id === selectedId) return;
    setSelectedId(id);
    setRejected(new Set());
    setBlockers(null);
    setError(null);
  }

  async function handleDownloadTemplate() {
    setError(null);
    try {
      const blob = await platformAdminImportApi.downloadTemplate();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'vetly-aktarim-sablonu.xlsx';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) {
      setError(errorMessageOf(err));
    }
  }

  async function handlePreview(e: FormEvent) {
    e.preventDefault();
    if (!file || !sourceSystem.trim()) {
      setError('Önizlemek için "Kaynak program" alanını doldurun ve bir .xlsx dosyası seçin.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const created = await platformAdminImportApi.preview(tenantId, file, sourceSystem.trim());
      replaceBatch(created);
      select(created.id);
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  function toggleMatch(sourceId: string) {
    setRejected((prev) => {
      const next = new Set(prev);
      if (next.has(sourceId)) next.delete(sourceId);
      else next.add(sourceId);
      return next;
    });
  }

  async function handleConfirm() {
    if (!selected || !confirm) return;
    const kind = confirm;
    setBusy(true);
    setError(null);
    try {
      if (kind === 'commit') {
        const started = await platformAdminImportApi.commit(tenantId, selected.id, Array.from(rejected));
        replaceBatch(started);
      } else if (kind === 'discard') {
        await platformAdminImportApi.discard(tenantId, selected.id);
        await reload();
      } else {
        const result = await platformAdminImportApi.rollback(tenantId, selected.id);
        if (result.rolledBack) {
          setBlockers(null);
          await reload();
        } else {
          setBlockers(result.blockers);
        }
      }
    } catch (err) {
      setError(errorMessageOf(err));
      if (kind !== 'commit') {
        reload().catch(() => undefined);
      }
    } finally {
      setConfirm(null);
      setBusy(false);
    }
  }

  const report = selected?.report ?? null;
  const sums = selected ? sumTotals(selected, rejected) : null;
  const canCommit = selected?.status === 'PREVIEWED' && canCommitReport(report);
  const progressPct = selected && selected.totalRows > 0
    ? Math.min(100, Math.round((selected.processedRows / selected.totalRows) * 100))
    : 0;

  const confirmTitle = confirm === 'commit' ? 'Aktarımı onayla'
    : confirm === 'rollback' ? 'Aktarımı geri al' : 'Önizlemeyi iptal et';

  return (
    <div className={styles.section}>
      <div className={styles.sectionTitle}>Veri aktarımı</div>

      {error && <div className={styles.alert} role="alert">{error}</div>}

      <form className={styles.toolbar} onSubmit={handlePreview} noValidate>
        <Button type="button" variant="secondary" onClick={handleDownloadTemplate}>
          Şablonu indir
        </Button>
        <div className={styles.toolbarField}>
          <FieldWrap label="Kaynak program">
            <Input
              value={sourceSystem}
              onChange={(e) => setSourceSystem(e.target.value)}
              placeholder="Örn. KolayVet"
              list={sourceSystemListId}
              autoComplete="off"
              required
            />
            <datalist id={sourceSystemListId}>
              {knownSourceSystems.map((name) => (
                <option key={name} value={name} />
              ))}
            </datalist>
          </FieldWrap>
        </div>
        <div className={styles.toolbarField}>
          <FieldWrap label="Excel dosyası (.xlsx)">
            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </FieldWrap>
        </div>
        <Button type="submit" variant="primary" disabled={busy}>
          {busy ? 'İşleniyor...' : 'Önizle'}
        </Button>
      </form>

      {!loaded ? (
        <div className={styles.empty}>Yükleniyor...</div>
      ) : batches.length === 0 ? (
        <div className={styles.empty}>Henüz veri aktarımı yapılmadı</div>
      ) : (
        <div className={styles.scroll}>
          <table className={styles.table}>
            <thead>
              <tr><th>Tarih</th><th>Kaynak</th><th>Dosya</th><th>Durum</th></tr>
            </thead>
            <tbody>
              {batches.map((b) => (
                <tr
                  key={b.id}
                  className={[styles.selectable, b.id === selectedId ? styles.selected : ''].join(' ')}
                  onClick={() => select(b.id)}
                >
                  <td>{formatDate(b.createdAt)}</td>
                  <td>{b.sourceSystem}</td>
                  <td>{b.fileName}</td>
                  <td><Badge tone={STATUS_TONES[b.status]}>{STATUS_LABELS[b.status]}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selected && (
        <div>
          {selected.status === 'COMMITTING' && (
            <>
              <div className={styles.subTitle}>
                Aktarılıyor: {selected.processedRows} / {selected.totalRows}
              </div>
              <div
                className={styles.progress}
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={selected.totalRows}
                aria-valuenow={selected.processedRows}
              >
                <div className={styles.progressBar} style={{ width: `${progressPct}%` }} />
              </div>
            </>
          )}

          {selected.status === 'FAILED' && selected.errorMessage && (
            <div className={styles.alert}>Aktarım başarısız oldu: {selected.errorMessage}</div>
          )}

          {report && (
            <>
              {report.structuralErrors.length > 0 && (
                <div className={styles.alert} role="alert">
                  Dosyada yapısal hatalar var; düzeltilmeden aktarılamaz.
                  <ul>
                    {report.structuralErrors.map((m, i) => <li key={i}>{m}</li>)}
                  </ul>
                </div>
              )}

              <div className={styles.subTitle}>Özet</div>
              <div className={styles.scroll}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Kayıt türü</th><th>Dosyada</th><th>Oluşturulacak</th><th>Eşleşen</th>
                      <th>Zaten aktarılmış</th><th>Hatalı satır</th>
                    </tr>
                  </thead>
                  <tbody>
                    {Object.entries(report.totals ?? {}).map(([type, t]) => (
                      <tr key={type}>
                        <td>{ENTITY_LABELS[type] ?? type}</td>
                        <td>{t?.rowsInFile}</td>
                        <td>{t?.toCreate}</td>
                        <td>{t?.matched}</td>
                        <td>{t?.alreadyImported}</td>
                        <td>{t?.errorRows}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {(report.errors.length > 0 || report.warnings.length > 0) && (
                <>
                  <div className={styles.subTitle}>Hatalar ve uyarılar</div>
                  <div className={styles.scroll}>
                    <table className={styles.table}>
                      <thead>
                        <tr><th>Tür</th><th>Sayfa</th><th>Satır</th><th>Kaynak ID</th><th>Mesaj</th></tr>
                      </thead>
                      <tbody>
                        {[
                          ...report.errors.map((x) => ({ ...x, kind: 'Hata' })),
                          ...report.warnings.map((x) => ({ ...x, kind: 'Uyarı' })),
                        ].map((x, i) => (
                          <tr key={i}>
                            <td>{x.kind}</td>
                            <td>{x.sheet}</td>
                            <td>{x.rowNumber}</td>
                            <td>{x.sourceId ?? '—'}</td>
                            <td>{x.message}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}

              {report.newSpecies.length > 0 && (
                <>
                  <div className={styles.subTitle}>Eklenecek yeni türler</div>
                  <div>{report.newSpecies.join(', ')}</div>
                  <p className={styles.note}>Bu türler tüm kliniklerin tür listesine eklenecek.</p>
                </>
              )}

              {(report.newBreeds?.length ?? 0) > 0 && (
                <>
                  <div className={styles.subTitle}>Eklenecek yeni ırklar</div>
                  <div>{report.newBreeds!.map((b) => `${b.name} (${b.species})`).join(', ')}</div>
                  <p className={styles.note}>Bu ırklar tüm kliniklerin ırk listesine eklenecek.</p>
                </>
              )}

              {report.ownerMatches.length > 0 && (
                <>
                  <div className={styles.subTitle}>Eşleşen sahipler</div>
                  <div className={styles.scroll}>
                    <table className={styles.table}>
                      <thead>
                        <tr>
                          <th>Mevcut kayda bağla</th><th>Dosyadaki sahip</th><th>Telefon</th><th>Mevcut sahip</th>
                        </tr>
                      </thead>
                      <tbody>
                        {report.ownerMatches.map((m) => (
                          <tr key={m.sourceId}>
                            <td>
                              <input
                                type="checkbox"
                                aria-label={`Mevcut kayda bağla: ${m.sourceName ?? m.sourceId}`}
                                checked={!rejected.has(m.sourceId)}
                                disabled={selected.status !== 'PREVIEWED'}
                                onChange={() => toggleMatch(m.sourceId)}
                              />
                            </td>
                            <td>{m.sourceName ?? m.sourceId}</td>
                            <td>{m.sourcePhone ?? '—'}</td>
                            <td>{m.existingOwnerName ?? m.existingOwnerId}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <p className={styles.note}>İşareti kaldırılan sahipler mevcut kayda bağlanmaz, yeni kayıt olarak eklenir.</p>
                </>
              )}
            </>
          )}

          {blockers && (
            <div className={styles.alert} role="alert">
              Bu aktarım geri alınamaz: klinik aktarılan kayıtlara yeni kayıt eklemiş
              <ul>
                {blockers.map((b, i) => (
                  <li key={i}>
                    {b.table}{b.rowId ? ` / ${b.rowId}` : ''} ({b.column})
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className={styles.actions}>
            {selected.status === 'PREVIEWED' && (
              <>
                <Button variant="primary" disabled={!canCommit || busy} onClick={() => setConfirm('commit')}>
                  Aktar
                </Button>
                <Button variant="secondary" disabled={busy} onClick={() => setConfirm('discard')}>
                  İptal et
                </Button>
              </>
            )}
            {selected.rollbackable && (
              <Button variant="danger" disabled={busy} onClick={() => setConfirm('rollback')}>
                Geri al
              </Button>
            )}
          </div>
        </div>
      )}

      <Modal open={confirm !== null} onClose={() => setConfirm(null)} width={460}>
        <div className={styles.modalTitle}>{confirmTitle}</div>
        <p className={styles.modalText}>
          {confirm === 'commit' && sums
            && `${sums.toCreate} kayıt oluşturulacak, ${sums.matched} kayıt mevcut kayıtlarla eşleşecek, ${sums.errorRows} hatalı satır atlanacak.`}
          {confirm === 'rollback' && 'Bu aktarımla eklenen kayıtlar silinecek. Devam etmek istiyor musunuz?'}
          {confirm === 'discard' && 'Önizleme iptal edilecek ve yüklenen dosya silinecek. Devam etmek istiyor musunuz?'}
        </p>
        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={() => setConfirm(null)}>
            Vazgeç
          </Button>
          <Button type="button" variant={confirm === 'rollback' ? 'danger' : 'primary'} disabled={busy} onClick={handleConfirm}>
            Onayla
          </Button>
        </div>
      </Modal>
    </div>
  );
}
