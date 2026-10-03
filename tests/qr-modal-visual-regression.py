"""Visual-only QR modal layout checks in Chromium; not an Android/native QR test.

Run with Python Playwright installed. The patterned square is deliberately a
non-scannable placeholder: screenshots prove layout only, never QR display on a
phone, camera scanning, pairing, fingerprint verification, or Nearby success.
"""
from __future__ import annotations

import json
import os
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "ui-validation" / "after" / "qr-modal"
OUT.mkdir(parents=True, exist_ok=True)

# Source-contract checks ensure the real native dialog owns the requested UI
# semantics while the QR payload/security implementation remains elsewhere.
source = (ROOT / "android/app/src/main/java/com/nexusoffline/MainActivity.kt").read_text()
assert '"QR ဖြင့် ချိတ်ဆက်ရန်"' in source
assert '"Connect with QR"' in source
assert "PairingQr.create(localEndpointName)" in source
assert "PairingQr.encode(payload)" in source
assert "QRCodeWriter().encode(encoded, BarcodeFormat.QR_CODE, 640, 640)" in source
assert "catch (_: Exception)" in source and '"QR_GENERATION_FAILED"' in source
assert "scaleType = android.widget.ImageView.ScaleType.FIT_CENTER" in source
assert "setCancelable(true)" in source and "setCanceledOnTouchOutside(true)" in source
assert "accessibilityPaneTitle = title.text" in source and "title.setAccessibilityHeading(true)" in source
assert "KEYCODE_ESCAPE" in source and "dialog.dismiss()" in source
assert "Close QR dialog" in source and "QR အသစ်ဖန်တီးရန်" in source
assert "payload.expiresAtMillis - System.currentTimeMillis()" in source
assert "showPairingQr(if (english) \"en\" else \"my\")" in source
assert "document.getElementById('pair-qr')?.focus({preventScroll:true})" in source

# The mock matrix below is intentionally not a valid QR payload.
def fake_matrix(size: int = 29) -> str:
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            dark = ((x * 17 + y * 29 + x * y * 7) % 11) < 5
            for ox, oy in ((0, 0), (size - 7, 0), (0, size - 7)):
                dx, dy = x - ox, y - oy
                if 0 <= dx < 7 and 0 <= dy < 7:
                    dark = dx in (0, 6) or dy in (0, 6) or (2 <= dx <= 4 and 2 <= dy <= 4)
            row.append("1" if dark else "0")
        rows.append("".join(row))
    return "|".join(rows)

MATRIX = fake_matrix()

