(() => {
  'use strict';
  const EN = {
    'NEXUS OFFLINE မူလစာမျက်နှာ':'NEXUS OFFLINE home page',
    'မူလစာမျက်နှာ':'Home', 'စကားပြောခန်း':'Chats', 'အနီးအနား':'Nearby', 'ဆက်တင်များ':'Settings',
    'သင့်စက်ထဲမှာပဲ':'Only on your device', 'Cloud account မလိုပါ။':'No cloud account required.',
    'ကျွန်ုပ်':'Me', 'LOCAL PROFILE':'LOCAL PROFILE', 'အဓိက မီနူး':'Main navigation', 'အခြား မီနူး':'More navigation',
    'ပရိုဖိုင်ပြင်ရန်':'Edit profile', 'မီနူးပိတ်ရန်':'Close menu', 'မီနူးဖွင့်ရန်':'Open menu',
    'စစ်ဆေးနေသည်':'Checking…', 'အလင်း/အမှောင် ပြောင်းရန်':'Toggle light/dark theme', 'အသွင်အပြင်':'Appearance',
    'သင့်ကိုယ်ပိုင် offline workspace':'YOUR PRIVATE OFFLINE WORKSPACE', 'သင့်ကွန်ရက်၊':'Your network,', 'သင့်စည်းမျဉ်း။':'your rules.',
    'အနီးအနား ဆက်သွယ်မှု၊ local AI နဲ့ သင့်စက်ထဲက chat history — တစ်နေရာတည်းမှာ။':'Nearby connections, local AI, and chat history on your device — all in one place.',
    'ဒီနေ့':'Today', 'Internet မရှိလည်း':'Even without internet,', 'အနီးကပ်နေပါ။':'stay connected.',
    'Cloud server မလိုတဲ့အနီးအနား chat နဲ့ သင့်စက်ထဲမှာပဲ အလုပ်လုပ်မယ့် AI ကို ရည်ရွယ်ထားပါတယ်။ သင့်စက်ပေါ်က လုပ်ဆောင်ချက်နဲ့ မရရှိသေးတဲ့ native feature တွေကို သီးခြားဖော်ပြထားပါတယ်။':'Designed for nearby chat without a cloud server and AI that runs on your device. Device-ready features and native features that are not yet available are shown separately.',
    'အနီးအနားကို ကြည့်ရန်':'Explore Nearby', 'စနစ်စစ်ဆေးရန်':'Run diagnostics', 'တစ်ချက်ကြည့် အခြေအနေ':'Status at a glance',
    'အသေးစိတ်ကြည့်ရန်':'View details', 'အင်တာနက် အခြေအနေ စစ်ဆေးနေသည်':'Checking internet status',
    'Android native bridge မတွေ့ရှိသေးပါ':'Android native bridge not detected', 'Web preview — native မချိတ်ထား':'Web preview — native bridge not connected',
    'မရရှိသေး':'Not available', 'မတပ်ဆင်ရသေး':'Not installed', 'Local AI engine/model မတပ်ဆင်ရသေးပါ':'Local AI engine/model is not installed',
    'Fake response မထုတ်ပေးပါ':'No fake responses are generated', 'စတင်အသုံးပြုရန်':'Get started',
    'Local chat မှတ်စုရေးရန်':'Write a local chat note', 'ဒီစက်ထဲမှာပဲ သိမ်းမည့် draft/queue':'A draft/queue saved only on this device',
    'Local AI စနစ်အခြေအနေ':'Local AI status', 'Model မရှိပါက setup state ကိုပြမည်':'Shows setup status when no model is installed',
    'Native pairing အကြောင်း':'About native pairing', 'Android app လိုအပ်သည့် feature များ':'Features that require the Android app',
    'သင့်ဒေတာ၊ သင့်စက်ထဲမှာ':'Your data stays on your device', 'Storage အခြေအနေ စစ်ဆေးနေသည်။':'Checking storage status.',
    'Encrypted data ဖတ်မရပါက app က အဟောင်းကို အလိုအလျောက် မဖျက်/မရေးပါ။':'If encrypted data cannot be read, the app will not automatically delete or overwrite it.',
    'Production verification:':'Production verification:', 'LOCAL STORAGE':'LOCAL STORAGE',
    'မပို့ရသေးသောစာများကို queue ထဲသိမ်းထားပြီး secure Nearby session ရှိမှသာ ပို့မည်။':'Unsent messages stay in the queue and are sent only through a secure Nearby session.',
    'Offline group':'Offline group', 'Local မှတ်စုအသစ်':'New local note', 'ရှာဖွေရန်…':'Search…',
    'စကားပြောခန်းရှာရန်':'Search chats', 'LOCAL THREADS':'LOCAL THREADS', 'ကိုယ်ပိုင်မှတ်စု':'Personal notes',
    'ဒီစက်ထဲမှာသာ':'Only on this device', 'ကိုက်ညီသော မှတ်စုမရှိပါ။':'No matching notes.',
    'စောင် စောင့်ဆိုင်းနေသည်':'waiting', 'Native transport မရှိသေးသဖြင့် မပို့ရသေးပါ':'Not sent because native transport is unavailable',
    'Local only · ပို့သူမရှိ':'Local only · no recipient', 'Group member action':'Group member action',
    'စကားပြောခန်းအချက်အလက်':'Chat information', 'ဒီစက်ထဲက မှတ်စုသာ':'Note stored only on this device',
    'ဤနေရာတွင်ရေးသမျှကို ဤ browser/device ထဲတွင်ပဲ သိမ်းမည်။ Nearby connection မရှိသဖြင့် အခြား device သို့ ပို့မည်မဟုတ်ပါ။':'Anything written here stays in this browser/device. Without a Nearby connection, it will not be sent to another device.',
    'သင့် local မှတ်စုကို စတင်ပါ':'Start your local note', 'ပို့ရန်မဟုတ်ပါ။ စာကို local queue ထဲမှာသိမ်းပြီး status ကို ထင်ရှားစွာပြပါမယ်။':'This note will not be sent. It is saved in the local queue and its status is shown clearly.',
    'Local မှတ်စုစာသား':'Local note text', 'ဒီစက်ထဲမှာပဲ သိမ်းမည့် မှတ်စုရေးပါ…':'Write a note to keep on this device…',
    'ဒီစက်ထဲသာ · မပို့ရသေး':'This device only · not sent', 'Local queue ထဲသိမ်းရန်':'Save to local queue',
    'Queue ထဲသိမ်းရန်':'Save to queue', 'ချိတ်ဆက်မှု ပြန်စစ်ရန်':'Check connection again', 'မူလ queue ကို စောင့်ဆိုင်းနေသည်':'The local queue is waiting',
    'သင့်စက်ထဲတွင်သာ inference လုပ်မည် — runtime/model အောင်မြင်စွာ load ဖြစ်မှ အသုံးပြုနိုင်သည်။':'Inference runs only on your device. It is available after the runtime and model load successfully.',
    'စစ်ဆေးရန်လို':'Setup required', 'အခြေအနေ မစစ်ရသေး':'Status not checked yet',
    'Android native runtime နဲ့ model ကို အရင်စစ်မည်။ Web preview တွင် inference မလုပ်ပါ။':'The Android native runtime and model are checked first. The web preview does not run inference.',
    'အသုံးပြုမည့် local AI model':'Local AI model to use', 'Model မရွေးရသေးပါ':'No model selected',
    '.litertlm model ထည့်ရန်':'Import .litertlm model', 'Load':'Load', 'Unload':'Unload', 'Model မတင်ရသေးပါ':'Model is not loaded',
    'Prompt (ဤစက်ပေါ်တွင်သာ)':'Prompt (on this device only)', 'Loaded model သို့ မေးခွန်းရေးပါ…':'Ask the loaded model a question…',
    'Generate':'Generate', 'Cancel generation':'Cancel generation', 'Local AI response':'Local AI response',
    'အဖြေ မရှိသေးပါ':'No response yet', 'Status ပြန်စစ်ရန်':'Check status again', 'အရေးကြီးသည့်အချက်များ':'Important notes',
    'သင့် model ကိုရွေးသွင်းပါ':'Import a model you choose', '.litertlm ဖိုင်ကို Android document picker မှ private app storage သို့ copy လုပ်မည်။ Auto-download မရှိပါ။':'The .litertlm file is copied from the Android document picker to private app storage. There is no automatic download.',
    'Model size, RAM, supported backend နှင့်လိုင်စင် ကွာခြားသည်။ Load ကိုယ်တိုင်က compatibility check အဆုံးသတ်ဖြစ်ပြီး အာမခံမပေးပါ။':'Model size, RAM, supported backends, and licenses vary. Loading is the final compatibility check; compatibility is not guaranteed.',
    'Model weight များကို app ကမဖြန့်ဝေပါ။ အသုံးပြုသူရွေးချယ်သော model ၏လိုင်စင်ကိုစစ်ပါ။ Import ပြီးမှစက်ထဲတွင်ပဲ inference စတင်မည်; cloud fallback မရှိပါ။ Clear data လုပ်လျှင် model/cache ကိုပါ ဖျက်မည်။':'The app does not distribute model weights. Check the license of any model you choose. On-device inference starts after import; there is no cloud fallback. Clearing data also deletes the model/cache.',
    'Runtime အခြေအနေ':'Runtime status',
    'Model အခြေအနေ':'Model status',
    'လိုက်ဖက်မှု စစ်ရန်':'Compatibility check',
    'Model ထည့်တင်ရန်':'Load model',
    'Model ဖြုတ်ရန်':'Unload model',
    'Model ဖျက်ရန်':'Delete model',
    'Inference စမ်းရန်':'Test inference',
    'ဖိုင်အမည်':'Filename',
    'အရွယ်အစား / လိုအပ်သည့် storage':'Size / required storage',
    'စစ်ဆေးတွေ့ရှိသည့် format':'Detected format',
    'Context အရှည်':'Context length',
    'လိုင်စင်':'License',
    'ခန့်မှန်း RAM လိုအပ်ချက်':'Estimated RAM requirement',
    'လက်ရှိ RAM':'Available RAM',
    'လက်ရှိ private storage':'Available private storage',
    'လိုက်ဖက်မှု':'Compatibility',
    'Load လုပ်ချိန်တွင် initialize လုပ်မည်':'Initialized when loading',
    'Android runtime မရရှိသေးပါ':'Android runtime unavailable',
    'Model တင်ပြီးပါပြီ':'Model loaded',
    'Model ရွေးပြီး စစ်ဆေးပါ':'Select and inspect a model',
    'Network inference မရှိ၊ cloud fallback မရှိ':'No network inference or cloud fallback',
    'Web preview တွင် Local AI inference မရပါ။ Android runtime မရရှိသေးပါ။ Cloud fallback မရှိပါ။':'Local AI inference is unavailable in the web preview. Android runtime unavailable. No cloud fallback.',
    'ဖိုင်ထဲတွင် မဖော်ပြထားပါ':'Not declared in file',
    'tokens (metadata)':'tokens (metadata)',
    'မသိရှိပါ':'Unknown',
    'အတည်မပြုရသေး — upstream ကိုစစ်ပါ':'Unverified — check upstream',
    'rough estimate':'rough estimate',
    'အောင်မြင်':'Pass',
    'Container':'Container',
    'Runtime / ABI':'Runtime / ABI',
    'Estimated RAM fits now':'Estimated RAM fits now',
    'Free storage for another copy':'Free storage for another copy',
    'Native load remains required; inference not proven':'Native load remains required; inference not proven',
    'Android app လိုအပ်သည်':'Android app required',
    'Android document picker တွင် local model ရွေးပါ':'Choose a local model in the Android document picker',
    'Compatibility Check ကို model ရွေးပြီး လုပ်နိုင်သည်။':'Select a model to run the compatibility check.',
    'Imported model ဖျက်မလား?':'Delete this imported model?',
    'Private app storage မှ model file ကို အပြီးဖျက်မည်။':'Permanently delete this model file from private app storage.',
    'Model ဖျက်မရပါ':'Could not delete model',
    'Local model ကိုဖျက်ပြီးပါပြီ':'Local model deleted',
    'Loaded local model သို့ prompt ပို့လိုက်သည် — device ထဲတွင်သာ':'Prompt sent to loaded local model — on this device only',
    'Local test inference မစနိုင်ပါ':'Could not start local test inference',
    'Actual local inference စတင်ခဲ့သည်; native result ကိုစောင့်နေသည်':'Actual local inference started; waiting for the native result',
    'Inference ကို ဒီ Android စက်ပေါ်မှာပဲ လုပ်မည်။ Runtime နဲ့ model တကယ် load ဖြစ်မှ အဖြေထုတ်မည်။':'Inference runs only on this Android device. Responses are produced only after the runtime and model load successfully.',
    'သိမ်းထားသော local model':'Stored local models',
    'Local model ထည့်ရန်':'Import local model',
    'ဖိုင်နှင့် လိုအပ်ချက်များ':'File and requirements',
    'RAM သည် format/size မှရသော ခန့်မှန်းတန်ဖိုးသာဖြစ်သည်။ Runtime load သည် နောက်ဆုံးစစ်ဆေးမှုဖြစ်ပြီး တကယ့် inference ကို Android စက်ပေါ်မှသာ အတည်ပြုနိုင်သည်။':'RAM is estimated from format/size only. Runtime loading is the final check; real inference must be validated on an Android device.',
    'Offline inference အကြောင်း':'About offline inference',
    'LiteRT-LM 0.17.1 · CPU backend။ Android AAR minSdk 24၊ ARM64-v8a နှင့် x86_64 native libraries ပါသည်။':'LiteRT-LM 0.17.1 · CPU backend. The Android AAR declares minSdk 24 and includes ARM64-v8a and x86_64 native libraries.',
    'ကိုယ်ပိုင် model file':'Your own model file',
    'SAF document picker ကနေ .litertlm ကိုရွေးပါ။ Private app storage သို့ copy လုပ်၍ header/section များကို bounds စစ်ပြီး format ကိုစစ်ဆေးမည်။ Model auto-download မရှိပါ။':'Choose a .litertlm file in the SAF document picker. It is copied to private app storage, and header/section bounds and format are inspected. No model is auto-downloaded.',
    'Compatibility နဲ့ memory':'Compatibility and memory',
    'ABI၊ container version၊ TFLite identifier၊ section အကန့်အသတ်၊ လွတ်နေသော storage နဲ့ RAM ခန့်မှန်းချက်ကို စစ်မည်။ Runtime မှ model load ဖြစ်ခြင်းက နောက်ဆုံး compatibility check ဖြစ်ပြီး တကယ့် inference ကို Android ဖုန်းပေါ်တွင် စမ်းသပ်မှသာ အတည်ပြုနိုင်သည်.':'Checks the ABI, container version, TFLite identifier, bounded sections, free storage, and estimated RAM. Native model loading is the final compatibility check; actual inference must be exercised on an Android phone.',
    'Model weights app ထဲမပါဝင်ပါ။ Imported file ၏ architecture/license မပါရှိနိုင်သဖြင့် upstream ကိုစစ်ပါ။ Prompt နဲ့ output ကို network သို့ မပို့ပါ။ Native inference မစမ်းရသေးသည့် environment တွင် status ကို NOT EXECUTED ဟုပဲပြမည်; fake response/cloud fallback မရှိပါ။ Clear data လုပ်လျှင် imported model/cache ကိုပါ ဖျက်မည်။':'Model weights are not bundled with the app. The imported file may not state its architecture/license; verify its upstream terms. Prompts and output are not sent over the network. If native inference has not been exercised on this device, status remains NOT EXECUTED. No fake response or cloud fallback is used. Clearing app data also removes imported models and cache.',
    'Android APK တွင် Google Nearby Connections ကို အသုံးပြုထားသည်။ Web preview မှာ native radio မရရှိပါ။':'The Android APK uses Google Nearby Connections. Native radio is not available in the web preview.',
    'အနီးနားက စက်များကို':'Connect nearby', 'လုံခြုံစွာ ချိတ်ဆက်ပါ။':'devices securely.',
    'Android build တွင် Nearby Connections transport ပါဝင်သည်။ ချိတ်ဆက်မှု၊ pairing code နဲ့ encrypted session များကို စက်အစစ်တွင် စမ်းသပ်ရန် လိုအပ်သည်။':'The Android build includes Nearby Connections transport. Connections, pairing codes, and encrypted sessions still need testing on real devices.',
    'Native transport အခြေအနေ မစစ်ရသေးပါ':'Native transport status not checked yet', 'စက်ရှာရန်':'Find devices',
    'Pairing QR ပြရန်':'Show pairing QR', 'QR scan လုပ်ရန်':'Scan QR', 'Secure ဖိုင်ပို့ရန်':'Send secure file',
    'မစတင်ရသေးပါ':'Not started', 'ဖိုင်လွှဲပြောင်းမှု အခြေအနေ':'File transfer status', 'ရပ်ရန်':'Stop',
    'ဖိုင်ကို ထပ်ရွေးပြီးပို့ရန်':'Choose and resend the file', 'လက်ခံထားသော attachment များ':'Received attachments',
    'ပြန်စစ်ရန်':'Refresh', 'Legacy plaintext migration လိုအပ်သည်':'Legacy plaintext migration required',
    'ဖိုင် ပြောင်းမရသေးပါ။ မအောင်မြင်ပါက မူရင်းဖိုင်ကို မဖျက်ဘဲထားသည်။':'Files still need migration. If migration fails, the original file is kept.',
    'Migration ပြန်ကြိုးစားရန်':'Retry migration', 'လက်ခံထားသော attachment မရှိသေးပါ။':'No attachments received yet.',
    'ဖိုင်ကို AES-GCM ဖြင့် encrypt လုပ်ပြီး authenticated manifest၊ SHA-256 စစ်ဆေးမှုနှင့် Nearby progress/cancel ဖြင့်ပို့သည်။ Retry သည် ဖိုင်ကို ပြန်ရွေး၍ အသစ်ပြန်ပို့ခြင်းဖြစ်ပြီး resume မဟုတ်ပါ။ QR bootstrap source ပါဝင်သည်၊ သို့သော် camera/phone pairing၊ Nearby radio၊ Local AI runtime/model နှင့် physical-device/10-phone verification မပြီးသေးပါ။':'Files are encrypted with AES-GCM and sent with an authenticated manifest, SHA-256 verification, and Nearby progress/cancel support. Retry sends a newly selected file; it does not resume. QR bootstrap code is included, but camera/phone pairing, Nearby radio, Local AI runtime/model, and physical-device/10-phone verification are not complete.',
    'ဖိုင်လုံခြုံရေး':'File security',
    'အတည်ပြုထားသော manifest · SHA-256 integrity':'Authenticated manifest · SHA-256 integrity',
    'Progress update နှင့် cancel control ပါဝင်သည်':'Progress updates and cancellation are supported',
    'QR bootstrap source ပါဝင်သည်':'QR bootstrap source is included',
    'မပြီးသေး':'Not complete yet',
    'Camera/phone pairing · Nearby radio · Local AI inference/runtime/model · physical-device/10-phone verification မပြီးသေးပါ။':'Camera/phone pairing, Nearby radio, Local AI inference/runtime/model, and physical-device/10-phone verification are not complete.',
    'Retry သည် ဖိုင်ကို ပြန်ရွေးပြီး ထပ်ပို့သည်; resume မဟုတ်ပါ။':'Retry asks you to select the file again and resend it; it does not resume.',

    'ချိတ်ဆက်ထားသော စက်များ':'Connected devices', 'လက်ရှိ ချိတ်ဆက်ထားသော စက်မရှိပါ':'No devices are currently connected',
    'Web Logic စမ်းသပ်မှုနှင့် real device ချိတ်ဆက်မှုကို သီးခြားထားပါတယ်။ Native bridge ရှိမှ တကယ့်စက်များကို ပြပါမယ်။':'Web Logic tests are kept separate from real-device connections. Real devices appear only when a native bridge is available.',
    'လုံခြုံသော ချိတ်ဆက်မှု':'Secure connection', 'တွေ့ရှိမှု':'Discovery',
    'အနီးအနား Android device များကို local radio ဖြင့် ရှာပါ။':'Find nearby Android devices over local radio.',
    'နှစ်ဖက်လုံးက verification code ကို စစ်ဆေးပြီး အတည်ပြုပါ။':'Both sides check and confirm the verification code.',
    'ECDH P‑256 key exchange နဲ့ AES‑GCM ဖြင့် စာသားကို ကာကွယ်ပါ။':'Protect messages with ECDH P‑256 key exchange and AES‑GCM.',
    'Logic စမ်းသပ်မှု၊ native transport နဲ့ real devices ကို သီးခြားပြထားပါတယ်။':'Logic tests, native transport, and real devices are shown separately.',
    'Web Logic စမ်းသပ်ရန်':'Run Web Logic tests', 'ဒီ browser ထဲမှာ စမ်းသပ်နိုင်သော crypto နှင့် app logic များ။':'Cryptography and app logic that can be tested in this browser.',
    'စမ်းသပ်မှု မလုပ်ရသေးပါ':'No tests run yet', 'Pending':'Pending', '02':'02', '01':'01', '03':'03', '04':'04',
    'Android bridge, radio transport နဲ့ permission state။':'Android bridge, radio transport, and permission status.',
    'APK ထဲမှာ Nearby Connections bridge ပါဝင်သည် · radio test pending':'Nearby Connections bridge is included in the APK · radio test pending',
    'မစစ်ရသေး':'Not checked yet', 'Physical Android phones မှ တကယ်ပြန်လာသော အခြေအနေသာ ရေတွက်သည်။':'Counts only status reported by real Android phones.',
    'Physical device test မလုပ်ရသေးပါ':'Physical-device testing has not been run', 'Build, storage နဲ့ runtime':'Build, storage, and runtime',
    'နောက်ဆုံးစမ်းသပ်ချိန် — မရှိသေး':'Last test run — none yet',
    'Nearby endpoint count ကို native callback မှသာ ဖြည့်သည်။ QR scan၊ Android Keystore၊ open/export/share နဲ့ physical tests ကို ဒီ dashboard က PASS ဟု မသတ်မှတ်ပါ။':'Nearby endpoint counts come only from native callbacks. This dashboard does not mark QR scanning, Android Keystore, open/export/share, or physical tests as PASS.',
    'APK တည်ဆောက်မှုရှိသော်လည်း Nearby transport, offline message flow နဲ့ Android ဖုန်း 10 လုံးပေါ်က acceptance test များ မစမ်းရသေးပါ။ Logic test အောင်မြင်ခြင်းက physical verification ကို အစားမထိုးပါ။':'The APK builds, but Nearby transport, offline messaging, and acceptance tests across 10 Android phones have not been tested. Passing logic tests does not replace physical verification.',
    'Private keys / plaintext ကို log မလုပ်ပါ':'Private keys/plaintext are not logged', 'Simulated device များ မပြပါ':'Simulated devices are not shown',
    'ဒီ browser/device ထဲမှာသာ သိမ်းထားမည့် profile နဲ့ preference များ။':'Profile and preferences saved only on this browser/device.',
    'Account သို့မဟုတ် cloud sync မလိုပါ။':'No account or cloud sync required.', 'ဖော်ပြမည့်အမည်':'Display name',
    'ဤစက်ထဲမှာသာ သိမ်းမည်။':'Saved only on this device.', 'Device ID':'Device ID',
    'သီးခြား local identity — network ပေါ်မပို့ပါ။':'A separate local identity — not sent over the network.',
    'Profile သိမ်းရန်':'Save profile', 'သင့်မျက်နှာပြင်နှင့် ကိုက်ညီအောင် ရွေးပါ။':'Choose what works for your screen.',
    'အမှောင်':'Dark', 'အလင်း':'Light', 'စနစ်အတိုင်း':'System', 'သင့်ဒေတာကို ထိန်းချုပ်ပါ':'Control your data',
    'တွက်ချက်နေသည်':'Calculating…', 'Storage အခြေအနေ စစ်ဆေးနေသည်':'Checking storage status',
    'Cloud account မလို':'No cloud account required', 'Android: Keystore AES-GCM encrypted':'Android: encrypted with Keystore AES-GCM',
    'Browser preview: tab memory only':'Browser preview: tab memory only', 'Local app data ဖျက်ရန်':'Delete local app data',
    'ပိတ်ရန်':'Close', 'အတည်ပြုရန်':'Confirm', 'မလုပ်တော့ပါ':'Cancel', 'ဆက်လုပ်မည်':'Continue',
    'Network':'Network', 'Notifications':'Notifications', 'Accessibility':'Accessibility', 'Loading…':'Loading…',
    'Browser online':'Browser online', 'Browser offline':'Browser offline', 'Online':'Online', 'Offline':'Offline',
    'အင်တာနက် အခြေအနေ':'Internet status', 'Browser က network ရှိကြောင်း ပြထားသည်':'The browser reports a network connection',
    'Browser က network မရှိကြောင်း ပြထားသည်':'The browser reports no network connection', 'ဤအခြေအနေသည် Nearby ကို မဆုံးဖြတ်ပါ':'This status does not determine Nearby availability',
    'Secure Nearby':'Secure Nearby', 'Encrypted group':'Encrypted group', 'PEER':'PEER', 'GROUP':'GROUP', 'LOCAL':'LOCAL',
    'ဒီစက်ထဲမှာသာ':'Only on this device', 'Group မှ ထွက်ထားသည် · ပို့ခြင်းပိတ်ထားသည်':'Left the group · sending is disabled',
    'Offline group · bounded encrypted fan-out/relay':'Offline group · bounded encrypted fan-out/relay', 'Encrypted Nearby peer':'Encrypted Nearby peer',
    'ဒီစက်ထဲမှာပဲ သိမ်းမည့် မှတ်စုရေးပါ…':'Write a note to keep on this device…',
    'Group မှထွက်ထားသောကြောင့် ပို့၍မရပါ':'Cannot send because you left the group', 'စာရေးပါ… · session မရှိလျှင် queue ထဲမှာသာ သိမ်းမည်':'Write a message… · queued until a session is available',
    'Group မှ ထွက်ထားသောမှတ်စု':'Note from a group you left', 'Encrypted Nearby message':'Encrypted Nearby message',
    'Group မှထွက်ထား · မပို့နိုင်':'Left group · cannot send', 'Secure session ရှိမှသာ encrypt လုပ်ပို့မည်':'Encrypted and sent only when a secure session is available',
    'Manage group members':'Manage group members', 'Leave group':'Leave group',
    'Secure peers ဖိတ်ကြား၍ group roster ထပ်တူပြုရန်':'Invite secure peers and sync the group roster', 'ဤ offline group မှ ထွက်ရန်':'Leave this offline group',
    'ပို့ရန် / queue သိမ်းရန်':'Send / save to queue', 'စကားစတင်ရေးပါ':'Start a conversation',
    'Secure Nearby session မတည်ဆောက်မချင်း စာကို local queue ထဲမှာသာ သိမ်းမည်။':'Messages stay in the local queue until a secure Nearby session is established.',
    'RECEIVED · encrypted Nearby':'RECEIVED · encrypted Nearby', 'SENT · encrypted Nearby':'SENT · encrypted Nearby', 'LOCAL QUEUE · မပို့ရသေး':'LOCAL QUEUE · not sent',
    'Android private storage · Keystore AES-GCM':'Android private storage · Keystore AES-GCM',
    'Secure storage unavailable · data writes stopped':'Secure storage unavailable · data writes stopped',
    'Browser preview · current session only · not saved':'Browser preview · current session only · not saved',
    'Android build တွင် profile နဲ့ chat history ကို app-private file ထဲ AES-GCM ဖြင့် encrypt လုပ်ပြီး key ကို Android Keystore က ထိန်းသည်။ Browser preview သည် session memory သာဖြစ်သည်။':'On Android, the profile and chat history are encrypted with AES-GCM in an app-private file, and the key is protected by Android Keystore. The browser preview uses session memory only.',
    'Android secure storage ကို ဖွင့်မရသောကြောင့် ရှိပြီးသား encrypted data ကို မပြင်ဘဲ သိမ်းထားပြီး ထပ်မသိမ်းတော့ပါ။':'Android secure storage could not be opened, so existing encrypted data is kept unchanged and no further saves are made.',
    'Browser preview သည် profile နှင့် chat ကို tab ဖွင့်ထားသည့် အချိန်အတွင်းသာ memory ထဲထားပြီး browser storage ထဲမသိမ်းပါ။':'The browser preview keeps the profile and chats in memory only while the tab is open and does not save them to browser storage.',
    'Mock Nearby bridge · simulation only':'Mock Nearby bridge · simulation only', 'Android Nearby bridge ရရှိသည်':'Android Nearby bridge available',
    'NexusNativeNearby bridge မတွေ့ရှိပါ':'NexusNativeNearby bridge not detected', 'SIMULATION ONLY · physical radio/device မဟုတ်ပါ':'SIMULATION ONLY · not a physical radio/device',
    'Nearby Connections ရရှိသည် · လက်တွေ့ချိတ်ဆက်မှု မစမ်းရသေး':'Nearby Connections available · real connections not yet tested',
    'Android native bridge မတွေ့ရှိပါ':'Android native bridge not detected', 'Mock protocol simulation · physical Nearby radio မဟုတ်ပါ':'Mock protocol simulation · not a physical Nearby radio',
    'Nearby Connections radio transport ကို အသုံးပြုနိုင်သည်':'Nearby Connections radio transport is available', 'MOCK TEST ONLY · no physical devices':'MOCK TEST ONLY · no physical devices',
    'Native transport ရှိသည် · real devices မစမ်းရသေး':'Native transport available · real devices not yet tested',
    'Available':'Available', 'Unavailable':'Unavailable', 'Mock only':'Mock only', 'Mock test bridge':'Mock test bridge',
    'Bridge ရှိကြောင်းသာ တွေ့ပါသည်။ Secure pairing/session မရှိသဖြင့် queue မပို့ဘဲ စောင့်ထားပါသည်။':'A bridge was detected, but the queue is held because no secure pairing/session exists.',
    'Native bridge မရှိသေးသဖြင့် queue ထဲကစာများကို မပို့ဘဲ စောင့်ထားပါသည်။':'Messages remain queued because the native bridge is unavailable.',
    'Secure session မရှိပါ — မပို့ဘဲထားပါသည်':'No secure session — not sent', 'မပို့ရသေးသောစာများ local queue ထဲမှာပဲ ရှိနေသည်':'Unsent messages remain in the local queue',
    'Attachment list ကို ဖတ်မရပါ':'Could not read the attachment list', 'Attachment':'Attachment', 'Unknown file':'Unknown file',
    'GCM + SHA-256 verified':'GCM + SHA-256 verified', 'Integrity failure':'Integrity failure', 'Metadata unavailable':'Metadata unavailable',
    'Metadata invalid':'Invalid metadata', 'Keystore key unavailable':'Keystore key unavailable', 'Not verified':'Not verified',
    'ဖွင့်ရန်':'Open', 'မျှဝေရန်':'Share', 'Share':'Share', 'ထုတ်ယူရန်':'Export', 'Export':'Export', 'ဖျက်ရန်':'Delete',
    'Attachment ဖျက်မလား?':'Delete attachment?', 'ဤဖိုင်':'this file', 'encrypted storage နှင့်ဆက်စပ် temporary files များမှ ဖျက်မည်။ ပြန်မရနိုင်ပါ။':'will be deleted from encrypted storage and related temporary files. This cannot be undone.',
    'ဖိုင်ဖျက်မည်':'Delete file', 'မအောင်မြင်ပါ':'failed', 'ဖျက်မစနိုင်ပါ':'Could not delete', 'Attachment action မစတင်နိုင်ပါ':'Could not perform attachment action',
    'ဖိုင်':'file', 'verified':'verified', 'encrypted':'encrypted', 'plaintext':'plaintext', 'pending':'pending',
    'Setup required':'Setup required', 'Model loaded':'Model loaded', 'Android runtime unavailable':'Android runtime unavailable',
    'Runtime unavailable or ABI unsupported':'Runtime unavailable or ABI unsupported', 'Runtime unavailable':'Runtime unavailable',
    'Imported model':'Imported model', 'LiteRT-LM':'LiteRT-LM', 'unknown':'unknown', 'available':'available', 'unavailable':'unavailable',
    'Granted':'Granted', 'Not granted':'Not granted', 'Not requested':'Not requested', 'Not installed':'Not installed',
    'SIMULATION ONLY':'SIMULATION ONLY', 'Native Android bridge':'Native Android bridge', 'Android native runtime လိုအပ်သည်':'Android native runtime required',
    'Web preview တွင် Local AI inference မရှိပါ; Android runtime/ABI မရရှိနိုင်သေး။ Cloud fallback မသုံးပါ။':'Local AI inference is unavailable in the web preview; the Android runtime/ABI is not available. No cloud fallback is used.',
    'အမည်တစ်ခု ထည့်ပါ။':'Enter a name.', 'Profile ကို ဒီစက်ထဲမှာ သိမ်းပြီးပါပြီ':'Profile saved on this device',
    'အဟောင်း local data ကို Android Keystore အောက်ရှိ AES-GCM encrypted storage သို့ ပြောင်းရွှေ့ပြီးဖြစ်သည်':'Legacy local data has been migrated to AES-GCM encrypted storage protected by Android Keystore',
    'Browser preview ဖြစ်သည် — data ကို tab ဖွင့်ထားသည့်အချိန်အတွင်းသာထားပြီး မသိမ်းပါ':'This is a browser preview — data stays in memory while the tab is open and is not saved',
    'Android secure storage မရရှိပါ — ရှိပြီးသား data ကို မပြင်ဘဲထားသည်':'Android secure storage is unavailable — existing data is kept unchanged',
    'Web Logic စမ်းသပ်မှု ပြီးပါပြီ။ Native/physical test မဟုတ်ပါ။':'Web Logic tests are complete. This is not a native/physical test.',
    'စမ်းသပ်နေသည်…':'Testing…', 'မအောင်မြင်':'Failed', 'နောက်ဆုံးစမ်းသပ်ချိန် —':'Last test run —',
    'လုံခြုံသောသိမ်းဆည်းမှု မအောင်မြင်ပါ။ ဒေတာကို ထပ်မရေးဘဲ ထိန်းထားသည်။':'Secure storage failed. Data is retained without further writes.',
    'ဒီနေ့':'Today', 'ဥပမာ — Piangpi':'For example — Piangpi',
    'LOCAL DEVICE':'LOCAL DEVICE', 'Language / ဘာသာစကား':'Language', 'Choose the language used throughout the app.':'Choose the language used throughout the app.', 'ဘယ်ဘာသာစကားကို သုံးမလဲ':'Which language would you like to use?', 'ဘာသာစကားရွေးချယ်မှုကို ဒီစက်ထဲမှာသာ သိမ်းမည်။':'Your language choice is saved only on this device.', 'ဘာသာစကား':'Language',
    'မြန်မာ':'မြန်မာ', 'အင်္ဂလိပ်':'English', 'ဘာသာစကားကို ပြောင်းပြီးပါပြီ':'Language updated',
    'ဖိုင်ကို AES-GCM ဖြင့် encrypt လုပ်ပြီး authenticated manifest၊ SHA-256 စစ်ဆေးမှုနှင့် Nearby progress/cancel ဖြင့်ပို့သည်။':'Files are encrypted with AES-GCM and sent with an authenticated manifest, SHA-256 verification, and Nearby progress/cancel support.',
    'LiteRT-LM Android 0.17.1 (CPU) — APK ထဲပါဝင်သည်။ ARM64-v8a / x86_64 ကိုထောက်ပံ့သည်။':'LiteRT-LM Android 0.17.1 (CPU) is included in the APK and supports ARM64-v8a / x86_64.',
    'စမ်းသပ်ရန်လို':'Testing required','OS runtime state':'OS runtime state',
    'QR identity ကိုက်ညီသည့် စက်:':'Device matching QR identity:','QR filter ဖယ်ရန်':'Clear QR filter','QR မကိုက်ညီ':'QR does not match','တွေ့ရှိထားသည်':'Discovered',
    'စမ်းသပ်မှုပုံစံသာ':'Simulation only','ဤ peer များသည် mock test များဖြစ်ပြီး physical Android device မဟုတ်ပါ။':'These peers are mock tests, not physical Android devices.',
    'Scan လုပ်ထားသော QR identity နှင့် မကိုက်ညီပါ':'Does not match the scanned QR identity','ချိတ်ဆက်မှု စတင်မရပါ။ Permission/transport အခြေအနေကို စစ်ပါ။':'Could not start the connection. Check permission and transport status.',
    'Pairing request ပို့ပြီးပါပြီ။ ဖုန်းနှစ်လုံးပေါ်က code ကို တိုက်စစ်ပါ။':'Pairing request sent. Compare the codes on both phones.',
    'ဒီ web preview မှာ native Nearby မရရှိပါ။':'Native Nearby is unavailable in this web preview.', 'Nearby discovery ကို ရပ်လိုက်ပါပြီ':'Nearby discovery stopped',
    'Nearby စတင်မရပါ။ Permission စစ်ဆေးပါ။':'Could not start Nearby. Check permissions.', 'ရှာဖွေမှု ရပ်ရန်':'Stop discovery',
    'Advertising / discovery စတင်နေသည် · peer အရေအတွက်မှာ စမ်းသပ်မှုမဟုတ်ပါ':'Advertising/discovery started · peer count is not a test result',
    'Attachment နှင့်သက်ဆိုင်သော temporary copy များကို ဖျက်ပြီးပါပြီ':'Related temporary attachment copies were deleted',
    'Attachment ကို ရွေးထားသောနေရာသို့ export လုပ်ပြီးပါပြီ':'Attachment exported to the selected location','လုံခြုံစွာ ပြင်ဆင်ပြီးပါပြီ':'prepared securely',
    'Export ကို ပယ်ဖျက်လိုက်သည်':'Export cancelled','Attachment action မအောင်မြင်ပါ':'Attachment action failed',
    'Nearby device သည် scan လုပ်ထားသော QR identity နှင့် မကိုက်ညီပါ':'Nearby device does not match the scanned QR identity',
    'Pairing ကို ပယ်ဖျက်လိုက်ပါပြီ':'Pairing cancelled','Nearby pairing မအောင်မြင်ပါ':'Nearby pairing failed',
    'Nearby connection ပြတ်တောက်သွားသည်':'Nearby connection disconnected','မပို့ရသေးသောစာများ queue ထဲမှာ ရှိနေသည်':'unsent messages remain in the queue',
    'ဖိုင်ကို encrypt လုပ်မရပါ':'Could not encrypt the file','ဖိုင်၏ integrity စစ်ဆေးမှု မအောင်မြင်ပါ':'File integrity check failed',
    'ဖိုင်ရွေးချယ်မှုကို ပယ်ဖျက်လိုက်သည်':'File selection cancelled','ဖိုင်အခြေအနေ စစ်ဆေးနေသည်':'Checking file status',
    'Encrypted file ရောက်ရှိပြီး integrity စစ်ဆေးနေသည်':'Encrypted file received; checking integrity','ဖိုင်ကို ပို့ပြီးပါပြီ':'File sent',
    'လက်ခံသူ၏ အတည်ပြုချက်ကို စောင့်နေသည်':'Waiting for recipient confirmation','လွှဲပြောင်းမှု မအောင်မြင်ပါ':'Transfer failed',
    'လွှဲပြောင်းမှုကို ရပ်လိုက်သည်':'Transfer stopped','ဖိုင်ပို့ရန် အတည်ပြုထားသော secure session လိုအပ်သည်':'An approved secure session is required to send files',
    'အခြားဖိုင်လွှဲပြောင်းမှု ပြီးမှ ထပ်ကြိုးစားပါ':'Try again after the other file transfer finishes','ဖိုင်ရွေးချယ်မှု စတင်မရပါ':'Could not open file selection',
    'Pairing code/fingerprint နှစ်ဖက်အတည်ပြုပြီး · ECDH P-256 / AES-GCM session active':'Pairing code/fingerprint confirmed by both sides · ECDH P-256 / AES-GCM session active',
    'Secure Nearby session ရရှိသည် · queue retry လုပ်နေသည်':'Secure Nearby session established · retrying queued messages',
    'Group membership ကို လက်ခံပြီးပါပြီ':'Group membership accepted','Group owner က membership မှ ဖယ်ရှားလိုက်သည်':'The group owner removed this membership',
    'Incoming file ကို လက်မခံရန် ရွေးချယ်ခဲ့သည်':'The incoming file was declined','လက်ခံဘက်က ဖိုင် manifest ကို ပယ်ချလိုက်သည်':'The recipient rejected the file manifest',
    'Encrypted Nearby စာတစ်စောင် လက်ခံရရှိသည်':'Encrypted Nearby message received','Group owner ထံ leave request ပို့ပြီး group မှထွက်ပါပြီ':'Leave request sent to the group owner; you left the group',
    'Owner offline ဖြစ်သည် · ဤစက်တွင် group မှ ထွက်ထားသည်':'Owner is offline · this device has left the group',
    'QR ပြုလုပ်မရပါ':'Could not create QR','QR scan မစတင်နိုင်ပါ':'Could not start QR scan','QR pairing မအောင်မြင်ပါ':'QR pairing failed',
    'Nearby pairing မအောင်မြင်ပါ':'Nearby pairing failed','Nearby permission မရရှိပါ':'Nearby permission not granted',
    'Nearby permissions ရရှိသည် · radio ချိတ်ဆက်မှု မစမ်းရသေး':'Nearby permissions granted · radio connection not tested yet',
    'Wi-Fi သို့မဟုတ် Bluetooth ကို ဖုန်း Settings မှ ကိုယ်တိုင်ဖွင့်ပြီး ထပ်ကြိုးစားပါ။':'Turn on Wi-Fi or Bluetooth in phone Settings and try again.',
    'Wi-Fi သို့မဟုတ် Bluetooth ကို ဖုန်း Settings မှ ဖွင့်ပါ။':'Turn on Wi-Fi or Bluetooth in phone Settings.',
    'Nearby payload ပို့မအောင်မြင်ပါ · စာများကို queue ထဲပြန်ထားသည်':'Nearby payload send failed · messages returned to the queue',
    'Nearby transport ပြဿနာ':'Nearby transport error','ဖိုင်ပို့ရန် အတည်ပြုထားသော secure session လိုအပ်သည်':'An approved secure session is required to send files',
    'Attachment list ကို ဖတ်မရပါ':'Could not read the attachment list','Model import မစနိုင်ပါ':'Could not start model import',
    'Model import မအောင်မြင်ပါ':'Model import failed','Model load မစနိုင်ပါ':'Could not start model loading','Unload မလုပ်နိုင်ပါ':'Could not unload model',
    'Local generation မစနိုင်ပါ':'Could not start local generation','Generation cancel မလုပ်နိုင်ပါ':'Could not cancel generation',
    'Android document picker တွင် .litertlm model ရွေးပါ':'Choose a .litertlm model in the Android document picker',
    'Model ကို local runtime ထဲတွင် စတင် load လုပ်နေသည်…':'Loading the model into the local runtime…',
    'Local model မှ inference လုပ်နေသည်…':'Running inference with the local model…','Generation ရပ်လိုက်ပြီး model ကို unload လုပ်နေသည်…':'Generation stopped; unloading the model…',
    'Local model ကို private storage ထဲထည့်ပြီးပါပြီ':'Local model saved to private storage','Local AI ·':'Local AI ·',
    'Local AI inference မရှိပါ':'Local AI inference is unavailable','အမည်တစ်ခု ထည့်ပါ။':'Enter a name.',
    'Local မှတ်စုခန်းအမည် ထည့်ပါ။ ဒီစက်ထဲမှာသာ သိမ်းမည်။':'Enter a name for the local chat. It will be saved only on this device.',
    'မှတ်စု':'Note','Local မှတ်စုခန်း အသစ် ဖန်တီးပြီးပါပြီ':'New local chat created','Secure session ရှိသည့် queue များကို ပြန်စစ်ပြီးပါပြီ':'Queued messages were retried for secure sessions',
    'Local AI status ပြန်စစ်ပြီးပါပြီ':'Local AI status refreshed','Local note သာဖြစ်သည် · အခြားစက်သို့ မပို့ပါ':'This is a local note · not sent to another device',
    'Encrypted payload ကို Nearby transport သို့ပို့လိုက်သည် · delivery receipt မရှိ':'Encrypted payload sent over Nearby transport · no delivery receipt',
    'Secure peer မရရှိသေးပါ · စာကို local queue ထဲမှာထားသည်':'Secure peer unavailable · message kept in the local queue',
    'Group မှထွက်ထားသောကြောင့် မပို့ပါ':'Not sent because you left the group','ပြောင်းရွှေ့':'migrated',
    'Legacy plaintext attachment migration ကို ထပ်စစ်နေသည်':'Retrying legacy plaintext attachment migration',
    'Legacy migration ပြန်မစနိုင်ပါ':'Could not restart legacy migration','ဖိုင်ပို့ရန် အတည်ပြုထားသော secure session လိုအပ်သည်':'An approved secure session is required to send files',
    'Local app data ကို မဖျက်နိုင်ပါ':'Could not delete local app data','Local app data နှင့် imported AI model များကို ဖျက်ပြီးပါပြီ':'Local app data and imported AI models were deleted',
    'Local app data ဖျက်မလား?':'Delete local app data?','ဖျက်မည်':'Delete','Local app data':'Local app data',
    'Privacy':'Privacy','GROUP':'GROUP','PEER':'PEER','LOCAL':'LOCAL',
    'လွှဲပြောင်းနေသည်':'Transferring','အန္တရာယ်မရှိဘဲ ရပ်ထားသည်':'stopped safely','ဖိုင်အခြေအနေ စစ်ဆေးနေသည်':'Checking file status',
    'လက်ရှိ secure peers':'Synchronize the roster with','ဦးနှင့် roster ကို synchronize လုပ်မလား?':'secure peer(s)?',
    'အခြားဖုန်းပေါ်ရှိ code နှင့် ကိုက်ညည်မှုကို နှိုင်းယှဉ်ပါ။ တူညီမှသာ ချိတ်ဆက်မှုကို အတည်ပြုပါ။':'Compare it with the code on the other phone. Confirm only if they match.',
    'pairing code':'pairing code','ဖုန်းနှစ်လုံးပေါ်က code ကို တိုက်စစ်ပါ။':'Compare the codes on both phones.',
    'Offline group draft ဖန်တီးပြီးပါပြီ':'Offline group draft created','အဖွဲ့ဝင်များကို ချိတ်ဆက်ပြီး Manage members မှ invite လုပ်ပါ':'connect peers and invite them from Manage members',
    'Group member limit':'Group member limit','ဖြစ်သည် ·':'is ·','peers ရှိနေသည်':'peers are connected',
    'Group roster':'Group roster','ကို secure peer များထံ ပို့ပြီးပါပြီ':'sent to secure peers','ကို စောင့်နေသည်':'waiting for',
    'QR identity':'QR identity','မှန်ကန်သည်။ Nearby စက်အမည်ကိုက်ညီမှု၊ ထို့နောက် နှစ်ဖက် fingerprint ကို စစ်ဆေးပါ။':'is valid. Check that the nearby device name matches, then verify both fingerprints.',
    'QR pairing မအောင်မြင်ပါ':'QR pairing failed','ကိုက်ညီမှု':'match','Pairing request':'Pairing request',
    'ဖိုင်ပို့ရန်':'Send file','ချိတ်ဆက်ရန်':'Connect','ရပ်ရန်':'Stop','စက်ရှာရန်':'Find devices',
    'Verified':'Verified','Not verified':'Not verified','In progress':'In progress','READY':'READY','LOADING':'LOADING','GENERATING':'GENERATING',
    'pending':'pending','no':'no','real':'real','simulated':'simulated','discovered':'discovered','advertising':'advertising','discovering':'discovering',
    'မအောင်မြင်ပါ ·':'failed ·','မစနိုင်ပါ ·':'could not start ·','ပြဿနာ —':'error —','အမှား':'error',
    'Local app data ကို မဖျက်နိုင်ပါ ·':'Could not delete local app data ·','Model import မစနိုင်ပါ ·':'Could not start model import ·',
    'Model load မစနိုင်ပါ ·':'Could not start model loading ·','Local generation မစနိုင်ပါ ·':'Could not start local generation ·',
    'QR ပြုလုပ်မရပါ ·':'Could not create QR ·','QR scan မစတင်နိုင်ပါ ·':'Could not start QR scan ·',
    'Attachment action မစတင်နိုင်ပါ ·':'Could not start attachment action ·','ဖျက်မစနိုင်ပါ ·':'Could not delete ·',
    'Legacy plaintext attachment migration ကို ထပ်စစ်နေသည်':'Retrying legacy plaintext attachment migration',
    'Model import မအောင်မြင်ပါ ·':'Model import failed ·','Nearby pairing မအောင်မြင်ပါ':'Nearby pairing failed',
    'မပို့ရသေးသောစာများ local queue ထဲမှာပဲ ရှိနေသည်':'Unsent messages remain in the local queue'
  };
  const MY = {
    'Home':'မူလစာမျက်နှာ','Chats':'စကားပြောခန်း','Settings':'ဆက်တင်များ',
    'Main navigation':'အဓိက မီနူး','More navigation':'အခြား မီနူး','Only on your device':'သင့်စက်ထဲမှာပဲ','No cloud account required.':'Cloud account မလိုပါ။',
    'Me':'ကျွန်ုပ်','Edit profile':'ပရိုဖိုင်ပြင်ရန်','Close menu':'မီနူးပိတ်ရန်','Open menu':'မီနူးဖွင့်ရန်',
    'Checking…':'စစ်ဆေးနေသည်','Toggle light/dark theme':'အလင်း/အမှောင် ပြောင်းရန်','Appearance':'အသွင်အပြင်',
    'YOUR PRIVATE OFFLINE WORKSPACE':'သင့်ကိုယ်ပိုင် offline workspace','Your network,':'သင့်ကွန်ရက်၊','your rules.':'သင့်စည်းမျဉ်း။',
    'Nearby connections, local AI, and chat history on your device — all in one place.':'အနီးအနား ဆက်သွယ်မှု၊ local AI နဲ့ သင့်စက်ထဲက chat history — တစ်နေရာတည်းမှာ။',
    'Today':'ဒီနေ့','Even without internet,':'Internet မရှိလည်း','stay connected.':'အနီးကပ်နေပါ။',
    'Explore Nearby':'အနီးအနားကို ကြည့်ရန်','Run diagnostics':'စနစ်စစ်ဆေးရန်','Status at a glance':'တစ်ချက်ကြည့် အခြေအနေ','View details':'အသေးစိတ်ကြည့်ရန်',
    'Get started':'စတင်အသုံးပြုရန်','Write a local chat note':'Local chat မှတ်စုရေးရန်','Local AI status':'Local AI စနစ်အခြေအနေ',
    'Start a local note':'သင့် local မှတ်စုကို စတင်ပါ','Local note text':'Local မှတ်စုစာသား','Save to queue':'Queue ထဲသိမ်းရန်',
    'Check connection again':'ချိတ်ဆက်မှု ပြန်စစ်ရန်','Local AI model to use':'အသုံးပြုမည့် local AI model','No model selected':'Model မရွေးရသေးပါ',
    'Import .litertlm model':'.litertlm model ထည့်ရန်','Model is not loaded':'Model မတင်ရသေးပါ','Ask the loaded model a question…':'Loaded model သို့ မေးခွန်းရေးပါ…',
    'No response yet':'အဖြေ မရှိသေးပါ','Check status again':'Status ပြန်စစ်ရန်','Important notes':'အရေးကြီးသည့်အချက်များ',
    'Find devices':'စက်ရှာရန်','Show pairing QR':'Pairing QR ပြရန်','Scan QR':'QR scan လုပ်ရန်','Send secure file':'Secure ဖိုင်ပို့ရန်',
    'File transfer status':'ဖိုင်လွှဲပြောင်းမှု အခြေအနေ','Stop':'ရပ်ရန်','Choose and resend the file':'ဖိုင်ကို ထပ်ရွေးပြီးပို့ရန်','Received attachments':'လက်ခံထားသော attachment များ',
    'Refresh':'ပြန်စစ်ရန်','Retry migration':'Migration ပြန်ကြိုးစားရန်','Connected devices':'ချိတ်ဆက်ထားသော စက်များ',
    'Secure connection':'လုံခြုံသော ချိတ်ဆက်မှု','Discovery':'တွေ့ရှိမှု','Run Web Logic tests':'Web Logic စမ်းသပ်ရန်','No tests run yet':'စမ်းသပ်မှု မလုပ်ရသေးပါ',
    'Not checked yet':'မစစ်ရသေး','Physical-device testing has not been run':'Physical device test မလုပ်ရသေးပါ',
    'Build, storage, and runtime':'Build, storage နဲ့ runtime','Last test run — none yet':'နောက်ဆုံးစမ်းသပ်ချိန် — မရှိသေး',
    'Display name':'ဖော်ပြမည့်အမည်','Save profile':'Profile သိမ်းရန်','Choose what works for your screen.':'သင့်မျက်နှာပြင်နှင့် ကိုက်ညီအောင် ရွေးပါ။',
    'Dark':'အမှောင်','Light':'အလင်း','System':'စနစ်အတိုင်း','Control your data':'သင့်ဒေတာကို ထိန်းချုပ်ပါ',
    'Calculating…':'တွက်ချက်နေသည်','Close':'ပိတ်ရန်','Confirm':'အတည်ပြုရန်','Cancel':'မလုပ်တော့ပါ','Continue':'ဆက်လုပ်မည်',
    'Online':'Online','Offline':'Offline','Search…':'ရှာဖွေရန်…','Search chats':'စကားပြောခန်းရှာရန်','Personal notes':'ကိုယ်ပိုင်မှတ်စု',
    'No matching notes.':'ကိုက်ညီသော မှတ်စုမရှိပါ။','waiting':'စောင် စောင့်ဆိုင်းနေသည်','Open':'ဖွင့်ရန်','Share':'မျှဝေရန်','Export':'ထုတ်ယူရန်','Delete':'ဖျက်ရန်',
    'Delete attachment?':'Attachment ဖျက်မလား?','Delete file':'ဖိုင်ဖျက်မည်','Enter a name.':'အမည်တစ်ခု ထည့်ပါ။',
    'Profile saved on this device':'Profile ကို ဒီစက်ထဲမှာ သိမ်းပြီးပါပြီ','Setup required':'စစ်ဆေးရန်လို','Model loaded':'Model loaded',
    'Granted':'ခွင့်ပြုထားသည်','Not granted':'ခွင့်မပြုရသေး','Not requested':'တောင်းဆိုရသေးခြင်းမရှိ','Not installed':'မတပ်ဆင်ရသေး',
    'Available':'ရရှိနိုင်သည်','Unavailable':'မရရှိနိုင်ပါ','Native Android bridge':'Native Android bridge',
    'Language':'ဘာသာစကား','Language / ဘာသာစကား':'ဘာသာစကား','Choose the language used throughout the app.':'အက်ပ်တစ်ခုလုံးတွင် အသုံးပြုမည့် ဘာသာစကားကို ရွေးပါ။','Which language would you like to use?':'ဘယ်ဘာသာစကားကို သုံးမလဲ','Your language choice is saved only on this device.':'ဘာသာစကားရွေးချယ်မှုကို ဒီစက်ထဲမှာသာ သိမ်းမည်။','Choose a language':'ဘာသာစကားကို ရွေးပါ','မြန်မာ':'မြန်မာ','English':'အင်္ဂလိပ်',
    'Language updated':'ဘာသာစကား ပြောင်းလဲပြီးပါပြီ','Main navigation':'အဓိက မီနူး','Browser online':'Browser online','Browser offline':'Browser offline',
    'GROUP':'အဖွဲ့','PEER':'စက်','LOCAL':'စက်တွင်း','Pending':'စောင့်ဆိုင်းနေသည်','PASS':'အောင်မြင်','FAIL':'မအောင်မြင်','PENDING':'စောင့်ဆိုင်းနေသည်',
    'REAL DEVICES ONLY':'စက်အစစ်များသာ','PRIVATE BY DESIGN':'ဒီဇိုင်းအရ သီးသန့်','DEVICE-FIRST NETWORK':'စက်အခြေပြု ကွန်ရက်',
    'SYSTEM STATUS':'စနစ်အခြေအနေ','GET STARTED':'စတင်အသုံးပြုရန်','PRIVACY CHECK':'ကိုယ်ရေးလုံခြုံမှု စစ်ဆေးချက်',
    'ON-DEVICE ONLY':'စက်တွင်း၌သာ','LOCAL MODEL STATUS':'Local model အခြေအနေ','RUNTIME / MODEL REQUIREMENTS':'Runtime / model လိုအပ်ချက်များ',
    'DEVICE TO DEVICE':'စက်အချင်းချင်း','NATIVE TRANSPORT STATUS':'Native transport အခြေအနေ','RECEIVED FILES':'လက်ခံရရှိသော ဖိုင်များ',
    'CONNECTED DEVICES':'ချိတ်ဆက်ထားသော စက်များ','SECURE PAIRING':'လုံခြုံသော ချိတ်ဆက်ခြင်း','TRANSPARENT STATUS':'ရှင်းလင်းမြင်သာသော အခြေအနေ',
    'Browser logic':'Browser logic','Web Logic Test':'Web Logic စမ်းသပ်ချက်','Native Transport':'Native transport','Not physically verified':'စက်အစစ်တွင် မစမ်းသပ်ရသေး',
    'Not verified':'မစစ်ဆေးရသေး','Real Connected Devices':'တကယ်ချိတ်ဆက်ထားသော စက်များ','Native diagnostics':'Native စစ်ဆေးချက်များ',
    'Production verification pending':'Production verification စောင့်ဆိုင်းနေသည်','Production verification:':'Production verification:',
    'Local profile':'စက်တွင်း ပရိုဖိုင်','Account သို့မဟုတ် cloud sync မလိုပါ။':'Account သို့မဟုတ် cloud sync မလိုပါ။',
    'YOUR DEVICE':'သင့်စက်','LOCAL STORAGE':'စက်တွင်းသိမ်းဆည်းမှု','STORAGE & PRIVACY':'သိမ်းဆည်းမှုနှင့် ကိုယ်ရေးလုံခြုံမှု',
    'LOCAL THREADS':'စက်တွင်း စကားပြောခန်းများ','LOCAL PROFILE':'စက်တွင်း ပရိုဖိုင်','PRIVATE AI. NEARBY CHAT. NO INTERNET REQUIRED.':'သီးသန့် AI။ အနီးအနားစကားပြော။ အင်တာနက်မလိုပါ။',
    'Model loaded':'Model တင်ပြီးပါပြီ','Cancel generation':'ထုတ်လုပ်မှုကို ပယ်ဖျက်ရန်','Generate':'ထုတ်လုပ်ရန်',
    'Load':'တင်ရန်','Unload':'ဖြုတ်ရန်','Group member action':'အဖွဲ့ဝင် လုပ်ဆောင်ချက်','Offline group':'အင်တာနက်မဲ့အဖွဲ့',
    'Local only · no recipient':'စက်တွင်းသာ · လက်ခံသူမရှိ','Encrypted Nearby peer':'Encrypted Nearby စက်','Secure Nearby':'Secure Nearby',
    'Encrypted group':'စာဝှက်ထားသော အဖွဲ့','Only on this device':'ဒီစက်ထဲမှာသာ','Manage group members':'အဖွဲ့ဝင်များကို စီမံရန်',
    'Leave group':'အဖွဲ့မှ ထွက်ရန်','SIMULATION ONLY':'စမ်းသပ်မှုပုံစံသာ','Not started':'မစတင်ရသေးပါ',
    'Delete local app data':'စက်တွင်း app data ဖျက်ရန်','Android: encrypted with Keystore AES-GCM':'Android: Keystore AES-GCM ဖြင့် စာဝှက်ထားသည်',
    'Browser preview: tab memory only':'Browser preview: tab memory ထဲတွင်သာ','No devices are currently connected':'လက်ရှိ ချိတ်ဆက်ထားသော စက်မရှိပါ',
    'Testing required':'စမ်းသပ်ရန်လို','Which language would you like to use?':'ဘယ်ဘာသာစကားကို သုံးမလဲ',
    'Your language choice is saved only on this device.':'ဘာသာစကားရွေးချယ်မှုကို ဒီစက်ထဲမှာသာ သိမ်းမည်။',
    'Device matching QR identity:':'QR identity ကိုက်ညီသည့် စက်:','Clear QR filter':'QR filter ဖယ်ရန်','QR does not match':'QR မကိုက်ညီ',
    'Transferring':'လွှဲပြောင်းနေသည်','stopped safely':'အန္တရာယ်မရှိဘဲ ရပ်ထားသည်','Find devices':'စက်ရှာရန်','Connect':'ချိတ်ဆက်ရန်',
    'Comparing codes on the other phone is required.':'အခြားဖုန်းပေါ်ရှိ code နှင့် ကိုက်ညီမှုကို နှိုင်းယှဉ်ရန် လိုအပ်သည်။',
    'Could not start QR scan':'QR scan မစတင်နိုင်ပါ','Could not create QR':'QR ပြုလုပ်မရပါ','Could not start model import':'Model import မစနိုင်ပါ',
    'Could not start model loading':'Model load မစနိုင်ပါ','Could not start local generation':'Local generation မစနိုင်ပါ'
  };
  const maps = { en: new Map(Object.entries(EN)), my: new Map(Object.entries(MY)) };
  const attrNames = ['aria-label','aria-description','aria-valuetext','title','placeholder','alt'];
  const records = new WeakMap();
  const normalize = value => String(value).replace(/\s+/gu,' ').trim();
  function translate(value, language='my') {
    const lang = language === 'en' ? 'en' : 'my';
    const text = String(value), key = normalize(text), map = maps[lang];
    if (!key) return text;
    if (map.has(key)) return preserveSpace(text, map.get(key));
    // Translate known fixed phrases inside dynamic strings while leaving names, IDs,
    // status codes, and any unknown text intact.
    const entries = [...map.entries()].filter(([from]) => from.length > 3).sort((a,b)=>b[0].length-a[0].length);
    const escaped = entries.map(([from])=>from.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'));
    const matcher = escaped.length ? new RegExp(escaped.join('|'),'gu') : null;
    const translated = matcher ? key.replace(matcher,match=>map.get(match) ?? match) : key;
    return preserveSpace(text, translated);
  }
  function preserveSpace(original, translated) {
    const leading = original.match(/^\s*/u)?.[0] || '';
    const trailing = original.match(/\s*$/u)?.[0] || '';
    return leading + translated + trailing;
  }
  function updateText(node, language) {
    const current = node.nodeValue;
    let record = records.get(node);
    const source = record && current === record.last ? record.source : current;
    const next = translate(source, language);
    if (!record || current !== record.last) record = {source, last: next};
    else record.last = next;
    records.set(node, record);
    if (current !== next) node.nodeValue = next;
  }
  function updateAttributes(element, language) {
    let saved = records.get(element);
    if (!saved || saved.kind !== 'attributes') { saved = {kind:'attributes', values:Object.create(null)}; records.set(element,saved); }
    for (const name of attrNames) {
      if (!element.hasAttribute(name)) continue;
      const current = element.getAttribute(name), rec = saved.values[name];
      const source = rec && current === rec.last ? rec.source : current;
      const next = translate(source, language);
      saved.values[name] = {source,last:next};
      if (current !== next) element.setAttribute(name,next);
    }
  }
  function apply(language) {
    const lang = language === 'en' ? 'en' : 'my';
    document.documentElement.lang = lang === 'en' ? 'en' : 'my';
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) updateText(node, lang);
    document.body.querySelectorAll('*').forEach(element => updateAttributes(element,lang));
    const description = document.querySelector('meta[name="description"]');
    if (description) updateAttributes(description,lang);
    return lang;
  }
  let currentLanguage = 'my', observer;
  function setLanguage(language) {
    currentLanguage = language === 'en' ? 'en' : 'my';
    try { localStorage.setItem('nexus-language-v1',currentLanguage); } catch {}
    apply(currentLanguage);
    return currentLanguage;
  }
  function getSavedLanguage() {
    try { return localStorage.getItem('nexus-language-v1') === 'en' ? 'en' : 'my'; } catch { return 'my'; }
  }
  function start(language) {
    currentLanguage = language === 'en' ? 'en' : 'my';
    apply(currentLanguage);
    if (!observer) {
      observer = new MutationObserver(recordsList => {
        for (const record of recordsList) {
          if (record.type === 'characterData') updateText(record.target,currentLanguage);
          else {
            record.addedNodes.forEach(node => {
              if (node.nodeType === Node.TEXT_NODE) updateText(node,currentLanguage);
              else if (node.nodeType === Node.ELEMENT_NODE) {
                const walker = document.createTreeWalker(node,NodeFilter.SHOW_TEXT);
                let text; while ((text = walker.nextNode())) updateText(text,currentLanguage);
                updateAttributes(node,currentLanguage);
                node.querySelectorAll('*').forEach(element => updateAttributes(element,currentLanguage));
              }
            });
            if (record.type === 'attributes') updateAttributes(record.target,currentLanguage);
          }
        }
      });
      observer.observe(document.body,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:attrNames});
    }
  }
  window.NexusI18n = { translate, apply, start, setLanguage, getSavedLanguage, get language(){return currentLanguage;} };
})();
