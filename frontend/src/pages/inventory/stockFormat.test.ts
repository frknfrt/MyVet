import { describe, expect, it } from 'vitest';
import { formatMovement, formatStock, isDecimalUnit } from './stockFormat';

describe('formatStock (spec 2026-10-24)', () => {
  it('shows closed bottles and open ml for ml items', () => {
    expect(formatStock(0, 49, 'ML')).toBe('0 şişe + 49 ml');
  });
  it('shows closed boxes and open vials for vaccines', () => {
    expect(formatStock(1, 19, 'FLAKON')).toBe('1 kutu + 19 flakon');
  });
  it('shows tubes and grams for g items', () => {
    expect(formatStock(1, 4, 'G')).toBe('1 tüp + 4 g');
  });
  it('shows plain count for adet items', () => {
    expect(formatStock(3, 0, 'ADET')).toBe('3 adet');
  });
  it('shows only the closed part when the open amount is unknown', () => {
    expect(formatStock(1, null, 'FLAKON')).toBe('1 kutu');
  });
  it('uses a Turkish decimal comma', () => {
    expect(formatStock(0, 47.5, 'ML')).toBe('0 şişe + 47,5 ml');
  });
  it('treats only ml and g as decimal entry', () => {
    expect(isDecimalUnit('ML')).toBe(true);
    expect(isDecimalUnit('G')).toBe(true);
    expect(isDecimalUnit('FLAKON')).toBe(false);
    expect(isDecimalUnit('ADET')).toBe(false);
  });
});

describe('formatMovement (stok hareketi satırı)', () => {
  it('shows only the open change when no closed unit moved', () => {
    expect(formatMovement('OUT', 0, -1, 'ML')).toBe('-1 ml açık');
  });
  it('shows an opened bottle with the open amount it left', () => {
    expect(formatMovement('OUT', 1, 49, 'ML')).toBe('-1 şişe, +49 ml açık');
  });
  it('shows a sync that adds boxes and removes vials', () => {
    expect(formatMovement('IN', 2, -3.5, 'FLAKON')).toBe('+2 kutu, -3,5 flakon açık');
  });
  it('keeps the plain count for adet items and old movements', () => {
    expect(formatMovement('OUT', 2, null, 'ADET')).toBe('-2');
    expect(formatMovement('IN', 1, null, 'ML')).toBe('+1 şişe');
  });
});
