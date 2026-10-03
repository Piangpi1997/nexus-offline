"""Responsive UI and visual evidence checks for the NEXUS OFFLINE web client.

These browser checks cover layout/navigation only; they do not represent a
physical Android runtime test. Run against the local static app server.
"""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

URL = os.environ.get("NEXUS_TEST_URL", "http://127.0.0.1:4174/")
WIDTHS = (320, 360, 390, 412, 480)
PAGES = ("home", "chats", "ai", "nearby", "diagnostics", "settings")
OUT = Path(__file__).resolve().parents[1] / "ui-validation" / "after"
OUT.mkdir(parents=True, exist_ok=True)


def assert_page(page, name):
    page.wait_for_function(
        "name => document.querySelector(`#page-${name}`)?.classList.contains('active')",
        arg=name,
        timeout=5000,
    )
    assert page.locator("#page-title").inner_text().strip(), f"missing page title: {name}"


def metrics(page):
    return page.evaluate("""() => {
      const visible = e => e.getClientRects().length && getComputedStyle(e).visibility !== 'hidden';
      const targets = [...document.querySelectorAll('button,a,[role=button]')].filter(visible);
      const text = [...document.querySelectorAll('h1,h2,h3,p,small,.native-state strong,.native-state small,.attachment-description')]
        .filter(visible).filter(e => !['nowrap','pre'].includes(getComputedStyle(e).whiteSpace));
      const rect = e => e.getBoundingClientRect();
      const intersects = (a,b) => Math.min(a.right,b.right)-Math.max(a.left,b.left) > 1 && Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top) > 1;
      const inViewport = e => { const r=rect(e); return r.right > 0 && r.left < innerWidth; };
      const activeTargets = targets.filter(e => (e.closest('.page.active') || e.closest('.topbar')) && inViewport(e));
      const targetOverlaps = [];
      for (let i=0;i<activeTargets.length;i++) for (let j=i+1;j<activeTargets.length;j++) {
        if (intersects(rect(activeTargets[i]),rect(activeTargets[j]))) targetOverlaps.push([activeTargets[i].id||activeTargets[i].innerText,activeTargets[j].id||activeTargets[j].innerText]);
      }
      const textTargetOverlaps = [];
      for (const t of text.filter(e => e.closest('.page.active') || e.closest('.topbar'))) for (const c of activeTargets) {
        if (c.contains(t) || t.contains(c)) continue;
        if (intersects(rect(t),rect(c))) textTargetOverlaps.push([(t.innerText||'').trim().slice(0,48),c.id||c.innerText||c.getAttribute('aria-label')]);
      }
      return {
        width: innerWidth,
        scrollWidth: document.documentElement.scrollWidth,
        shortTargets: targets.map(e => ({label:(e.innerText||e.getAttribute('aria-label')||e.id||'').trim(),w:Math.round(e.getBoundingClientRect().width),h:Math.round(e.getBoundingClientRect().height)}))
          .filter(x => x.w < 44 || x.h < 44),
        textOverflow: text.map(e => ({tag:e.tagName,text:(e.innerText||'').trim().slice(0,100),w:e.clientWidth,scroll:e.scrollWidth,h:e.clientHeight,scrollH:e.scrollHeight}))
          .filter(x => x.scroll > x.w + 2),
        malformedUnicode: document.body.innerText.includes(String.fromCharCode(0xfffd)),
        hasBurmese: /[\u1000-\u109f]/.test(document.body.innerText),
        targetOverlaps,
        textTargetOverlaps,
        hiddenViolations: [...document.querySelectorAll('[hidden]')]
          .filter(e => getComputedStyle(e).display !== 'none' || e.getClientRects().length)
          .map(e => e.id || e.className || e.tagName)
      };
    }""")


def assert_layout(page, label):
    m = metrics(page)
    assert m["scrollWidth"] <= m["width"], f"horizontal overflow {label}: {m}"
    assert not m["shortTargets"], f"under-44px visible control {label}: {m['shortTargets']}"
    assert not m["textOverflow"], f"text does not wrap {label}: {m['textOverflow']}"
    assert not m["targetOverlaps"], f"visible controls overlap {label}: {m['targetOverlaps']}"
    assert not m["textTargetOverlaps"], f"text overlaps a visible control {label}: {m['textTargetOverlaps']}"
    assert not m["hiddenViolations"], f"hidden element became visible {label}: {m['hiddenViolations']}"
    assert m["hasBurmese"] and not m["malformedUnicode"], f"Myanmar text malformed/missing {label}"


