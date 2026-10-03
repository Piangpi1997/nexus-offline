# Optional / Future Online Integrations — NEXUS OFFLINE

ဤစာတမ်းသည် အနာဂတ်တွင် သီးခြားအွန်လိုင်း feature များ စီစဉ်လိုပါကသာ အသုံးဝင်မည့် မှတ်တမ်းဖြစ်သည်။ OpenAI, Todoist, Google Calendar နှင့် Notion integration များသည် NEXUS OFFLINE core ၏ dependency မဟုတ်သလို production blocker လည်း မဟုတ်ပါ။ Offline-first app ကို backend, OAuth provider, credential သို့မဟုတ် Internet မရှိဘဲ ဆက်လက်အသုံးပြုနိုင်ရမည်။

## လက်ရှိ source အခြေအနေ

လက်ရှိ source တွင် FastAPI/backend, PostgreSQL/Alembic schema, provider API client, OAuth callback, token lifecycle သို့မဟုတ် provider credential မရှိပါ။ Android manifest တွင် `INTERNET` permission မပါပါ။ ထို့ကြောင့် live provider connection များကို configure သို့မဟုတ် စမ်းသပ်ထားသည်ဟု မဆိုပါနှင့်။ ဤအချက်သည် offline core ၏ အောင်မြင်မှု သို့မဟုတ် production-ready ဖြစ်မှုအတွက် blocker မဟုတ်ပါ။

## အနာဂတ်တွင် အွန်လိုင်း feature တစ်ခုကို သီးခြားရွေးချယ်မှသာ

Provider တစ်ခုကို product scope ထဲသို့ ထည့်မည်ဟု သီးခြားဆုံးဖြတ်ပြီးနောက်မှ လိုအပ်သော service boundary, consent, authentication, data flow, callback URI, scope, token storage, disconnect/revoke, privacy notice နှင့် secret placement ကို သတ်မှတ်ပါ။ ယခု source မသတ်မှတ်သေးသော endpoint, variable, scope သို့မဟုတ် callback တန်ဖိုးများကို မခန့်မှန်းပါနှင့်။ API key သို့မဟုတ် OAuth secret ကို Android asset/APK ထဲတွင် မထည့်ပါနှင့်။

## Provider အမည်များ

OpenAI, Todoist, Google Calendar, Notion — **Optional / Future Online Integrations; not implemented; not required by the offline core.**

Release signing variables, if intentionally configured for a future signed release, are build/signing inputs only; they do not enable provider integration. No secret values are included in this file.
