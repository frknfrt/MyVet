// Faz 1: TARBIL sayfasinda aktif asiyi gosteren kucuk kart. Form doldurma Faz 2'de.
// Izole dunyada calisir; Shadow DOM ile TARBIL'in CSS'inden yalitilir.
import type { BackgroundRequest, BackgroundResponse } from '../shared/messages';
import type { Submission } from '../shared/types';

function send<T>(req: BackgroundRequest): Promise<BackgroundResponse<T>> {
  return chrome.runtime.sendMessage(req);
}

const host = document.createElement('div');
host.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;';
const root = host.attachShadow({ mode: 'closed' });

// Renkler frontend/src/styles/tokens.css'ten (paper, text-ink, border, text-muted, warning-700, success-700).
const STYLE = `
  .card{font:13px/1.4 system-ui,sans-serif;background:#ffffff;color:#1b1229;border:1px solid #e9e5ef;border-radius:10px;
        box-shadow:0 20px 44px -18px rgba(24,15,36,.18);padding:10px;width:280px;display:flex;flex-direction:column;gap:6px}
  .muted{color:#6e6579;font-size:12px} .warn{color:#a65022;font-size:12px} .ok{color:#146245}
  button{border:1px solid #e9e5ef;background:#ffffff;color:#1b1229;border-radius:6px;padding:5px 8px;cursor:pointer}
  .row{display:flex;gap:6px;flex-wrap:wrap} .x{margin-left:auto;border:none;background:none;font-size:16px}
`;

function el(html: string): HTMLElement {
  const t = document.createElement('template');
  t.innerHTML = html.trim();
  return t.content.firstElementChild as HTMLElement;
}

function escapeHtml(s: string) {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}

async function render() {
  const res = await send<Submission | null>({ type: 'GET_ACTIVE' });
  root.innerHTML = `<style>${STYLE}</style>`;
  if (!res.ok || !res.data) {
    host.remove();
    return;
  }
  const s = res.data;
  if (!host.isConnected) document.body.appendChild(host);

  if (s.status === 'SUBMITTED') {
    root.appendChild(el(`<div class="card"><div class="row"><strong>Vetly</strong><button class="x" data-a="close">×</button></div>
      <div class="ok">✓ ${escapeHtml(s.patientName)} — bu aşı ${new Date(s.submittedAt!).toLocaleDateString('tr-TR')} tarihinde TARBİL'e kaydedilmiş. Tekrar girmeyin.</div></div>`));
  } else {
    root.appendChild(el(`<div class="card">
      <div class="row"><strong>Vetly</strong><button class="x" data-a="close">×</button></div>
      <div><strong>${escapeHtml(s.patientName)}</strong></div>
      ${s.microchipNumber ? `<div>Çip: ${escapeHtml(s.microchipNumber)}</div>` : '<div class="warn">Çip numarası yok.</div>'}
      <div>${escapeHtml(s.vaccineName)} · ${new Date(s.administeredDate).toLocaleDateString('tr-TR')}</div>
      <div class="muted">Otomatik doldurma yakında. Şimdilik bilgileri TARBİL'e girip Kaydet'e basın, ardından işaretleyin.</div>
      <div class="row"><button data-a="submitted">Kaydedildi olarak işaretle</button><button data-a="dismiss">Bildirilmeyecek</button></div>
      <div class="muted" data-note></div></div>`));
  }

  root.querySelectorAll('button').forEach((b) =>
    b.addEventListener('click', async () => {
      const action = (b as HTMLElement).dataset.a;
      const note = root.querySelector('[data-note]');
      if (action === 'close') {
        host.remove();
        return;
      }
      const r =
        action === 'submitted'
          ? await send<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'MANUAL', tarbilReference: null })
          : await send({ type: 'DISMISS', id: s.id, reason: 'Bildirim gerekmiyor' });
      if (note) note.textContent = r.ok ? 'Vetly güncellendi.' : r.error;
      if (r.ok) setTimeout(render, 1200);
    }),
  );
}

render();
chrome.storage.session.onChanged.addListener((changes) => {
  if ('activeSubmissionId' in changes) render();
});
