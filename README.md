# YouTube Starter — the core mechanism, explained

[![Download APK](https://custom-icon-badges.herokuapp.com/badge/-Download%20APK-2ea44f?style=for-the-badge&logo=download&logoColor=white)](https://github.com/munnakumar678997/youtube-starter/releases/latest/download/youtube-starter.apk)

Click the button above to download the latest build (built automatically by GitHub Actions on every push to `main`).

## Yahi hai wo "part" jo aapne maanga

`app/src/main/java/com/example/youtubestarter/MainActivity.java` khol ke dekho —
poora mechanism bas ~15 lines ka hai. Ussi file mein top pe ek comment block
hai jo har cheez explain karta hai.

**Sach ye hai:** TubeEdit (aur is jaise saare "YouTube mod" apps) mein koi
YouTube API nahi hai. Wo sab sirf ek `WebView` (Android ka built-in
mini-browser, jo Chrome jaisa hi engine use karta hai) hai jo seedha
`https://m.youtube.com/` load karta hai:

```java
webView.loadUrl("https://m.youtube.com/");
```

Bas. Search, video playback, recommendations — sab **Google ki apni real
website** hai jo WebView ke andar render ho rahi hai. Baaki jo bhi cheez app
mein dikhti hai (download button, ad-block, PIP, etc.) wo sab is real
website ke upar **JavaScript inject karke** banayi gayi hai — asli data/UI
YouTube ka hi hai, hum sirf usko chhed rahe hain.

**Yahi wajah hai bugs ki:** Google jab bhi apni website ka HTML/JS structure
badalta hai (jo wo regularly karta hai), hamara injected JavaScript uske
saath sync nahi rehta aur cheezein toot jaati hain. Ye ek fundamentally
fragile approach hai — kisi doosre ki live website ke upar reverse-engineered
JS patches lagana.

---

## Agar aap SACH mein scratch se apna YouTube-jaisa app banana chahte ho

WebView approach mein aapki apni koi UI nahi hoti — wahi Google ki asli
website dikhti hai. Agar goal hai **apna custom UI + real YouTube data**,
to teen real raaste hain:

### Option A — YouTube Data API v3 (Official, Google se)
- Google ka official API — search, video info, channel info, comments sab
  milta hai structured JSON mein.
- **Limitation:** Ye sirf METADATA deta hai (title, thumbnail, description,
  view count). **Actual video playback ke liye ye direct video file/stream
  URL NAHI deta** — copyright/policy ki wajah se.
- Video chalane ke liye aapko Google ka **YouTube IFrame Player API**
  (ek embedded web player) ya **YouTube Android Player API** use karna
  padega — matlab playback ke liye phir bhi Google ka hi player component
  use hoga (embedded), bas search/browse UI aapka apna custom bana sakte ho.
- Free tier: roughly 10,000 "units" per day quota (1 search ≈ 100 units,
  matlab ~100 searches/day free) — API key chahiye (Google Cloud Console se).
- Sabse **safe/legal/stable** raasta hai, lekin quota-limited hai.

### Option B — Innertube (reverse-engineered internal API)
- Isi TubeEdit project ke `scripts/innertube.js` mein already use ho raha
  hai (downloads/playlist ke liye) — `youtubei.js` naam ki library YouTube
  ke apne INTERNAL (undocumented) API ko call karti hai jo khud YouTube
  app/website use karta hai.
- Isse **actual video stream URLs bhi milte hain** (isiliye download feature
  kaam karta hai) — matlab aap apna custom video player bhi bana sakte ho,
  Google ke embed pe depend nahi karna padta.
- **Koi official quota nahi, koi API key nahi chahiye** — lekin ye
  unofficial/undocumented hai, kabhi bhi Google ke server-side changes se
  toot sakta hai, aur YouTube ke Terms of Service ke against maana jaata hai.
- Ye woh library hai: [`youtubei.js`](https://github.com/LuanRT/YouTube.js)
  — isi ko `scripts/innertube.js` mein `import()` kiya gaya hai.

### Option C — WebView approach (jo abhi hai)
- Sabse simple, hamesha "kaam karta hai" (kyunki real website hi hai),
  lekin **koi custom UI nahi** — bas real YouTube ko chhed rahe ho.

---

## Meri suggestion

Agar aapka goal genuinely **apna khud ka, stable, custom-designed** app hai:
- **Playback/data ke liye Option B (innertube/youtubei.js)** use karo — isse
  aapko real video stream URLs milenge apne custom player mein daalne ke liye,
  bina Google API quota ki tension ke.
- Apna **poora UI khud React Native / native Android (Kotlin/Java) mein
  banao** — WebView ka use sirf tab karo jab bilkul zaroori ho (jaise koi
  legal/consent page), baaki sab native components.
- Ye architecture **zyada stable** rahegi kyunki aap kisi doosre ki live
  website ke DOM structure pe depend nahi karoge — sirf ek data API
  (`yt.getInfo()`, `yt.search()` jaisa) use karoge jo structured JSON deta
  hai, jisko parse karna easy aur predictable hai.

Bata do agar chahiye to main is naye starter project mein **innertube.js
based search+playback ka example bhi add kar doon** (search karke results
list dikhana, tap karne pe real video stream URL nikaal ke custom player
mein chalana) — taaki aap wahi se apna scratch-build shuru kar sako.