def assert_low_box_hierarchy(page, name, width):
    if name == "home":
        rows = page.evaluate("""() => [...document.querySelectorAll('#page-home .status-card')].map(e => {
          const s=getComputedStyle(e), r=e.getBoundingClientRect();
          return {height:r.height, border:s.borderTopWidth, title:e.querySelector('h3')?.textContent.trim()};
        })""")
        assert len(rows) == 3, f"Home must retain all three status rows: {rows}"
        assert all(float(row["height"]) <= 112 for row in rows), f"Home status rows are not compact at {width}px: {rows}"
        assert all(row["border"] == "0px" for row in rows), f"Home status cards gained unnecessary outlines at {width}px: {rows}"
    if name == "ai":
        ai = page.evaluate("""() => ({
          nestedPanels:document.querySelectorAll('#page-ai .panel .panel').length,
          status:[...document.querySelectorAll('#page-ai .ai-status-card')].map(e=>({top:getComputedStyle(e).borderTopWidth,left:getComputedStyle(e).borderLeftWidth,right:getComputedStyle(e).borderRightWidth,radius:getComputedStyle(e).borderRadius})),
          model:{border:getComputedStyle(document.querySelector('#page-ai .ai-model-information')).borderTopWidth,radius:getComputedStyle(document.querySelector('#page-ai .ai-model-information')).borderRadius}
        })""")
        assert ai["nestedPanels"] == 0, f"Local AI contains nested main panels at {width}px: {ai}"
        assert all(x["left"] == "0px" and x["right"] == "0px" and x["radius"] == "0px" for x in ai["status"]), f"Local AI status rows look like nested cards at {width}px: {ai}"
        assert ai["model"] == {"border": "0px", "radius": "0px"}, f"Local AI model details retain a nested card outline at {width}px: {ai}"
    if name == "nearby":
        nearby = page.evaluate("""() => ({
          nestedPanels:document.querySelectorAll('#page-nearby .panel .panel').length,
          attachmentPanel:{border:getComputedStyle(document.querySelector('#page-nearby #attachment-panel')).borderTopWidth,radius:getComputedStyle(document.querySelector('#page-nearby #attachment-panel')).borderRadius},
          pairing:[...document.querySelectorAll('#page-nearby .pairing-step')].map(e=>({radius:getComputedStyle(e).borderRadius,background:getComputedStyle(e).backgroundColor}))
        })""")
        assert nearby["nestedPanels"] == 0, f"Nearby contains nested main panels at {width}px: {nearby}"
        assert nearby["attachmentPanel"]["radius"] == "14px", f"Nearby attachment section should retain one modest main container at {width}px: {nearby}"
        assert all(x["radius"] == "0px" for x in nearby["pairing"]), f"Pairing steps should use dividers, not nested rounded cards, at {width}px: {nearby}"


def open_route(page, name):
    if not page.locator(f"#page-{name}").evaluate("e => e.classList.contains('active')"):
        menu = page.locator("#mobile-menu")
        if menu.is_visible():
            menu.click()
        page.locator(f".nav-item[data-page='{name}']").click()
        page.wait_for_function("!document.querySelector('#sidebar').classList.contains('open')", timeout=1500)
        if menu.is_visible():
            page.wait_for_function("document.querySelector('#sidebar').getBoundingClientRect().right <= 0.5", timeout=1500)
    assert_page(page, name)


