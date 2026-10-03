(() => {
  'use strict';
  const KEY = 'nexus-offline-v1';
  const PAGE_TITLES = {home:'မူလစာမျက်နှာ',chats:'စကားပြောခန်း',ai:'Local AI',nearby:'အနီးအနား',diagnostics:'Diagnostics',settings:'ဆက်တင်များ'};
  const MAX_GROUP_MEMBERS=12,MAX_GROUP_TTL=8,validMemberId=value=>typeof value==='string'&&/^[-A-Za-z0-9_]{2,40}$/.test(value);
  const nativeBridge = window.NexusNativeNearby || null;
  const activeFileTransfers = new Map(), incomingFileTransfers = new Map(), seenCipherIvs = new Map();
  let storageMode = 'memory', storageIssue = '', loadedState = null, legacyMigrationNotice = false;
  const parseBridgeResult = value => { try { return typeof value === 'string' ? JSON.parse(value) : value; } catch { return null; } };
  try {
    if (nativeBridge && typeof nativeBridge.loadSecureState === 'function' && typeof nativeBridge.saveSecureState === 'function') {
      const saved = parseBridgeResult(nativeBridge.loadSecureState());
      if (!saved?.ok) { storageMode = 'locked'; storageIssue = saved?.error || 'SECURE_STORAGE_UNAVAILABLE'; }
      else {
        storageMode = 'android-keystore';
        if (saved.found && typeof saved.data === 'string') { loadedState = JSON.parse(saved.data); localStorage.removeItem(KEY); }
        else {
          const legacy = localStorage.getItem(KEY);
          if (legacy) {
            const candidate = JSON.parse(legacy);
            const migrated = parseBridgeResult(nativeBridge.saveSecureState(JSON.stringify(candidate)));
            if (migrated?.ok) { loadedState = candidate; localStorage.removeItem(KEY); legacyMigrationNotice = true; }
            else { loadedState = candidate; storageMode = 'locked'; storageIssue = 'LEGACY_MIGRATION_FAILED'; }
          }
        }
      }
    } else {
      const legacy = localStorage.getItem(KEY);
      if (legacy) { try { loadedState = JSON.parse(legacy); } catch {} }
      localStorage.removeItem(KEY);
      storageMode = 'memory';
    }
  } catch { storageMode = nativeBridge ? 'locked' : 'memory'; storageIssue = 'SECURE_STORAGE_READ_FAILED'; }
  const defaultState = () => ({
    name: 'ကျွန်ုပ်', deviceId: 'NX-' + crypto.randomUUID().slice(0,8).toUpperCase(), theme:'dark', language:window.NexusI18n.getSavedLanguage(),
    threads:[{id:'local',name:'ကိုယ်ပိုင်မှတ်စု'}], activeThread:'local', messages:[], lastDiagnostics:null, completedFileTransfers:[]
  });
  let state;
  try { state = {...defaultState(), ...(loadedState || {})}; }
  catch { state = defaultState(); }
  state.language=state.language==='en'?'en':'my';
  const tr = value => window.NexusI18n.translate(value,state.language);
  const foundDevices = new Map(), connectedEndpoints = new Map(), secureSessions = new Map();
  let nativeAvailable = false, simulatedBridge = false;
  let expectedQrIdentity = null, attachmentItems = [];
  state.duplicateCount = Number(state.duplicateCount || 0); state.rejectedCount = Number(state.rejectedCount || 0);
  if (!Array.isArray(state.threads) || !state.threads.length) state.threads = [{id:'local',name:'ကိုယ်ပိုင်မှတ်စု'}];
  if (!Array.isArray(state.messages)) state.messages = [];
  if (!Array.isArray(state.completedFileTransfers)) state.completedFileTransfers = [];
  const legacyGroupIds=new Map();
  for(const thread of state.threads.filter(item=>item.kind==='group')){
    const oldId=String(thread.groupId||thread.id.replace(/^group:/,'')),groupId=oldId.replace(/-/g,'').toLowerCase();
    if(!/^[a-f0-9]{32}$/.test(groupId)){thread.kind='left-group';thread.left=true;continue;}
    legacyGroupIds.set(thread.id,`group:${groupId}`);thread.id=`group:${groupId}`;thread.groupId=groupId;thread.ownerId=thread.ownerId||state.deviceId;thread.membershipVersion=Number.isSafeInteger(thread.membershipVersion)?thread.membershipVersion:1;thread.left=!!thread.left;
    const members=[...new Set([state.deviceId,...(Array.isArray(thread.members)?thread.members.filter(validMemberId):[])])];thread.members=members.slice(0,MAX_GROUP_MEMBERS);thread.acknowledgedMembers=Array.isArray(thread.acknowledgedMembers)?thread.acknowledgedMembers.filter(id=>thread.members.includes(id)):[state.deviceId];
  }
  if(Array.isArray(state.messages))state.messages.forEach(message=>{if(legacyGroupIds.has(message.threadId))message.threadId=legacyGroupIds.get(message.threadId);});
  if(legacyGroupIds.has(state.activeThread))state.activeThread=legacyGroupIds.get(state.activeThread);
  if (!state.activeThread || !state.threads.some(t => t.id === state.activeThread)) state.activeThread = state.threads[0].id;

  const $ = (selector, root=document) => root.querySelector(selector);
  const $$ = (selector, root=document) => [...root.querySelectorAll(selector)];
  let persistenceWarningShown = false;
  const persist = () => {
    if (storageMode === 'android-keystore') {
      const result = parseBridgeResult(nativeBridge.saveSecureState(JSON.stringify(state)));
      if (!result?.ok) {
        storageMode = 'locked'; storageIssue = result?.error || 'SECURE_STORAGE_WRITE_FAILED';
        if (!persistenceWarningShown) { toast('လုံခြုံသောသိမ်းဆည်းမှု မအောင်မြင်ပါ။ ဒေတာကို ထပ်မရေးဘဲ ထိန်းထားသည်။'); persistenceWarningShown = true; }
      } else localStorage.removeItem(KEY);
    }
    updateStorageMeter();
  };
  const safe = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const timeLabel = stamp => new Intl.DateTimeFormat(state.language==='en'?'en-US':'my-MM',{hour:'2-digit',minute:'2-digit'}).format(new Date(stamp));
  function toast(message) {
    const el = document.createElement('div'); el.className='toast'; el.textContent=message; $('#toast-region').append(el);
    setTimeout(()=>el.remove(),3400);
  }
  function applyTheme(theme) {
    state.theme = theme;
    const effective = theme === 'system' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light':'dark') : theme;
    document.body.classList.toggle('light', effective === 'light');
    $$('.theme-option').forEach(b => b.classList.toggle('selected', b.dataset.theme === theme));
    const use = $('#appearance-toggle use'); if(use) use.setAttribute('href',effective==='light'?'#i-sun':'#i-moon');
    persist();
  }
  function setProfile() {
    const name = state.name || 'ကျွန်ုပ်';
    $('#profile-name-small').textContent = name;
    const letter = [...name.trim()][0] || 'N';
    $('#avatar-small').textContent = letter; $('#avatar-top').textContent = letter;
    $('#display-name').value = name;
    $('#device-id').textContent = state.deviceId;
  }
  function updateInternet() {
    const online = navigator.onLine;
    $('#internet-label').textContent = online ? 'Browser online':'Browser offline';
    $('#internet-pill .status-dot').className = 'status-dot' + (online?'':' amber');
    $('#internet-status-tag').textContent = online ? 'Online':'Offline';
    $('#internet-status-tag').className = 'status-tag ' + (online?'':'warn');
    $('#internet-description').textContent = online ? 'Browser က network ရှိကြောင်း ပြထားသည်':'Browser က network မရှိကြောင်း ပြထားသည်';
    $('#internet-foot').textContent = 'ဤအခြေအနေသည် Nearby ကို မဆုံးဖြတ်ပါ';
  }
  function activeThread() { return state.threads.find(t => t.id === state.activeThread) || state.threads[0]; }
  function renderThreads(filter='') {
    const root=$('#thread-items'), query=filter.trim().toLowerCase();
    const visible=state.threads.filter(t=>t.name.toLowerCase().includes(query));
    root.innerHTML=visible.map(t=>`<button class="thread-item ${t.id===state.activeThread?'selected':''}" data-thread="${safe(t.id)}"><span class="thread-avatar"><svg><use href="#i-lock"/></svg></span><span class="thread-text"><strong>${safe(t.name)}</strong><small>${t.kind==='peer'?'Secure Nearby':t.kind==='group'?'Encrypted group':'ဒီစက်ထဲမှာသာ'}</small></span><span class="thread-time">${t.kind==='peer'?'PEER':t.kind==='group'?'GROUP':'LOCAL'}</span></button>`).join('');
    $('#thread-count').textContent=String(state.threads.length).padStart(2,'0');
    $('#no-search-match').hidden=visible.length>0;
    $$('#thread-items [data-thread]').forEach(button=>button.addEventListener('click',()=>{state.activeThread=button.dataset.thread;persist();renderThreads($('#chat-search').value);renderMessages();}));
  }
  function renderMessages() {
    const room=activeThread();
    $('.room-header h2').textContent=room.name;
    const localOnly=room.id==='local'||!room.kind,leftGroup=room.kind==='group'&&!!room.left;
    $('.room-header p').innerHTML=localOnly?'<span class="status-dot amber"></span> Local only · ပို့သူမရှိ':leftGroup?'<span class="status-dot amber"></span> Group မှ ထွက်ထားသည် · ပို့ခြင်းပိတ်ထားသည်':room.kind==='group'?'<span class="status-dot mint"></span> Offline group · bounded encrypted fan-out/relay':'<span class="status-dot mint"></span> Encrypted Nearby peer';
    $('#message-input').placeholder=tr(localOnly?'ဒီစက်ထဲမှာပဲ သိမ်းမည့် မှတ်စုရေးပါ…':leftGroup?'Group မှထွက်ထားသောကြောင့် ပို့၍မရပါ':'စာရေးပါ… · session မရှိလျှင် queue ထဲမှာသာ သိမ်းမည်');
    $('#message-input').disabled=leftGroup;$('#message-input').setAttribute('aria-label',tr(localOnly?'Local မှတ်စုစာသား':leftGroup?'Group မှ ထွက်ထားသောမှတ်စု':'Encrypted Nearby message'));$('.send-button').disabled=leftGroup;
    $('.composer-bottom span').innerHTML=localOnly?'<svg><use href="#i-lock"/></svg> ဒီစက်ထဲသာ · မပို့ရသေး':leftGroup?'<svg><use href="#i-lock"/></svg> Group မှထွက်ထား · မပို့နိုင်':'<svg><use href="#i-lock"/></svg> Secure session ရှိမှသာ encrypt လုပ်ပို့မည်';
    const groupAction=$('#group-action-button');if(groupAction){groupAction.hidden=room.kind!=='group';groupAction.textContent=tr(room.ownerId===state.deviceId?'Manage group members':'Leave group');groupAction.setAttribute('aria-label',tr(room.ownerId===state.deviceId?'Secure peers ဖိတ်ကြား၍ group roster ထပ်တူပြုရန်':'ဤ offline group မှ ထွက်ရန်'));groupAction.disabled=leftGroup&&room.ownerId!==state.deviceId;}
    $('.send-button span').textContent=localOnly?'Queue ထဲသိမ်းရန်':'ပို့ရန် / queue သိမ်းရန်';
    $('#empty-chat h3').textContent=localOnly?'သင့် local မှတ်စုကို စတင်ပါ':'စကားစတင်ရေးပါ';
    $('#empty-chat p').textContent=localOnly?'ပို့ရန်မဟုတ်ပါ။ စာကို local queue ထဲမှာသိမ်းပြီး status ကို ထင်ရှားစွာပြပါမယ်။':'Secure Nearby session မတည်ဆောက်မချင်း စာကို local queue ထဲမှာသာ သိမ်းမည်။';
    const list=$('#message-list');
    const messages=state.messages.filter(m=>m.threadId===room.id);
    $('#empty-chat').hidden=messages.length>0;
    list.innerHTML=messages.map(m=>`<article class="message-bubble"><p>${safe(m.text)}</p><div class="message-meta"><svg><use href="#i-clock"/></svg><span>${timeLabel(m.createdAt)}</span><span>·</span><span class="message-label">${m.status==='received-secure'?'RECEIVED · encrypted Nearby':m.status==='sent-secure'?'SENT · encrypted Nearby':'LOCAL QUEUE · မပို့ရသေး'}</span></div></article>`).join('');
    list.scrollTop=list.scrollHeight;
    const count=state.messages.filter(m=>m.status==='queued-local').length;
    $('#queue-count').textContent=count; $('#queue-badge').textContent=count;
  }
  function setSidebarOpen(open, restoreFocus=false) {
    const sidebar=$('#sidebar'), menu=$('#mobile-menu'), scrim=$('#sidebar-scrim');
    const isMobile=matchMedia('(max-width:760px)').matches, expanded=isMobile&&!!open;
    sidebar.classList.toggle('open',expanded);
    if(scrim)scrim.hidden=!expanded;
    document.body.classList.toggle('sidebar-open',expanded);
    sidebar.inert=isMobile&&!expanded;
    sidebar.setAttribute('aria-hidden',String(isMobile&&!expanded));
    menu.setAttribute('aria-expanded',String(expanded));
    menu.setAttribute('aria-label',tr(expanded?'မီနူးပိတ်ရန်':'မီနူးဖွင့်ရန်'));
    if(restoreFocus&&isMobile)menu.focus({preventScroll:true});
  }
  function showPage(name,{historyMode='push'}={}) {
    if (!PAGE_TITLES[name]) return;
    const current=$('.page.active')?.id.replace(/^page-/,'');
    $$('.page').forEach(p=>{const active=p.id===`page-${name}`;p.classList.toggle('active',active);p.setAttribute('aria-hidden',String(!active));});
    $$('.nav-item').forEach(b=>{const active=b.dataset.page===name;b.classList.toggle('active',active);if(active)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});
    $('#page-title').textContent=tr(PAGE_TITLES[name]);
    document.title=`${tr(PAGE_TITLES[name])} · NEXUS OFFLINE`;
    setSidebarOpen(false);
    const nextHash=`#${name}`;
    if(historyMode!=='none'&&location.hash!==nextHash){
      const method=historyMode==='replace'?'replaceState':'pushState';
      history[method]({page:name},'',nextHash);
    }
    if(current!==name){window.scrollTo(0,0);const heading=$(`#page-${name} h1`);if(heading){heading.tabIndex=-1;heading.focus({preventScroll:true});}}
    if(name==='settings') updateStorageMeter();
    if(name==='nearby') refreshAttachments();
    if(name==='diagnostics') refreshNativeDiagnostics();
    if(name==='ai') refreshLocalAIStatus();
  }
  function syncPageFromLocation() {
    const route=location.hash.slice(1);
    showPage(PAGE_TITLES[route]?route:'home',{historyMode:'none'});
  }
  function updateStorageMeter() {
    let size=0, label='';
    if(storageMode==='android-keystore'&&nativeBridge&&typeof nativeBridge.getSecureStorageBytes==='function') {
      const result=parseBridgeResult(nativeBridge.getSecureStorageBytes());
      size=result?.ok?Number(result.bytes)||0:0; label='Android private storage · Keystore AES-GCM';
    } else {
      try { size=new Blob([JSON.stringify(state)]).size; } catch { size=0; }
      label=storageMode==='locked'?'Secure storage unavailable · data writes stopped':'Browser preview · current session only · not saved';
    }
    $('#storage-size').textContent=`${size<1024?`${size} B`:`${(size/1024).toFixed(1)} KB`}${storageMode==='android-keystore'?'':' · session'}`;
    $('#meter-fill').style.width=`${Math.min(100,Math.max(3,size/50000*100))}%`;
    const backend=$('#storage-backend-label'); if(backend)backend.textContent=label;
    const privacy=$('#privacy-storage-copy');
    if(privacy)privacy.textContent=storageMode==='android-keystore'
      ? 'Android build တွင် profile နဲ့ chat history ကို app-private file ထဲ AES-GCM ဖြင့် encrypt လုပ်ပြီး key ကို Android Keystore က ထိန်းသည်။ Browser preview သည် session memory သာဖြစ်သည်။'
      : storageMode==='locked'
        ? 'Android secure storage ကို ဖွင့်မရသောကြောင့် ရှိပြီးသား encrypted data ကို မပြင်ဘဲ သိမ်းထားပြီး ထပ်မသိမ်းတော့ပါ။'
        : 'Browser preview သည် profile နှင့် chat ကို tab ဖွင့်ထားသည့် အချိန်အတွင်းသာ memory ထဲထားပြီး browser storage ထဲမသိမ်းပါ။';
  }
  function detectNativeBridge() {
    const bridge=window.NexusNativeNearby;
    const present=!!bridge;
    simulatedBridge=!!bridge&&bridge.isMock===true;
    nativeAvailable=present;
    const label=present?(simulatedBridge?'Mock Nearby bridge · simulation only':'Android Nearby bridge ရရှိသည်'):'NexusNativeNearby bridge မတွေ့ရှိပါ';
    $('#native-state-title').textContent=label;
    $('#native-state-detail').textContent=present?(simulatedBridge?'SIMULATION ONLY · physical radio/device မဟုတ်ပါ':'Nearby Connections ရရှိသည် · လက်တွေ့ချိတ်ဆက်မှု မစမ်းရသေး'):'Android native bridge မတွေ့ရှိပါ';
    $('#nearby-description').textContent=present?(simulatedBridge?'Mock protocol simulation · physical Nearby radio မဟုတ်ပါ':'Nearby Connections radio transport ကို အသုံးပြုနိုင်သည်'):'Android native bridge မတွေ့ရှိသေးပါ';
    $('#nearby-foot').textContent=present?(simulatedBridge?'MOCK TEST ONLY · no physical devices':'Native transport ရှိသည် · real devices မစမ်းရသေး'):'Web preview — native မချိတ်ထား';
    $('#start-discovery').disabled=!present||typeof bridge.startDiscovery!=='function'||typeof bridge.startAdvertising!=='function';
    $('#pair-qr').disabled=!present||typeof bridge.createPairingQr!=='function';
    $('#scan-pair-qr').disabled=!present||typeof bridge.scanPairingQr!=='function';
    const bridgeMetric=$('#native-bridge-status'),transportMetric=$('#native-transport-type');
    if(bridgeMetric)bridgeMetric.textContent=present?(simulatedBridge?'Mock only':'Available'):'Unavailable';
    if(transportMetric)transportMetric.textContent=present?(simulatedBridge?'Mock test bridge':'Nearby Connections'):'—';
    return present;
  }
  function refreshQueue() {
    const connected=detectNativeBridge();
    const msg=connected?'Bridge ရှိကြောင်းသာ တွေ့ပါသည်။ Secure pairing/session မရှိသဖြင့် queue မပို့ဘဲ စောင့်ထားပါသည်။':'Native bridge မရှိသေးသဖြင့် queue ထဲကစာများကို မပို့ဘဲ စောင့်ထားပါသည်။';
    $('#retry-status').textContent=msg;
    toast(connected?'Secure session မရှိပါ — မပို့ဘဲထားပါသည်':'မပို့ရသေးသောစာများ local queue ထဲမှာပဲ ရှိနေသည်');
  }
  const utf8 = value => new TextEncoder().encode(value);
  const toB64 = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes)));
  const fromB64 = value => Uint8Array.from(atob(value), c=>c.charCodeAt(0));
  function bridgeCall(method,...args) {
    try { const bridge=window.NexusNativeNearby; return bridge&&typeof bridge[method]==='function'?bridge[method](...args):null; }
    catch { return null; }
  }
  function bridgeJson(value) { try { return typeof value==='string'?JSON.parse(value):value; } catch { return {ok:false,error:'INVALID_BRIDGE_RESPONSE'}; } }
  const formatBytes = value => { const bytes=Math.max(0,Number(value)||0);return bytes<1024?`${bytes} B`:bytes<1024*1024?`${(bytes/1024).toFixed(1)} KB`:`${(bytes/1024/1024).toFixed(1)} MB`; };
  function refreshAttachments() {
    if(!detectNativeBridge()){renderAttachmentList({items:[]});return;}
    const response=bridgeJson(bridgeCall('listAttachments'));
    if(!response?.ok)toast('Attachment list ကို ဖတ်မရပါ');
  }
  function renderAttachmentList(event) {
    const list=$('#attachment-list');if(!list)return;
    attachmentItems=Array.isArray(event.items)?event.items.filter(x=>x&&/^[a-f0-9]{32}$/i.test(String(x.transferId||''))):[];
    $('#attachment-count').textContent=String(attachmentItems.length);
    $('#attachments-empty').hidden=attachmentItems.length>0;
    list.innerHTML=attachmentItems.map(item=>{
      const verified=item.integrityStatus==='verified';
      const labels={verified:'GCM + SHA-256 verified', 'integrity-failed':'Integrity failure','metadata-unavailable':'Metadata unavailable','metadata-invalid':'Metadata invalid','key-unavailable':'Keystore key unavailable'};
      const received=Number(item.receivedAtMillis)||Date.now();
      return `<article class="attachment-card"><div class="attachment-meta"><strong>${safe(item.filename||'Unknown file')}</strong><small class="attachment-description">${safe(item.mimeType||'application/octet-stream')} · ${formatBytes(item.sizeBytes)} · ${timeLabel(received)}</small><span class="attachment-integrity ${verified?'verified':'failed'}">${safe(labels[item.integrityStatus]||'Not verified')}</span></div><div class="attachment-actions"><button class="button secondary" data-attachment-action="open" data-transfer-id="${safe(item.transferId)}" aria-label="${safe(item.filename||'Attachment')} ဖွင့်ရန်" ${verified?'':'disabled'}>ဖွင့်ရန်</button><button class="button secondary" data-attachment-action="share" data-transfer-id="${safe(item.transferId)}" aria-label="${safe(item.filename||'Attachment')} မျှဝေရန်" ${verified?'':'disabled'}>Share</button><button class="button ghost" data-attachment-action="export" data-transfer-id="${safe(item.transferId)}" aria-label="${safe(item.filename||'Attachment')} ထုတ်ယူရန်" ${verified?'':'disabled'}>Export</button><button class="button danger" data-attachment-action="delete" data-transfer-id="${safe(item.transferId)}" aria-label="${safe(item.filename||'Attachment')} ဖျက်ရန်">ဖျက်ရန်</button></div></article>`;
    }).join('');
    list.querySelectorAll('[data-attachment-action]').forEach(button=>button.addEventListener('click',()=>{
      const transferId=button.dataset.transferId,action=button.dataset.attachmentAction;
      if(action==='delete'){
        const item=attachmentItems.find(x=>x.transferId===transferId);
        openConfirm('Attachment ဖျက်မလား?',`${item?.filename||'ဤဖိုင်'} ကို encrypted storage နှင့်ဆက်စပ် temporary files များမှ ဖျက်မည်။ ပြန်မရနိုင်ပါ။`,'ဖိုင်ဖျက်မည်',()=>{
          const response=bridgeJson(bridgeCall('deleteAttachment',transferId));
          if(!response?.ok)toast(`ဖျက်မစနိုင်ပါ · ${response?.error||'မအောင်မြင်ပါ'}`);
        });
        return;
      }
      const response=bridgeJson(bridgeCall('useAttachment',transferId,action));
      if(!response?.ok)toast(`Attachment action မစတင်နိုင်ပါ · ${response?.error||'မအောင်မြင်ပါ'}`);
    }));
    const health=$('#attachment-health');
    if(health)health.textContent=`${attachmentItems.length} ဖိုင် · ${attachmentItems.filter(x=>x.integrityStatus==='verified').length} verified`;
  }
  function refreshNativeDiagnostics() {
    const result=bridgeJson(bridgeCall('getStatus'));
    if(!result?.available)return;
    simulatedBridge=simulatedBridge||result.isMock===true;
    const app=$('#build-identity');if(app)app.textContent=`${result.applicationId||'unknown'} · ${result.versionName||'unknown'} (${result.versionCode??'?'}) · ${result.buildType||'build unknown'}`;
    const stateBytes=bridgeJson(bridgeCall('getSecureStorageBytes'));
    const stateNode=$('#encrypted-state-size');if(stateNode)stateNode.textContent=`${stateBytes?.ok?formatBytes(stateBytes.bytes):'Unavailable'} · ${safe(result.secureStateHealth||'health unknown')}`;
    const migration=$('#migration-health');if(migration)migration.textContent=`${Number(result.legacyPlaintextAttachmentCount)||0} plaintext item(s) pending`;
    const ai=$('#local-ai-health');if(ai)ai.textContent=result.localAI?.runtimeInstalled?`${safe(result.localAI.runtimeName||'available')} · ${safe(result.localAI.state||'unknown')} · ${result.localAI.installedModels?.length||0} model(s)`:'Runtime unavailable or ABI unsupported';
    const endpoints=$('#endpoint-health');if(endpoints)endpoints.textContent=simulatedBridge?`0 real · ${Number(result.connectedCount)||connectedEndpoints.size} simulated (not physical)`:`${Number(result.connectedCount)||0} · native endpoint callbacks`;
    const nearby=$('#nearby-service-status');if(nearby)nearby.textContent=`Play Services ${result.googlePlayServicesAvailable?'available':'unavailable'} · Nearby ${result.nearbyAvailable?'available':'unavailable'}`;
    const radio=$('#native-radio-state');if(radio)radio.textContent=`${result.advertising?'advertising':'not advertising'} · ${result.discovering?'discovering':'not discovering'}`;
    const origin=$('#bridge-origin');if(origin)origin.textContent=simulatedBridge?'SIMULATION ONLY · no physical devices':'Native Android bridge';
    const queue=$('#message-queue-health');if(queue)queue.textContent=String(state.messages.filter(message=>message.status==='queued-local').length);
    const qr=$('#qr-health');if(qr)qr.textContent=`scanner ${result.qrScannerAvailable?'available':'unavailable'} · version ${result.pairingQrVersion??'?'}`;
    const replay=$('#protocol-replay-health');if(replay)replay.textContent=`${Number(state.duplicateCount)||0} duplicate · ${Number(state.rejectedCount)||0} rejected`;
    const attachment=$('#attachment-health');if(attachment)attachment.textContent=`${safe(result.attachmentHealth||'unknown')} · ${Number(result.attachmentCount)||0} · ${formatBytes(result.attachmentEncryptedBytes)} encrypted · ${Number(result.attachmentVerifiedCount)||0} verified`;
    const bridgeMetric=$('#native-bridge-status');if(bridgeMetric)bridgeMetric.textContent=simulatedBridge?'Mock (simulation only)':'Available';
    const transport=$('#native-transport-type');if(transport)transport.textContent=simulatedBridge?'Mock test bridge':result.transport||'Nearby Connections';
    const permission=$('#native-permission-status');if(permission)permission.textContent=result.permissionGranted?'Granted':'Not granted';
    const sessions=$('#active-session-count');if(sessions)sessions.textContent=simulatedBridge?`0 real · ${[...secureSessions.values()].filter(session=>session.secure).length} simulated`:[...secureSessions.values()].filter(session=>session.secure).length;
  }
  function refreshLocalAIStatus() {
    const result=bridgeJson(bridgeCall('getLocalAIStatus'))||{};
    const select=$('#ai-model-select');if(!select)return result;
    const models=Array.isArray(result.installedModels)?result.installedModels:[];
    const previous=select.value;select.replaceChildren(new Option(tr('Model မရွေးရသေးပါ'),''));
    models.forEach(model=>select.add(new Option(`${model.displayName||tr('Imported model')} · ${formatBytes(model.requiredStorageBytes||0)}`,String(model.id||''))));
    if(models.some(model=>model.id===previous))select.value=previous;
    else if(result.loadedModelId&&models.some(model=>model.id===result.loadedModelId))select.value=result.loadedModelId;
    else if(models.length===1)select.value=String(models[0].id||'');
    const selected=models.find(model=>model.id===select.value)||null;
    const installed=!!result.runtimeInstalled,stateName=String(result.state||'UNAVAILABLE'),loaded=!!result.loadedModelId;
    const busy=['LOADING','GENERATING'].includes(stateName),noModel=models.length===0;
    const invalidModel=!!selected&&(selected.compatibility==='LOW_AVAILABLE_RAM_ESTIMATE'||selected.compatibility==='MODEL_INSPECTION_FAILED'||String(selected.compatibility||'').startsWith('CORRUPT_')||String(selected.compatibility||'').startsWith('UNSUPPORTED_'));
    $('#import-ai-model').disabled=!nativeAvailable||busy;
    $('#check-ai-compatibility').disabled=!nativeAvailable||!selected||busy;
    $('#load-ai-model').disabled=!installed||!selected||loaded||busy||invalidModel;
    $('#unload-ai-model').disabled=!loaded&&!busy;
    $('#delete-ai-model').disabled=!nativeAvailable||!selected||loaded||busy;
    $('#ai-prompt').disabled=!loaded||stateName!=='READY';$('#generate-ai').disabled=!loaded||stateName!=='READY';$('#test-ai-inference').disabled=!loaded||stateName!=='READY';$('#cancel-ai').disabled=stateName!=='GENERATING';
    $('#ai-runtime-status-value').textContent=installed?`${result.runtimeName||'LiteRT-LM'} · ${tr('Load လုပ်ချိန်တွင် initialize လုပ်မည်')}`:tr('Android runtime မရရှိသေးပါ');
    $('#ai-model-status-value').textContent=noModel?'LOCAL AI — MODEL NOT INSTALLED':loaded?'MODEL LOADED · '+(selected?.displayName||''):'MODEL INSTALLED · NOT LOADED';
    $('#ai-status-tag').textContent=loaded?tr('Model တင်ပြီးပါပြီ'):noModel?'LOCAL AI — MODEL NOT INSTALLED':tr('Model ရွေးပြီး စစ်ဆေးပါ');
    $('#ai-capability').textContent=installed?`${result.runtimeName||'LiteRT-LM'} · ABI ${Array.isArray(result.supportedAbis)?result.supportedAbis.join(', '):'unknown'} · ${models.length} model(s) · ${tr('Network inference မရှိ၊ cloud fallback မရှိ')}`:tr('Web preview တွင် Local AI inference မရပါ။ Android runtime မရရှိသေးပါ။ Cloud fallback မရှိပါ။');
    $('#ai-operation-status').textContent=noModel?'LOCAL AI — MODEL NOT INSTALLED':String(result.errorCode||result.status||result.modelStatus||'MODEL_NOT_LOADED');
    const display=(id,value)=>{const node=$(id);if(node)node.textContent=String(value??'—');};
    display('#ai-info-filename',selected?.displayName||'—');
    display('#ai-info-size',selected?`${formatBytes(selected.requiredStorageBytes||0)} · ${tr('available')} ${result.availableStorageBytes==null?tr('မရရှိသေး'):formatBytes(result.availableStorageBytes)}`:'—');
    display('#ai-info-format',selected?`${selected.format||tr('Unknown')} ${selected.formatVersion||''}`.trim():'—');
    display('#ai-info-architecture',selected?.architecture||tr('ဖိုင်ထဲတွင် မဖော်ပြထားပါ'));
    display('#ai-info-context',selected?.contextLength?`${selected.contextLength} ${tr('tokens (metadata)')}`:tr('မသိရှိပါ'));
    display('#ai-info-license',selected?.license||tr('အတည်မပြုရသေး — upstream ကိုစစ်ပါ'));
    display('#ai-info-ram',selected?.requiredRamBytes?`${formatBytes(selected.requiredRamBytes)} · ${tr('rough estimate')}`:'—');
    display('#ai-info-available-ram',selected?.availableRamBytes!=null?formatBytes(selected.availableRamBytes):result.availableRamBytes!=null?formatBytes(result.availableRamBytes):tr('မရရှိသေး'));
    display('#ai-info-available-storage',result.availableStorageBytes!=null?formatBytes(result.availableStorageBytes):tr('မရရှိသေး'));
    display('#ai-info-compatibility',selected?.compatibility||'—');
    const health=$('#local-ai-health');if(health)health.textContent=noModel?'LOCAL AI — MODEL NOT INSTALLED':`${safe(result.runtimeName||'LiteRT-LM')} · ${safe(stateName)} · ${models.length} model(s)`;
    return result;
  }

  function renderNearbyDevices() {
    const root=$('#nearby-device-list'); if(!root)return;
    const items=[...foundDevices.entries()].map(([id,name])=>({id,name,connected:connectedEndpoints.has(id)}));
    root.hidden=items.length===0;
    if(expectedQrIdentity&&expectedQrIdentity.expiresAt<=Date.now())expectedQrIdentity=null;
    const qrFilter=expectedQrIdentity?`<div class="qr-match-status">QR identity ကိုက်ညီသည့် စက်: <strong>${safe(expectedQrIdentity.identityRef)}</strong><button class="text-button" data-clear-qr>QR filter ဖယ်ရန်</button></div>`:'';
    const mockNote=simulatedBridge?'<div class="simulated-device-note" role="status"><strong>SIMULATION ONLY</strong> · ဤ peer များသည် mock test များဖြစ်ပြီး physical Android device မဟုတ်ပါ။</div>':'';
    root.innerHTML=mockNote+qrFilter+items.map(d=>{
      const qrMatches=!expectedQrIdentity||d.name===expectedQrIdentity.identityRef;
      const label=expectedQrIdentity?(qrMatches?'QR match':'QR မကိုက်ညီ'):'';
      return `<div class="nearby-device-row"><div><strong>${safe(d.name||'Nearby device')}</strong><small>${safe(d.id)} · ${label?`${safe(label)} · `:''}${secureSessions.get(d.id)?.secure?'Encrypted session':connectedEndpoints.has(d.id)?'Pairing…':'တွေ့ရှိထားသည်'}</small></div>${connectedEndpoints.has(d.id)?`<span class="status-tag ${secureSessions.get(d.id)?.secure?'':'warn'}">${secureSessions.get(d.id)?.secure?'Secure':'Pairing'}</span>${secureSessions.get(d.id)?.secure?`<button class="button secondary" data-file="${safe(d.id)}">ဖိုင်ပို့ရန်</button>`:''}`:`<button class="button secondary" data-connect="${safe(d.id)}" ${qrMatches?'':'disabled'}>ချိတ်ဆက်ရန်</button>`}</div>`;
    }).join('');
    root.querySelector('[data-clear-qr]')?.addEventListener('click',()=>{expectedQrIdentity=null;renderNearbyDevices();});
    root.querySelectorAll('[data-connect]').forEach(button=>button.addEventListener('click',()=>{
      const id=button.dataset.connect; if(expectedQrIdentity&&foundDevices.get(id)!==expectedQrIdentity.identityRef){toast('Scan လုပ်ထားသော QR identity နှင့် မကိုက်ညီပါ');return;} const response=bridgeJson(bridgeCall('connect',id));
      if(!response?.ok)toast('ချိတ်ဆက်မှု စတင်မရပါ။ Permission/transport အခြေအနေကို စစ်ပါ။');
      else toast('Pairing request ပို့ပြီးပါပြီ။ ဖုန်းနှစ်လုံးပေါ်က code ကို တိုက်စစ်ပါ။');
    }));
    root.querySelectorAll('[data-file]').forEach(button=>button.addEventListener('click',()=>requestFileSelection(button.dataset.file)));
    const count=$('#page-nearby .heading-count'); if(count)count.textContent=String(simulatedBridge?0:[...connectedEndpoints.keys()].length);
    const connectedMetric=$('#physical-connected-count'); if(connectedMetric)connectedMetric.textContent=String(simulatedBridge?0:[...connectedEndpoints.keys()].length);
    const secureMetric=$('#active-session-count'); if(secureMetric)secureMetric.textContent=simulatedBridge?`0 real · ${[...secureSessions.values()].filter(s=>s.secure).length} simulated`:[...secureSessions.values()].filter(s=>s.secure).length;
    const groupMetric=$('#group-member-count'); if(groupMetric)groupMetric.textContent=String(simulatedBridge?0:new Set([...secureSessions.values()].filter(s=>s.secure).map(s=>s.remoteDeviceId)).size);
    const permissionMetric=$('#native-permission-status'); if(permissionMetric&&nativeAvailable)permissionMetric.textContent='OS runtime state';
    const sendFileButton=$('#send-file'); if(sendFileButton)sendFileButton.disabled=!nativeAvailable||![...secureSessions.values()].some(s=>s.secure);
  }
  function startNearby() {
    if(!detectNativeBridge()){toast('ဒီ web preview မှာ native Nearby မရရှိပါ။');return;}
    const button=$('#start-discovery');
    if(button.dataset.running==='true') {
      bridgeCall('stopAdvertising'); bridgeCall('stopDiscovery'); button.dataset.running='false';
      button.innerHTML='<svg><use href="#i-radio"/></svg> စက်ရှာရန်'; toast('Nearby discovery ကို ရပ်လိုက်ပါပြီ'); return;
    }
    const a=bridgeJson(bridgeCall('startAdvertising')),d=bridgeJson(bridgeCall('startDiscovery'));
    if((a&&!a.ok)||(d&&!d.ok)){toast('Nearby စတင်မရပါ။ Permission စစ်ဆေးပါ။');return;}
    button.dataset.radioNotice='';
    button.dataset.running='true'; button.innerHTML='<svg><use href="#i-radio"/></svg> ရှာဖွေမှု ရပ်ရန်';
    $('#native-state-detail').textContent='Advertising / discovery စတင်နေသည် · peer အရေအတွက်မှာ စမ်းသပ်မှုမဟုတ်ပါ';
  }
  function onNativeEvent(raw) {
    let event; try { event=typeof raw==='string'?JSON.parse(raw):raw; } catch { return; }
    if(!event||typeof event.type!=='string')return;
    if(event.type==='localAIImportResult'){
      refreshLocalAIStatus();
      if(event.ok)toast(`Local model ကို private storage ထဲထည့်ပြီးပါပြီ · ${safe(event.displayName||'model')} · ${formatBytes(event.sizeBytes)}`);
      else if(!event.cancelled)toast(`Model import မအောင်မြင်ပါ · ${safe(event.error||'MODEL_IMPORT_FAILED')}`);
      refreshNativeDiagnostics();return;
    }
    if(event.type==='localAIState'){
      refreshLocalAIStatus();
      const inferenceStatus=$('#ai-inference-status');
      if(inferenceStatus&&event.code==='GENERATION_DONE'){inferenceStatus.textContent='ACTUAL INFERENCE — EXECUTED ON DEVICE';inferenceStatus.className='ai-inference-status is-success';}
      else if(inferenceStatus&&event.code==='GENERATION_FAILED'){inferenceStatus.textContent='ACTUAL INFERENCE — FAILED';inferenceStatus.className='ai-inference-status is-failure';}
      else if(inferenceStatus&&event.code==='GENERATION_CANCELLED_MODEL_UNLOADED'){inferenceStatus.textContent='ACTUAL INFERENCE — CANCELLED';inferenceStatus.className='ai-inference-status';}
      else if(inferenceStatus&&event.code==='GENERATION_STARTED'){inferenceStatus.textContent='ACTUAL INFERENCE — RUNNING';inferenceStatus.className='ai-inference-status';}
      if(['MODEL_LOADED','GENERATION_DONE','GENERATION_CANCELLED_MODEL_UNLOADED'].includes(event.code))toast(safe(event.code));
      else if(event.code&&/FAILED|ERROR|LIMIT|UNAVAILABLE|INVALID/.test(event.code))toast(`Local AI · ${safe(event.code)}`);
      refreshNativeDiagnostics();return;
    }
    if(event.type==='localAIGenerationChunk'){
      const output=$('#ai-output');
      if(output){if(event.replace)output.textContent=String(event.text||'');else output.textContent+=String(event.text||'');}
      return;
    }
    if(event.type==='attachmentList') { renderAttachmentList(event); refreshNativeDiagnostics(); return; }
    if(event.type==='attachmentAction') {
      if(event.ok&&event.action==='delete')toast('Attachment နှင့်သက်ဆိုင်သော temporary copy များကို ဖျက်ပြီးပါပြီ');
      else if(event.ok)toast(event.action==='export'?'Attachment ကို ရွေးထားသောနေရာသို့ export လုပ်ပြီးပါပြီ':`${safe(event.filename||'Attachment')} ကို လုံခြုံစွာ ပြင်ဆင်ပြီးပါပြီ`);
      else toast(event.error==='EXPORT_CANCELLED'?'Export ကို ပယ်ဖျက်လိုက်သည်':`Attachment action မအောင်မြင်ပါ · ${safe(event.error||'UNKNOWN')}`);
      refreshAttachments(); return;
    }
    if(event.type==='attachmentMigrationStatus') {
      const remaining=Math.max(0,Number(event.remaining)||0), warning=$('#attachment-migration-warning');
      warning.hidden=remaining===0;$('#migration-remaining-count').textContent=String(remaining);
      const health=$('#migration-health');if(health)health.textContent=remaining?`${remaining} legacy plaintext item(s) need retry`:'No legacy plaintext items pending';
      toast(remaining?`Migration မအောင်မြင်သေးသောဖိုင် ${remaining} ခုကို မဖျက်ဘဲထားသည်`:`Legacy attachment ${Number(event.migrated)||0} ခုကို encrypt လုပ်ပြီးပါပြီ`);
      refreshAttachments();refreshNativeDiagnostics();return;
    }
    if(event.type==='qrPairing') {
      if(event.ok){expectedQrIdentity={identityRef:String(event.identityRef||''),expiresAt:Number(event.expiresAt)||0};renderNearbyDevices();toast(`QR identity ${safe(expectedQrIdentity.identityRef)} မှန်ကန်သည်။ Nearby စက်အမည်ကိုက်ညီမှု၊ ထို့နောက် နှစ်ဖက် fingerprint ကို စစ်ဆေးပါ။`);}
      else if(event.error!=='QR_SCAN_CANCELLED')toast(`QR pairing မအောင်မြင်ပါ · ${safe(event.error||'INVALID_QR')}`);
      return;
    }
    if(event.type==='deviceFound') { if(event.endpointId)foundDevices.set(event.endpointId,event.endpointName||'Nearby device'); renderNearbyDevices(); return; }
    if(event.type==='deviceLost') { foundDevices.delete(event.endpointId); renderNearbyDevices(); return; }
    if(event.type==='connectionInitiated') {
      foundDevices.set(event.endpointId,event.endpointName||'Nearby device'); renderNearbyDevices();
      if(expectedQrIdentity&&expectedQrIdentity.expiresAt>Date.now()&&event.endpointName!==expectedQrIdentity.identityRef){bridgeCall('rejectConnection',event.endpointId);toast('Nearby device သည် scan လုပ်ထားသော QR identity နှင့် မကိုက်ညီပါ');return;}
      const code=String(event.authenticationDigits||'').trim();
      const approved=confirm(state.language==='en'?`Android Nearby verification code: ${code}\n\nCompare it with the code on the other phone. Confirm only if they match.`:`Android Nearby verification code: ${code}\n\nအခြားဖုန်းပေါ်ရှိ code နှင့် ကိုက်ညီမှုကို နှိုင်းယှဉ်ပါ။ တူညီမှသာ ချိတ်ဆက်မှုကို အတည်ပြုပါ။`);
      bridgeCall(approved?'acceptConnection':'rejectConnection',event.endpointId);
      if(!approved)toast('Pairing ကို ပယ်ဖျက်လိုက်ပါပြီ'); return;
    }
    if(event.type==='connectionEstablished') {
      connectedEndpoints.set(event.endpointId,event.endpointName||'Nearby device'); renderNearbyDevices();
      startSecureHandshake(event.endpointId,event.endpointName||'Nearby device').catch(()=>rejectSecureSession(event.endpointId)); return;
    }
    if(event.type==='connectionFailed') { connectedEndpoints.delete(event.endpointId); secureSessions.delete(event.endpointId); renderNearbyDevices(); toast('Nearby pairing မအောင်မြင်ပါ'); return; }
    if(event.type==='disconnected') { connectedEndpoints.delete(event.endpointId); secureSessions.delete(event.endpointId); renderNearbyDevices(); toast('Nearby connection ပြတ်တောက်သွားသည် · မပို့ရသေးသောစာများ queue ထဲမှာ ရှိနေသည်'); return; }
    if(event.type==='payload') { handleNativePayload(event.endpointId,event.data).catch(error=>{if(['FILE_DECLINED','GROUP_INVITATION_DECLINED'].includes(error?.message))return;rejectPacket();if(!secureSessions.get(event.endpointId)?.secure)rejectSecureSession(event.endpointId);}); return; }
    if(event.type==='filePicked') { handleFilePicked(event).catch(()=>failFileTransfer(event.transferId,'ဖိုင်ကို encrypt လုပ်မရပါ',event.endpointId)); return; }
    if(event.type==='filePayloadReady') { finishIncomingFile(event).catch(()=>failFileTransfer(event.transferId,'ဖိုင်၏ integrity စစ်ဆေးမှု မအောင်မြင်ပါ',event.endpointId)); return; }
    if(event.type==='fileTransferUpdate') { renderFileTransfer(event); return; }
    if(event.type==='attachmentMigrationWarning') {
      const count=Math.max(0,Number(event.remaining)||0);
      $('#attachment-migration-warning').hidden=count===0;$('#migration-remaining-count').textContent=String(count);
      toast(`ယခင် version မှ attachment ${count} ခုကို encrypt ပြောင်းမရသေးပါ — app-private storage ထဲ plaintext ကျန်နိုင်သည်`); return;
    }
    if(event.type==='fileReceived') {
      state.completedFileTransfers=[...new Set([...(state.completedFileTransfers||[]),event.transferId])].slice(-500);
      incomingFileTransfers.delete(event.transferId); persist();
      renderFileTransfer({transferId:event.transferId,direction:'receive',filename:event.filename,bytesTransferred:event.size,totalBytes:event.size,status:'SUCCESS'});
      refreshAttachments();toast(`Integrity စစ်ဆေးပြီး attachment ကို Keystore ဖြင့် encrypted သိမ်းထားသည် · ${safe(event.filename)}`); return;
    }
    if(event.type==='fileTransferError') { failFileTransfer(event.transferId,event.code||'FILE_TRANSFER_FAILED',event.endpointId); return; }
    if(event.type==='filePickCancelled') { toast('ဖိုင်ရွေးချယ်မှုကို ပယ်ဖျက်လိုက်သည်'); return; }
    if(event.type==='radioDisabled') {
      const button=$('#start-discovery');button.dataset.running='false';button.innerHTML='<svg><use href="#i-radio"/></svg> စက်ရှာရန်';
      $('#native-state-detail').textContent='Wi-Fi သို့မဟုတ် Bluetooth ကို ဖုန်း Settings မှ ကိုယ်တိုင်ဖွင့်ပြီး ထပ်ကြိုးစားပါ။';
      if(button.dataset.radioNotice!=='shown'){toast('Wi-Fi သို့မဟုတ် Bluetooth ကို ဖုန်း Settings မှ ဖွင့်ပါ။');button.dataset.radioNotice='shown';}return;
    }
    if(event.type==='transportError') { const code=safe(event.code||'TRANSPORT_ERROR'); $('#native-state-detail').textContent=`Nearby error: ${code}`;if(['ADVERTISE_FAILED','DISCOVERY_FAILED','PERMISSION_DENIED','PLAY_SERVICES_UNAVAILABLE'].includes(event.code)){const button=$('#start-discovery');button.dataset.running='false';button.innerHTML='<svg><use href="#i-radio"/></svg> စက်ရှာရန်';}toast(`Nearby transport ပြဿနာ — ${code}`); return; }
    if(event.type==='sendFailed') {
      const peer=secureSessions.get(event.endpointId)?.remoteDeviceId;
      for(const message of state.messages)if(peer&&Array.isArray(message.sentTo)&&message.sentTo.includes(peer)){message.sentTo=message.sentTo.filter(id=>id!==peer);message.status='queued-local';}
      persist();renderMessages();toast('Nearby payload ပို့မအောင်မြင်ပါ · စာများကို queue ထဲပြန်ထားသည်');return;
    }
    if(event.type==='permissionChanged') { $('#native-state-detail').textContent=event.granted?'Nearby permissions ရရှိသည် · radio ချိတ်ဆက်မှု မစမ်းရသေး':'Nearby permission မရရှိပါ'; const metric=$('#native-permission-status');if(metric)metric.textContent=event.granted?'Granted':'Denied';if(!event.granted){const button=$('#start-discovery');button.dataset.running='false';button.innerHTML='<svg><use href="#i-radio"/></svg> စက်ရှာရန်';}return; }
    if(event.type==='status') { const c=Array.isArray(event.connected)?event.connected:[]; const live=new Set(c.map(p=>p.endpointId)); for(const id of connectedEndpoints.keys())if(!live.has(id)){connectedEndpoints.delete(id);secureSessions.delete(id);} c.forEach(p=>{connectedEndpoints.set(p.endpointId,p.endpointName||'Nearby device');}); renderNearbyDevices(); refreshNativeDiagnostics(); }
  }
  window.NexusNativeNearbyEvent=onNativeEvent;
  function ensureSession(endpointId) {
    let session=secureSessions.get(endpointId);
    if(!session){session={endpointId,secure:false,localApproved:false,remoteApproved:false,seenIvs:new Set()};secureSessions.set(endpointId,session);}
    return session;
  }
  async function startSecureHandshake(endpointId,endpointName) {
    const session=ensureSession(endpointId); if(session.started)return; session.started=true; session.endpointName=endpointName;
    if(!crypto?.subtle)throw new Error('WebCrypto unavailable');
    session.keyPair=await crypto.subtle.generateKey({name:'ECDH',namedCurve:'P-256'},true,['deriveBits']);
    session.nonce=crypto.getRandomValues(new Uint8Array(16));
    session.localHello={type:'hello',version:1,deviceId:state.deviceId,deviceName:state.name||'NEXUS',publicKey:await crypto.subtle.exportKey('jwk',session.keyPair.publicKey),nonce:toB64(session.nonce)};
    const result=bridgeJson(bridgeCall('send',endpointId,JSON.stringify(session.localHello)));
    if(!result?.ok)throw new Error('Hello could not be sent');
    if(session.remoteHello)await finishHandshake(endpointId,session);
  }
  async function finishHandshake(endpointId,session) {
    if(!session.localHello||!session.remoteHello||session.key||session.building)return;
    session.building=true;
    const remote=session.remoteHello;
    if(typeof remote.deviceId!=='string'||!/^[-A-Za-z0-9_]{2,40}$/.test(remote.deviceId)||!remote.publicKey||typeof remote.nonce!=='string')throw new Error('Malformed identity');
    const remoteNonce=fromB64(remote.nonce); if(remoteNonce.length!==16)throw new Error('Bad nonce');
    const imported=await crypto.subtle.importKey('jwk',remote.publicKey,{name:'ECDH',namedCurve:'P-256'},false,[]);
    const shared=await crypto.subtle.deriveBits({name:'ECDH',public:imported},session.keyPair.privateKey,256);
    const orderedIds=[state.deviceId,remote.deviceId].sort();
    const localFirst=String(state.deviceId)<=String(remote.deviceId);
    const orderedNonces=localFirst?[session.nonce,remoteNonce]:[remoteNonce,session.nonce];
    const orderedPubs=localFirst?[session.localHello.publicKey,remote.publicKey]:[remote.publicKey,session.localHello.publicKey];
    const pubText=orderedPubs.map(k=>`${k.x}.${k.y}`).join('|');
    const salt=new Uint8Array(32); salt.set(orderedNonces[0]); salt.set(orderedNonces[1],16);
    const material=await crypto.subtle.importKey('raw',shared,'HKDF',false,['deriveKey']);
    session.key=await crypto.subtle.deriveKey({name:'HKDF',hash:'SHA-256',salt,info:utf8(`NEXUS-OFFLINE-P2P-v1|${orderedIds.join('|')}|${pubText}`)},material,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
    const digest=new Uint8Array(await crypto.subtle.digest('SHA-256',utf8(`${orderedIds.join('|')}|${pubText}`)));
    const shortCode=String((((digest[0]<<16)|(digest[1]<<8)|digest[2])%1000000)).padStart(6,'0');
    session.remoteDeviceId=remote.deviceId; session.remoteName=String(remote.deviceName||remote.deviceId).slice(0,32); session.building=false;
    const approved=confirm(`Secure key fingerprint: ${shortCode}\n\n${safe(session.remoteName)} (${safe(remote.deviceId)}) နှင့် အခြားဖုန်းပေါ်က fingerprint ကို နှိုင်းယှဉ်ပါ။ တူညီမှသာ အတည်ပြုပါ။`);
    if(!approved){rejectSecureSession(endpointId);return;}
    session.localApproved=true;
    await sendEncryptedFrame(endpointId,{type:'pair-approved',deviceId:state.deviceId},true);
    maybeActivateSession(endpointId,session);
  }
  async function sendEncryptedFrame(endpointId,frame,handshake=false) {
    const session=secureSessions.get(endpointId); if(!session?.key||(!session.secure&&!handshake))return false;
    const iv=crypto.getRandomValues(new Uint8Array(12));
    const cipher=await crypto.subtle.encrypt({name:'AES-GCM',iv},session.key,utf8(JSON.stringify(frame)));
    const wire=JSON.stringify({type:'ciphertext-v1',iv:toB64(iv),ciphertext:toB64(cipher)});
    return !!bridgeJson(bridgeCall('send',endpointId,wire))?.ok;
  }
  async function acceptFileManifest(endpointId,session,data) {
    const validId=typeof data.transferId==='string'&&/^[a-f0-9]{32}$/i.test(data.transferId);
    const filename=typeof data.filename==='string'?sanitizeFilename(data.filename):'';
    const size=Number(data.size), hash=String(data.sha256||'').toLowerCase();
    if(!validId||data.senderId!==session.remoteDeviceId||!filename||filename!==data.filename||!Number.isSafeInteger(size)||size<1||size>256*1024*1024||! /^[a-f0-9]{64}$/.test(hash))throw new Error('Bad file manifest');
    if((state.completedFileTransfers||[]).includes(data.transferId)||incomingFileTransfers.has(data.transferId))throw new Error('Duplicate file transfer');
    if(!confirm(`Encrypted file request\n\n${filename}\n${size.toLocaleString()} bytes\n\nဤဖိုင်ကို လက်ခံသိမ်းဆည်းမလား?`))throw new Error('FILE_DECLINED');
    const fileIv=fromB64(String(data.fileIv||'')),wrapIv=fromB64(String(data.wrapIv||'')),wrapped=fromB64(String(data.wrappedKey||''));
    if(fileIv.length!==12||wrapIv.length!==12||wrapped.length!==48)throw new Error('Bad wrapped file key');
    const clearKey=new Uint8Array(await crypto.subtle.decrypt({name:'AES-GCM',iv:wrapIv},session.key,wrapped));
    if(clearKey.length!==32)throw new Error('Bad file key size');
    const item={endpointId,transferId:data.transferId,filename,size,sha256:hash,fileIv:toB64(fileIv),key:toB64(clearKey),payloadId:null};
    clearKey.fill(0);
    const accepted=bridgeJson(bridgeCall('expectIncomingFile',endpointId,item.transferId,item.filename,item.size,item.sha256));
    if(!accepted?.ok)throw new Error('Native receiver rejected manifest');
    incomingFileTransfers.set(item.transferId,item);
    if(!await sendEncryptedFrame(endpointId,{type:'file-ready',transferId:item.transferId,senderId:state.deviceId})) {
      bridgeCall('cancelFileTransfer',item.transferId);incomingFileTransfers.delete(item.transferId);throw new Error('File ready acknowledgement failed');
    }
    renderFileTransfer({transferId:item.transferId,direction:'receive',filename:item.filename,bytesTransferred:0,totalBytes:item.size,status:'WAITING'});
  }
  async function handleFilePicked(event) {
    const {transferId,endpointId,filename}=event,size=Number(event.size),session=secureSessions.get(endpointId);
    if(!session?.secure||! /^[a-f0-9]{32}$/i.test(String(transferId))||!Number.isSafeInteger(size)||size<1||size>256*1024*1024) {
      bridgeCall('cancelFileTransfer',String(transferId||''));throw new Error('File or secure session unavailable');
    }
    const rawKey=crypto.getRandomValues(new Uint8Array(32)),fileIv=crypto.getRandomValues(new Uint8Array(12)),wrapIv=crypto.getRandomValues(new Uint8Array(12));
    const wrapped=new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv:wrapIv},session.key,rawKey));
    const prepared=bridgeJson(bridgeCall('prepareOutgoingFile',transferId,toB64(rawKey),toB64(fileIv)));
    rawKey.fill(0);
    if(!prepared?.ok||prepared.filename!==filename||Number(prepared.size)!==size||! /^[a-f0-9]{64}$/i.test(String(prepared.sha256||''))) {
      bridgeCall('cancelFileTransfer',transferId);throw new Error('File encryption or metadata check failed');
    }
    const item={transferId,endpointId,filename,size,sha256:String(prepared.sha256).toLowerCase(),fileIv:toB64(fileIv),payloadId:null};
    activeFileTransfers.set(transferId,item);
    const sent=await sendEncryptedFrame(endpointId,{type:'file-manifest',transferId,senderId:state.deviceId,filename,size,sha256:item.sha256,fileIv:item.fileIv,wrapIv:toB64(wrapIv),wrappedKey:toB64(wrapped)});
    fileIv.fill(0);wrapIv.fill(0);wrapped.fill(0);
    if(!sent){activeFileTransfers.delete(transferId);bridgeCall('cancelFileTransfer',transferId);throw new Error('Encrypted file manifest send failed');}
    renderFileTransfer({transferId,direction:'send',filename,bytesTransferred:0,totalBytes:size,status:'WAITING'});
  }
  function requestFileSelection(endpointId) {
    const session=[...secureSessions.values()].find(item=>item.endpointId===endpointId&&item.secure);
    if(!session){toast('ဖိုင်ပို့ရန် အတည်ပြုထားသော secure session လိုအပ်သည်');return;}
    const result=bridgeJson(bridgeCall('pickAndSendFile',endpointId));
    if(!result?.ok)toast(result?.error==='FILE_TRANSFER_BUSY'?'အခြားဖိုင်လွှဲပြောင်းမှု ပြီးမှ ထပ်ကြိုးစားပါ':'ဖိုင်ရွေးချယ်မှု စတင်မရပါ');
  }
  function renderFileTransfer(event) {
    const panel=$('#file-transfer-status');if(!panel)return;
    const item=activeFileTransfers.get(event.transferId)||incomingFileTransfers.get(event.transferId);
    const filename=event.filename||item?.filename||'File transfer';
    const total=Number(event.totalBytes)||Number(item?.size)||0,done=Number(event.bytesTransferred)||0;
    const percent=total?Math.min(100,Math.round(done/total*100)):0;
    const successText=event.direction==='receive'?'Encrypted file ရောက်ရှိပြီး integrity စစ်ဆေးနေသည်':'ဖိုင်ကို ပို့ပြီးပါပြီ';
    const states={WAITING:'လက်ခံသူ၏ အတည်ပြုချက်ကို စောင့်နေသည်',IN_PROGRESS:`လွှဲပြောင်းနေသည် · ${percent}%`,SUCCESS:successText,FAILURE:'လွှဲပြောင်းမှု မအောင်မြင်ပါ',CANCELED:'လွှဲပြောင်းမှုကို ရပ်လိုက်သည်'};
    panel.hidden=false;$('#file-transfer-text').textContent=`${filename} · ${states[event.status]||'ဖိုင်အခြေအနေ စစ်ဆေးနေသည်'}`;
    $('#file-transfer-progress').value=percent;
    const cancel=$('#cancel-file-transfer');cancel.disabled=['SUCCESS','FAILURE','CANCELED'].includes(event.status);cancel.dataset.transferId=event.transferId||'';
    const endpointId=event.endpointId||item?.endpointId||'';
    const retry=$('#retry-file-transfer');retry.hidden=!['FAILURE','CANCELED'].includes(event.status)||event.direction==='receive'||!secureSessions.get(endpointId)?.secure;retry.dataset.endpointId=endpointId;
    if(['SUCCESS','FAILURE','CANCELED'].includes(event.status)&&event.direction==='send')activeFileTransfers.delete(event.transferId);
  }
  function failFileTransfer(transferId,message,endpointId='') {
    const wasSending=activeFileTransfers.has(String(transferId));
    const item=activeFileTransfers.get(String(transferId))||incomingFileTransfers.get(String(transferId));
    endpointId=endpointId||item?.endpointId||'';
    if(transferId) { bridgeCall('cancelFileTransfer',String(transferId));activeFileTransfers.delete(String(transferId));incomingFileTransfers.delete(String(transferId)); }
    const panel=$('#file-transfer-status');if(panel){panel.hidden=false;$('#file-transfer-text').textContent=`${message} · အန္တရာယ်မရှိဘဲ ရပ်ထားသည်`;$('#file-transfer-progress').value=0;$('#cancel-file-transfer').disabled=true;}
    renderFileTransfer({transferId,direction:wasSending||!item?'send':'receive',endpointId,filename:item?.filename,status:'FAILURE'});
    toast(message);
  }
  async function finishIncomingFile(event) {
    const item=incomingFileTransfers.get(event.transferId);
    if(!item||item.endpointId!==event.endpointId||String(event.payloadId).length>32)throw new Error('Unexpected incoming file');
    item.payloadId=String(event.payloadId);
    const result=bridgeJson(bridgeCall('decryptIncomingFile',item.payloadId,item.transferId,item.key,item.fileIv,item.filename,item.size,item.sha256));
    if(!result?.ok)throw new Error(result?.error||'File authentication failed');
    item.key='';
  }
  function maybeActivateSession(endpointId,session) {
    if(!session.localApproved||!session.remoteApproved||session.secure)return;
    session.secure=true;
    if(expectedQrIdentity&&session.endpointName===expectedQrIdentity.identityRef)expectedQrIdentity=null;
    const id=session.remoteDeviceId, threadId=`peer:${id}`;
    if(!state.threads.some(t=>t.id===threadId))state.threads.unshift({id:threadId,name:session.remoteName||id,kind:'peer',peerId:id});
    renderThreads($('#chat-search').value); renderNearbyDevices(); persist();
    $('#native-state-detail').textContent='Pairing code/fingerprint နှစ်ဖက်အတည်ပြုပြီး · ECDH P-256 / AES-GCM session active';
    $('#retry-status').textContent='Secure Nearby session ရရှိသည် · queue retry လုပ်နေသည်';
    toast(`${session.remoteName||id} နှင့် encrypted session အတည်ပြုပြီးပါပြီ`);
    syncGroupMembershipForPeer(id,endpointId).catch(()=>{});
    drainQueuedMessages(endpointId).catch(()=>{});
  }
  function rejectSecureSession(endpointId) { bridgeCall('disconnect',endpointId); connectedEndpoints.delete(endpointId); secureSessions.delete(endpointId); renderNearbyDevices(); }
  function rejectPacket() { state.rejectedCount++; persist(); const metric=$('#rejected-count');if(metric)metric.textContent=`${Number(state.duplicateCount||0)} / ${Number(state.rejectedCount||0)}`; }
  function groupEnvelopeError(data,remoteSender) {
    if(!/^[a-f0-9]{32}$/i.test(String(data.groupId||'')))return 'BAD_GROUP_ID';
    if(typeof data.id!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(data.id))return 'BAD_MESSAGE_ID';
    if(!validMemberId(data.ownerId)||!validMemberId(data.originId)||!validMemberId(data.senderId)||data.senderId!==remoteSender)return 'BAD_GROUP_SENDER';
    if(!Number.isSafeInteger(data.membershipVersion)||data.membershipVersion<1)return 'BAD_MEMBERSHIP_VERSION';
    if(!Array.isArray(data.memberIds)||data.memberIds.length<1||data.memberIds.length>MAX_GROUP_MEMBERS||data.memberIds.some(id=>!validMemberId(id))||new Set(data.memberIds).size!==data.memberIds.length)return 'BAD_MEMBER_LIST';
    if(!data.memberIds.includes(data.ownerId)||!data.memberIds.includes(data.originId)||!data.memberIds.includes(data.senderId)||!data.memberIds.includes(state.deviceId))return 'GROUP_MEMBERSHIP_MISMATCH';
    if(!Number.isInteger(data.ttlRemaining)||data.ttlRemaining<0||data.ttlRemaining>MAX_GROUP_TTL)return 'BAD_GROUP_TTL';
    if(!Array.isArray(data.hopPath)||data.hopPath.length<1||data.hopPath.length>MAX_GROUP_TTL+1||data.hopPath.some(id=>!data.memberIds.includes(id))||new Set(data.hopPath).size!==data.hopPath.length||data.hopPath[0]!==data.originId||data.hopPath.at(-1)!==data.senderId||data.hopPath.includes(state.deviceId))return 'GROUP_ROUTE_LOOP_OR_MISMATCH';
    return null;
  }
  function groupForwardTargets(memberIds,hopPath,localId,connectedIds,ttl=MAX_GROUP_TTL) {
    if(ttl<=0||ttl>MAX_GROUP_TTL||hopPath.includes(localId)||hopPath.length>MAX_GROUP_TTL)return [];
    return [...new Set(connectedIds)].filter(id=>memberIds.includes(id)&&id!==localId&&!hopPath.includes(id));
  }
  async function relayGroupMessage(incomingEndpointId,data) {
    if(data.ttlRemaining<=0||data.hopPath.length>=MAX_GROUP_TTL+1)return;
    const targets=groupForwardTargets(data.memberIds,data.hopPath,state.deviceId,[...secureSessions.values()].filter(s=>s.secure&&s.endpointId!==incomingEndpointId).map(s=>s.remoteDeviceId),data.ttlRemaining);
    const next={...data,senderId:state.deviceId,hopPath:[...data.hopPath,state.deviceId],ttlRemaining:data.ttlRemaining-1};
    for(const session of secureSessions.values())if(session.secure&&session.endpointId!==incomingEndpointId&&targets.includes(session.remoteDeviceId)){
      try{await sendEncryptedFrame(session.endpointId,next);}catch{}
    }
  }
  function removeGroupLocal(thread,reason='GROUP_LEFT') {
    if(!thread)return;
    thread.left=true;thread.leaveReason=reason;thread.members=(thread.members||[]).filter(id=>id!==state.deviceId);
    for(const message of state.messages)if(message.threadId===thread.id&&message.status==='queued-local')message.status='group-left';
    persist();renderThreads($('#chat-search').value);renderMessages();
  }
  async function broadcastGroupMembership(thread,memberIds) {
    const old=[...(thread.members||[])],next=[...new Set(memberIds)];
    if(next.length>MAX_GROUP_MEMBERS||!next.includes(state.deviceId)||!next.includes(thread.ownerId))return false;
    thread.members=next;thread.membershipVersion=(Number(thread.membershipVersion)||1)+1;thread.removedMemberIds=[...new Set([...(thread.removedMemberIds||[]),...old.filter(id=>!next.includes(id))])];thread.acknowledgedMembers=[state.deviceId];persist();renderMessages();
    const audience=new Set([...old,...next]);
    for(const session of secureSessions.values())if(session.secure&&audience.has(session.remoteDeviceId)){
      try{await sendEncryptedFrame(session.endpointId,{type:'group-membership',groupId:thread.groupId,groupName:thread.name,ownerId:state.deviceId,senderId:state.deviceId,membershipVersion:thread.membershipVersion,memberIds:next});}catch{}
    }
    return true;
  }
  async function syncGroupMembershipForPeer(remoteId,endpointId) {
    for(const thread of state.threads.filter(item=>item.kind==='group'&&item.ownerId===state.deviceId&&((item.members||[]).includes(remoteId)||(item.removedMemberIds||[]).includes(remoteId)))){
      try{await sendEncryptedFrame(endpointId,{type:'group-membership',groupId:thread.groupId,groupName:thread.name,ownerId:state.deviceId,senderId:state.deviceId,membershipVersion:thread.membershipVersion,memberIds:thread.members});}catch{}
    }
  }
  async function receiveGroupMembership(endpointId,session,data) {
    if(!/^[a-f0-9]{32}$/i.test(String(data.groupId||''))||data.ownerId!==session.remoteDeviceId||data.senderId!==session.remoteDeviceId||!Number.isSafeInteger(data.membershipVersion)||data.membershipVersion<1||!Array.isArray(data.memberIds)||data.memberIds.length<1||data.memberIds.length>MAX_GROUP_MEMBERS||data.memberIds.some(id=>!validMemberId(id))||new Set(data.memberIds).size!==data.memberIds.length||!data.memberIds.includes(data.ownerId))throw new Error('Invalid group membership update');
    const threadId=`group:${data.groupId}`,thread=state.threads.find(t=>t.id===threadId);
    if(thread&&thread.ownerId!==data.ownerId)throw new Error('GROUP_OWNER_MISMATCH');
    if(thread&&data.membershipVersion<Number(thread.membershipVersion||0))return;
    if(thread&&data.membershipVersion===Number(thread.membershipVersion||0)){
      if(JSON.stringify([...thread.members].sort())!==JSON.stringify([...data.memberIds].sort()))throw new Error('GROUP_ROSTER_EQUIVOCATION');
      if(data.memberIds.includes(state.deviceId))await sendEncryptedFrame(endpointId,{type:'group-membership-ack',groupId:data.groupId,membershipVersion:data.membershipVersion,senderId:state.deviceId});
      return;
    }
    if(data.memberIds.includes(state.deviceId)){
      if(!confirm(`Offline group invitation/update\n\n${safe(String(data.groupName||'Offline group').slice(0,40))}\nOwner: ${safe(data.ownerId)}\nMembers: ${data.memberIds.length}\n\nအဖွဲ့ဝင်အဖြစ် ဝင်မလား?`))return;
      const next=thread||{id:threadId,groupId:data.groupId,name:String(data.groupName||'Offline group').slice(0,40),kind:'group',ownerId:data.ownerId,members:[]};
      Object.assign(next,{ownerId:data.ownerId,membershipVersion:data.membershipVersion,members:[...data.memberIds],kind:'group',left:false});
      if(!thread)state.threads.unshift(next);persist();renderThreads($('#chat-search').value);renderMessages();
      await sendEncryptedFrame(endpointId,{type:'group-membership-ack',groupId:data.groupId,membershipVersion:data.membershipVersion,senderId:state.deviceId});
      toast('Group membership ကို လက်ခံပြီးပါပြီ');
    }else if(thread){
      Object.assign(thread,{membershipVersion:data.membershipVersion,members:[...data.memberIds]});
      removeGroupLocal(thread,'REMOVED_BY_OWNER');
      toast('Group owner က membership မှ ဖယ်ရှားလိုက်သည်');
    }
  }
  async function receiveGroupLeave(session,data) {
    if(data.senderId!==session.remoteDeviceId||data.ownerId!==state.deviceId||!/^[a-f0-9]{32}$/i.test(String(data.groupId||'')))throw new Error('Invalid group leave');
    const thread=state.threads.find(t=>t.id===`group:${data.groupId}`);if(!thread||thread.ownerId!==state.deviceId||!(thread.members||[]).includes(data.senderId))return;
    await broadcastGroupMembership(thread,thread.members.filter(id=>id!==data.senderId));toast(`${safe(data.senderId)} group မှ ထွက်သွားသည်`);
  }
  async function handleNativePayload(endpointId,raw) {
    const frame=JSON.parse(String(raw)); if(!frame||typeof frame!=='object')throw new Error('Malformed packet');
    const session=ensureSession(endpointId);
    if(frame.type==='hello') {
      if(frame.version!==1||!frame.publicKey||typeof frame.deviceId!=='string')throw new Error('Bad hello');
      session.remoteHello=frame;
      if(!session.started)await startSecureHandshake(endpointId,connectedEndpoints.get(endpointId)||'Nearby device');
      await finishHandshake(endpointId,session); return;
    }
    if(frame.type!=='ciphertext-v1'||!session.key)throw new Error('Unexpected packet');
    const iv=fromB64(frame.iv),cipher=fromB64(frame.ciphertext); if(iv.length!==12||cipher.length>24*1024)throw new Error('Malformed ciphertext');
    const ivToken=String(frame.iv);if(session.seenIvs?.has(ivToken))throw new Error('Replayed encrypted frame');
    const clear=await crypto.subtle.decrypt({name:'AES-GCM',iv},session.key,cipher);
    session.seenIvs||=new Set();session.seenIvs.add(ivToken);if(session.seenIvs.size>4096)session.seenIvs.delete(session.seenIvs.values().next().value);
    const data=JSON.parse(new TextDecoder().decode(clear));
    if(data.type==='pair-approved') { if(data.deviceId!==session.remoteDeviceId)throw new Error('Identity mismatch'); session.remoteApproved=true; maybeActivateSession(endpointId,session); return; }
    if(!session.secure)throw new Error('Session not approved');
    if(data.type==='group-membership'){await receiveGroupMembership(endpointId,session,data);return;}
    if(data.type==='group-membership-ack'){
      if(data.senderId!==session.remoteDeviceId||!/^[a-f0-9]{32}$/i.test(String(data.groupId||''))||!Number.isSafeInteger(data.membershipVersion))throw new Error('Invalid group membership ack');
      const thread=state.threads.find(t=>t.id===`group:${data.groupId}`);
      if(thread?.ownerId===state.deviceId&&thread.membershipVersion===data.membershipVersion&&thread.members.includes(data.senderId)){
        thread.acknowledgedMembers=[...new Set([...(thread.acknowledgedMembers||[]),data.senderId])];persist();renderNearbyDevices();
      }return;
    }
    if(data.type==='group-leave'){await receiveGroupLeave(session,data);return;}
    if(data.type==='file-manifest') {
      try { await acceptFileManifest(endpointId,session,data); }
      catch(error) { if(error?.message==='FILE_DECLINED')toast('Incoming file ကို လက်မခံရန် ရွေးချယ်ခဲ့သည်');if(typeof data.transferId==='string'&&/^[a-f0-9]{32}$/i.test(data.transferId))await sendEncryptedFrame(endpointId,{type:'file-reject',transferId:data.transferId,senderId:state.deviceId});throw error; }
      return;
    }
    if(data.type==='file-ready') {
      const item=activeFileTransfers.get(data.transferId);
      if(!item||data.senderId!==session.remoteDeviceId||item.endpointId!==endpointId)throw new Error('Unexpected file-ready response');
      const result=bridgeJson(bridgeCall('sendPreparedFile',item.transferId));
      if(!result?.ok)throw new Error('Native file send could not start');
      item.payloadId=String(result.payloadId||'');renderFileTransfer({transferId:item.transferId,direction:'send',filename:item.filename,bytesTransferred:0,totalBytes:item.size,status:'IN_PROGRESS'});return;
    }
    if(data.type==='file-reject') { const item=activeFileTransfers.get(data.transferId);if(item&&item.endpointId===endpointId&&data.senderId===session.remoteDeviceId)failFileTransfer(item.transferId,'လက်ခံဘက်က ဖိုင် manifest ကို ပယ်ချလိုက်သည်');return; }
    if(data.type!=='message'||typeof data.id!=='string'||data.id.length>80||typeof data.text!=='string'||data.text.length>3000||data.senderId!==session.remoteDeviceId)throw new Error('Invalid message');
    const isGroup=data.groupId!==undefined&&data.groupId!==null;
    if(isGroup){const groupError=groupEnvelopeError(data,session.remoteDeviceId);if(groupError)throw new Error(groupError);}
    const messageTime=Date.parse(data.createdAt); if(!Number.isFinite(messageTime)||messageTime>Date.now()+5*60*1000||messageTime<Date.now()-180*24*60*60*1000)throw new Error('Stale message');
    if(state.messages.some(m=>m.id===data.id)){state.duplicateCount++;persist();const metric=$('#rejected-count');if(metric)metric.textContent=`${Number(state.duplicateCount||0)} / ${Number(state.rejectedCount||0)}`;return;}
    let threadId,groupThread=null;
    if(isGroup) {
      threadId=`group:${data.groupId}`;
      groupThread=state.threads.find(t=>t.id===threadId)||null;
      if(!groupThread){
        if(data.ownerId!==session.remoteDeviceId||data.originId!==session.remoteDeviceId||data.hopPath.length!==1)throw new Error('GROUP_INVITATION_REQUIRED');
        if(!confirm(`Offline group invitation\n\n${safe(String(data.groupName||'Offline group').slice(0,40))}\nOwner: ${safe(data.ownerId)}\nMembers: ${data.memberIds.length}\n\nအဖွဲ့ဝင်အဖြစ် ဝင်မလား?`))throw new Error('GROUP_INVITATION_DECLINED');
        groupThread={id:threadId,name:String(data.groupName||'Offline group').slice(0,40),kind:'group',groupId:data.groupId,ownerId:data.ownerId,membershipVersion:data.membershipVersion,members:[...data.memberIds],acknowledgedMembers:[state.deviceId]};
        state.threads.unshift(groupThread);
        await sendEncryptedFrame(endpointId,{type:'group-membership-ack',groupId:data.groupId,membershipVersion:data.membershipVersion,senderId:state.deviceId});
      }else{
        if(groupThread.left||groupThread.ownerId!==data.ownerId||Number(groupThread.membershipVersion)!==data.membershipVersion||JSON.stringify([...groupThread.members].sort())!==JSON.stringify([...data.memberIds].sort()))throw new Error('GROUP_MEMBERSHIP_MISMATCH');
      }
    } else {
      threadId=`peer:${data.senderId}`;
      if(!state.threads.some(t=>t.id===threadId))state.threads.unshift({id:threadId,name:String(data.senderName||data.senderId).slice(0,40),kind:'peer',peerId:data.senderId});
    }
    const currentThread=state.threads.find(t=>t.id===state.activeThread);
    if(!currentThread?.kind)state.activeThread=threadId;
    state.messages.push({id:data.id,threadId,text:data.text,createdAt:new Date(data.createdAt||Date.now()).toISOString(),status:'received-secure',senderId:isGroup?data.originId:data.senderId});
    renderThreads($('#chat-search').value); renderMessages(); persist();
    state.receivedCount=Number(state.receivedCount||0)+1; persist();
    const counter=$('#sent-count');if(counter)counter.textContent=`${Number(state.sentCount||0)} / ${Number(state.receivedCount||0)}`;
    toast('Encrypted Nearby စာတစ်စောင် လက်ခံရရှိသည်');
    if(isGroup)relayGroupMessage(endpointId,data).catch(()=>{});
  }
  async function dispatchMessage(message) {
    const thread=state.threads.find(t=>t.id===message.threadId); if(!nativeAvailable||!thread||thread.id==='local'||thread.left)return;
    const sessions=[...secureSessions.values()].filter(s=>s.secure&&s.remoteDeviceId);
    let targets=[];
    if(thread.kind==='peer')targets=sessions.filter(s=>s.remoteDeviceId===thread.peerId);
    else if(thread.kind==='group') {
      if(!Array.isArray(thread.members)||thread.members.length>MAX_GROUP_MEMBERS||!thread.members.includes(state.deviceId)||!thread.ownerId)return;
      targets=sessions.filter(s=>thread.members.includes(s.remoteDeviceId));
    }
    const sent=new Set(message.sentTo||[]);
    for(const session of targets) {
      if(sent.has(session.remoteDeviceId))continue;
      const packet=thread.kind==='group'?{type:'message',id:message.id,text:message.text,createdAt:message.createdAt,senderId:state.deviceId,senderName:state.name,groupId:thread.groupId,groupName:thread.name,ownerId:thread.ownerId,originId:state.deviceId,membershipVersion:thread.membershipVersion,memberIds:thread.members,ttlRemaining:MAX_GROUP_TTL,hopPath:[state.deviceId]}:{type:'message',id:message.id,text:message.text,createdAt:message.createdAt,senderId:state.deviceId,senderName:state.name,groupId:null};
      try { if(await sendEncryptedFrame(session.endpointId,packet)){sent.add(session.remoteDeviceId);message.sentTo=[...sent];} } catch {}
    }
    const expected=thread.kind==='group'?(thread.members||[]).filter(id=>id!==state.deviceId):[thread.peerId];
    const oldStatus=message.status;
    message.status=expected.length>0&&expected.every(id=>sent.has(id))?'sent-secure':'queued-local';
    if(message.status==='sent-secure'&&oldStatus!=='sent-secure')state.sentCount=Number(state.sentCount||0)+1;
    persist(); renderMessages();
    const counter=$('#sent-count'),rejectCounter=$('#rejected-count');
    if(counter)counter.textContent=`${Number(state.sentCount||0)} / ${Number(state.receivedCount||0)}`;
    if(rejectCounter)rejectCounter.textContent=`${Number(state.duplicateCount||0)} / ${Number(state.rejectedCount||0)}`;
  }
  async function drainQueuedMessages(endpointId) {
    const peer=secureSessions.get(endpointId)?.remoteDeviceId; if(!peer)return;
    for(const message of state.messages) {
      if(message.status==='queued-local'||message.status==='sent-secure') {
        const thread=state.threads.find(t=>t.id===message.threadId);
        if(!thread?.left&&(thread?.kind==='peer'&&thread.peerId===peer||thread?.kind==='group'&&thread.members?.includes(peer)))await dispatchMessage(message);
      }
    }
  }
  function createOfflineGroup() {
    const members=[...new Set([state.deviceId,...[...secureSessions.values()].filter(s=>s.secure).map(s=>s.remoteDeviceId)])].slice(0,MAX_GROUP_MEMBERS);
    const id=crypto.randomUUID().replace(/-/g,'').toLowerCase(); const thread={id:`group:${id}`,groupId:id,name:`Offline group ${state.threads.filter(t=>t.kind==='group').length+1}`,kind:'group',ownerId:state.deviceId,membershipVersion:1,members,acknowledgedMembers:[state.deviceId],left:false,removedMemberIds:[]};
    state.threads.unshift(thread); state.activeThread=thread.id; persist(); renderThreads($('#chat-search').value); renderMessages(); showPage('chats');
    toast(`Offline group draft ဖန်တီးပြီးပါပြီ · ${Math.max(0,members.length-1)} secure peer(s); အဖွဲ့ဝင်များကို ချိတ်ဆက်ပြီး Manage members မှ invite လုပ်ပါ`);
  }
  async function manageActiveGroup() {
    const thread=activeThread();if(thread.kind!=='group')return;
    if(thread.ownerId!==state.deviceId){
      if(!confirm(state.language==='en'?`Leave offline group “${thread.name}”?`:`Offline group “${safe(thread.name)}” မှ ထွက်ပါမလား?`))return;
      const owner=[...secureSessions.values()].find(session=>session.secure&&session.remoteDeviceId===thread.ownerId);
      if(owner)await sendEncryptedFrame(owner.endpointId,{type:'group-leave',groupId:thread.groupId,ownerId:thread.ownerId,senderId:state.deviceId});
      removeGroupLocal(thread,'LEFT_BY_USER');toast(owner?'Group owner ထံ leave request ပို့ပြီး group မှထွက်ပါပြီ':'Owner offline ဖြစ်သည် · ဤစက်တွင် group မှ ထွက်ထားသည်');return;
    }
    const connected=[...secureSessions.values()].filter(session=>session.secure&&validMemberId(session.remoteDeviceId)).map(session=>session.remoteDeviceId);
    const proposed=[...new Set([...(thread.members||[]),...connected])];
    if(proposed.length>MAX_GROUP_MEMBERS){toast(state.language==='en'?`Group member limit is ${MAX_GROUP_MEMBERS} · ${proposed.length} peers are connected`:`Group member limit ${MAX_GROUP_MEMBERS} ဖြစ်သည် · ${proposed.length} peers ရှိနေသည်`);return;}
    const peerCount=Math.max(0,proposed.length-1);
    if(!confirm(state.language==='en'?`Synchronize the roster with ${peerCount} secure peer(s)?\n\nPeers must approve the group invitation/update themselves.`:`လက်ရှိ secure peers ${peerCount} ဦးနှင့် roster ကို synchronize လုပ်မလား?\n\nPeer များက group invitation/update ကို ကိုယ်တိုင်အတည်ပြုရမည်။`))return;
    const ok=await broadcastGroupMembership(thread,proposed);if(ok)toast(`Group roster v${thread.membershipVersion} ကို secure peer များထံ ပို့ပြီးပါပြီ · acknowledgment ကို စောင့်နေသည်`);
  }
  function showRoomInfo() {
    const room=activeThread();
    const detail=room.kind==='group'?`${room.name} · owner ${room.ownerId||'unknown'} · ${room.members?.length||0} roster members · v${room.membershipVersion||1} · acknowledged ${room.acknowledgedMembers?.length||0}`:room.kind==='peer'?`${room.name} · peer ref ${room.peerId||'unknown'} · encrypted only when session is active`:'Local-only note · not sent to another device';
    $('#retry-status').textContent=detail;toast(detail);
  }
  const bytesToHex = bytes => [...new Uint8Array(bytes)].map(b=>b.toString(16).padStart(2,'0')).join('');
  async function deriveTestKey(privateKey, publicKey, salt) {
    const shared=await crypto.subtle.deriveBits({name:'ECDH',public:publicKey},privateKey,256);
    const material=await crypto.subtle.importKey('raw',shared,'HKDF',false,['deriveKey']);
    return crypto.subtle.deriveKey({name:'HKDF',hash:'SHA-256',salt,info:new TextEncoder().encode('NEXUS-OFFLINE diagnostic session v1')},material,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
  }
  function sanitizeFilename(input) {
    let leaf=String(input).replace(/\\/g,'/').split('/').pop()||'file';
    leaf=leaf.replace(/[\u0000-\u001f\u007f<>:"|?*]/g,'_').replace(/^\.+/,'').trim();
    return leaf.slice(0,160)||'file';
  }
  function routeTargets(members,sender,ttl=8) { return ttl>0 ? [...new Set(members)].filter(id=>id!==sender) : []; }
  function drawTests(results) {
    const list=$('#test-list');
    list.innerHTML=results.map(r=>`<div class="${r.pass?'pass':'fail'}"><span class="test-circle"></span><span>${safe(r.label)}</span><span class="test-state">${r.pass?'PASS':'FAIL'}</span></div>`).join('');
    const passed=results.filter(r=>r.pass).length;
    $('#logic-result').innerHTML=`<span class="status-dot ${passed===results.length?'':'red'}"></span><span>${passed}/${results.length} Web Logic checks ${passed===results.length?'PASS':'မအောင်မြင်'}</span>`;
    state.lastDiagnostics=new Date().toISOString(); persist();
    $('#last-run').innerHTML=`<svg><use href="#i-clock"/></svg> နောက်ဆုံးစမ်းသပ်ချိန် — ${timeLabel(state.lastDiagnostics)}`;
  }
  async function runDiagnostics() {
    const button=$('#run-tests'); button.disabled=true; button.innerHTML='<svg><use href="#i-refresh"/></svg> စမ်းသပ်နေသည်…';
    const results=[];
    const check=async(label,fn)=>{try{results.push({label,pass:!!(await fn())});}catch{results.push({label,pass:false});}};
    await check('ECDH P-256 + HKDF + AES-GCM round-trip',async()=>{
      if(!crypto?.subtle) return false;
      const a=await crypto.subtle.generateKey({name:'ECDH',namedCurve:'P-256'},false,['deriveBits']);
      const b=await crypto.subtle.generateKey({name:'ECDH',namedCurve:'P-256'},false,['deriveBits']);
      const salt=crypto.getRandomValues(new Uint8Array(16));
      const aKey=await deriveTestKey(a.privateKey,b.publicKey,salt);
      const bKey=await deriveTestKey(b.privateKey,a.publicKey,salt);
      const plain=new TextEncoder().encode('local cryptography diagnostic');
      const iv=crypto.getRandomValues(new Uint8Array(12));
      const cipher=await crypto.subtle.encrypt({name:'AES-GCM',iv},aKey,plain);
      const decoded=await crypto.subtle.decrypt({name:'AES-GCM',iv},bKey,cipher);
      return bytesToHex(decoded)===bytesToHex(plain)&&bytesToHex(cipher)!==bytesToHex(plain);
    });
    await check('AES-GCM tampered ciphertext rejection',async()=>{
      if(!crypto?.subtle) return false;
      const key=await crypto.subtle.generateKey({name:'AES-GCM',length:256},false,['encrypt','decrypt']);
      const iv=crypto.getRandomValues(new Uint8Array(12));
      const cipher=new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv},key,new TextEncoder().encode('integrity check')));
      cipher[cipher.length-1]^=1;
      try { await crypto.subtle.decrypt({name:'AES-GCM',iv},key,cipher); return false; } catch { return true; }
    });
    await check('Duplicate ID / replay guard logic',async()=>{
      const seen=new Set(); const accept=id=>{if(seen.has(id))return false;seen.add(id);return true;};
      return accept('msg-1')===true&&accept('msg-1')===false&&seen.size===1;
    });
    await check('10 / 12 member sender-exclusion routing',async()=>{
      const ten=Array.from({length:10},(_,i)=>`peer-${i+1}`), twelve=Array.from({length:12},(_,i)=>`peer-${i+1}`);
      const out10=routeTargets(ten,'peer-1'),out12=routeTargets(twelve,'peer-1');
      return out10.length===9&&out12.length===11&&!out12.includes('peer-1')&&routeTargets(twelve,'peer-1',0).length===0;
    });
    await check('Group envelope owner, identity, membership and route validation',async()=>{
      const remote='peer-remote',members=[state.deviceId,remote,'peer-next'];
      const packet={groupId:'a'.repeat(32),id:crypto.randomUUID(),ownerId:state.deviceId,originId:remote,senderId:remote,membershipVersion:1,memberIds:members,ttlRemaining:MAX_GROUP_TTL,hopPath:[remote]};
      return groupEnvelopeError(packet,remote)===null;
    });
    await check('Group malformed member lists and relay loops rejected',async()=>{
      const remote='peer-remote',members=[state.deviceId,remote,'peer-next'];
      const packet={groupId:'a'.repeat(32),id:crypto.randomUUID(),ownerId:state.deviceId,originId:remote,senderId:remote,membershipVersion:1,memberIds:members,ttlRemaining:MAX_GROUP_TTL,hopPath:[remote]};
      return groupEnvelopeError({...packet,memberIds:[...members,remote]},remote)==='BAD_MEMBER_LIST'&&groupEnvelopeError({...packet,hopPath:[remote,state.deviceId]},remote)==='GROUP_ROUTE_LOOP_OR_MISMATCH';
    });
    await check('Group relay excludes sender/visited peers and stops at TTL zero',async()=>{
      const members=['peer-a','peer-b','peer-c','peer-d'];
      const targets=groupForwardTargets(members,['peer-a','peer-b'],'peer-c',['peer-a','peer-b','peer-c','peer-d','outsider'],3);
      return targets.length===1&&targets[0]==='peer-d'&&groupForwardTargets(members,['peer-a'],'peer-c',['peer-d'],0).length===0;
    });
    await check('Queue state / file name validation',async()=>{
      const item={id:crypto.randomUUID(),status:'queued-local'};
      const unsafe=sanitizeFilename('../folder\\secret?.pdf');
      const maxBytes=256*1024*1024;
      return item.status==='queued-local'&&unsafe==='secret_.pdf'&&maxBytes===268435456;
    });
    await check('File-key wrap / unwrap and tamper rejection',async()=>{
      if(!crypto?.subtle)return false;
      const sessionKey=await crypto.subtle.generateKey({name:'AES-GCM',length:256},false,['encrypt','decrypt']);
      const fileKey=crypto.getRandomValues(new Uint8Array(32)),iv=crypto.getRandomValues(new Uint8Array(12));
      const wrapped=new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv},sessionKey,fileKey));
      const unwrapped=new Uint8Array(await crypto.subtle.decrypt({name:'AES-GCM',iv},sessionKey,wrapped));
      wrapped[0]^=1;let tamperRejected=false;try{await crypto.subtle.decrypt({name:'AES-GCM',iv},sessionKey,wrapped);}catch{tamperRejected=true;}
      const matches=bytesToHex(fileKey)===bytesToHex(unwrapped);fileKey.fill(0);unwrapped.fill(0);
      return matches&&tamperRejected;
    });
    drawTests(results);
    button.disabled=false; button.innerHTML='<svg><use href="#i-refresh"/></svg> Web Logic စမ်းသပ်ရန်';
    toast('Web Logic စမ်းသပ်မှု ပြီးပါပြီ။ Native/physical test မဟုတ်ပါ။');
  }
  function openConfirm(title,body,confirmText,handler) {
    $('#modal-title').textContent=title; $('#modal-body').textContent=body; $('#modal-confirm').textContent=confirmText;
    const modal=$('#modal-backdrop'),previous=document.activeElement; modal.hidden=false;
    const close=()=>{modal.hidden=true;$('#modal-confirm').onclick=null;if(previous instanceof HTMLElement)previous.focus({preventScroll:true});};
    $('#modal-close').onclick=close;$('#modal-cancel').onclick=close;modal.onclick=e=>{if(e.target===modal)close();};
    $('#modal-confirm').onclick=()=>{handler();close();};
    $('#modal-confirm').focus({preventScroll:true});
  }
  function init() {
    const languageSelect=$('#language-select');
    window.NexusI18n.start(state.language);
    if(languageSelect){
      languageSelect.value=state.language;
      languageSelect.addEventListener('change',()=>{
        state.language=languageSelect.value==='en'?'en':'my';
        window.NexusI18n.setLanguage(state.language);
        persist();
        const currentPage=$('.page.active')?.id.replace(/^page-/,'')||'home';
        $('#page-title').textContent=tr(PAGE_TITLES[currentPage]);
        document.title=`${tr(PAGE_TITLES[currentPage])} · NEXUS OFFLINE`;
        $('#today-date').textContent=new Intl.DateTimeFormat(state.language==='en'?'en-US':'my-MM',{weekday:'short',month:'short',day:'numeric'}).format(new Date());
        toast(tr('ဘာသာစကားကို ပြောင်းပြီးပါပြီ'));
      });
    }
    setProfile(); applyTheme(state.theme||'dark'); updateInternet(); detectNativeBridge(); renderThreads(); renderMessages(); updateStorageMeter();
    refreshAttachments();refreshNativeDiagnostics();refreshLocalAIStatus();
    if(legacyMigrationNotice)toast('အဟောင်း local data ကို Android Keystore အောက်ရှိ AES-GCM encrypted storage သို့ ပြောင်းရွှေ့ပြီးဖြစ်သည်');
    else if(storageMode==='memory')toast('Browser preview ဖြစ်သည် — data ကို tab ဖွင့်ထားသည့်အချိန်အတွင်းသာထားပြီး မသိမ်းပါ');
    else if(storageMode==='locked')toast('Android secure storage မရရှိပါ — ရှိပြီးသား data ကို မပြင်ဘဲထားသည်');
    const sentMetric=$('#sent-count'),rejectedMetric=$('#rejected-count');
    if(sentMetric)sentMetric.textContent=`${Number(state.sentCount||0)} / ${Number(state.receivedCount||0)}`;
    if(rejectedMetric)rejectedMetric.textContent=`${Number(state.duplicateCount||0)} / ${Number(state.rejectedCount||0)}`;
    $('#today-date').textContent=new Intl.DateTimeFormat(state.language==='en'?'en-US':'my-MM',{weekday:'short',month:'short',day:'numeric'}).format(new Date());
    if(state.lastDiagnostics) $('#last-run').innerHTML=`<svg><use href="#i-clock"/></svg> နောက်ဆုံးစမ်းသပ်ချိန် — ${timeLabel(state.lastDiagnostics)}`;
    document.addEventListener('click',e=>{const target=e.target instanceof Element?e.target.closest('[data-page]'):null;if(!target)return;if(target.matches('a'))e.preventDefault();showPage(target.dataset.page);});
    setSidebarOpen(false);
    $('#mobile-menu').addEventListener('click',()=>setSidebarOpen(!$('#sidebar').classList.contains('open')));
    $('#sidebar-scrim').addEventListener('click',()=>setSidebarOpen(false,true));
    window.addEventListener('resize',()=>setSidebarOpen($('#sidebar').classList.contains('open')));
    window.addEventListener('keydown',e=>{if(e.key!=='Escape')return;if(!$('#modal-backdrop').hidden){$('#modal-cancel').click();return;}if($('#sidebar').classList.contains('open'))setSidebarOpen(false,true);});
    window.addEventListener('popstate',syncPageFromLocation);window.addEventListener('hashchange',syncPageFromLocation);
    window.NexusHandleSystemBack=()=>{
      if(!$('#modal-backdrop').hidden){$('#modal-cancel').click();return true;}
      if($('#sidebar').classList.contains('open')){setSidebarOpen(false,true);return true;}
      const current=$('.page.active')?.id.replace(/^page-/,'');
      if(current&&current!=='home'&&history.length>1){history.back();return true;}
      return false;
    };
    $('#appearance-toggle').addEventListener('click',()=>applyTheme(state.theme==='light'?'dark':'light'));
    window.addEventListener('online',updateInternet); window.addEventListener('offline',updateInternet);
    $('#message-form').addEventListener('submit',async e=>{
      e.preventDefault(); const input=$('#message-input'),text=input.value.trim(); if(!text)return;
      state.messages.push({id:crypto.randomUUID(),threadId:state.activeThread,text,createdAt:new Date().toISOString(),status:'queued-local'});
      const message=state.messages[state.messages.length-1]; persist();input.value='';renderMessages();renderThreads($('#chat-search').value);
      const thread=state.threads.find(t=>t.id===message.threadId);
      if(thread?.left){message.status='group-left';toast('Group မှထွက်ထားသောကြောင့် မပို့ပါ');}
      else if(thread?.kind==='peer'||thread?.kind==='group') { await dispatchMessage(message); $('#retry-status').textContent=message.status==='sent-secure'?'Encrypted payload ကို Nearby transport သို့ပို့လိုက်သည် · delivery receipt မရှိ':'Secure peer မရရှိသေးပါ · စာကို local queue ထဲမှာထားသည်'; }
      else $('#retry-status').textContent='Local note သာဖြစ်သည် · အခြားစက်သို့ မပို့ပါ';
    });
    $('#new-local-chat').addEventListener('click',()=>{
      const name=prompt(tr('Local မှတ်စုခန်းအမည် ထည့်ပါ။ ဒီစက်ထဲမှာသာ သိမ်းမည်။'),tr('မှတ်စု')+' '+(state.threads.length+1));
      if(name===null)return;const clean=name.trim().slice(0,40);if(!clean){toast('အမည်တစ်ခု ထည့်ပါ။');return;}
      const thread={id:crypto.randomUUID(),name:clean};state.threads.unshift(thread);state.activeThread=thread.id;persist();renderThreads($('#chat-search').value);renderMessages();toast('Local မှတ်စုခန်း အသစ် ဖန်တီးပြီးပါပြီ');
    });
    $('#chat-search').addEventListener('input',e=>renderThreads(e.target.value));
    $('#retry-queue').addEventListener('click',async()=>{if(!detectNativeBridge()){refreshQueue();return;}for(const [endpointId,s] of secureSessions)if(s.secure)await drainQueuedMessages(endpointId);toast('Secure session ရှိသည့် queue များကို ပြန်စစ်ပြီးပါပြီ');});
    $('#check-ai').addEventListener('click',()=>{refreshLocalAIStatus();toast(tr('Local AI status ပြန်စစ်ပြီးပါပြီ'));});
    $('#import-ai-model').addEventListener('click',()=>{const result=bridgeJson(bridgeCall('importLocalAIModel',''));if(!result?.ok)toast(`${tr('Model import မစနိုင်ပါ')} · ${safe(result?.error||tr('Android app လိုအပ်သည်'))}`);else $('#ai-operation-status').textContent=tr('Android document picker တွင် local model ရွေးပါ');});
    $('#ai-model-select').addEventListener('change',()=>{refreshLocalAIStatus();$('#ai-compatibility-status').textContent=tr('Compatibility Check ကို model ရွေးပြီး လုပ်နိုင်သည်။');});
    $('#check-ai-compatibility').addEventListener('click',()=>{
      const modelId=$('#ai-model-select').value;if(!modelId)return;
      const report=bridgeJson(bridgeCall('checkLocalAIModelCompatibility',modelId));
      if(!report?.ok){$('#ai-compatibility-status').textContent=String(report?.error||'MODEL_NOT_INSTALLED');return;}
      const yes=tr('အောင်မြင်');const no=tr('မအောင်မြင်');const unknown=tr('မသိရှိပါ');
      const ram=report.ramEstimateFits==null?unknown:(report.ramEstimateFits?yes:no);
      const storage=report.storageSufficientForCopy==null?unknown:(report.storageSufficientForCopy?yes:no);
      const parts=[`${tr('Container')}: ${report.formatValidated?yes:no}`,`${tr('Runtime / ABI')}: ${report.runtimeAvailable&&report.abiSupported?yes:no}`,`${tr('Estimated RAM fits now')}: ${ram}`,`${tr('Free storage for another copy')}: ${storage}`,tr('Native load remains required; inference not proven')];
      $('#ai-compatibility-status').textContent=parts.join(' · ');
      $('#ai-info-compatibility').textContent=String(report.compatibility||'STRUCTURE_OK_RUNTIME_LOAD_REQUIRED');
    });
    $('#load-ai-model').addEventListener('click',()=>{const result=bridgeJson(bridgeCall('loadLocalAIModel',$('#ai-model-select').value));if(!result?.ok)toast(`${tr('Model load မစနိုင်ပါ')} · ${safe(result?.error||'UNKNOWN')}`);else $('#ai-operation-status').textContent=tr('Model ကို local runtime ထဲတွင် စတင် load လုပ်နေသည်…');});
    $('#unload-ai-model').addEventListener('click',()=>{const result=bridgeJson(bridgeCall('unloadLocalAIModel'));if(!result?.ok)toast(tr('Unload မလုပ်နိုင်ပါ'));else refreshLocalAIStatus();});
    $('#delete-ai-model').addEventListener('click',()=>{
      const modelId=$('#ai-model-select').value;if(!modelId)return;
      const modelName=$('#ai-model-select').selectedOptions[0]?.textContent||tr('Imported model');
      openConfirm(tr('Imported model ဖျက်မလား?'),`${tr('Private app storage မှ model file ကို အပြီးဖျက်မည်။')} ${modelName}`,tr('ဖျက်မည်'),()=>{
        const result=bridgeJson(bridgeCall('deleteLocalAIModel',modelId));
        if(!result?.ok)toast(`${tr('Model ဖျက်မရပါ')} · ${safe(result?.error||'MODEL_DELETE_FAILED')}`);
        else{refreshLocalAIStatus();toast(tr('Local model ကိုဖျက်ပြီးပါပြီ'));}
      });
    });
    $('#ai-prompt-form').addEventListener('submit',event=>{event.preventDefault();const prompt=$('#ai-prompt').value.trim();if(!prompt)return;$('#ai-output').textContent='';const result=bridgeJson(bridgeCall('generateLocalAI',prompt));if(!result?.ok)toast(`${tr('Local generation မစနိုင်ပါ')} · ${safe(result?.error||'MODEL_NOT_LOADED')}`);else{$('#ai-operation-status').textContent=tr('Loaded local model သို့ prompt ပို့လိုက်သည် — device ထဲတွင်သာ');const status=$('#ai-inference-status');if(status){status.textContent='ACTUAL INFERENCE — RUNNING';status.className='ai-inference-status';}}});
    $('#test-ai-inference').addEventListener('click',()=>{
      const prompt='Reply with one short sentence.';
      $('#ai-output').textContent='';
      const result=bridgeJson(bridgeCall('generateLocalAI',prompt));
      if(!result?.ok){$('#ai-operation-status').textContent=String(result?.error||'MODEL_NOT_LOADED');toast(tr('Local test inference မစနိုင်ပါ'));}
      else{$('#ai-operation-status').textContent=tr('Actual local inference စတင်ခဲ့သည်; native result ကိုစောင့်နေသည်');const status=$('#ai-inference-status');if(status){status.textContent='ACTUAL INFERENCE — RUNNING';status.className='ai-inference-status';}}
    });
    $('#cancel-ai').addEventListener('click',()=>{const result=bridgeJson(bridgeCall('cancelLocalAIGeneration'));if(!result?.ok)toast(tr('Generation cancel မလုပ်နိုင်ပါ'));else $('#ai-operation-status').textContent=tr('Generation ကိုရပ်ပြီး model ကို unload လုပ်နေသည်…');});
    $('#run-tests').addEventListener('click',runDiagnostics);
    $('#save-profile').addEventListener('click',()=>{const value=$('#display-name').value.trim().slice(0,32);if(!value){toast('အမည်တစ်ခု ထည့်ပါ။');return;}state.name=value;persist();setProfile();toast('Profile ကို ဒီစက်ထဲမှာ သိမ်းပြီးပါပြီ');});
    $$('.theme-option').forEach(button=>button.addEventListener('click',()=>applyTheme(button.dataset.theme)));
    $('#clear-data').addEventListener('click',()=>openConfirm('Local app data ဖျက်မလား?','Encrypted profile, chat history, local queue, imported Local AI model/cache နှင့် app-private received files များကို ဖျက်မည်။ ဤလုပ်ဆောင်ချက်ကို ပြန်မရနိုင်ပါ။','ဖျက်မည်',()=>{
      let cleared=true,clearError='';
      if(nativeBridge&&typeof nativeBridge.clearSecureState==='function'&&storageMode!=='memory'){const result=parseBridgeResult(nativeBridge.clearSecureState());cleared=!!result?.ok;clearError=result?.error||'';}
      else localStorage.removeItem(KEY);
      if(cleared){localStorage.removeItem(KEY);localStorage.removeItem('nexus-language-v1');}
      if(!cleared){storageMode='locked';storageIssue=clearError||'SECURE_STORAGE_CLEAR_FAILED';toast(`Local app data ကို မဖျက်နိုင်ပါ · ${safe(storageIssue)}`);updateStorageMeter();return;}
      for(const id of [...activeFileTransfers.keys(),...incomingFileTransfers.keys()])bridgeCall('cancelFileTransfer',id);
      activeFileTransfers.clear();incomingFileTransfers.clear();storageMode=nativeBridge?'android-keystore':'memory';storageIssue='';persistenceWarningShown=false;
      state=defaultState();persist();setProfile();renderThreads();renderMessages();refreshLocalAIStatus();updateStorageMeter();toast('Local app data နှင့် imported AI model များကို ဖျက်ပြီးပါပြီ');
    }));
    $('#start-discovery').addEventListener('click',startNearby);
    $('#create-group').addEventListener('click',createOfflineGroup);
    $('#group-action-button').addEventListener('click',manageActiveGroup);
    $('.room-info').addEventListener('click',showRoomInfo);
    $('#pair-qr').addEventListener('click',()=>{const result=bridgeJson(bridgeCall('createPairingQr'));if(!result?.ok)toast(`QR ပြုလုပ်မရပါ · ${result?.error||'Native Android လိုအပ်သည်'}`);});
    $('#scan-pair-qr').addEventListener('click',()=>{const result=bridgeJson(bridgeCall('scanPairingQr'));if(!result?.ok)toast(`QR scan မစတင်နိုင်ပါ · ${result?.error||'Native Android လိုအပ်သည်'}`);});
    $('#refresh-attachments').addEventListener('click',refreshAttachments);
    $('#retry-attachment-migration').addEventListener('click',()=>{const result=bridgeJson(bridgeCall('retryAttachmentMigration'));if(!result?.ok)toast('Legacy migration ပြန်မစနိုင်ပါ');else toast('Legacy plaintext attachment migration ကို ထပ်စစ်နေသည်');});
    $('#send-file').addEventListener('click',()=>{const session=[...secureSessions.values()].find(s=>s.secure);if(session)requestFileSelection(session.endpointId);else toast('ဖိုင်ပို့ရန် အတည်ပြုထားသော secure session လိုအပ်သည်');});
    $('#cancel-file-transfer').addEventListener('click',()=>{const id=$('#cancel-file-transfer').dataset.transferId;if(!id)return;const sending=activeFileTransfers.has(id),item=activeFileTransfers.get(id)||incomingFileTransfers.get(id);bridgeCall('cancelFileTransfer',id);activeFileTransfers.delete(id);incomingFileTransfers.delete(id);renderFileTransfer({transferId:id,endpointId:item?.endpointId,direction:sending?'send':'receive',filename:item?.filename,status:'CANCELED'});});
    $('#retry-file-transfer').addEventListener('click',()=>requestFileSelection($('#retry-file-transfer').dataset.endpointId));
    const hash=location.hash.slice(1);showPage(PAGE_TITLES[hash]?hash:'home',{historyMode:'none'});
    if('serviceWorker' in navigator && location.protocol.startsWith('http')) navigator.serviceWorker.register('./sw.js').catch(()=>{});
  }
  init();
})();
