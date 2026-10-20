import { describe, expect, it } from 'vitest';
import { compareVersions } from './version';

describe('compareVersions', () => {
  it('compares numerically, not as text', () => {
    expect(compareVersions('0.10.0', '0.9.0')).toBeGreaterThan(0);
    expect(compareVersions('0.1.0', '0.2.0')).toBeLessThan(0);
    expect(compareVersions('0.2.0', '0.2.0')).toBe(0);
    expect(compareVersions(' 1.0.0 ', '0.99.99')).toBeGreaterThan(0);
  });

  it('treats malformed versions as 0.0.0', () => {
    expect(compareVersions('abc', '0.0.0')).toBe(0);
    expect(compareVersions('abc', '0.1.0')).toBeLessThan(0);
    expect(compareVersions('1.2', '0.0.1')).toBeLessThan(0);
  });
});
