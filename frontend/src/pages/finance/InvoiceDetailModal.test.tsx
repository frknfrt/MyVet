// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import { InvoiceDetailModal } from './InvoiceDetailModal';
import { billingApi, InvoiceDetail } from '../../api/billingApi';
import { appointmentApi } from '../../api/appointmentApi';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
let container: HTMLDivElement;
let root: Root;
afterEach(async () => {
  if (root) await act(async () => root.unmount());
  container?.remove();
  vi.restoreAllMocks();
});

it('keeps the request id after a lost response and creates a new id after confirmed success', async () => {
  const invoice: InvoiceDetail = {
    id: 'invoice', ownerId: 'owner', ownerName: 'Test', status: 'ISSUED', totalAmount: 100, paidAmount: 0,
    taxAmount: 0, issuedAt: null, encounterId: null, eInvoiceRef: null, lines: [], payments: [],
  };
  vi.spyOn(billingApi, 'getInvoice').mockResolvedValue(invoice);
  vi.spyOn(appointmentApi, 'listServiceTypes').mockResolvedValue([]);
  const record = vi.spyOn(billingApi, 'recordPayment')
    .mockRejectedValueOnce(new Error('response lost'))
    .mockResolvedValue(undefined);
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () => root.render(<InvoiceDetailModal invoiceId="invoice" onClose={() => {}} onChanged={() => {}} />));

  async function enterAmount() {
    const input = container.querySelector('input[type="number"]') as HTMLInputElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, '25');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }
  async function submit() {
    await act(async () => container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
  }

  await enterAmount();
  await submit();
  expect(container.querySelector('[role="alert"]')).not.toBeNull();
  const firstId = record.mock.calls[0][1].requestId;
  expect(firstId).toBeTruthy();

  await submit();
  expect(record.mock.calls[1][1].requestId).toBe(firstId);
  expect(container.querySelector('[role="alert"]')).toBeNull();

  await enterAmount();
  await submit();
  expect(record.mock.calls[2][1].requestId).not.toBe(firstId);
});
