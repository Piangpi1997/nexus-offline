# NEXUS OFFLINE — Android ဖုန်းစမ်းသပ်ရန် လမ်းညွှန်

**လက်ရှိအခြေအနေ — စမ်းသပ်ရန် ပြင်ဆင်ထားပြီး၊ ဖုန်းပေါ်တွင် မစမ်းရသေးပါ။** ဤကွန်ပျူတာတွင် `/dev/kvm` မရှိ၊ ADB တွင် ဖုန်းမတွေ့၊ ယခင် software x86_64 AVD များလည်း boot gate မပြည့်ခဲ့ပါ။ အရင်က အဆင်သင့်မဖြစ်သေးသော emulator သို့ install command တစ်ကြိမ် စမ်းခဲ့ရာ PackageManager မရှိသဖြင့် `cmd: Can't find service: package` ဖြင့်ပျက်ကွက်ပြီး app မတပ်ဆင်နိုင်ခဲ့ပါ။ ယခုစစ်ဆေးမှုတွင် gate မပြည့်၍ install ထပ်မစမ်းခဲ့ပါ။ `PHYSICAL_DEVICE_TEST_MM.md` သည် Android 24+ ဖုန်းအစစ်ဖြင့် နောက်တစ်ဆင့် စမ်းသပ်ရာတွင် အသုံးပြုရန်ဖြစ်သည်။ Browser mock နှင့် JVM tests ကို ဖုန်းစမ်းသပ်မှု PASS အဖြစ် မရေတွက်ပါနှင့်။

## စမ်းသပ်မည့် APK တည်ဆောက်ခြင်း

ဤ public source-only repository တွင် APK မပါဝင်ပါ။ Repository root မှ Debug APK ကို locally တည်ဆောက်ပြီး hash ကို မှတ်တမ်းတင်ပါ:

```sh
cd android
./gradlew assembleDebug --no-daemon
cd ..
APK=android/app/build/outputs/apk/debug/app-debug.apk
sha256sum "$APK"
```

ဖုန်းနှင့်စမ်းသပ်မည့် ကွန်ပျူတာတွင် APK ကိုထားပြီး USB debugging ကိုဖွင့်ပါ။ Android “Allow USB debugging?” ခွင့်ပြုချက်ကို ဖုန်းပေါ်တွင် ကိုယ်တိုင်အတည်ပြုပါ။ Install မလုပ်မီ hash၊ package/version နှင့် API/ABI ကို စစ်ဆေးပါ။

```sh
cd /path/to/nexus-offline
sha256sum android/app/build/outputs/apk/debug/app-debug.apk
adb devices -l
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abi
```

ဖုန်း serial နံပါတ်ပါသော `device` အဖြစ်ပြမှ ဆက်လုပ်ပါ။ `offline`, `unauthorized` သို့မဟုတ် list ဗလာဖြစ်နေပါက မတပ်ဆင်ပါနှင့်။ Local build ကို သီးခြားမှတ်တမ်းတင်ထားသော trusted hash ရှိပါက install မလုပ်မီ နှိုင်းယှဉ်ပါ။

## တစ်ဖုန်း install/launch helper

Project root မှ အောက်ပါ executable helper ကိုသုံးနိုင်သည်။ ၎င်းသည် ADB ချိတ်ထားသော authorized ARM64 device တစ်လုံးတည်းနှင့် Android API ≥24 ကိုစစ်ပြီး model/API/ABI, available storage, total/available RAM, APK SHA-256/package/version ကိုပြပြီးမှ `install -r` နှင့် `am start -W` ကိုလုပ်သည်။ Install result နှင့် ActivityManager launch status ကို သီးခြားဖော်ပြပြီး screenshot/logcat evidence ကိုသိမ်းသည်။ Device မရှိခြင်း၊ API နိမ့်ခြင်း၊ ARM64 မဟုတ်ခြင်း သို့မဟုတ် APK hash မကိုက်ခြင်းတွင် install မလုပ်ဘဲရပ်မည်။ Local AI import/inference ကို helper က မလုပ်ပါ။

```sh
cd /path/to/nexus-offline
APK=android/app/build/outputs/apk/debug/app-debug.apk
sha256sum "$APK"  # record and verify the build identity before installing
./tools/android-device-smoke-test.sh "$APK"
```

Evidence ကို `test-evidence/android/<serial>/<UTC timestamp>/` အောက်တွင် screenshot နှင့် logcat အဖြစ်သိမ်းသည်။ လက်ရှိ ADB device list ဗလာဖြစ်သဖြင့် helper သည် install မတိုင်မီ gate တွင်သာရပ်ခဲ့ပြီး ဖုန်းစမ်းသပ်မှု `NOT EXECUTED` ဖြစ်သည်။

