import { useEffect, useState } from 'react';
import type { Submission } from '../shared/types';
import { SubmissionCard } from './SubmissionCard';
import { sendToBackground } from './useBackground';

export function PendingList({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [items, setItems] = useState<Submission[] | null>(null);
  const [error, setError] = useState<{ message: string; upgrade: boolean } | null>(null);

  async function load() {
    const res = await sendToBackground<Submission[]>({ type: 'LIST_PENDING' });
    if (res.ok) {
      setItems(res.data);
      setError(null);
    } else if (res.code === 'UNAUTHORIZED') {
      onUnauthorized();
    } else {
      setError({
        message: res.code === 'OFFLINE' ? 'Çevrimdışı — Vetly\'ye ulaşılamıyor.' : res.error,
        upgrade: res.code === 'UPGRADE_REQUIRED',
      });
    }
  }

  useEffect(() => {
    load();
  }, []);

  // Guncelleme gerekiyorsa kalici uyari: yenilemek ise yaramaz, yeni surum kurulmali.
  if (error?.upgrade) return <div className="warning">{error.message}</div>;
  if (error) return <div className="banner">{error.message} <button onClick={load}>Yenile</button></div>;
  if (!items) return <div className="muted">Yükleniyor…</div>;
  if (items.length === 0) return <div className="muted">TARBİL'e aktarılmayı bekleyen aşı yok.</div>;
  return (
    <>
      <div className="row">
        <span className="muted">{items.length} bekleyen aşı</span>
        <button onClick={load}>Yenile</button>
      </div>
      {items.map((s) => (
        <SubmissionCard key={s.id} submission={s} onDone={load} />
      ))}
    </>
  );
}
