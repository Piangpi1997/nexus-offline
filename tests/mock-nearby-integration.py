"""Mock-only browser integration checks for the JS secure-session protocol.

This harness does not test Android radios, Google Play Services, permissions,
Nearby Connections, real phones, or physical group capacity. It connects two
browser tabs through BroadcastChannel solely to exercise the web/native bridge
contract and encrypted message handlers.
Requires Python Playwright and a Chromium executable.
"""
from playwright.sync_api import sync_playwright
import os

URL = os.environ.get("NEXUS_TEST_URL", "http://localhost:4173/")
INIT = r"""
(() => {
  const phone = new URLSearchParams(location.search).get('phone') || 'A';
  const index = phone === 'A' ? 0 : 1;
  const endpointId = index === 0 ? 'peer-A' : 'peer-B';
  const originalGet = Storage.prototype.getItem;
  const originalSet = Storage.prototype.setItem;
  const originalRemove = Storage.prototype.removeItem;
  const mappedKey = key => key === 'nexus-offline-v1' && index === 1 ? 'nexus-offline-v1-phone-B' : key;
  Storage.prototype.getItem = function(key) { return originalGet.call(this, mappedKey(key)); };
  Storage.prototype.setItem = function(key, value) { return originalSet.call(this, mappedKey(key), value); };
  Storage.prototype.removeItem = function(key) { return originalRemove.call(this, mappedKey(key)); };
  window.__confirmHistory = [];
  window.confirm = message => { window.__confirmHistory.push(String(message)); return true; };
  window.__bridgeWires = [];
  window.__fileCalls = [];
  window.__mockSecureState = null;
  const bus = new BroadcastChannel('nexus-offline-mock-integration');
  window.__mockBus = bus;
  bus.onmessage = event => {
    if (event.data?.target !== index || event.data?.kind !== 'payload') return;
    if (typeof window.NexusNativeNearbyEvent === 'function') {
      window.NexusNativeNearbyEvent(JSON.stringify({ type: 'payload', endpointId, data: event.data.data }));
    }
  };
  const ok = () => JSON.stringify({ ok: true });
  window.NexusNativeNearby = {
    isMock: true,
    isAvailable: () => true,
    getStatus: () => JSON.stringify({ available: true, isMock: true, permissionGranted: true, connectedCount: 1 }),
    requestPermissions: ok,
    startAdvertising: ok,
    startDiscovery: ok,
    stopAdvertising: ok,
    stopDiscovery: ok,
    connect: () => ok(),
    acceptConnection: () => ok(),
    rejectConnection: () => ok(),
    disconnect: () => ok(),
    send: (_endpoint, data) => {
      window.__bridgeWires.push(String(data));
      bus.postMessage({ kind: 'payload', target: 1 - index, data: String(data) });
      return ok();
    },
    loadSecureState: () => JSON.stringify({ok:true,found:!!window.__mockSecureState,data:window.__mockSecureState}),
    saveSecureState: raw => { window.__mockSecureState=String(raw); return ok(); },
    clearSecureState: () => { window.__mockSecureState=null; return ok(); },
    getSecureStorageBytes: () => JSON.stringify({ok:true,bytes:window.__mockSecureState?.length||0}),
    pickAndSendFile: () => { window.__fileCalls.push('pickAndSendFile'); return ok(); },
    prepareOutgoingFile: transferId => { window.__fileCalls.push('prepareOutgoingFile'); return JSON.stringify({ok:true,transferId,filename:'report.pdf',size:123,sha256:'a'.repeat(64)}); },
    sendPreparedFile: transferId => { window.__fileCalls.push('sendPreparedFile'); return JSON.stringify({ok:true,transferId,payloadId:'9901'}); },
    expectIncomingFile: () => { window.__fileCalls.push('expectIncomingFile'); return ok(); },
    decryptIncomingFile: () => { window.__fileCalls.push('decryptIncomingFile'); return JSON.stringify({ok:true}); },
    cancelFileTransfer: () => { window.__fileCalls.push('cancelFileTransfer'); return ok(); },
    sendGroup: ok
  };
})();
"""

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path="/usr/bin/chromium", headless=True,
                                args=["--no-sandbox", "--disable-dev-shm-usage"])
    context = browser.new_context()
    context.add_init_script(INIT)
    errors = []
    a, b = context.new_page(), context.new_page()
    a.on("pageerror", lambda e: errors.append(f"A: {e}"))
    b.on("pageerror", lambda e: errors.append(f"B: {e}"))
    a.goto(URL + "?phone=A#nearby", wait_until="networkidle")
    b.goto(URL + "?phone=B#nearby", wait_until="networkidle")
    a.wait_for_function("typeof window.NexusNativeNearbyEvent === 'function'")
    b.wait_for_function("typeof window.NexusNativeNearbyEvent === 'function'")
    a.evaluate("window.NexusNativeNearbyEvent({type:'connectionEstablished',endpointId:'peer-A',endpointName:'NX-peer-B'})")
    b.evaluate("window.NexusNativeNearbyEvent({type:'connectionEstablished',endpointId:'peer-B',endpointName:'NX-peer-A'})")
    a.wait_for_function("document.querySelector('#native-state-detail').textContent.includes('session active')", timeout=20000)
    b.wait_for_function("document.querySelector('#native-state-detail').textContent.includes('session active')", timeout=20000)
    assert a.locator('#native-state-title').inner_text().startswith('Mock Nearby bridge'), "mock bridge was not labeled as simulation"
    assert a.locator('#physical-connected-count').inner_text() == '0', "simulated peer was counted as physically connected"
    assert 'simulated' in a.locator('#endpoint-health').inner_text(), "diagnostics did not distinguish simulated endpoints"

    a.locator('.nav-item[data-page="chats"]').click()
    b.locator('.nav-item[data-page="chats"]').click()
    a.locator('#thread-items button[data-thread^="peer:"]').first.click()
    text = "Encrypted mock-bridge integration test"
    a.locator('#message-input').fill(text)
    a.locator('.send-button').click()
    b.locator('#message-list').get_by_text(text).wait_for(timeout=10000)
    wire = a.evaluate("window.__bridgeWires.filter(w => { try { return JSON.parse(w).type === 'ciphertext-v1'; } catch { return false; } }).slice(-1)[0]")
    assert wire and text not in wire, "message text appeared in bridge wire data"
    assert a.locator('#native-state-detail').inner_text().find('AES-GCM') >= 0, "secure session not shown"

    a.evaluate("wire => window.__mockBus.postMessage({kind:'payload',target:1,data:wire})", wire)
    b.wait_for_function("document.querySelector('#rejected-count').textContent.endsWith('/ 1')", timeout=10000)
    a.evaluate("wire => { const f=JSON.parse(wire); const c=f.ciphertext; f.ciphertext=(c[0]==='A'?'B':'A')+c.slice(1); const iv=crypto.getRandomValues(new Uint8Array(12)); f.iv=btoa(String.fromCharCode(...iv)); window.__mockBus.postMessage({kind:'payload',target:1,data:JSON.stringify(f)}); }", wire)
    b.wait_for_function("document.querySelector('#rejected-count').textContent.endsWith('/ 2')", timeout=10000)

    a.locator('#create-group').click()
    group_text = "Mock encrypted group fan-out"
    a.locator('#message-input').fill(group_text)
    a.locator('.send-button').click()
    b.locator('#thread-items button[data-thread^="group:"]').first.wait_for(timeout=10000)
    b.locator('#thread-items button[data-thread^="group:"]').first.click()
    b.locator('#message-list').get_by_text(group_text).wait_for(timeout=10000)
    reply_text = "Mock group reply should be sent"
    b.locator('#message-input').fill(reply_text)
    b.locator('.send-button').click()
    a.locator('#message-list').get_by_text(reply_text).wait_for(timeout=10000)
    b.wait_for_function("JSON.parse(window.__mockSecureState).messages.some(m => m.text === 'Mock group reply should be sent' && m.status === 'sent-secure')", timeout=10000)
    assert a.locator('#group-action-button').inner_text() == 'အဖွဲ့ဝင်များကို စီမံရန်'
    a.locator('#group-action-button').click()
    a.wait_for_function("JSON.parse(window.__mockSecureState).threads.some(t => t.kind === 'group' && t.membershipVersion === 2 && t.acknowledgedMembers?.length >= 2)", timeout=10000)
    b.wait_for_function("JSON.parse(window.__mockSecureState).threads.some(t => t.kind === 'group' && t.membershipVersion === 2 && !t.left)", timeout=10000)
    assert b.locator('#group-action-button').inner_text() == 'အဖွဲ့မှ ထွက်ရန်'
    b.locator('#group-action-button').click()
    a.wait_for_function("JSON.parse(window.__mockSecureState).threads.some(t => t.kind === 'group' && t.membershipVersion === 3 && t.members.length === 1)", timeout=10000)
    b.wait_for_function("JSON.parse(window.__mockSecureState).threads.some(t => t.kind === 'group' && t.left)", timeout=10000)
    assert b.locator('#message-input').is_disabled(), "departed member can still send to the group"
    assert a.evaluate("localStorage.getItem('nexus-offline-v1') === null") and b.evaluate("localStorage.getItem('nexus-offline-v1') === null"), "app state was written to plaintext localStorage"

    transfer_id = "0123456789abcdef0123456789abcdef"
    a.evaluate("id => window.NexusNativeNearbyEvent({type:'filePicked',transferId:id,endpointId:'peer-A',filename:'report.pdf',size:123})", transfer_id)
    a.wait_for_function("window.__fileCalls.includes('sendPreparedFile')", timeout=10000)
    assert b.evaluate("window.__fileCalls.includes('expectIncomingFile')"), "authenticated receiver did not accept the file manifest"
    assert b.evaluate("window.__confirmHistory.some(text => text.includes('report.pdf') && text.includes('123 bytes'))"), "incoming file consent prompt missing"
    assert a.evaluate("window.__bridgeWires.some(w => JSON.parse(w).type === 'ciphertext-v1')"), "file manifest was not sent inside an encrypted frame"
    a.locator('.nav-item[data-page="nearby"]').click()
    a.locator('#cancel-file-transfer').click()
    assert a.evaluate("window.__fileCalls.includes('cancelFileTransfer')"), "file transfer cancellation did not reach native bridge"
    a.evaluate("id => window.NexusNativeNearbyEvent({type:'fileTransferError',transferId:id,endpointId:'peer-A',code:'TEST_FAILED'})", transfer_id)
    a.locator('#retry-file-transfer').click()
    assert a.evaluate("window.__fileCalls.includes('pickAndSendFile')"), "retry did not ask the user to select the file again"

    a.locator('.nav-item[data-page="diagnostics"]').click()
    a.locator('#run-tests').click()
    a.wait_for_function("!document.querySelector('#run-tests').disabled", timeout=10000)
    assert a.locator('#logic-result').inner_text().startswith('9/9'), "WebCrypto or group protocol diagnostic failed: " + a.locator('#logic-result').inner_text()

    confirmations_a = a.evaluate("window.__confirmHistory")
    confirmations_b = b.evaluate("window.__confirmHistory")
    assert any('fingerprint' in s for s in confirmations_a + confirmations_b), "fingerprint confirmation prompt missing"
    assert not errors, "browser errors: " + repr(errors)
    preview_context = browser.new_context()
    preview = preview_context.new_page()
    preview.on("pageerror", lambda e: errors.append(f"preview: {e}"))
    preview.goto(URL + "?preview#chats", wait_until="networkidle")
    preview.locator('#message-input').fill("This preview message is session-only")
    preview.locator('.send-button').click()
    preview.locator('#message-list').get_by_text("This preview message is session-only").wait_for(timeout=5000)
    assert preview.evaluate("localStorage.getItem('nexus-offline-v1') === null"), "browser preview wrote app state into localStorage"
    preview.reload(wait_until="networkidle")
    assert preview.locator('#message-list').get_by_text("This preview message is session-only").count() == 0, "browser preview data unexpectedly persisted after reload"
    assert not errors, "browser errors: " + repr(errors)
    print("PASS: both mock peers derived and approved an ECDH/AES-GCM session")
    print("PASS: message text was absent from the bridge wire payload")
    print("PASS: duplicate message ID rejected")
    print("PASS: tampered AES-GCM ciphertext rejected")
    print("PASS: encrypted group fan-out and local-sender exclusion")
    print("PASS: encrypted group roster update, acceptance, leave, and departed-member send blocking")
    print("PASS: mock peers are visibly labeled simulation-only and excluded from physical counts")
    print("PASS: Android bridge storage contract persisted app state; plaintext localStorage remained empty")
    print("PASS: wrapped file-key manifest/receiver consent/ready ACK/cancel path across mock encrypted peers")
    print("PASS: transfer failure offered retry by reselecting the file")
    print("PASS: 9/9 browser WebCrypto and bounded-group protocol diagnostics")
    print("PASS: browser preview remained memory-only and discarded state after reload")
    print("LIMIT: this is a mock browser bridge test, not an Android/phone test")
    browser.close()
