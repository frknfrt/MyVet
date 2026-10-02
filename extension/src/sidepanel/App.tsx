import { useEffect, useState } from 'react';
import type { ExtensionState } from '../shared/messages';
import { PairingView } from './PairingView';
import { PendingList } from './PendingList';
import { sendToBackground } from './useBackground';

export function App() {
  const [state, setState] = useState<ExtensionState | null>(null);
  const [message, setMessage] = useState<string | undefined>();
  const [loadError, setLoadError] = useState<string | null>(null);

  async function refresh() {
    const res = await sendToBackground<ExtensionState>({ type: 'GET_STATE' });
    if (res.ok) {
      setState(res.data);
      setLoadError(null);
    } else {
      setLoadError(res.error);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  if (!state && loadError) {
    return (
      <div className="panel">
        <div className="banner">
          {loadError} <button onClick={refresh}>Yenile</button>
        </div>
      </div>
    );
  }
  if (!state) return <div className="panel muted">Yükleniyor…</div>;
  if (!state.paired) return <PairingView onPaired={setState} message={message} />;

  return (
    <div className="panel">
      <div className="header">
        <div>
          <strong>{state.profile?.clinicName ?? 'Vetly'}</strong>
          <div className="muted">{state.profile?.staffName ?? 'Çevrimdışı'}</div>
        </div>
        <button onClick={async () => setState((await sendToBackground<ExtensionState>({ type: 'UNPAIR' })).ok ? { ...state, paired: false } : state)}>
          Bağlantıyı kes
        </button>
      </div>
      {state.pendingConfirmations > 0 && (
        <div className="banner">{state.pendingConfirmations} onay Vetly'ye gönderilmeyi bekliyor (bağlantı gelince otomatik).</div>
      )}
      <PendingList
        onUnauthorized={() => {
          setMessage('Bağlantı iptal edildi ya da geçersiz. Yeniden bağlayın.');
          setState({ ...state, paired: false });
        }}
      />
    </div>
  );
}
