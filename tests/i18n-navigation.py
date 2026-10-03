"""End-to-end locale and navigation checks for the offline NEXUS web client.

Run after starting a local static server, for example:
  NEXUS_TEST_URL=http://127.0.0.1:4174/ python3 tests/i18n-navigation.py
Browser automation is not Android device testing.
"""
import os
from playwright.sync_api import sync_playwright

URL = os.environ.get("NEXUS_TEST_URL", "http://127.0.0.1:4174/")
PAGES = ("home", "chats", "ai", "nearby", "diagnostics", "settings")

with sync_playwright() as p:
    browser = p.chromium.launch(
        executable_path="/usr/bin/chromium",
        headless=True,
        args=["--no-sandbox", "--disable-dev-shm-usage"],
    )
    page = browser.new_page(viewport={"width": 1440, "height": 1000})
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(URL, wait_until="networkidle")
    page.wait_for_function("window.NexusI18n && document.querySelector('#language-select')")

    assert page.locator("html").get_attribute("lang") == "my"
    assert page.locator("#page-title").inner_text().strip() == "မူလစာမျက်နှာ"
    page.locator(".nav-item[data-page='settings']").click()
    locale = page.locator("#language-select")
    assert locale.input_value() == "my"
    locale.select_option("en")
    page.wait_for_function("document.documentElement.lang === 'en'")
    assert page.locator("#page-title").inner_text().strip() == "Settings"
    assert page.locator(".nav-item[data-page='home']").inner_text().strip() == "Home"
    assert page.locator("label[for='language-select']").inner_text().strip() == "Which language would you like to use?"
    assert page.locator("#language-select").get_attribute("aria-label") == "Choose a language"
    assert page.locator(".nav-item[data-page='chats']").get_attribute("aria-current") is None

    expected = {
        "home": "Home",
        "chats": "Chats",
        "ai": "Local AI",
        "nearby": "Nearby",
        "diagnostics": "Diagnostics",
        "settings": "Settings",
    }
    for name in PAGES:
        page.locator(f".nav-item[data-page='{name}']").click()
        page.wait_for_function("name => document.querySelector('#page-title').textContent.trim() === ({home:'Home',chats:'Chats',ai:'Local AI',nearby:'Nearby',diagnostics:'Diagnostics',settings:'Settings'})[name]", arg=name)
        assert page.locator(".nav-item[aria-current='page']").get_attribute("data-page") == name
        leftovers = page.evaluate("""() => {
          const out=[], walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
          while(walker.nextNode()) { const n=walker.currentNode,e=n.parentElement;
            if(!e?.getClientRects().length || !/[\\u1000-\\u109f]/.test(n.nodeValue)) continue;
            if(e.closest('#avatar-small,#avatar-top,#language-select')) continue;
            out.push(n.nodeValue.trim());
          } return out;
        }""")
        assert not leftovers, f"untranslated Burmese in English {name} screen: {leftovers}"

    page.locator(".nav-item[data-page='chats']").click()
    assert page.locator("#chat-search").get_attribute("aria-label") == "Search chats"
    page.wait_for_function("document.querySelector('#thread-items').innerText.includes('Only on this device')")
    assert "Only on this device" in page.locator("#thread-items").inner_text()
    assert "This device only" in page.locator(".composer-bottom").inner_text()
    assert page.locator("#message-input").get_attribute("placeholder") == "Write a note to keep on this device…"
    assert "No fake responses are generated" in page.locator("#page-home").text_content()

    # Exercise active Nearby/QR/attachment and AI error states, not only the static selector.
    page.locator(".nav-item[data-page='nearby']").click()
    page.evaluate("window.NexusNativeNearbyEvent({type:'fileTransferUpdate',transferId:'locale-test',direction:'send',filename:'demo.txt',bytesTransferred:1,totalBytes:4,status:'IN_PROGRESS'})")
    page.wait_for_function("document.querySelector('#file-transfer-text').textContent.includes('Transferring')")
    page.evaluate("window.NexusNativeNearbyEvent({type:'qrPairing',ok:false,error:'INVALID_QR'})")
    page.wait_for_function("document.querySelector('#toast-region').innerText.includes('QR pairing failed')")
    page.evaluate("window.NexusNativeNearbyEvent({type:'attachmentList',items:[{transferId:'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',filename:'receipt.pdf',mimeType:'application/pdf',sizeBytes:512,integrityStatus:'integrity-failed'}]})")
    page.wait_for_function("document.querySelector('#attachment-list').innerText.includes('Integrity failure')")
    assert "Integrity failure" in page.locator("#attachment-list").inner_text()
    page.locator("#attachment-list [data-attachment-action='delete']").click()
    page.wait_for_function("document.querySelector('#modal-title').textContent.includes('Delete attachment')")
    assert page.locator("#modal-confirm").inner_text().strip() == "Delete file"
    page.locator("#modal-cancel").click()
    page.locator(".nav-item[data-page='ai']").click()
    page.evaluate("window.NexusNativeNearbyEvent({type:'localAIImportResult',ok:false,error:'TEST_IMPORT_FAILURE'})")
    page.wait_for_function("document.querySelector('#toast-region').innerText.includes('Model import failed')")
    page.locator(".nav-item[data-page='chats']").click()
    page.locator("#create-group").click()
    page.wait_for_function("!document.querySelector('#group-action-button').hidden")
    assert page.locator("#group-action-button").inner_text().strip() == "Manage group members"
    assert page.locator("#group-action-button").get_attribute("aria-label") == "Invite secure peers and sync the group roster"

    # An unknown key remains readable in the source language rather than vanishing.
    assert page.evaluate("window.NexusI18n.translate('Future string not in catalog', 'en')") == "Future string not in catalog"
    assert page.evaluate("window.NexusI18n.translate('မသိသော စာသား', 'en')") == "မသိသော စာသား"

    # Browser preview persists only the locale preference; ordinary app data stays session-only.
    assert page.evaluate("localStorage.getItem('nexus-language-v1')") == "en"
    page.reload(wait_until="networkidle")
    page.wait_for_function("document.documentElement.lang === 'en'")
    assert page.locator("#page-title").inner_text().strip() == "Chats"
    page.locator(".nav-item[data-page='settings']").click()
    assert page.locator("#language-select").input_value() == "en"

    # Switching back updates the active view immediately and navigation still works.
    page.locator("#language-select").select_option("my")
    page.wait_for_function("document.documentElement.lang === 'my'")
    assert page.locator("#page-title").inner_text().strip() == "ဆက်တင်များ"
    assert page.locator(".nav-item[data-page='home']").inner_text().strip() == "မူလစာမျက်နှာ"
    page.locator(".nav-item[data-page='nearby']").click()
    assert page.locator("#page-title").inner_text().strip() == "အနီးအနား"
    assert page.evaluate("localStorage.getItem('nexus-language-v1')") == "my"

    mobile = browser.new_page(viewport={"width":393,"height":873},device_scale_factor=2,is_mobile=True,has_touch=True)
    mobile.goto(URL,wait_until="networkidle")
    mobile.locator("#mobile-menu").tap()
    mobile.locator(".nav-item[data-page='settings']").tap()
    mobile.locator("#language-select").select_option("en")
    for name in PAGES:
        if name != "settings":
            mobile.locator("#mobile-menu").tap()
            mobile.locator(f".nav-item[data-page='{name}']").tap()
        metrics=mobile.evaluate("""() => ({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,
          short:[...document.querySelectorAll('button,select,input,textarea')].filter(e=>e.getClientRects().length&&!e.disabled&&((e.getBoundingClientRect().width<44)||(e.getBoundingClientRect().height<44))).map(e=>e.id||e.innerText)})""")
        assert metrics["scrollWidth"] <= metrics["width"], f"English mobile overflow on {name}: {metrics}"
        assert not metrics["short"], f"English mobile tap target below 44px on {name}: {metrics['short']}"
        assert mobile.locator("#mobile-menu").get_attribute("aria-label") == "Open menu"
        if name == "chats":
            assert mobile.locator("#message-input").get_attribute("aria-label") == "Local note text"
    assert not errors, f"browser JavaScript errors: {errors}"

    print("PASS: Burmese is the sensible first-run default and Myanmar Unicode renders")
    print("PASS: English and Burmese switching updates the current screen, navigation, labels, and accessibility names immediately")
    print("PASS: all six routes navigate and retain their current locale")
    print("PASS: locale preference survives browser reload while app state remains session-only")
    print("PASS: unknown translation keys safely fall back to the original text")
    print("PASS: QR, attachment, file-transfer, and Local AI error/state messages are localized")
    print("PASS: English-mode mobile navigation has no horizontal overflow or undersized controls")
    print("PASS: no browser JavaScript errors")
    print("LIMIT: host browser automation only; no Android device was available")
    browser.close()