HTML = r"""<!doctype html><html lang="my"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root{color-scheme:dark;--bg:#0a1017;--panel:#101923;--panel2:#17232d;--line:#354650;--text:#f1f6f8;--muted:#a7b7c2;--mint:#92f0ce;--amber:#f3c47b;--font:Inter,"Noto Sans Myanmar","Myanmar Text",system-ui,sans-serif}
*{box-sizing:border-box}html,body{margin:0;min-width:320px;background:var(--bg);color:var(--text);font-family:var(--font);line-height:1.5}
.mock-app{min-height:100vh;padding:24px;background:linear-gradient(135deg,#0c1620,#0a1017 60%)}.mock-app h1{font-size:24px}.mock-panel{max-width:560px;padding:20px;background:#111c26;border:1px solid #253641;border-radius:18px}.mock-panel p{color:var(--muted)}
.backdrop{position:fixed;inset:0;background:rgba(0,0,0,.72);display:grid;place-items:center;padding:16px;z-index:999;}
.dialog{width:min(100%,420px);max-height:calc(100vh - 32px);overflow-y:auto;overscroll-behavior:contain;padding:14px 16px;background:var(--panel);border:1px solid var(--line);border-radius:20px;box-shadow:0 20px 60px #0009;font-size:var(--base,14px)}
.dialog h2{margin:0;font-size:1.286em;line-height:1.35}.public-id{margin-top:10px;padding:9px 12px;min-height:44px;display:flex;align-items:center;justify-content:space-between;gap:8px;background:var(--panel2);border:1px solid var(--line);border-radius:12px;font-size:.82em}.public-id strong{font-size:1.1em;overflow-wrap:anywhere}
.instruction{margin:10px 0 0;font-size:.93em;line-height:1.5}.qr-frame{display:grid;place-items:center;width:min(236px,calc(100vw - 112px),calc(100vh - 360px));min-width:132px;aspect-ratio:1;margin:12px auto 10px;padding:8px;background:white;border:1px solid var(--line);border-radius:12px}.qr-grid{display:grid;width:100%;height:100%;grid-template-columns:repeat(29,1fr);grid-template-rows:repeat(29,1fr);overflow:hidden}.qr-grid i.dark{background:#000}.qr-grid i:not(.dark){background:#fff}
.security{margin:0;padding:8px 10px;background:var(--panel2);border:1px solid var(--line);border-radius:10px;color:var(--muted);font-size:.86em;line-height:1.45}.timer{margin:6px 0 0;text-align:center;color:var(--mint);font-weight:700;font-size:.86em}.expired{margin:4px 0 0;color:var(--amber);font-size:.86em;font-weight:700}.actions{display:flex;gap:8px;margin-top:10px}.actions button{flex:1;min-width:0;min-height:48px;padding:8px;border:1px solid var(--line);border-radius:12px;background:#18372f;color:var(--mint);font:inherit;font-size:.95em}.actions button.close{background:var(--panel2);color:var(--text)}button:focus-visible{outline:3px solid var(--mint);outline-offset:2px}
.large{--base:20px}.hidden{display:none!important}
</style></head><body><main class="mock-app"><h1>NEXUS OFFLINE</h1><section class="mock-panel"><h2>Nearby</h2><p>Visual layout check in a Chromium viewport. The dimmed page is a mock app surface.</p><button id="pair-qr">Pairing QR</button></section></main>
<div class="backdrop"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="qr-title" tabindex="-1">
<header><h2 id="qr-title"></h2></header>
<div class="public-id"><span>Public ID</span><strong>NX-4D21A9C0</strong></div>
<p class="instruction" id="instruction"></p>
<div class="qr-frame" role="img" aria-label="Non-scannable mock QR pattern; visual QA only"><div class="qr-grid" id="mock-qr"></div></div>
<p class="security" id="security"></p><p class="timer" id="timer" aria-live="polite"></p>
<p class="expired hidden" id="expired"></p>
<div class="actions"><button id="new-qr"></button><button class="close" id="close" aria-label=""></button></div>
</section></div>
<script>
const params=new URLSearchParams(location.search),lang=params.get('lang')==='en'?'en':'my',isExpired=params.get('expired')==='1',large=params.get('large')==='1';
const dialog=document.querySelector('.dialog');if(large)dialog.classList.add('large');
document.documentElement.lang=lang;
document.querySelector('#qr-title').textContent=lang==='en'?'Connect with QR':'QR ဖြင့် ချိတ်ဆက်ရန်';
document.querySelector('#instruction').textContent=lang==='en'?'Scan the QR, then choose the matching Public ID in Nearby.':'QR ကို scan လုပ်ပြီး Nearby မှ တူညီသော Public ID ကို ရွေးပါ။';
document.querySelector('#security').textContent=lang==='en'?'Before connecting, compare the fingerprint on both devices.':'ချိတ်ဆက်မီ စက်နှစ်လုံး၏ fingerprint ကို တိုက်စစ်ပါ။';
document.querySelector('#timer').textContent=isExpired?(lang==='en'?'Expired':'သက်တမ်းကုန်သွားပါပြီ'):(lang==='en'?'Expires in 01:59':'01:59 အတွင်း သက်တမ်းကုန်မည်');
document.querySelector('#expired').textContent=lang==='en'?'QR expired. This is not an error. Create a new QR and scan again.':'QR ကုဒ် သက်တမ်းကုန်သွားပါပြီ။ အမှားမဟုတ်ပါ။ QR အသစ်ဖန်တီးပြီး ထပ်မံ scan လုပ်ပါ။';
if(isExpired)document.querySelector('#expired').classList.remove('hidden');
document.querySelector('#new-qr').textContent=lang==='en'?'New QR':'QR အသစ်';
const close=document.querySelector('#close');close.textContent=lang==='en'?'Close':'ပိတ်ရန်';close.setAttribute('aria-label',lang==='en'?'Close QR dialog':'QR modal ကို ပိတ်ရန်');
const rows=""" + json.dumps(MATRIX) + r""".split('|');const grid=document.querySelector('#mock-qr');for(const row of rows)for(const bit of row){const cell=document.createElement('i');if(bit==='1')cell.className='dark';grid.append(cell)}
let previous=document.querySelector('#pair-qr');function dismiss(){document.querySelector('.backdrop').remove();previous.focus({preventScroll:true})}
close.addEventListener('click',dismiss);window.addEventListener('keydown',e=>{if(e.key==='Escape'){dismiss();e.preventDefault()}});window.NexusHandleSystemBack=()=>{dismiss();return true};dialog.focus({preventScroll:true});
</script></body></html>"""


def metrics(page: object) -> dict:
    return page.evaluate("""() => {
      const dialog=document.querySelector('.dialog'), qr=document.querySelector('.qr-frame');
      const rect=e=>e.getBoundingClientRect(); const d=rect(dialog), q=rect(qr);
      const text=document.querySelector('#instruction');
      const range=document.createRange();range.selectNodeContents(text);
      const lineCount=range.getClientRects().length;
      const clipped=[...dialog.querySelectorAll('h2,p,strong,button')].some(e=>e.scrollHeight>e.clientHeight+2 && getComputedStyle(e).overflow!=='visible');
      return {width:innerWidth,height:innerHeight,dialog:{left:d.left,top:d.top,right:d.right,bottom:d.bottom,clientHeight:dialog.clientHeight,scrollHeight:dialog.scrollHeight},qr:{width:q.width,height:q.height},instructionLines:lineCount,clipped,scrollWidth:document.documentElement.scrollWidth,targets:[...dialog.querySelectorAll('button')].map(e=>({name:e.textContent.trim(),w:e.getBoundingClientRect().width,h:e.getBoundingClientRect().height}))};
    }""")


