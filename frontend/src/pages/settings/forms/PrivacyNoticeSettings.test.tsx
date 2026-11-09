// @vitest-environment jsdom
import {act} from 'react';
import {createRoot,type Root} from 'react-dom/client';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {PrivacyNoticeSettings} from './PrivacyNoticeSettings';
(globalThis as {IS_REACT_ACT_ENVIRONMENT?:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
const mocks=vi.hoisted(() => ({get:vi.fn(),save:vi.fn()}));
vi.mock('../../../api/formsApi',() => ({formsApi:{getPrivacyNotice:mocks.get,savePrivacyNotice:mocks.save}}));
let root:Root,container:HTMLDivElement;
const notice={version:1,text:'Kliniğin aydınlatma metni',hash:'v1'};
beforeEach(() => {
 vi.resetAllMocks();mocks.get.mockResolvedValue(notice);
 container=document.createElement('div');document.body.append(container);root=createRoot(container);
});
afterEach(() => {act(() => root.unmount());container.remove();});
async function render(){await act(async () => root.render(<PrivacyNoticeSettings/>));}
function button(text:string){return [...container.querySelectorAll('button')].find(b => b.textContent===text)!;}
it('shows the error and retry action instead of an endless loading message',async () => {
 mocks.get.mockRejectedValueOnce(new Error('İstenen kaynak bulunamadı'));
 await render();expect(container.querySelector('[role="alert"]')?.textContent).toBe('İstenen kaynak bulunamadı');
 expect(container.textContent).not.toContain('Metin yükleniyor');expect(button('Tekrar dene')).toBeTruthy();
 expect(container.querySelector('textarea')).toBeNull();
 await act(async () => button('Tekrar dene').click());
 expect(mocks.get).toHaveBeenCalledTimes(2);
 expect(container.querySelector('[role="alert"]')).toBeNull();
 expect(container.querySelector('textarea')?.value).toBe(notice.text);
 expect(container.textContent).not.toContain('Metin yükleniyor');
});
it('shows loading only while the request is pending',async () => {
 let resolve!:(value:typeof notice)=>void;mocks.get.mockReturnValue(new Promise<typeof notice>(r => {resolve=r;}));
 await render();expect(container.querySelector('[role="status"]')?.textContent).toBe('Metin yükleniyor…');
 await act(async () => resolve(notice));
 expect(container.querySelector('[role="status"]')).toBeNull();expect(container.querySelector('textarea')?.value).toBe(notice.text);
});
it('retains edited text and does not show loading if saving fails',async () => {
 await render();mocks.save.mockRejectedValue(new Error('Metin değişti'));
 const input=container.querySelector('textarea')!;
 act(() => {Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')!.set!.call(input,'Düzenlenen metin');input.dispatchEvent(new Event('input',{bubbles:true}));});
 await act(async () => button('Yeni sürüm olarak kaydet').click());
 expect(mocks.save).toHaveBeenCalledWith('Düzenlenen metin',1);
 expect(container.querySelector('[role="alert"]')?.textContent).toBe('Metin değişti');
 expect(input.value).toBe('Düzenlenen metin');expect(container.textContent).not.toContain('Metin yükleniyor');
});
