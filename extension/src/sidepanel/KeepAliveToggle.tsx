import { useEffect, useState } from 'react';
import { chromeLocalStore } from '../background/chromeStorage';
import { KEEP_ALIVE_ORIGINS, isKeepAliveEnabled, setKeepAliveEnabled } from '../tarbil/core/keepAlive';
import { readKeepAliveStatus, statusText, summarizeOrigin, type KeepAliveStatus } from '../tarbil/core/keepAliveStatus';

const LABELS: Record<string, string> = {
  'https://hbsapp.tarbil.gov.tr': 'Aşı (hbsapp)',
  'https://vetilac.tarbil.gov.tr': 'İlaç (vetilac)',
};

const time = (ms: number) => new Date(ms).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

/**
 * TARBIL oturumunu canli tutma ayari (varsayilan acik) ve son durum. Istekler arka plandan (Chrome acik kaldikca) ve
 * acik TARBIL sekmesinden gider; oturum kapanirsa ne zaman kapandigi ve son basarili istek burada gorunur.
 */
export function KeepAliveToggle() {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [status, setStatus] = useState<KeepAliveStatus>({});
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    isKeepAliveEnabled(chromeLocalStore()).then(setEnabled);
    const refresh = () => {
      readKeepAliveStatus(chromeLocalStore()).then(setStatus);
      setNow(Date.now());
    };
    refresh();
    const timer = window.setInterval(refresh, 30_000);
    return () => window.clearInterval(timer);
  }, []);

  if (enabled === null) return null;
  return (
    <div>
      <label className="muted" style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <input
          type="checkbox"
          checked={enabled}
          onChange={async (e) => {
            const next = e.target.checked;
            await setKeepAliveEnabled(chromeLocalStore(), next);
            setEnabled(next);
          }}
        />
        TARBİL oturumunu açık tut (Chrome açıkken birkaç dakikada bir sayfa ister; veri göndermez)
      </label>
      {enabled && (
        <div className="muted" style={{ marginTop: 4, fontSize: 12 }}>
          {KEEP_ALIVE_ORIGINS.map((origin) => {
            const summary = summarizeOrigin(status[origin], now);
            return (
              <div key={origin} className={summary.state === 'closed' ? 'warning' : undefined}>
                {LABELS[origin] ?? origin}: {statusText(summary, time)}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