with sync_playwright() as p:
    browser = p.chromium.launch(executable_path="/usr/bin/chromium", headless=True, args=["--no-sandbox", "--disable-dev-shm-usage"])
    results = []
    scenarios = [
        *((width, 720, "my", False, False, f"qr-modal-burmese-{width}.png") for width in (320, 360, 390, 412)),
        (390, 720, "en", False, False, "qr-modal-english-390.png"),
        (390, 720, "my", True, False, "qr-modal-expired-390.png"),
        (320, 700, "my", False, True, "qr-modal-large-text-320.png"),
        (320, 480, "my", False, False, "qr-modal-small-height-320x480.png"),
    ]
    for width, height, lang, expired, large, filename in scenarios:
        page = browser.new_page(viewport={"width": width, "height": height}, device_scale_factor=1)
        page.set_content(HTML, wait_until="load")
        # set_content produces about:blank; configure each scenario directly in the fixture.
        page.evaluate("""([lang,expired,large])=>{
          const d=document.querySelector('.dialog');if(large)d.classList.add('large');document.documentElement.lang=lang;
          document.querySelector('#qr-title').textContent=lang==='en'?'Connect with QR':'QR ဖြင့် ချိတ်ဆက်ရန်';
          document.querySelector('#instruction').textContent=lang==='en'?'Scan the QR, then choose the matching Public ID in Nearby.':'QR ကို scan လုပ်ပြီး Nearby မှ တူညီသော Public ID ကို ရွေးပါ။';
          document.querySelector('#security').textContent=lang==='en'?'Before connecting, compare the fingerprint on both devices.':'ချိတ်ဆက်မီ စက်နှစ်လုံး၏ fingerprint ကို တိုက်စစ်ပါ။';
          document.querySelector('#timer').textContent=expired?(lang==='en'?'Expired':'သက်တမ်းကုန်သွားပါပြီ'):(lang==='en'?'Expires in 01:59':'01:59 အတွင်း သက်တမ်းကုန်မည်');
          document.querySelector('#expired').textContent=lang==='en'?'QR expired. This is not an error. Create a new QR and scan again.':'QR ကုဒ် သက်တမ်းကုန်သွားပါပြီ။ အမှားမဟုတ်ပါ။ QR အသစ်ဖန်တီးပြီး ထပ်မံ scan လုပ်ပါ။';
          document.querySelector('#expired').classList.toggle('hidden',!expired);
          document.querySelector('#new-qr').textContent=lang==='en'?'New QR':'QR အသစ်';
          const c=document.querySelector('#close');c.textContent=lang==='en'?'Close':'ပိတ်ရန်';c.setAttribute('aria-label',lang==='en'?'Close QR dialog':'QR modal ကို ပိတ်ရန်');
        }""", [lang, expired, large])
        page.evaluate("document.querySelector('.dialog').focus({preventScroll:true})")
        page.wait_for_timeout(50)
        result = metrics(page)
        assert result["dialog"]["left"] >= 0 and result["dialog"]["right"] <= width + 0.5, (filename, result)
        assert result["dialog"]["top"] >= 0 and result["dialog"]["bottom"] <= height + 0.5, (filename, result)
        assert abs(result["qr"]["width"] - result["qr"]["height"]) <= 1, (filename, result)
        assert result["qr"]["width"] >= 128, (filename, result)
        assert result["scrollWidth"] <= width, (filename, result)
        assert not result["clipped"], (filename, result)
        assert all(target["w"] >= 44 and target["h"] >= 44 for target in result["targets"]), (filename, result)
        if lang == "my" and not expired and not large:
            assert 1 <= result["instructionLines"] <= 3, (filename, result)
        if large:
            assert result["dialog"]["clientHeight"] <= height - 24, (filename, result)
        page.screenshot(path=str(OUT / filename), full_page=False)
        # Browser-only keyboard/focus behavior fixture: not a physical Android back test.
        page.keyboard.press("Escape")
        assert page.locator(".backdrop").count() == 0
        assert page.evaluate("document.activeElement.id") == "pair-qr"
        page.close()
        results.append({"screenshot": f"ui-validation/after/qr-modal/{filename}", **result})
    browser.close()

report = {
    "scope": "Chromium visual fixture only; non-scannable QR placeholder; no Android or pairing test",
    "results": results,
}
(OUT / "qr-modal-visual-regression.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(report, ensure_ascii=False, indent=2))
