// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { hasFreshSuccess, isConfirmClick, markStaleSuccess } from './success';

const CONFIRM = ['_cntVACCINEBodyContent_btnInsert', '_cntVACCINEBodyContent_btnInsert2'];
const PANEL = '_UCVACCINENotification_pnlNotifiSuccess';

describe('success capture', () => {
  it('recognizes a click inside an official confirm button', () => {
    document.body.innerHTML = '<a id="x_cntVACCINEBodyContent_btnInsert"><input id="in" type="button"></a><button id="other"></button>';
    expect(isConfirmClick(document.getElementById('in'), CONFIRM)).toBe(true);
    expect(isConfirmClick(document.getElementById('other'), CONFIRM)).toBe(false);
    expect(isConfirmClick(null, CONFIRM)).toBe(false);
  });

  it('ignores empty panels and panels that were there before the click', () => {
    document.body.innerHTML = `<div id="a${PANEL}"></div><div id="b${PANEL}">Eski mesaj</div>`;
    const stale = new WeakSet<Element>();
    expect(hasFreshSuccess(document, PANEL, stale)).toBe(true); // metinli b var, henuz stale degil
    markStaleSuccess(document, PANEL, stale);
    expect(hasFreshSuccess(document, PANEL, stale)).toBe(false);

    document.body.insertAdjacentHTML('beforeend', `<div id="c${PANEL}">Kaydedildi</div>`);
    expect(hasFreshSuccess(document, PANEL, stale)).toBe(true);
  });
});
