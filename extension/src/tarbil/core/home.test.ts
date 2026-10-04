import { describe, expect, it } from 'vitest';
import { shouldRedirectHome } from './home';

const NOW = 10_000_000;

describe('shouldRedirectHome', () => {
  it('redirects a freshly armed flow after e-Devlet login', () => {
    expect(shouldRedirectHome({ submissionId: 's1', documentType: 'VACCINATION', step: 'armed', updatedAt: NOW - 60_000 }, NOW)).toBe(true);
  });

  it('ignores an armed flow older than 30 minutes', () => {
    expect(shouldRedirectHome({ submissionId: 's1', documentType: 'VACCINATION', step: 'armed', updatedAt: NOW - 31 * 60_000 }, NOW)).toBe(false);
  });

  it('ignores flows that are past the armed step', () => {
    expect(shouldRedirectHome({ submissionId: 's1', documentType: 'VACCINATION', step: 'awaitingConfirm', updatedAt: NOW }, NOW)).toBe(false);
  });

  it('does not redirect again within a minute (no loop)', () => {
    expect(shouldRedirectHome({ submissionId: 's1', documentType: 'VACCINATION', step: 'armed', updatedAt: NOW - 120_000, redirectedAt: NOW - 30_000 }, NOW)).toBe(false);
  });

  it('does nothing without a flow', () => {
    expect(shouldRedirectHome(null, NOW)).toBe(false);
  });
});