## Install၊ launch နှင့် ပြန်တပ်ဆင်ခြင်း

ပထမဆုံး install:

```sh
adb install android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.nexusoffline.debug/com.nexusoffline.MainActivity
```

တူညီသော debug key ဖြင့် တည်ဆောက်ထားသည့် APK ကို app data မဖျက်ဘဲ update လုပ်ရန်:

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

သန့်ရှင်းသော install ကို စမ်းရန် — ဤ command က test app ၏ local data အားလုံးကိုဖျက်မည်:

```sh
adb uninstall com.nexusoffline.debug
adb install android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.nexusoffline.debug/com.nexusoffline.MainActivity
```

စမ်းသပ်ပြီးသော disposable install ၏ Android Clear storage/data အပြုအမူကို စမ်းရန်:

```sh
adb shell pm clear com.nexusoffline.debug
adb shell am start -n com.nexusoffline.debug/com.nexusoffline.MainActivity
```

## Screenshot နှင့် logcat evidence

စမ်းသပ်မှုမစမီ disposable test data သာသုံးပါ။ အခြားသူ၏စာ၊ ကိုယ်ရေးကိုယ်တာ၊ QR secret၊ key သို့မဟုတ် model အကြောင်းအရာကို screenshot/log ထဲမထည့်ပါနှင့်။ Device များစွာချိတ်ထားလျှင် `adb` command တိုင်းတွင် `-s <SERIAL>` ထည့်ပါ။

```sh
mkdir -p test-evidence/screenshots
adb exec-out screencap -p > test-evidence/screenshots/01-home.png
```

Screen တစ်ခုစီသို့ ကိုယ်တိုင်သွားပြီး အမည်ခွဲ၍ ထပ်သိမ်းပါ — `02-chat.png`, `03-connections.png`, `04-attachments.png`, `05-qr.png`, `06-local-ai.png`, `07-settings-burmese.png`, `08-settings-english.png`, `09-diagnostics.png`။ ခွင့်ပြုချက် dialog၊ error state နှင့် restart ပြီးနောက် language state ကိုပါ လိုအပ်သလို screenshot ယူပါ။

```sh
adb logcat -c
adb logcat -v threadtime > test-evidence/android-logcat.txt
```

စမ်းသပ်မှုအားလုံးပြီးလျှင် `Ctrl-C` ဖြင့် logcat ကိုရပ်ပါ။ `FATAL EXCEPTION`, `AndroidRuntime`, `SecurityException`, permission, WebView/Chromium, `FileProvider`, Keystore နှင့် Nearby အမှားများကို စစ်ပါ။ Log များကို မမျှဝေမီ စမ်းသပ်စာသား/ကိုယ်ရေးအချက်အလက်များ ဖယ်ရှားပါ။ App ကို မ run ရသေးလျှင် app logcat မရှိကြောင်း မှတ်တမ်းတင်ပါ။

## ဖုန်းတစ်လုံးဖြင့် လုပ်ရန် အစီအစဉ်

စမ်းသပ်မှုတစ်ခုစီတွင် `PASS`, `FAIL` သို့မဟုတ် `NOT EXECUTED` တစ်ခုကို ရွေးပြီး ဖုန်းမော်ဒယ်၊ Android API၊ လုပ်ဆောင်ချက်နှင့် screenshot/log reference ကို မှတ်ပါ။ Runtime gate မပြည့်ပါက `BLOCKED BY HOST ANDROID RUNTIME ENVIRONMENT` ဟုသာ မှတ်ပါ။

