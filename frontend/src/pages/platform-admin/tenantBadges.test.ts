import { describe, expect, it } from 'vitest';
import { isInternalPlan, planCodeLabel } from './tenantBadges';

describe('planCodeLabel', () => {
  it('INTERNAL planı "Test Hesabı (süresiz)" olarak gösterir', () => {
    expect(planCodeLabel('INTERNAL')).toBe('Test Hesabı (süresiz)');
  });

  it('TRIAL planı "Deneme" olarak gösterir', () => {
    expect(planCodeLabel('TRIAL')).toBe('Deneme');
  });

  it('diğer plan kodlarını olduğu gibi gösterir', () => {
    expect(planCodeLabel('PRO')).toBe('PRO');
  });
});

describe('isInternalPlan', () => {
  it('yalnızca INTERNAL için true döner', () => {
    expect(isInternalPlan('INTERNAL')).toBe(true);
    expect(isInternalPlan('TRIAL')).toBe(false);
  });
});
