# QR modal UI စစ်ဆေးမှု (2026-10-04)

Android native QR dialog ကို Burmese/English localization, accessibility, expiry state, button sizing နှင့် dismissal/focus behavior များအတွက် ပြင်ဆင်ထားသည်။ Public ID သည် QR မှ သီးခြားဖော်ပြထားပြီး QR image ကို square `FIT_CENTER` ဖြင့်ပြသသည်။ Fingerprint security hint, expiry timer, QR အသစ်ဖန်တီးရန်နှင့် ပိတ်ရန် action များ ပါဝင်သည်။ QR scan Activity-result processing တွင် မမျှော်လင့်ထားသော exception ကို generic error state အဖြစ် ကိုင်တွယ်ပြီး Activity ထဲသို့ မလွတ်စေပါ။

## Automated စစ်ဆေးမှု

Clean Android build အောင်မြင်ပြီး Android JVM unit test **81/81 PASS** ဖြစ်သည်။ `PairingQrTest` သည် **6/6 PASS** ဖြစ်ကာ payload parsing, endpoint check, expiry/replay rejection, cancellation နှင့် malformed scanner-result handling ကို စစ်ထားသည်။ Chromium/browser-host suite **6/6 PASS** ဖြစ်ပြီး QR modal visual fixture တစ်ခုတည်းတွင် viewport scenario **8/8 PASS** ဖြစ်သည်။

Viewport fixture သည် modal viewport အတွင်းရှိခြင်း၊ စာသား clipping/horizontal overflow မရှိခြင်း၊ square placeholder နှင့် အနည်းဆုံး 44px browser button target များ၊ Escape dismissal နှင့် focus return ကို စစ်သည်။ ဤ visual fixture သည် native Android screenshot မဟုတ်ဘဲ pairing QR payload ကို render မလုပ်ပါ။

## Screenshot-ууд

**Доорх бүх зураг Chromium viewport fixture-ээс авсан. QR дүрс нь зориудаар уншигдахгүй mock pattern; Android төхөөрөмжийн зураг биш, бодит QR үүсгэлт/уншилт, fingerprint баталгаажуулалт эсвэл pairing-ийн PASS биш.**

- Burmese, 320px: [зураг](ui-validation/after/qr-modal/qr-modal-burmese-320.png)
- Burmese, 360px: [зураг](ui-validation/after/qr-modal/qr-modal-burmese-360.png)
- Burmese, 390px: [зураг](ui-validation/after/qr-modal/qr-modal-burmese-390.png)
- Burmese, 412px: [зураг](ui-validation/after/qr-modal/qr-modal-burmese-412.png)
- English, 390px: [зураг](ui-validation/after/qr-modal/qr-modal-english-390.png)
- Expired state, 390px: [зураг](ui-validation/after/qr-modal/qr-modal-expired-390.png)
- Large text, 320px: [зураг](ui-validation/after/qr-modal/qr-modal-large-text-320.png)
- Short viewport, 320×480px: [зураг](ui-validation/after/qr-modal/qr-modal-small-height-320x480.png)

**Android төхөөрөмжээр QR scan хийх, QR pairing болон Nearby radio connection энэ validation-д хийгдээгүй. PRODUCTION VERIFICATION PENDING.**
