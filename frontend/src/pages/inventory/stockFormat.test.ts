import { describe, expect, it } from 'vitest';
import { formatAmount, formatMovement, formatStock, inputModeFor, isDecimalUnit, openedExpiryStatus } from './stockFormat';

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

describe('TABLET ve parça (spec 2026-10-25)', () => {
  it('shows boxes and loose tablets', () => {
    expect(formatStock(2, 8, 'TABLET')).toBe('2 kutu + 8 tablet');
    expect(formatMovement('OUT', 1, 5, 'TABLET')).toBe('-1 kutu, +5 tablet açık');
  });
  it('shows opened amount on ADET rows when present', () => {
    expect(formatStock(2, 0.5, 'ADET')).toBe('2 adet, 0,5 açık');
    expect(formatStock(2, 0, 'ADET')).toBe('2 adet');
  });
  it('formats amounts by unit and piece', () => {
    expect(formatAmount(1, 'ML', 0.5, 'pipet')).toBe('2 pipet (1 ml)');
    expect(formatAmount(2, 'TABLET')).toBe('2 tablet');
    expect(formatAmount(1.5, 'ML')).toBe('1,5 ml');
    expect(formatAmount(2, 'ADET')).toBe('2 adet');
  });
  it('gives the input mode for each unit', () => {
    const pipet = inputModeFor('ML', 0.5, 'pipet');
    expect(pipet).toMatchObject({ label: 'Miktar (pipet)', step: '1', min: 1 });
    expect(pipet.toBase(2)).toBe(1);
    expect(pipet.fromBase(1.5)).toBe(3);
    expect(inputModeFor('TABLET')).toMatchObject({ label: 'Miktar (tablet)', step: '1', min: 1 });
    expect(inputModeFor('ML')).toMatchObject({ label: 'Miktar (ml)', step: '0.01', min: 0.01 });
    expect(inputModeFor('ADET')).toMatchObject({ label: 'Adet', step: '1', min: 1 });
    expect(inputModeFor('TABLET').toBase(3)).toBe(3);
  });
  it('tells expired and soon-to-expire opened products', () => {
    const today = new Date('2026-10-25T09:00:00');
    expect(openedExpiryStatus('2026-10-24', today)).toBe('expired');
    expect(openedExpiryStatus('2026-10-27', today)).toBe('soon');
    expect(openedExpiryStatus('2026-11-04', today)).toBeNull();
    expect(openedExpiryStatus(null, today)).toBeNull();
  });
});
