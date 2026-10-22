import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { EXTENSION_VERSION } from './version';

describe('EXTENSION_VERSION', () => {
  it('matches manifest.json and package.json', () => {
    const manifest = JSON.parse(readFileSync(new URL('../../public/manifest.json', import.meta.url), 'utf8'));
    const pkg = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8'));
    expect(EXTENSION_VERSION).toBe(pkg.version);
    expect(manifest.version).toBe(pkg.version);
    expect(EXTENSION_VERSION).toBe('0.2.1');
  });
});
