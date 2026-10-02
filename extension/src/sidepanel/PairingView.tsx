import { useState } from 'react';
import type { ExtensionState } from '../shared/messages';
import { sendToBackground } from './useBackground';

export function PairingView({ onPaired, message }: { onPaired: (s: ExtensionState) => void; message?: string }) {
  const [code, setCode] = useState('');
  const [label, setLabel] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError(null);
    const res = await sendToBackground<ExtensionState>({ type: 'PAIR', code, label: label || 'Bu bilgisayar' });
    setBusy(false);
    if (res.ok) onPaired(res.data);
    else setError(res.code === 'UNAUTHORIZED' ? 'Kod geçersiz ya da süresi dolmuş. Vetly\'de yeni kod oluşturun.' : res.error);
  }

  return (
    <div className="panel">
      <h3>Vetly'ye bağlan</h3>
      {message && <div className="banner">{message}</div>}
      <div className="muted">Vetly &gt; Ayarlar &gt; Entegrasyonlar &gt; "Eklentiyi bağla" ile kod oluşturun.</div>
      <input placeholder="Eşleştirme kodu (ör. K7QM-2XPA)" value={code} onChange={(e) => setCode(e.target.value)} />
      <input placeholder="Bu bilgisayarın adı (ör. Muayene 1)" value={label} onChange={(e) => setLabel(e.target.value)} />
      {error && <div className="warning">{error}</div>}
      <button className="primary" disabled={busy || code.trim().length < 8} onClick={submit}>
        Bağlan
      </button>
    </div>
  );
}
