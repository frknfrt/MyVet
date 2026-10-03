import { useState } from 'react';
import type { Submission } from '../shared/types';
import { sendToBackground } from './useBackground';

const SEX = { MALE: 'Erkek', FEMALE: 'Dişi', UNKNOWN: 'Bilinmiyor' } as const;

export function SubmissionCard({ submission: s, onDone }: { submission: Submission; onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);

  async function markSubmitted() {
    setBusy(true);
    const res = await sendToBackground<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'MANUAL', tarbilReference: null });
    setBusy(false);
    if (res.ok && res.data && 'queued' in res.data) setNote("Vetly'ye ulaşılamadı; bağlantı gelince otomatik bildirilecek.");
    else if (res.ok) onDone();
    else setNote(res.error);
  }

  async function dismiss() {
    setBusy(true);
    const res = await sendToBackground({ type: 'DISMISS', id: s.id, reason: 'Bildirim gerekmiyor' });
    setBusy(false);
    if (res.ok) onDone();
    else setNote(res.error);
  }

  async function setActive() {
    await sendToBackground({ type: 'SET_ACTIVE', id: s.id });
    setNote("TARBİL'de \"Aşı Uygulama Belgesi Ekle\" sayfası açıksa form hemen doldurulur; değilse o sayfayı açın.");
  }

  if (s.status === 'SUBMITTED') {
    return (
      <div className="card">
        <strong>{s.patientName}</strong>
        <div className="success">✓ {new Date(s.submittedAt!).toLocaleDateString('tr-TR')} tarihinde TARBİL'e kaydedilmiş.</div>
      </div>
    );
  }

  return (
    <div className="card">
      <strong>{s.patientName}</strong>
      <div className="muted">
        {[s.speciesName, s.breedName, s.sex ? SEX[s.sex] : null].filter(Boolean).join(' · ')}
      </div>
      {s.microchipNumber ? (
        <div>Çip: {s.microchipNumber}</div>
      ) : (
        <div className="warning">Çip numarası yok. Hayvan TARBİL'de kayıtlı değilse önce kimliklendirme gerekir.</div>
      )}
      <div>
        {s.vaccineName}
        {s.lotNumber && ` · Lot ${s.lotNumber}`} · {new Date(s.administeredDate).toLocaleDateString('tr-TR')}
      </div>
      {!s.vaccineMapping && <div className="muted">İlk kez aktarılıyor: aşı ürününü ve hastalığı TARBİL'de siz seçeceksiniz.</div>}
      <div className="row">
        <button onClick={setActive} disabled={busy}>TARBİL'de doldur</button>
        <button className="primary" onClick={markSubmitted} disabled={busy}>Gönderildi olarak işaretle</button>
        <button onClick={dismiss} disabled={busy}>Bildirilmeyecek</button>
      </div>
      {note && <div className="muted">{note}</div>}
    </div>
  );
}
