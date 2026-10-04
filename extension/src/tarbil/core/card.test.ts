// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { createCard } from './card';

describe('card', () => {
  it('renders untrusted text as text, never as markup', () => {
    const card = createCard(document, 'open');

    card.show({ lines: [{ text: '<img src=x onerror=alert(1)>' }], actions: [] });

    expect(card.root.querySelector('img')).toBeNull();
    expect(card.root.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('reports action clicks by id', () => {
    const card = createCard(document, 'open');
    const handler = vi.fn();
    card.onAction(handler);

    card.show({ lines: [], actions: [{ id: 'fill', label: 'Formu doldur' }] });
    (card.root.querySelector('button[data-action="fill"]') as HTMLButtonElement).click();

    expect(handler).toHaveBeenCalledWith('fill');
  });

  it('can be hidden', () => {
    const card = createCard(document, 'open');
    card.show({ lines: [{ text: 'x' }], actions: [] });
    card.hide();
    expect(card.root.host.isConnected).toBe(false);
  });
});
