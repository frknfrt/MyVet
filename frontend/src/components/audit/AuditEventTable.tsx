import { useCallback, useEffect, useState } from 'react';
import type { AuditActionOption, AuditEvent, AuditEventPage, AuditQuery } from '../../api/auditApi';
import { ApiError } from '../../api/client';
import { Button } from '../ui/Button';
import styles from './AuditEventTable.module.css';

const dateTime = new Intl.DateTimeFormat('tr-TR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Istanbul' });

/** 'YYYY-MM-DD' (Istanbul gunu) -> o gunun 00:00'i, ISO instant. */
function dayStart(day: string): string {
  return new Date(`${day}T00:00:00+03:00`).toISOString();
}

/** 'YYYY-MM-DD' -> ertesi gunun 00:00'i (haric ust sinir). */
function nextDayStart(day: string): string {
  const d = new Date(`${day}T00:00:00+03:00`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString();
}

function actorText(e: AuditEvent): string {
  const name = e.actorType === 'PLATFORM_ADMIN' ? 'Vetly destek ekibi' : (e.actorName ?? 'Bilinmeyen personel');
  const notes: string[] = [];
  if (e.impersonatedBy) notes.push('Vetly destek ekibi aracılığıyla');
  if (e.channel === 'EXTENSION') notes.push('TARBİL eklentisi');
  return notes.length ? `${name} (${notes.join(', ')})` : name;
}

interface Props {
  /** Kararli (useCallback / modul seviyesi) olmali; aksi halde her render'da yeniden yuklenir. */
  fetchPage: (q: AuditQuery) => Promise<AuditEventPage>;
  actionOptions?: AuditActionOption[];
  staffOptions?: { id: string; name: string }[];
  /** Açıklama ve IP kolonları (yalnız Vetly admin panelinde; klinik ekranında gizli). */
  showDetails?: boolean;
}

export function AuditEventTable({ fetchPage, actionOptions, staffOptions, showDetails = false }: Props) {
  const [fromDay, setFromDay] = useState('');
  const [toDay, setToDay] = useState('');
  const [action, setAction] = useState('');
  const [actorId, setActorId] = useState('');
  const [items, setItems] = useState<AuditEvent[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (nextPage: number, append: boolean) => {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchPage({
          from: fromDay ? dayStart(fromDay) : undefined,
          to: toDay ? nextDayStart(toDay) : undefined,
          action: action || undefined,
          actorId: actorId || undefined,
          page: nextPage,
        });
        setItems((prev) => (append ? [...prev, ...result.items] : result.items));
        setPage(nextPage);
        setHasMore(result.hasMore);
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'İşlem geçmişi yüklenemedi');
      } finally {
        setLoading(false);
      }
    },
    [fetchPage, fromDay, toDay, action, actorId]
  );

  useEffect(() => {
    load(0, false);
  }, [load]);

  return (
    <div className={styles.wrap}>
      <div className={styles.filters}>
        <label>
          Başlangıç
          <input type="date" value={fromDay} onChange={(e) => setFromDay(e.target.value)} />
        </label>
        <label>
          Bitiş
          <input type="date" value={toDay} onChange={(e) => setToDay(e.target.value)} />
        </label>
        {actionOptions && (
          <label>
            İşlem
            <select value={action} onChange={(e) => setAction(e.target.value)}>
              <option value="">Tümü</option>
              {actionOptions.map((o) => (
                <option key={o.code} value={o.code}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        )}
        {staffOptions && (
          <label>
            Kişi
            <select value={actorId} onChange={(e) => setActorId(e.target.value)}>
              <option value="">Herkes</option>
              {staffOptions.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {error && <div className={styles.error}>{error}</div>}

      <div className={styles.scroll}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Tarih</th>
              <th>Kişi</th>
              <th>İşlem</th>
              <th>Kayıt</th>
              {showDetails && <th>Açıklama</th>}
              {showDetails && <th>IP</th>}
            </tr>
          </thead>
          <tbody>
            {items.map((e) => (
              <tr key={e.id}>
                <td>{dateTime.format(new Date(e.occurredAt))}</td>
                <td>{actorText(e)}</td>
                <td>{e.actionLabel}</td>
                <td>{e.targetLabel ?? '—'}</td>
                {showDetails && <td>{e.summary ?? '—'}</td>}
                {showDetails && <td>{e.ipAddress ?? '—'}</td>}
              </tr>
            ))}
            {!loading && items.length === 0 && (
              <tr>
                <td colSpan={showDetails ? 6 : 4} className={styles.empty}>
                  Kayıt yok.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {hasMore && (
        <Button variant="secondary" onClick={() => load(page + 1, true)} disabled={loading}>
          {loading ? 'Yükleniyor...' : 'Daha fazla yükle'}
        </Button>
      )}
    </div>
  );
}
