"""Attachment UI and metadata security checks using an explicitly mocked native bridge.

This test validates UI policy only; encryption, SAF, FileProvider, and real file
I/O are covered by Android unit tests and still require a physical-device pass.
"""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

URL = os.environ.get("NEXUS_TEST_URL", "http://127.0.0.1:4174/")
INIT = r"""
(() => {
  window.__attachmentCalls=[];
  const ok=value=>JSON.stringify(value||{ok:true});
  window.NexusNativeNearby={
    isMock:true,isAvailable:()=>true,
    getStatus:()=>ok({available:true,isMock:true,connectedCount:0,permissionGranted:true,googlePlayServicesAvailable:false,nearbyAvailable:false,qrScannerAvailable:true,pairingQrVersion:1,attachmentCount:0,attachmentEncryptedBytes:0,attachmentVerifiedCount:0,attachmentHealth:'LISTABLE',legacyPlaintextAttachmentCount:0,secureStateHealth:'NOT_INITIALIZED',localAI:{runtimeInstalled:false,state:'UNAVAILABLE',installedModels:[]},applicationId:'mock',versionName:'test',versionCode:1,buildType:'mock'}),
    loadSecureState:()=>ok({ok:true,found:false}),saveSecureState:()=>ok({ok:true}),clearSecureState:()=>{window.__attachmentCalls.push(['clearSecureState']);return ok({ok:true});},
    getSecureStorageBytes:()=>ok({ok:true,bytes:0}),listAttachments:()=>ok({ok:true,pending:true}),
    useAttachment:(id,action)=>{window.__attachmentCalls.push(['useAttachment',id,action]);return ok({ok:true,pending:true});},
    deleteAttachment:id=>{window.__attachmentCalls.push(['deleteAttachment',id]);return ok({ok:true,pending:true});},
    getLocalAIStatus:()=>ok({runtimeInstalled:false,state:'UNAVAILABLE',installedModels:[]})
  };
})();
"""

