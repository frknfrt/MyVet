import { describe, expect, it } from 'vitest';
import { VACCINE_PAGE_URL } from '../tarbil/selectors';
import { openVaccinePage, type TabsApi } from './tarbilTab';

const RECEIPT = 'https://hbsapp.tarbil.gov.tr/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx?type=1';

function fakeTabs(open: { id: number; url: string; windowId: number }[]) {
  const log: string[] = [];
  const tabs: TabsApi = {
    query: async () => open,
    update: async (id, props) => { log.push(`update:${id}:${JSON.stringify(props)}`); return undefined; },
    create: async (props) => { log.push(`create:${props.url}`); return undefined; },
  };
  const focusWindow = async (id: number) => { log.push(`focus:${id}`); };
  return { tabs, focusWindow, log };
}

describe('openVaccinePage', () => {
  it('switches to an already open vaccine receipt tab', async () => {
    const { tabs, focusWindow, log } = fakeTabs([
      { id: 1, url: 'https://hbsapp.tarbil.gov.tr/Default.aspx', windowId: 7 },
      { id: 2, url: RECEIPT, windowId: 7 },
    ]);

    await openVaccinePage(tabs, focusWindow);

    expect(log).toEqual(['update:2:{"active":true}', 'focus:7']);
  });

  it('opens the vaccine page in a new tab when none is open', async () => {
    const { tabs, focusWindow, log } = fakeTabs([]);

    await openVaccinePage(tabs, focusWindow);

    expect(log).toEqual([`create:${VACCINE_PAGE_URL}`]);
  });

  it('never navigates away from another TARBIL tab (it may hold unsaved work)', async () => {
    const { tabs, focusWindow, log } = fakeTabs([{ id: 3, url: 'https://hbsapp.tarbil.gov.tr/Modules/RECEIPT/Pages/VaccineDefault.aspx', windowId: 7 }]);

    await openVaccinePage(tabs, focusWindow);

    expect(log).toEqual([`create:${VACCINE_PAGE_URL}`]);
  });
});
