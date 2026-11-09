// @vitest-environment jsdom
import {act,type ReactNode} from 'react';
import {createRoot,type Root} from 'react-dom/client';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {NewOwnerPage} from './NewOwnerPage';
(globalThis as {IS_REACT_ACT_ENVIRONMENT?:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
const mocks=vi.hoisted(() => ({navigate:vi.fn(),register:vi.fn(),consent:vi.fn(),notice:vi.fn(),delivery:vi.fn()}));
vi.mock('react-router-dom',() => ({useNavigate:() => mocks.navigate}));
vi.mock('../../components/layout/AppShell',() => ({AppShell:({children}:{children:ReactNode}) => <div>{children}</div>}));
vi.mock('../../api/patientApi',() => ({patientApi:{registerOwner:mocks.register,recordConsent:mocks.consent}}));
vi.mock('../../api/formsApi',() => ({formsApi:{getPrivacyNotice:mocks.notice,recordNoticeDelivery:mocks.delivery}}));
let root:Root,container:HTMLDivElement;
beforeEach(async () => {
 vi.clearAllMocks();mocks.register.mockResolvedValue({id:'owner1'});mocks.notice.mockResolvedValue({version:1,text:'Klinik metni',hash:'notice1'});
 mocks.delivery.mockResolvedValue(undefined);container=document.createElement('div');document.body.append(container);root=createRoot(container);
 await act(async () => root.render(<NewOwnerPage/>));
});
afterEach(() => {act(() => root.unmount());container.remove();});
async function submit() {await act(async () => {container.querySelector('form')!.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));});}
it('registration never creates explicit consent or preselects service channels',async () => {
 await submit();expect(mocks.register).toHaveBeenCalledTimes(1);expect(mocks.consent).not.toHaveBeenCalled();
 expect(mocks.delivery).not.toHaveBeenCalled();expect(mocks.register.mock.calls[0][0]).toMatchObject({smsConsent:false,whatsappConsent:false,marketingConsent:false});
 expect(mocks.navigate).toHaveBeenCalledWith('/musteriler/owner1');
});
it('records the employees notice presentation separately with the exact notice hash',async () => {
 const label=Array.from(container.querySelectorAll('label')).find(l => l.textContent?.includes('klinik çalışanının beyanı'))!;
 await act(async () => label.querySelector<HTMLInputElement>('input')!.click());await submit();
 expect(mocks.delivery).toHaveBeenCalledWith('owner1','notice1');expect(mocks.consent).not.toHaveBeenCalled();
});
it('does not silently lose a failed delivery or create the owner twice',async () => {
 mocks.delivery.mockRejectedValue(new Error('Metin değişti'));
 const label=Array.from(container.querySelectorAll('label')).find(l => l.textContent?.includes('klinik çalışanının beyanı'))!;
 await act(async () => label.querySelector<HTMLInputElement>('input')!.click());await submit();
 expect(container.textContent).toContain('Hasta sahibi oluşturuldu; aydınlatma sunum kaydı oluşturulamadı');
 await submit();expect(mocks.register).toHaveBeenCalledTimes(1);expect(mocks.navigate).not.toHaveBeenCalled();
 expect(container.textContent).toContain('Oluşturulan hasta sahibine git');
});
