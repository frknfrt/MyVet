import { describe, expect, it } from 'vitest';
import { memoryStore } from '../background/chromeStorage';
import { createFlowStore } from './flowStore';

describe('flowStore', () => {
  it('arms a submission', async () => {
    const flow = createFlowStore(memoryStore(), () => 1000);
    await flow.arm('s1');
    expect(await flow.get()).toEqual({ submissionId: 's1', step: 'armed', updatedAt: 1000 });
  });

  it('does not touch another submission state', async () => {
    const flow = createFlowStore(memoryStore(), () => 1000);
    await flow.arm('s1');
    expect(await flow.update('s2', { step: 'done' })).toBeNull();
    expect((await flow.get())?.step).toBe('armed');
  });

  it('refreshes updatedAt only on step changes', async () => {
    let t = 1000;
    const flow = createFlowStore(memoryStore(), () => t);
    await flow.arm('s1');
    t = 5000;
    await flow.update('s1', { redirectedAt: 5000 });
    expect((await flow.get())?.updatedAt).toBe(1000);
    await flow.update('s1', { step: 'searching' });
    expect(await flow.get()).toMatchObject({ step: 'searching', updatedAt: 5000, redirectedAt: 5000 });
  });
});
