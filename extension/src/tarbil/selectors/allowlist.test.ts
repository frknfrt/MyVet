import { describe, expect, it } from 'vitest';
import { ALLOWED_BUTTONS, FORBIDDEN_BUTTON_PATTERNS, allowedButtonSuffix } from './allowlist';
import { RECEIPT } from './vaccineReceipt';

describe('button allowlist', () => {
  it('contains no button that finalizes an official record', () => {
    const all = Object.values(ALLOWED_BUTTONS).flatMap((page) => Object.values(page));
    for (const suffix of all) {
      expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)), suffix).toBe(false);
    }
  });

  it('treats the vaccine page Onayla buttons as forbidden', () => {
    for (const suffix of RECEIPT.insertButtons) {
      expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)), suffix).toBe(true);
    }
  });

  it('resolves only listed page/button pairs', () => {
    expect(allowedButtonSuffix('vaccineReceipt', 'petVet')).toBe(RECEIPT.petVet);
    expect(allowedButtonSuffix('vaccineReceipt', 'insert')).toBeNull();
    expect(allowedButtonSuffix('nope', 'petVet')).toBeNull();
    expect(allowedButtonSuffix('vaccineReceipt', 'toString')).toBeNull();
  });
});
