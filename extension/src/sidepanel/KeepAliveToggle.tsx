import { useEffect, useState } from 'react';
import { chromeLocalStore } from '../background/chromeStorage';
import { isKeepAliveEnabled, setKeepAliveEnabled } from '../tarbil/core/keepAlive';

/** TARBIL oturumunu canli tutma ayari (varsayilan acik). Yalniz acik bir TARBIL sekmesi varken calisir. */
export function KeepAliveToggle() {
  const [enabled, setEnabled] = useState<boolean | null>(null);

  useEffect(() => {
    isKeepAliveEnabled(chromeLocalStore()).then(setEnabled);
  }, []);

  if (enabled === null) return null;
  return (
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
      TARBİL oturumunu açık tut (TARBİL sekmesi açıkken birkaç dakikada bir sayfa yeniler; veri göndermez)
    </label>
  );
}