with sync_playwright() as p:
    browser=p.chromium.launch(executable_path="/usr/bin/chromium",headless=True,args=["--no-sandbox","--disable-dev-shm-usage"])
    page=browser.new_page(viewport={"width":1280,"height":900})
    errors=[]
    page.on("pageerror",lambda error:errors.append(str(error)))
    page.add_init_script(INIT)
    page.goto(URL+"#nearby",wait_until="networkidle")
    verified="0123456789abcdef0123456789abcdef"
    corrupt="fedcba9876543210fedcba9876543210"
    hostile='<img src=x onerror=window.__attachmentXss=1>.txt'
    page.evaluate("items=>window.NexusNativeNearbyEvent({type:'attachmentList',items})",[
        {"transferId":verified,"filename":hostile,"mimeType":"text/plain","sizeBytes":17,"receivedAtMillis":1700000000000,"integrityStatus":"verified"},
        {"transferId":corrupt,"filename":"corrupt.dat","mimeType":"application/octet-stream","sizeBytes":42,"receivedAtMillis":1700000000001,"integrityStatus":"integrity-failed"},
    ])
    assert page.locator("#attachment-list .attachment-card").count()==2
    assert page.locator("#attachment-list img").count()==0,"untrusted attachment filename created executable markup"
    assert page.locator("#attachment-list").inner_text().find(hostile)>=0,"hostile filename was not rendered as text"
    verified_card=page.locator(".attachment-card").filter(has_text=hostile)
    corrupt_card=page.locator(".attachment-card").filter(has_text="corrupt.dat")
    for action in ("open","share","export"):
        assert not verified_card.locator(f"[data-attachment-action='{action}']").is_disabled(),f"verified file {action} action unexpectedly disabled"
        assert corrupt_card.locator(f"[data-attachment-action='{action}']").is_disabled(),f"unverified file {action} action must be disabled"
    assert not corrupt_card.locator("[data-attachment-action='delete']").is_disabled(),"corrupt item must remain deletable"
    verified_card.locator("[data-attachment-action='export']").click()
    assert page.evaluate("id=>window.__attachmentCalls.some(x=>x[0]==='useAttachment'&&x[1]===id&&x[2]==='export')",verified)
    delete=corrupt_card.locator("[data-attachment-action='delete']")
    delete.click()
    assert page.locator("#modal-backdrop").is_visible()
    assert page.evaluate("window.__attachmentCalls.some(x=>x[0]==='deleteAttachment')") is False,"delete bridge ran before confirmation"
    page.locator("#modal-cancel").click()
    assert page.evaluate("window.__attachmentCalls.some(x=>x[0]==='deleteAttachment')") is False,"cancel still deleted attachment"
    delete.click()
    page.locator("#modal-confirm").click()
    assert page.evaluate("id=>window.__attachmentCalls.some(x=>x[0]==='deleteAttachment'&&x[1]===id)",corrupt)
    assert page.evaluate("window.__attachmentXss===undefined"),"untrusted attachment metadata executed script"

    # Regression: a long wrapped attachment description must stay above the
    # actions, with the full action row contained by its card at narrow widths.
    layout_id="11111111111111111111111111111111"
    page.evaluate("id=>window.NexusNativeNearbyEvent({type:'attachmentList',items:[{transferId:id,filename:'AES-GCM SHA-256 Nearby Local AI QR physical-device/10-phone long attachment report.txt',mimeType:'application/vnd.nexus.long-technical-attachment',sizeBytes:9172,receivedAtMillis:1700000000002,integrityStatus:'verified'}]})",layout_id)
    layout_card=page.locator("#attachment-list .attachment-card").first
    layout_card.wait_for()
    long_description="ဖိုင်လုံခြုံရေးအတွက် AES-GCM encryption နှင့် SHA-256 integrity ကိုအသုံးပြုထားသည်။ Nearby transfer progress/cancel၊ QR pairing၊ Local AI နှင့် physical-device/10-phone verification ဆိုင်ရာ mixed technical metadata ကို မျက်နှာပြင်ကျဉ်းသော်လည်း သဘာဝအတိုင်း wrap လုပ်ရမည်။ "*2
    description=layout_card.locator(".attachment-description")
    description.evaluate("(e,text)=>{e.textContent=text}",long_description)
    for width in (320,360,390,412,480):
        page.set_viewport_size({"width":width,"height":900})
        page.wait_for_function("document.querySelector('#sidebar').getBoundingClientRect().right <= 0.5", timeout=1500)
        layout=layout_card.evaluate("""card=>{
          const box=e=>{const r=e.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,width:r.width,height:r.height}};
          const d=card.querySelector('.attachment-description'),a=card.querySelector('.attachment-actions');
          return {viewport:innerWidth,scrollWidth:document.documentElement.scrollWidth,card:box(card),description:box(d),descriptionWidth:d.clientWidth,descriptionScrollWidth:d.scrollWidth,actions:box(a),buttons:[...a.querySelectorAll('button')].map(box)};
        }""")
        assert layout["scrollWidth"] <= width, f"attachment page horizontal overflow at {width}px: {layout}"
        assert layout["descriptionScrollWidth"] <= layout["descriptionWidth"] + 1, f"attachment description does not wrap at {width}px: {layout}"
        assert layout["actions"]["top"] >= layout["description"]["bottom"] + 16, f"attachment description overlaps or crowds actions at {width}px: {layout}"
        assert layout["actions"]["left"] >= layout["card"]["left"] - 1 and layout["actions"]["right"] <= layout["card"]["right"] + 1, f"attachment action row escapes its card at {width}px: {layout}"
        if width in (320,390):
            evidence=Path(__file__).resolve().parents[1] / "ui-validation" / "after"
            evidence.mkdir(parents=True,exist_ok=True)
            page.screenshot(path=str(evidence/f"attachment-actions-{width}.png"),full_page=True)
        for i,button in enumerate(layout["buttons"]):
            assert button["width"] >= 44 and button["height"] >= 44, f"attachment action below 44px at {width}px: {layout}"
            for other in layout["buttons"][i+1:]:
                separated=button["right"] <= other["left"]+1 or other["right"] <= button["left"]+1 or button["bottom"] <= other["top"]+1 or other["bottom"] <= button["top"]+1
                assert separated, f"attachment action buttons overlap at {width}px: {layout}"
    print("PASS: attachment description wraps above actions with >=16px separation at 320, 360, 390, 412, and 480px")
    print("PASS: attachment action row stays inside its card; buttons are >=44px and do not overlap")
    assert not errors,"browser errors: "+repr(errors)
    print("PASS: untrusted attachment filename rendered as inert text")
    print("PASS: only GCM/SHA-256 verified attachments enable open/share/export")
    print("PASS: integrity-failed attachment remains deletable")
    print("PASS: deletion bridge runs only after explicit confirmation; cancel is safe")
    print("LIMIT: mock UI only; no Android content provider/SAF/filesystem verification")
    browser.close()
