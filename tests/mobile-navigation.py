"""Mobile and desktop navigation regression checks for NEXUS OFFLINE.

Requires Python Playwright and Chromium. This is a browser/mock interaction test;
it does not substitute for installing or tapping the APK on a physical phone.
Run with NEXUS_TEST_URL=http://127.0.0.1:4174/ after starting a local HTTP server.
"""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

URL = os.environ.get("NEXUS_TEST_URL", "http://127.0.0.1:4174/")
PAGES = ("home", "chats", "ai", "nearby", "diagnostics", "settings")
ROOT = Path(__file__).resolve().parents[1]


def assert_page(page, name):
    page.wait_for_function(
        "name => document.querySelector(`#page-${name}`)?.classList.contains('active')",
        arg=name,
        timeout=5000,
    )
    assert page.locator(f".nav-item[data-page='{name}']").get_attribute("aria-current") == "page"
    assert page.locator("#page-title").inner_text().strip()


with sync_playwright() as p:
    browser = p.chromium.launch(
        executable_path="/usr/bin/chromium",
        headless=True,
        args=["--no-sandbox", "--disable-dev-shm-usage"],
    )
    errors = []
    mobile = browser.new_page(
        viewport={"width": 393, "height": 873},
        device_scale_factor=2,
        is_mobile=True,
        has_touch=True,
    )
    mobile.on("pageerror", lambda error: errors.append(str(error)))
    mobile.goto(URL, wait_until="networkidle")
    assert_page(mobile, "home")

    menu = mobile.locator("#mobile-menu")
    box = menu.bounding_box()
    assert box and box["width"] >= 44 and box["height"] >= 44, f"small menu tap target: {box}"
    menu.tap()
    assert mobile.locator("#sidebar").evaluate("e => e.classList.contains('open')")
    assert mobile.locator("#mobile-menu").get_attribute("aria-expanded") == "true"
    assert mobile.locator("#sidebar-scrim").is_visible()
    scrim_box = mobile.locator("#sidebar-scrim").bounding_box()
    assert scrim_box and scrim_box["width"] >= 393
    mobile.locator("#sidebar-scrim").tap(position={"x": 350, "y": 180})
    assert not mobile.locator("#sidebar").evaluate("e => e.classList.contains('open')")

    for name in PAGES[1:]:
        menu.tap()
        mobile.locator(f".nav-item[data-page='{name}']").tap()
        assert_page(mobile, name)
        assert mobile.locator("#sidebar-scrim").is_hidden()

    # Escape and the native Android back bridge both close the mobile drawer.
    menu.tap()
    mobile.keyboard.press("Escape")
    assert mobile.locator("#sidebar").evaluate("e => !e.classList.contains('open')")
    menu.tap()
    assert mobile.evaluate("window.NexusHandleSystemBack()") is True
    assert mobile.locator("#sidebar").evaluate("e => !e.classList.contains('open')")

    # Audit visible touch controls across every screen at a 393 CSS-pixel viewport.
    for name in PAGES:
        if name == "home" and not mobile.locator("#page-home").evaluate("e => e.classList.contains('active')"):
            menu.tap()
            mobile.locator(".brand[data-page='home']").tap()
        elif name != "home":
            menu.tap()
            mobile.locator(f".nav-item[data-page='{name}']").tap()
        metrics = mobile.evaluate("""() => ({
          width: innerWidth,
          scrollWidth: document.documentElement.scrollWidth,
          undersized: [...document.querySelectorAll('button,a,[role=button]')]
            .filter(e => e.getClientRects().length && getComputedStyle(e).visibility !== 'hidden' && !e.disabled)
            .map(e => ({label: (e.innerText || e.getAttribute('aria-label') || e.id || '').trim(), width: e.getBoundingClientRect().width, height: e.getBoundingClientRect().height}))
            .filter(e => e.width < 44 || e.height < 44),
          unnamed: [...document.querySelectorAll('button,a,[role=button]')]
            .filter(e => e.getClientRects().length && getComputedStyle(e).visibility !== 'hidden')
            .filter(e => !(e.getAttribute('aria-label') || e.getAttribute('aria-labelledby') || e.title || e.innerText || e.textContent || '').trim()).length,
          longTextOverflow: [...document.querySelectorAll('h1,h2,h3,p,span,strong,label')]
            .filter(e => e.getClientRects().length && (e.innerText || '').length > 60)
            .filter(e => e.scrollWidth > e.clientWidth + 3).length,
          burmeseTextPresent: /[\u1000-\u109f]/.test(document.body.innerText),
          replacementCharacterPresent: document.body.innerText.includes(String.fromCharCode(0xfffd)),
          disabledControls: [...document.querySelectorAll('button')].filter(e => e.getClientRects().length && e.disabled).length
        })""")
        assert metrics["scrollWidth"] <= metrics["width"], f"horizontal overflow on {name}: {metrics}"
        assert not metrics["undersized"], f"under-44px target on {name}: {metrics['undersized']}"
        assert metrics["unnamed"] == 0, f"interactive control lacks an accessible name on {name}"
        assert metrics["longTextOverflow"] == 0, f"long text clips horizontally on {name}"
        assert metrics["burmeseTextPresent"] and not metrics["replacementCharacterPresent"], f"Burmese text is missing or malformed on {name}"
        if name in ("ai", "nearby"):
            assert metrics["disabledControls"] > 0, f"unavailable {name} controls are not visibly disabled"

    # Keyboard navigation must produce a visible focus indicator, not just a DOM focus state.
    mobile.locator("#mobile-menu").focus()
    mobile.keyboard.press("Tab")
    focus = mobile.evaluate("""() => {
      const e = document.querySelector(':focus-visible');
      if (!e) return null;
      const s = getComputedStyle(e);
      return {outlineWidth: parseFloat(s.outlineWidth) || 0, outlineStyle: s.outlineStyle, boxShadow: s.boxShadow};
    }""")
    assert focus and (focus["outlineWidth"] >= 2 or focus["boxShadow"] != "none"), f"keyboard focus indicator is not visible: {focus}"

    # The chat-room info button must perform a useful action, not be decorative.
    menu.tap()
    mobile.locator(".nav-item[data-page='chats']").tap()
    mobile.locator(".room-info").tap()
    assert "Local-only note" in mobile.locator("#retry-status").inner_text()

    # The sidebar brand was a plain #home anchor and previously changed only the
    # hash; it must now navigate the visible screen as well.
    menu.tap()
    mobile.locator(".brand[data-page='home']").tap()
    assert_page(mobile, "home")

    # All three dashboard quick actions are touchable buttons that open their page.
    for name in ("chats", "ai", "nearby"):
        menu.tap()
        mobile.locator(".brand[data-page='home']").tap()
        assert_page(mobile, "home")
        mobile.locator(f".quick-row[data-page='{name}']").tap()
        assert_page(mobile, name)
    mobile.go_back(wait_until="domcontentloaded")
    assert_page(mobile, "home")
    mobile.go_forward(wait_until="domcontentloaded")
    assert_page(mobile, "nearby")
    # The brand is in the mobile drawer, so use it once more with the drawer open.
    menu.tap()
    mobile.locator(".brand[data-page='home']").tap()
    assert_page(mobile, "home")
    mobile.wait_for_timeout(350)
    evidence = ROOT / "ui-validation" / "after"
    evidence.mkdir(parents=True, exist_ok=True)
    mobile.screenshot(path=str(evidence / "mobile-home-nav-393.png"), full_page=True)

    desktop = browser.new_page(viewport={"width": 1440, "height": 1000})
    desktop.on("pageerror", lambda error: errors.append(str(error)))
    desktop.goto(URL + "#home", wait_until="networkidle")
    for name in PAGES:
        desktop.locator(f".nav-item[data-page='{name}']").click()
        assert_page(desktop, name)
    assert not errors, f"browser JavaScript errors: {errors}"
    print("PASS: mobile hamburger opens/closes drawer and updates aria state")
    print("PASS: scrim receives outside taps and blocks the underlying page")
    print("PASS: all six sidebar destinations respond to touch and close the drawer")
    print("PASS: brand home link and all three dashboard quick actions navigate")
    print("PASS: browser back/forward restores the correct page")
    print("PASS: mobile menu touch target is at least 44 x 44 CSS px")
    print("PASS: all screens meet 44px targets, avoid overflow/clipping, name controls, and render Burmese without replacement glyphs")
    print("PASS: keyboard navigation shows a visible focus ring; unavailable AI/Nearby actions are disabled")
    print("PASS: Escape/system back close the drawer; room-info control responds")
    print("PASS: desktop sidebar destinations navigate; no browser JS errors")
    print("LIMIT: browser touch emulation only; no Android phone/emulator test")
    browser.close()