- [ ] APK SHA-256 စစ်ပြီး install လုပ်နိုင်မှု စမ်းပါ။ Package `com.nexusoffline.debug` နှင့် version ကို မှတ်ပါ။
- [ ] App launch၊ Home ပေါ်လာမှု၊ crash မရှိမှု၊ `AndroidRuntime` error မရှိမှု စစ်ပါ။
- [ ] ပထမဖွင့်ချိန် language သည် မြန်မာဖြစ်ကြောင်း စစ်ပါ။ မြန်မာ Unicode၊ punctuation၊ စာကြောင်းအရှည်၊ font scale နှင့် clipping ကိုကြည့်ပါ။
- [ ] Settings → Language မှ English သို့ပြောင်း၊ Home/Chat/Connections/Settings/Diagnostics သို့သွား၊ app ကိုပိတ်ဖွင့်ပြီး English ရွေးချယ်မှု တည်မြဲကြောင်းစစ်ပါ။ မြန်မာသို့ ပြန်ပြောင်းပြီး restart persistence ကိုထပ်စစ်ပါ။
- [ ] Home, Chat, Connections/Nearby, Attachments, QR, Local AI, Settings, Diagnostics အားလုံးဖွင့်ပါ။ Logo/Home, drawer open/close, scrim, system Back, route ပြောင်းပြီးနောက် locale၊ horizontal overflow နှင့် touch targets စစ်ပါ။
- [ ] Portrait/landscape, keyboard show/hide, background/foreground, force-stop/relaunch နှင့် process restart ကိုစမ်းပြီး state မပျောက်ခြင်း၊ crash မဖြစ်ခြင်း၊ WebView မျက်နှာပြင်ဖြူမနေခြင်းကို အတည်ပြုပါ။
- [ ] Nearby permission grant/deny/retry ကို သင့် Android API အလိုက်စမ်းပါ။ Android Settings မှ ပြန်ခွင့်ပြုပြီး UI ပြန်ကောင်းလာမှု၊ Bluetooth/Wi-Fi radio ပိတ်ထားစဉ် အမှားဖော်ပြမှုကို စစ်ပါ။ Permission စစ်ဆေးမှု PASS သည် device discovery PASS မဟုတ်ပါ။
- [ ] QR Pairing ကို camera ခွင့်ပြု/မပြု စမ်းပါ။ ဖုန်းတစ်လုံးတည်းဖြင့် real scan/peer pairing PASS မသတ်မှတ်ပါနှင့်။ Two-phone section ကိုအသုံးပြုပါ။
- [ ] Local AI model မရှိသေးချိန်တွင် **`LOCAL AI — MODEL NOT INSTALLED`** ကို တိတိကျကျပြပြီး runtime/model status သီးခြားဖြစ်ကြောင်းစစ်ပါ။ Real model မတင်/မload မလုပ်ရသေးလျှင် Test Inference ကို အောင်မြင်သလိုမပြပါနှင့်။
- [ ] Device ပေါ်တွင် တကယ်စမ်းသည့်အခါ Internet/mobile data ပိတ်ပြီး user-selected model ဖြင့်သာစမ်းပါ။ Phone model, Android/API, ABI, total/available RAM, model filename/version/size, import/load time, RAM after load, first-token latency, measurable generation speed, cancellation latency, crash/OOM နှင့် offline state ကို `PHYSICAL_DEVICE_TEST_CHECKLIST_MM.md` ထဲ မှတ်ပါ။ Helper သည် model import/inference ကို fake automate မလုပ်ပါ။
- [ ] App restart၊ Android system Clear storage/data လုပ်ပြီး နောက်ထပ် launch ကိုစမ်းပါ။ Disposable data သာသုံးပါ။
- [ ] Screenshot/logcat စစ်ပြီး crash၊ permission leak သို့မဟုတ် sensitive app logging ရှိ/မရှိ မှတ်ပါ။

## Attachment၊ Keystore နှင့် encrypted storage (ဖုန်း/peer လိုအပ်သည်)

Attachment receive/import, list, open/share/export နှင့် integrity ကို အဓိပ္ပာယ်ရှိစွာ စမ်းရန် ဒုတိယ physical phone ကိုသုံးပါ။ အောက်ပါအချက်များကိုသာ test file ဖြင့်လုပ်ပါ။ Private key သို့မဟုတ် plaintext secret ကို evidence ထဲမထည့်ပါနှင့်။

- [ ] Local encrypted state ရေး/ဖတ်၊ process restart ပြီး state တည်မြဲမှု၊ app private storage ထဲ plaintext မကျန်မှု စစ်ပါ။
- [ ] Nearby မှ test attachment လက်ခံ၊ encrypted-at-rest state၊ list refresh၊ SHA-256/integrity စစ်ပါ။ Tampered/corrupt test data ကိုငြင်းပယ်ပြီး delete ပြုလုပ်နိုင်သေးကြောင်းစစ်ပါ။
- [ ] Verified file ကို open, Android share chooser မှ share, SAF သို့ export လုပ်ပါ။ Exported bytes/hash တူကြောင်းနှင့် temporary plaintext file အောင်မြင်/မအောင်မြင် နှစ်မျိုးလုံးပြီး cleanup လုပ်ကြောင်းစစ်ပါ။
- [ ] ရှိပါက disposable legacy fixture ကို migrate လုပ်ပါ။ Encrypted copy commit မအောင်မြင်လျှင် legacy မူရင်းမပျက်ဘဲ ကျန်ကြောင်းစစ်ပါ။
- [ ] Process restart နှင့် Android Clear storage/data ပြီးနောက် local attachments, transfer temp, export temp နှင့် Keystore state အခြေအနေကိုစစ်ပါ။

