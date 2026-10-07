import { useEffect, useState } from 'react';
import { chromeLocalStore } from '../background/chromeStorage';
import { KEEP_ALIVE_ORIGINS, isKeepAliveEnabled, setKeepAliveEnabled } from '../tarbil/core/keepAlive';
import { readKeepAliveStatus, statusText, summarizeOrigin, type KeepAliveStatus, type OriginSummary } from '../tarbil/core/keepAliveStatus';

const LABELS: Record<string, string> = {
  'https://hbsapp.tarbil.gov.tr': 'Aşı (hbsapp)',
  'https://vetilac.tarbil.gov.tr': 'İlaç (vetilac)',
};

const time = (ms: number) => new Date(ms).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

const DOT: Record<OriginSummary['state'], string> = { open: 'dot-ok', closed: 'dot-warn', stale: 'dot-stale', never: 'dot-off' };

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
    <div className="card keepalive">
      <div className="keepalive-head">
        <div>
          <strong>TARBİL oturumu</strong>
          <div className="muted">Açık tutmak için Chrome açıkken birkaç dakikada bir sayfa istenir; veri gönderilmez.</div>
        </div>
        <label className="switch" title={enabled ? 'Açık tutma açık' : 'Açık tutma kapalı'}>
          <input
            type="checkbox"
            aria-label="TARBİL oturumunu açık tut"
            checked={enabled}
            onChange={async (e) => {
              const next = e.target.checked;
              await setKeepAliveEnabled(chromeLocalStore(), next);
              setEnabled(next);
            }}
          />
          <span className="slider" />
        </label>
      </div>
      {enabled ? (
        <ul className="keepalive-status">
          {KEEP_ALIVE_ORIGINS.map((origin) => {
            const summary = summarizeOrigin(status[origin], now);
            return (
              <li key={origin}>
                <span className={`dot ${DOT[summary.state]}`} />
                <span className="keepalive-site">{LABELS[origin] ?? origin}</span>
                <span className={summary.state === 'closed' ? 'warning' : 'muted'}>{statusText(summary, time)}</span>
              </li>
            );
          })}
        </ul>
      ) : (
        <div className="warning">Kapalı: TARBİL boşta kalınca oturumu kapatır.</div>
      )}
    </div>
  );
}
