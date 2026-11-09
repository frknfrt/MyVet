"use strict";
const token=document.querySelector('meta[name="deploy-session"]').content;
const $=id=>document.getElementById(id);
const definitions=[
 ['source','Kaynak kontrolü','Git sürümü ve uygulama kaynaklarının kopyası'],
 ['build','Uygulamaları derle','Backend, TARBİL eklentisi ve frontend'],
 ['package','Paketi doğrula','SHA-256, migration kataloğu ve indirme dosyaları'],
 ['connect','Sunucuya bağlan','SSH anahtarı ve staging yüklemesi'],
 ['preflight','Canlı ön kontroller','Migration uyumu, disk ve sunucu yerleşimi'],
 ['backup','Yedek al','Veritabanı, çalışan JAR ve frontend'],
 ['backend','Backend’i yayınla','Atomik değişim, yeniden başlatma ve readiness'],
 ['frontend','Frontend’i yayınla','Yeni dosyalar ve index güncellemesi'],
 ['smoke','Canlıyı doğrula','HTTPS, statik dosyalar ve eklenti sürümü']
];
const stageNodes=new Map();
for(const [id,title,description] of definitions){
 const li=document.createElement('li');li.className='stage';
 const icon=document.createElement('span');icon.className='stage-icon';icon.textContent=String(stageNodes.size+1);icon.setAttribute('aria-hidden','true');
 const body=document.createElement('div');body.className='stage-content';const top=document.createElement('div');top.className='stage-top';
 const name=document.createElement('span');name.className='stage-title';name.textContent=title;
 const state=document.createElement('span');state.className='stage-status';state.textContent='Bekliyor';top.append(name,state);
 const desc=document.createElement('p');desc.className='stage-desc';desc.textContent=description;body.append(top,desc);li.append(icon,body);$('stages').append(li);
 stageNodes.set(id,{li,icon,state,desc,description,number:stageNodes.size+1});
}
let state={job:null,releases:[]},starting=false,lastSelectedJob=null,lastLogs='';
function showError(message){$('api-error').hidden=!message;$('api-error').textContent=message||'';}
async function api(path,options={}){
 const response=await fetch(path,{...options,headers:{'X-Deploy-Token':token,'Content-Type':'application/json',...(options.headers||{})}});
 const data=await response.json();if(!response.ok)throw new Error(data.error||'İşlem tamamlanamadı');return data;
}
function selected(){return state.releases.find(r=>r.releaseId===$('release').value);}
function detail(key,value){const row=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=key;dd.textContent=value;row.append(dt,dd);return row;}
function renderRelease(){
 const release=selected();$('release-details').replaceChildren();
 if(release)$('release-details').append(detail('Commit',String(release.commit||'').slice(0,12)),detail('Migration',String(release.migrationCount)),detail('Eklenti',release.extensionVersion),detail('Kaynak',release.sourceMode==='commit'?'Son commit':'Çalışma klasörü'),detail('Yerel değişiklik',String(release.dirtyCount)),...(release.ignoredLocalChangeCount?[detail('Dışarıda bırakılan',String(release.ignoredLocalChangeCount))]:[]));
 const busy=starting||state.job?.status==='running';
 for(const id of ['prepare','allow-dirty','allow-dirty-release','source-mode','release','close-panel'])$(id).disabled=busy;
 $('dirty-release-option').hidden=!release?.dirtyCount;
 const dirtyAccepted=!release?.dirtyCount||$('allow-dirty-release').checked;
 $('check').disabled=busy||!release||!dirtyAccepted;$('deploy').disabled=busy||!release||!dirtyAccepted;
}
function formatLog(line){
 try{if(line.startsWith('@@VETLY_EVENT@@')){const e=JSON.parse(line.slice('@@VETLY_EVENT@@'.length));return '['+e.stage+'] '+e.message;}
 if(line.startsWith('@@VETLY_RESULT@@')){const r=JSON.parse(line.slice('@@VETLY_RESULT@@'.length));return 'Sonuç: '+r.status+' · '+(r.releaseId||'');}}catch{}
 return line;
}
function render(){
 const old=$('release').value;const options=[];
 const placeholder=document.createElement('option');placeholder.value='';placeholder.textContent=state.releases.length?'Bir paket seçin':'Henüz paket yok';options.push(placeholder);
 for(const r of state.releases){const option=document.createElement('option');option.value=r.releaseId;option.textContent=r.releaseId;options.push(option);}
 $('release').replaceChildren(...options);
 const job=state.job;
 if(job?.mode==='Prepare'&&job.status==='succeeded'&&job.id!==lastSelectedJob){$('release').value=job.releaseId||'';lastSelectedJob=job.id;}
 else $('release').value=state.releases.some(r=>r.releaseId===old)?old:state.releases[0]?.releaseId||'';
 const labels={running:'Çalışıyor',succeeded:'Tamamlandı',failed:'Durduruldu'};
 $('job-status').textContent=job?labels[job.status]||job.status:'Hazır';$('job-status').className='pill '+(job?.status||'');
 $('job-description').textContent=job?({Prepare:'Paket hazırlama',Check:'Yalnız canlı ön kontrol',Deploy:'Kontrollü canlı yayın'}[job.mode]+' · '+new Date(job.startedAt).toLocaleTimeString('tr-TR')):'Henüz bir işlem başlatılmadı';
 let completed=0,total=0;
 for(const [id,node] of stageNodes){
  let item=job?.stages?.[id];const excluded=job?.mode==='Prepare'&&definitions.findIndex(d=>d[0]===id)>2||job?.mode==='Check'&&['build','backup','backend','frontend','smoke'].includes(id);
  if(!item&&excluded&&job?.status!=='running')item={status:'skip',message:'Bu çalışmada uygulanmaz'};
  if(!excluded)total++;
  if(['ok','skip'].includes(item?.status)&&!excluded)completed++;
  const status=item?.status||'waiting';node.li.className='stage '+status;
  node.icon.textContent=status==='ok'?'✓':status==='error'?'!':status==='skip'?'–':String(node.number);
  node.state.textContent={ok:'Tamamlandı',start:'Çalışıyor',error:'Başarısız',skip:'Atlandı',waiting:'Bekliyor'}[status];
  node.desc.textContent=item?.message||node.description;
 }
 $('progress').max=total||9;$('progress').value=completed;$('progress-count').textContent=completed+' / '+(total||9);
 $('job-result').hidden=!job||job.status==='running';$('job-result').className='job-result '+(job?.status||'');
 if(job&&job.status!=='running'){
  const remote=job.result?.remote;let text=job.status==='succeeded'?(job.mode==='Prepare'?'Paket hazır. Sağdaki listeden inceleyip kontrol edebilir veya yayınlayabilirsiniz.':job.mode==='Check'?'Ön kontroller geçti. Çalışan uygulama değiştirilmedi.':'Yayın ve canlı kontroller başarıyla tamamlandı.'):(job.result?.error||'Pipeline başarısız oldu. Günlüğü inceleyin; sonraki aşamalar uygulanmadı.');
  if(remote?.backendRestored)text+=' Önceki backend geri alındı.';if(remote?.frontendRestored)text+=' Önceki frontend geri alındı.';
  if(remote?.manualRecoveryRequired)text+=' Migration veya kurtarma durumu manuel inceleme gerektiriyor. Veritabanı otomatik geri yüklenmedi.';
  $('job-result').textContent=text;
 }
 const logs=(job?.logs||[]).map(e=>formatLog(e.text)).join('\n');
 if(logs!==lastLogs){$('logs').textContent=logs||'İşlem başlatıldığında komut çıktıları burada görünecek.';lastLogs=logs;if($('autoscroll').checked)$('logs').scrollTop=$('logs').scrollHeight;}
 renderRelease();
}
async function refresh(){try{state=await api('/api/state');render();}catch(e){showError('Panel bağlantısı kurulamadı: '+e.message);}}
async function start(mode){
 if(starting)return;starting=true;renderRelease();showError('');
 try{await api('/api/run',{method:'POST',body:JSON.stringify({mode,releaseId:mode==='Prepare'?null:$('release').value||null,sourceMode:$('source-mode').value,allowDirty:mode==='Prepare'?$('source-mode').value==='working-tree'&&$('allow-dirty').checked:$('allow-dirty-release').checked})});await refresh();}
 catch(e){showError(e.message);}finally{starting=false;renderRelease();}
}
$('prepare').addEventListener('click',()=>start('Prepare'));
$('check').addEventListener('click',()=>start('Check'));
$('release').addEventListener('change',()=>{$('allow-dirty-release').checked=false;renderRelease();});
$('allow-dirty-release').addEventListener('change',renderRelease);
$('source-mode').addEventListener('change',()=>{
 const committed=$('source-mode').value==='commit';$('dirty-option').hidden=committed;
 if(committed)$('allow-dirty').checked=false;
 $('source-hint').textContent=committed?'Changelist’teki tüm commitlenmemiş değişiklikler dışarıda kalır.':'Çalışma klasörünüz kopyalanır. Yerel değişiklikleri dahil etmek için aşağıdan açıkça izin verin.';
});
$('deploy').addEventListener('click',()=>{const r=selected();if(!r)return;$('confirm-package').textContent='Paket: '+r.releaseId+' · Commit: '+r.commit.slice(0,12);$('confirm').showModal();});
$('confirm').addEventListener('close',()=>{if($('confirm').returnValue==='deploy')start('Deploy');});
$('close-panel').addEventListener('click',async()=>{
 try{await api('/api/shutdown',{method:'POST',body:'{}'});clearInterval(poll);
 const title=document.createElement('h1');title.textContent='Panel kapatıldı';const note=document.createElement('p');note.textContent='Yeniden açmak için Start-DeployUI başlatıcısını kullanabilirsiniz.';document.querySelector('main').replaceChildren(title,note);$('close-panel').disabled=true;
 }catch(e){showError(e.message);}
});
refresh();const poll=setInterval(refresh,1000);
