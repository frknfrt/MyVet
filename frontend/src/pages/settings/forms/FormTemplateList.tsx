import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../../api/client';
import { formsApi, type FormTemplateSummary } from '../../../api/formsApi';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { openProtectedFile } from './openProtectedFile';
import styles from './FormTemplateList.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function FormTemplateList() {
  const [rows, setRows] = useState<FormTemplateSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [showArchived, setShowArchived] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);
  const active = useRef(false);
  const archivedFilter = useRef(showArchived);

  function load() {
    if (!active.current) return;
    const request = ++generation.current;
    setLoading(true);
    setError(null);
    formsApi
      .listTemplates(archivedFilter.current ? 'ALL' : 'ACTIVE')
      .then(rows => { if (request === generation.current) setRows(rows); })
      .catch(e => { if (request === generation.current) setError(errorMessageOf(e)); })
      .finally(() => { if (request === generation.current) setLoading(false); });
  }
  useEffect(() => {
    active.current = true;
    archivedFilter.current = showArchived;
    load();
    return () => { active.current = false; generation.current++; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showArchived]);

  async function archive(row: FormTemplateSummary) {
    if (!window.confirm(`"${row.title}" arşivlensin mi? Yeni form doldurulurken listelenmez; imzalı formlar korunur.`)) return;
    try {
      await formsApi.archiveTemplate(row.id);
      load();
    } catch (e) {
      if (active.current) setError(errorMessageOf(e));
    }
  }

  async function openReference(row: FormTemplateSummary) {
    try {
      const detail = await formsApi.getTemplate(row.id);
      if (!active.current || !detail.sourceImageId) return;
      await openProtectedFile(formsApi.sourceImageUrl(detail.sourceImageId));
    } catch (e) {
      if (active.current) setError(errorMessageOf(e));
    }
  }

  return (
    <div>
      <p><Link to="/ayarlar/formlar/kvkk">KVKK aydınlatma metnini düzenle</Link></p>
      <div className={styles.head}>
        <label className={styles.check}>
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Arşivlenenleri göster
        </label>
        <Link to="/ayarlar/formlar/yeni" className={styles.newLink}>
          + Yeni form
        </Link>
      </div>

      {error && <div role="alert" className={styles.error}>{error}</div>}

      {loading ? (
        <div className={styles.empty}>Yükleniyor…</div>
      ) : rows.length === 0 ? (
        <div className={styles.empty}>Henüz form şablonu yok. Mevcut kağıt formunuzun fotoğrafından başlayabilirsiniz.</div>
      ) : (
        <div className={styles.list}>
          {rows.map((row) => (
            <div key={row.id} className={styles.row}>
              <div className={styles.main}>
                <div className={styles.name}>{row.title}</div>
                <div className={styles.meta}>
                  Son düzenleme: {new Date(row.updatedAt).toLocaleDateString('tr-TR')} · {row.signedCount} imzalı form
                </div>
              </div>
              <Badge tone={row.status === 'ACTIVE' ? 'success' : 'neutral'}>{row.status === 'ACTIVE' ? 'Aktif' : 'Arşivde'}</Badge>
              <div className={styles.actions}>
                {row.status === 'ACTIVE' && (
                  <Link to={`/ayarlar/formlar/${row.id}`} className={styles.link}>
                    Düzenle
                  </Link>
                )}
                {row.hasSourceImage && (
                  <Button type="button" variant="tertiary" onClick={() => openReference(row)}>
                    Referans görsel
                  </Button>
                )}
                {row.status === 'ACTIVE' && (
                  <Button type="button" variant="tertiary" onClick={() => archive(row)}>
                    Arşivle
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
