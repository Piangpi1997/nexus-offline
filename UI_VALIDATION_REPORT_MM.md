# UI validation report — 2026-10-03

**PRODUCTION VERIFICATION PENDING.** Local AI model at-rest encryption သည် **NOT IMPLEMENTED** အတိုင်းရှိနေဆဲဖြစ်သည်။ ဤ UI ပြင်ဆင်မှုကြောင့် Nearby၊ crypto၊ QR၊ Local AI၊ SAF၊ Keystore သို့မဟုတ် production status မပြောင်းထားပါ။

## UI ပြင်ဆင်မှု

Nearby လုပ်ဆောင်ချက်ခလုတ်များကို absolute positioning မှဖယ်ရှားပြီး ခေါင်းစဉ်၊ ဖော်ပြချက်နှင့် status နောက်တွင် ပုံမှန် content flow အတိုင်းထားသည်။ မိုဘိုင်းအကျယ်ကျဉ်းသည့်အခါ ခလုတ်များ wrap လုပ်ပြီး card အတွင်းတွင်ရှိသည်။ လက်ခံရရှိသော attachment များတွင် ခေါင်းစဉ်/status → wrap လုပ်နိုင်သော file metadata/description → actions အစီအစဉ်ထားပြီး ဖော်ပြချက်နှင့် actions ကြား အနည်းဆုံး 16px ခြားထားသည်။

Home status cards၊ hero outline နှင့် shadow များကို ပေါ့ပါးစေပြီး Burmese စာသားအတွက် line-height နှင့် natural wrapping တိုးထားသည်။ AES-GCM၊ SHA-256၊ Nearby progress/cancel နှင့် QR bootstrap အကြောင်းကို တိုတောင်းသော row များအဖြစ် ခွဲပြထားပြီး မပြီးသေးသော camera pairing၊ Nearby radio၊ Local AI နှင့် physical-device verification ကို သီးခြားဖော်ပြထားသည်။ NOT EXECUTED ကို PASS ဟု မပြောင်းထားပါ။ Nearby control ID များ၊ event handler များ၊ navigation/data attributes နှင့် bridge/data flow များကို ထိန်းသိမ်းထားသည်။ Root source နှင့် Android asset mirror တို့၏ HTML/CSS/JS/localization ဖိုင်များကို SHA-256 ဖြင့်တိုက်စစ်ရာ ဖိုင်တစ်စုံစီ တူညီကြောင်း အတည်ပြုထားသည်။

ပြင်ဆင်ထားသော source ဖိုင်များမှာ `index.html`, `styles.css`, `app.js`, `i18n.js` နှင့် တူညီသော Android WebView assets `android/app/src/main/assets/` အောက်ရှိဖိုင်လေးခုဖြစ်သည်။ Regression tests တွင် `tests/ui-responsive-regression.py`, `tests/attachment-ui-security.py` နှင့် screenshot path ကိုထိန်းသိမ်းအောင် ပြင်ထားသော `tests/mobile-navigation.py` ပါဝင်သည်။

## စမ်းသပ်မှု

Chromium browser စမ်းသပ်မှုများဖြစ်သော `mobile-navigation.py`, `i18n-navigation.py`, `mock-nearby-integration.py`, `attachment-ui-security.py` နှင့် `ui-responsive-regression.py` အားလုံး **PASS** ဖြစ်သည်။ Localization/navigation၊ mocked ECDH + AES-GCM၊ tamper/replay rejection၊ encrypted message/file flow၊ QR/attachment/Local AI UI state များ အောင်မြင်ခဲ့သည်။

320, 360, 390, 412, 480px အကျယ်များနှင့် route ခြောက်ခုတွင် horizontal overflow မတွေ့ပါ။ မြင်ရသော control များ 44px အောက်မကျ၊ `hidden` element များ မပေါ်လာ၊ Burmese နှင့် ရှည်လျားသော English/technical text များ wrap လုပ်နိုင်ခဲ့သည်။ Drawer navigation/containment နှင့် 125% text scaling ကို 320, 390, 480px တွင် စမ်းသပ်ထားသည်။ Attachment regression သည် viewport ငါးခုစလုံးတွင် description-actions ကြား အနည်းဆုံး 16px ခြားနေမှု၊ action row သည် card အတွင်းရှိမှု၊ မထပ်သော အနည်းဆုံး 44px ခလုတ်များကို အတည်ပြုခဲ့သည်။

Android JVM unit tests **64/64 PASS**, failure/error/skip **0**. Attachment lifecycle/metadata/cipher, group/native protocol, QR pairing နှင့် Local AI model store/inspector/engine စမ်းသပ်မှုများ ပါဝင်သည်။ Official Gradle wrapper မှ `:app:testDebugUnitTest :app:assembleDebug` build အောင်မြင်ခဲ့သည်။ SDK path ကို ထို command အတွက်သာ သတ်မှတ်ခဲ့သည်။ လက်ရှိ config အတိုင်း Debug variant တွင် minification မဖွင့်ထားပါ; Release R8/minification နှင့် ProGuard rules မပြောင်းထားပါ။

## Screenshot နှင့် APK

မပြင်မီ/ပြင်ပြီး Home: [320px မပြင်မီ](ui-validation/before/home-320.png) · [320px ပြင်ပြီး](ui-validation/after/home-320.png) · [390px ပြင်ပြီး](ui-validation/after/home-390.png)

မပြင်မီ/ပြင်ပြီး Nearby: [320px မပြင်မီ](ui-validation/before/nearby-320.png) · [320px ပြင်ပြီး](ui-validation/after/nearby-320.png) · [390px ပြင်ပြီး](ui-validation/after/nearby-390.png)

Long description နှင့် action row ပါသော mock attachment preview: [320px](ui-validation/after/attachment-actions-320.png) · [390px](ui-validation/after/attachment-actions-390.png)

Verified local Debug APK (not included in this source-only repository): SHA-256 `a0423320d6dd887a774a0be561dfd10d31a8c8881488cdfa6ad374f25ab4ccb6`. The APK itself is intentionally excluded.

**Physical-device ကန့်သတ်ချက်:** Android ဖုန်း/emulator runtime test သို့မဟုတ် physical screenshot မရယူထားပါ။ ဤ computer တွင် `adb` မရှိသလို ခွင့်ပြုထားသော Android စမ်းသပ်ကိရိယာလည်း မရှိပါ။ Screenshots များသည် host Chromium mobile emulation သာဖြစ်သည်။ Android radio/Nearby၊ camera pairing၊ SAF/Keystore file behavior၊ Local AI inference နှင့် physical-device/10-phone verification များသည် မစစ်ရသေးပါ။

Validation အတွက် အသုံးပြုခဲ့သော working directory တွင် Git metadata မရှိခဲ့ပါ။ ယခု public repository တွင် source-only scope ဖြင့် သီးခြားစီထည့်သွင်းထားသည်။