with sync_playwright() as p:
    browser = p.chromium.launch(
        executable_path="/usr/bin/chromium",
        headless=True,
        args=["--no-sandbox", "--disable-dev-shm-usage"],
    )
    errors = []
    for width in WIDTHS:
        page = browser.new_page(
            viewport={"width": width, "height": 900},
            device_scale_factor=1,
            is_mobile=True,
            has_touch=True,
        )
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(URL + "#home", wait_until="networkidle")
        assert_page(page, "home")
        page.wait_for_timeout(3300)  # Let the startup toast clear before saving evidence.
        page.screenshot(path=str(OUT / f"home-{width}.png"), full_page=True)
        assert_layout(page, f"home at {width}px")
        assert_low_box_hierarchy(page, "home", width)

        for name in PAGES[1:]:
            open_route(page, name)
            assert_layout(page, f"{name} at {width}px")
            assert_low_box_hierarchy(page, name, width)
            if name == "nearby":
                # Exercise mixed Burmese/English technical strings at the narrow width.
                page.locator(".nearby-copy > p").evaluate("e => e.textContent = 'ဖိုင်လုံခြုံရေး · AES-GCM · SHA-256 · Nearby · Local AI · QR · physical-device/10-phone verification. ' + 'ဒီစက်တွင် စစ်ဆေးရန်လိုသည့် အခြေအနေများကို ဆက်လက်ဖော်ပြထားသည်။'")
                assert_layout(page, f"long mixed technical Nearby copy at {width}px")
                flow = page.evaluate("""() => {
                  const hero=document.querySelector('.nearby-hero');
                  const description=hero.querySelector('.nearby-copy > p').getBoundingClientRect();
                  const status=hero.querySelector('.native-state').getBoundingClientRect();
                  const actions=hero.querySelector('.nearby-buttons').getBoundingClientRect();
                  const buttons=[...hero.querySelectorAll('.nearby-buttons button')].map(e=>e.getBoundingClientRect());
                  return {hero:hero.getBoundingClientRect().toJSON(),description:description.toJSON(),status:status.toJSON(),actions:actions.toJSON(),buttons:buttons.map(x=>x.toJSON())};
                }""")
                assert flow["actions"]["top"] >= flow["status"]["bottom"] + 15, f"Nearby action row overlaps status/description at {width}px: {flow}"
                assert flow["actions"]["left"] >= flow["hero"]["left"] - 1 and flow["actions"]["right"] <= flow["hero"]["right"] + 1, f"Nearby action row escapes its section at {width}px: {flow}"
                for i, a in enumerate(flow["buttons"]):
                    for b in flow["buttons"][i + 1:]:
                        assert a["right"] <= b["left"] + 1 or b["right"] <= a["left"] + 1 or a["bottom"] <= b["top"] + 1 or b["bottom"] <= a["top"] + 1, f"Nearby buttons overlap at {width}px: {flow}"
                if width in (320, 390, 480):
                    page.screenshot(path=str(OUT / f"nearby-{width}.png"), full_page=True)
        # Exercise drawer containment/open state and return to Home for a screenshot comparison.
        menu = page.locator("#mobile-menu")
        menu.click()
        page.wait_for_function("document.querySelector('#sidebar').getBoundingClientRect().left >= -1", timeout=1500)
        drawer = page.locator("#sidebar").bounding_box()
        assert drawer and drawer["x"] >= -1 and drawer["x"] + drawer["width"] <= width + 1, f"drawer exceeds viewport at {width}px: {drawer}"
        assert page.locator("#sidebar-scrim").is_visible()
        page.locator("#sidebar-scrim").click(position={"x": width - 8, "y": 180})
        assert page.locator("#sidebar-scrim").is_hidden()
        page.wait_for_function("document.querySelector('#sidebar').getBoundingClientRect().right <= 0.5", timeout=1500)
        open_route(page, "home")
        page.screenshot(path=str(OUT / f"mobile-home-navigation-{width}.png"), full_page=True)
        page.close()

    # Check enlarged text without browser zoom (which scales the viewport itself).
    for width in (320, 390, 480):
        page = browser.new_page(viewport={"width": width, "height": 900}, is_mobile=True, has_touch=True)
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(URL + "#home", wait_until="networkidle")
        page.evaluate("""() => {
          for (const e of document.querySelectorAll('body *')) {
            const s=getComputedStyle(e).fontSize;
            if (s.endsWith('px')) e.style.setProperty('font-size', `${parseFloat(s)*1.25}px`, 'important');
          }
          document.documentElement.style.setProperty('font-size','125%','important');
        }""")
        assert_layout(page, f"Home at {width}px with 125% text")
        page.locator("#mobile-menu").click()
        page.locator(".nav-item[data-page='nearby']").click()
        assert_layout(page, f"Nearby at {width}px with 125% text")
        flow=page.evaluate("""() => ({
          status:document.querySelector('.native-state').getBoundingClientRect().toJSON(),
          actions:document.querySelector('.nearby-buttons').getBoundingClientRect().toJSON(),
          hero:document.querySelector('.nearby-hero').getBoundingClientRect().toJSON()
        })""")
        assert flow["actions"]["top"] >= flow["status"]["bottom"] + 15, f"scaled-text action overlap at {width}px: {flow}"
        assert flow["actions"]["right"] <= flow["hero"]["right"] + 1, f"scaled-text action escapes section at {width}px: {flow}"
        page.close()

    desktop = browser.new_page(viewport={"width": 1440, "height": 1000})
    desktop.on("pageerror", lambda error: errors.append(str(error)))
    desktop.goto(URL + "#home", wait_until="networkidle")
    for name in PAGES:
        desktop.locator(f".nav-item[data-page='{name}']").click()
        assert_page(desktop, name)
        assert_layout(desktop, f"desktop {name}")
    assert not errors, f"browser JavaScript errors: {errors}"
    print("PASS: five requested mobile widths (320, 360, 390, 412, 480px), six routes, zero horizontal overflow")
    print("PASS: visible buttons/links remain at least 44px; hidden elements remain hidden")
    print("PASS: Burmese and mixed technical text wraps without horizontal clipping or replacement glyphs")
    print("PASS: Nearby status-to-action vertical order, action-row containment, and no button intersections")
    print("PASS: mobile drawer stays within each viewport, scrim closes, and mobile/desktop navigation works")
    print("PASS: 125% text-scaling scenario at 320, 390, and 480px for Home and Nearby")
    print("PASS: updated mobile screenshot evidence saved under ui-validation/after")
    print("LIMIT: host Chromium mobile emulation only; no physical Android screenshot/runtime test")
    browser.close()
