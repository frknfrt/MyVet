import { describe, expect, it } from 'vitest';
import { TARBIL_POPUP_PATTERN, allowTarbilPopups } from './tarbilPopups';

describe('allowTarbilPopups', () => {
  it('allows pop-ups only for the TARBIL vaccine site', async () => {
    const calls: unknown[] = [];
    await allowTarbilPopups({ popups: { set: async (d) => { calls.push(d); } } });

    expect(calls).toEqual([{ primaryPattern: 'https://hbsapp.tarbil.gov.tr/*', setting: 'allow' }]);
    expect(TARBIL_POPUP_PATTERN).toBe('https://hbsapp.tarbil.gov.tr/*');
  });

  it('swallows errors so the extension keeps working (the card still offers the open button)', async () => {
    await expect(allowTarbilPopups({ popups: { set: async () => { throw new Error('policy'); } } })).resolves.toBe(false);
  });
});
