// @vitest-environment jsdom
import { act, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it } from 'vitest';
import { FindingsBySystem, markRemainingNormal, PhysicalExamCard, summarizeFindings, toFindingsBySystem } from './PhysicalExamCard';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let latest: FindingsBySystem;
afterEach(() => container?.remove());

function Harness({ initial, readOnly = false }: { initial: FindingsBySystem; readOnly?: boolean }) {
  const [findings, setFindings] = useState(initial);
  latest = findings;
  return <PhysicalExamCard findings={findings} onChange={setFindings} readOnly={readOnly} />;
}

async function mount(initial: FindingsBySystem, readOnly = false) {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<Harness initial={initial} readOnly={readOnly} />);
  });
}

function button(text: string) {
  return Array.from(container.querySelectorAll('button')).find((b) => b.textContent?.includes(text));
}

const rows = () => container.querySelector('#physical-exam-rows') as HTMLElement;
const withSkinAbnormal = toFindingsBySystem([{ system: 'SKIN_COAT', status: 'ABNORMAL', note: 'Alopesi' }]);

describe('PhysicalExamCard', () => {
  it('marks only unexamined systems normal and keeps abnormal findings', () => {
    const next = markRemainingNormal(withSkinAbnormal);
    expect(next.SKIN_COAT).toEqual({ status: 'ABNORMAL', note: 'Alopesi' });
    expect(summarizeFindings(next)).toEqual({ abnormal: 1, normal: 9, notExamined: 0 });
  });

  it('starts collapsed with a summary when nothing is abnormal and expands on demand', async () => {
    await mount(toFindingsBySystem([]));
    expect(rows().hidden).toBe(true);
    expect(container.textContent).toContain('10 muayene edilmedi');
    await act(async () => button('Sistemleri göster')!.click());
    expect(rows().hidden).toBe(false);
  });

  it('starts expanded when a finding is abnormal', async () => {
    await mount(withSkinAbnormal);
    expect(rows().hidden).toBe(false);
    expect(container.textContent).toContain('1 anormal');
  });

  it('applies "Kalanları normal işaretle" and hides it once nothing is left', async () => {
    await mount(withSkinAbnormal);
    await act(async () => button('Kalanları normal işaretle')!.click());
    expect(summarizeFindings(latest)).toEqual({ abnormal: 1, normal: 9, notExamined: 0 });
    expect(button('Kalanları normal işaretle')).toBeUndefined();
  });

  it('hides the bulk action when read-only', async () => {
    await mount(toFindingsBySystem([]), true);
    expect(button('Kalanları normal işaretle')).toBeUndefined();
  });
});
