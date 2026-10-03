// TARBIL sayfasindaki "Vetly karti". Shadow DOM ile TARBIL CSS'inden yalitilir; icerik YALNIZ textContent ile yazilir.

export type Tone = 'strong' | 'muted' | 'warn' | 'ok';
export interface CardLine {
  text: string;
  tone?: Tone;
}
export interface CardAction {
  id: string;
  label: string;
}
export interface CardView {
  lines: CardLine[];
  actions: CardAction[];
}
export interface Card {
  show(view: CardView): void;
  hide(): void;
  onAction(handler: (id: string) => void): void;
}

// Renkler frontend/src/styles/tokens.css'ten (paper, text-ink, border, text-muted, warning-700, success-700).
const STYLE = `
  .card{font:13px/1.4 system-ui,sans-serif;background:#ffffff;color:#1b1229;border:1px solid #e9e5ef;border-radius:10px;
        box-shadow:0 20px 44px -18px rgba(24,15,36,.18);padding:10px;width:300px;display:flex;flex-direction:column;gap:6px}
  .strong{font-weight:600} .muted{color:#6e6579;font-size:12px} .warn{color:#a65022} .ok{color:#146245}
  button{border:1px solid #e9e5ef;background:#ffffff;color:#1b1229;border-radius:6px;padding:5px 8px;cursor:pointer}
  .row{display:flex;gap:6px;flex-wrap:wrap;align-items:center} .x{margin-left:auto;border:none;background:none;font-size:16px}
`;

export function createCard(doc: Document, mode: ShadowRootMode = 'closed'): Card & { root: ShadowRoot } {
  const host = doc.createElement('div');
  host.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;';
  const root = host.attachShadow({ mode });
  let handler: (id: string) => void = () => undefined;

  return {
    root,
    show(view) {
      root.replaceChildren();
      const style = doc.createElement('style');
      style.textContent = STYLE;
      const card = doc.createElement('div');
      card.className = 'card';

      const head = doc.createElement('div');
      head.className = 'row';
      const title = doc.createElement('strong');
      title.textContent = 'Vetly';
      const close = doc.createElement('button');
      close.className = 'x';
      close.textContent = '×';
      close.title = 'Kapat';
      close.addEventListener('click', () => host.remove());
      head.append(title, close);
      card.appendChild(head);

      for (const line of view.lines) {
        const div = doc.createElement('div');
        if (line.tone) div.className = line.tone;
        div.textContent = line.text;
        card.appendChild(div);
      }
      if (view.actions.length > 0) {
        const row = doc.createElement('div');
        row.className = 'row';
        for (const a of view.actions) {
          const b = doc.createElement('button');
          b.textContent = a.label;
          b.dataset.action = a.id;
          b.addEventListener('click', () => handler(a.id));
          row.appendChild(b);
        }
        card.appendChild(row);
      }
      root.append(style, card);
      if (!host.isConnected) doc.body.appendChild(host);
    },
    hide() {
      host.remove();
    },
    onAction(h) {
      handler = h;
    },
  };
}