## Two-phone Nearby offline acceptance

**ဖုန်းနှစ်လုံး မရှိပါက `NOT EXECUTED` ဟုမှတ်ပါ။** Browser/mock tabs ကို အစားမထိုးပါနှင့်။ ဖုန်းနှစ်လုံးစလုံးတွင် တူညီသော Debug APK ကိုတပ်ပြီး test data သာသုံးပါ။ Internet နှင့် mobile data ကိုပိတ်ပါ၊ သို့သော် Nearby အတွက် Bluetooth/Wi-Fi radios များကို ဖွင့်ထားပါ။ Internet ပါသော Wi-Fi router ကိုမသုံးပါနှင့်။

ဖုန်းနှစ်လုံး authorization/install ပြီးနောက် **read-only diagnostics** အတွက် `DEVICE_SERIALS=<serialA>,<serialB> ./tools/two-phone-test-preflight.sh` ကိုသုံးနိုင်သည်။ ၎င်းသည် model/API/ABI/app version/permission state/radio settings ကိုဖတ်ပြသော်လည်း radios မပြောင်း၊ pairing/message/file transfer မလုပ်၊ test PASS မကြေညာပါ။ Test flow နှင့် evidence ကို လူကိုယ်တိုင်ပြီးစီးမှ result သတ်မှတ်ပါ။

- [ ] Phone A advertise လုပ်၊ Phone B discover လုပ်နိုင်မှု.
- [ ] Connection request, reject/accept, explicit user confirmation.
- [ ] Pairing QR create/scan; expired, replayed, malformed QR rejection.
- [ ] Authentication fingerprint/code ကို **လူနှစ်ဦးလုံး နှိုင်းယှဉ်အတည်ပြု**; QR scan တစ်ခုတည်းကို identity verification မသတ်မှတ်ပါနှင့်။
- [ ] Secure encrypted session; Burmese/English message A→B နှင့် B→A.
- [ ] Duplicate message နှင့် tampered ciphertext ကို reject လုပ်မှု.
- [ ] Disconnect, offline queued message, reconnect, queued delivery နှင့် local history after restart.
- [ ] Attachment receive/send; consent, progress, cancel/retry-by-reselection, corrupt payload rejection, SHA-256 match, encrypted storage, open/share/SAF export.
- [ ] Android permission deny/revoke/grant, Bluetooth/Wi-Fi disable/enable, background/foreground နှင့် process restart စမ်းပါ။
- [ ] Test IDs, offline အခြေအနေ, OS/API, evidence paths, failures, throughput, logcat review ကိုမှတ်တမ်းတင်ပါ။

## Ten-phone scale test

2-phone offline test များအားလုံး PASS မဖြစ်မချင်း မစတင်ပါနှင့်။ ဖုန်း 10 လုံးရရှိလျှင် Internet မပါသော controlled environment တွင် group create/join, message, concurrent message, duplicate/replay rejection, disconnect/rejoin, queued delivery, file transfer, member leave နှင့် long-run stability ကိုစမ်းပါ။ Result ကို physical test evidence အဖြစ်သီးခြားသိမ်းပါ။ Protocol simulation ကို 10-phone PASS အဖြစ်မဖော်ပြပါနှင့်။

ဖြည့်သွင်းရန် [TEN_PHONE_ACCEPTANCE_MM.md](TEN_PHONE_ACCEPTANCE_MM.md) template ကိုသုံးပါ။ 2-phone result သည် direct physical evidence ဖြင့် `PASS` ဖြစ်မှသာ 10-phone test စတင်နိုင်သည်။

## Result မှတ်တမ်း

| စမ်းသပ်ချက် | Result | ဖုန်း/API | Evidence path / မှတ်ချက် |
|---|---|---|---|
| APK install/launch | NOT EXECUTED | — | Runtime gate မပြည့်သေး |
| Burmese/English + restart | NOT EXECUTED | — | — |
| Android UI/permissions/lifecycle | NOT EXECUTED | — | — |
| Keystore/attachments/clear-data | NOT EXECUTED | — | — |
| QR camera | NOT EXECUTED | — | — |
| Two-phone offline Nearby | NOT EXECUTED | — | ဖုန်း 2 လုံး လိုအပ် |
| Ten-phone offline | NOT EXECUTED | — | 2-phone PASS ပြီးမှ |
| Local AI inference | NOT EXECUTED | — | ARM64 + model + offline လိုအပ် |
