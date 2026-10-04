import { describe, expect, it } from 'vitest';
import { memoryStore } from '../background/chromeStorage';
import { FLOW_KEY, createFlowStore } from './flowStore';

describe('flowStore', () => {
  it('arms a submission', async () => {
    const flow = createFlowStore(memoryStore(), () => 1000);
    await flow.arm('s1');
    expect(await flow.get()).toEqual({ submissionId: 's1', documentType: 'VACCINATION', step: 'armed', updatedAt: 1000 });
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

  it('records the document type when arming', async () => {
    const flow = createFlowStore(memoryStore(), () => 1000);
    await flow.arm('r1', 'PRESCRIPTION');
    expect(await flow.get()).toMatchObject({ submissionId: 'r1', documentType: 'PRESCRIPTION', step: 'armed' });
  });

  it('treats a stored flow without documentType as a vaccination', async () => {
    const store = memoryStore();
    await store.set(FLOW_KEY, { submissionId: 'old', step: 'searching', updatedAt: 5 });
    const flow = createFlowStore(store, () => 10);

    expect(await flow.get()).toMatchObject({ submissionId: 'old', documentType: 'VACCINATION', step: 'searching' });
    expect(await flow.update('old', { stepData: { itemIndex: 1 } })).toMatchObject({ documentType: 'VACCINATION', stepData: { itemIndex: 1 } });
  });
});
