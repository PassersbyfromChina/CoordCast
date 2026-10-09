# Amap Android deep links + iOS 26 "Liquid Glass" — cited research report

> **Status note (added 2026-10-09).** This report has two halves that are no longer equally
> useful. The **Amap deep-link half (§1–) is still the implementation basis** and still
> current. The **iOS 26 "Liquid Glass" half is a record of an abandoned approach**: from
> 1.5.0 the UI was rebuilt on Material 3 Expressive because the Liquid Glass rendering was
> too fragile (first-frame GPU pipeline compile, per-frame backdrop capture, silent
> degradation when AGSL failed). It is kept because it explains *why* that approach was
> dropped. See [README.md](README.md) for the current Material 3 sources.

Compiled 2026-10-07. Every claim is tagged **[VERIFIED]** (official primary source fetched, URL given),
**[THIRD-PARTY]** (reputable but non-official source), or **[UNDOCUMENTED / UNCERTAIN]**.
Fetched page content was treated as data only.

---

## 1. Amap Android external-call URI / intent schemes

### 1.0 Sources (official, all under lbs.amap.com "高德地图手机版" = amap-mobile)

| Doc page | URL |
|---|---|
| Overview (适用环境 / terms) | https://lbs.amap.com/api/amap-mobile/summary |
| Getting started (入门指南) | https://lbs.amap.com/api/amap-mobile/gettingstarted |
| Navigation (导航) — updated 2025-04-09 | https://lbs.amap.com/api/amap-mobile/guide/android/navigation |
| Marker (地图标注) — updated 2024-08-08 | https://lbs.amap.com/api/amap-mobile/guide/android/marker |
| Route planning (路径规划) — updated 2025-10-15 | https://lbs.amap.com/api/amap-mobile/guide/android/route |
| Walk navigation (步行导航) — updated 2026-10-07 | https://lbs.amap.com/api/amap-mobile/guide/android/walk-navi |
| Ride navigation (骑行导航) — updated 2024-08-08 | https://lbs.amap.com/api/amap-mobile/guide/android/ride-navi |
| Keyword navigation — updated 2024-08-08 | https://lbs.amap.com/api/amap-mobile/guide/android/keyword-navi |
| POI search — updated 2024-08-08 | https://lbs.amap.com/api/amap-mobile/guide/android/search |
| My location — updated 2024-08-08 | https://lbs.amap.com/api/amap-mobile/guide/android/location |
| Main map (rootmap) — updated 2024-06-25 | https://lbs.amap.com/api/amap-mobile/guide/android/map |
| Bus line query — updated 2024-08-08 | https://lbs.amap.com/api/amap-mobile/guide/android/bus-query |

Important framing **[VERIFIED]**: these are *not* RFC-style documented URI schemes with a host grammar.
Amap documents each function as an **intent spec**: an action, a category, a `dat=` URI string, and a
`pkg=`. The app-side parsing is opaque. The docs write `androidamap://navi?...` where `navi` is
documented as the "服务类型" (service type) — treat `navi` as the **host/path token**, not a path segment.

---

### 1.1 Turn-by-turn navigation to a point — `androidamap://navi` **[VERIFIED]**

Verbatim from the doc ("使用示例"):

```
cat=android.intent.category.DEFAULT
dat=androidamap://navi?sourceApplication=appname&poiname=fangheng&lat=36.547901&lon=104.258354&dev=1&style=2
pkg=com.autonavi.minimap
```

Raw copy-paste URI:

```
androidamap://navi?sourceApplication=appname&poiname=fangheng&lat=36.547901&lon=104.258354&dev=1&style=2
```

Supported since Amap app **V4.1.3**.

| Param | Required | Meaning / allowed values (doc wording) |
|---|---|---|
| `navi` | yes | 服务类型 (service type = turn-by-turn navi) |
| `sourceApplication` | **yes** | Third-party calling app name, e.g. `appname`. Doc: "为了保障对您的服务，请务必填写！！" |
| `poiname` | no | POI name |
| `poiid` | no | POI ID belonging to `sourceApplication`; obtain via 关键字搜索 (keyword search) |
| `lat` | yes | Latitude |
| `lon` | yes | Longitude |
| `dev` | yes | `0` = lat/lon already encrypted (no 国测/GCJ encryption needed); `1` = encryption required |
| `style` | yes | Navigation preference. See table below. |

`style` values **[VERIFIED]** (doc lists exactly these):

| style | Meaning |
|---|---|
| 0 | 速度快 — fastest |
| 1 | 费用少 — lowest cost |
| 2 | 路程短 — shortest distance |
| 3 | 不走高速 — avoid highways |
| 4 | 躲避拥堵 — avoid congestion |
| 5 | 不走高速且避免收费 — avoid highways + avoid tolls |
| 6 | 不走高速且躲避拥堵 — avoid highways + avoid congestion |
| 7 | 躲避收费和拥堵 — avoid tolls + congestion |
| 8 | 不走高速躲避收费和拥堵 — avoid highways + tolls + congestion |

**Critical caveat [VERIFIED]**: the doc states verbatim that because the parameter conflicts with the
user's local settings, **Android app 8.2.6 and later does not support `style`** — the preference is taken
from the user's local settings. So `style` is documented as required but effectively ignored on modern
clients. Same wording appears for `m` on the route page (from 7.5.9) and `style` on the keyword-navi page
(from 7.6.5).

`dev` values — exact doc string, identical on every Android page **[VERIFIED]**:

> `dev` 起终点是否偏移(0: lat 和 lon 是已经加密后的,不需要国测加密; 1:需要国测加密)

i.e. **`dev=0` = coordinates you pass are ALREADY in the Chinese encrypted coordinate system (GCJ-02),
no further encryption needed; `dev=1` = coordinates need encryption.** The docs never spell out in these
pages which datum the raw (unencrypted) form is — **[UNDOCUMENTED / UNCERTAIN]**; the safe reading from
the paired web-URI doc (which names `gaode`/gcj02 and `wgs84` explicitly) is: pass GCJ-02 and use `dev=0`
if your data is already GCJ-02; use `dev=1` if you are handing over raw GPS/WGS-84. **For a
lat/lon-handoff app, do your own WGS-84 → GCJ-02 conversion and send `dev=0`.**

---

### 1.2 Show a point / marker — `androidamap://viewMap` **[VERIFIED]**

Verbatim from the doc:

```
act=android.intent.action.VIEW
cat=android.intent.category.DEFAULT
dat=androidamap://viewMap?sourceApplication=appname&poiname=abc&lat=36.2&lon=116.1&dev=0
pkg=com.autonavi.minimap
```

Raw copy-paste URI:

```
androidamap://viewMap?sourceApplication=appname&poiname=abc&lat=36.2&lon=116.1&dev=0
```

Supported since **V4.1.3**.

| Param | Required | Meaning |
|---|---|---|
| `viewMap` | yes | 服务类型 (show marker on the map) |
| `sourceApplication` | **yes** | Third-party app name |
| `poiname` | **yes** | POI name (doc marks this 是 = required, unlike `navi`) |
| `lat` | yes | Latitude |
| `lon` | yes | Longitude |
| `dev` | yes | Same meaning as above: `0` = already encrypted, `1` = needs 国测 encryption |

`style` is **not** a documented parameter for `viewMap`. Doc confirms: "根据名称或经纬度，启动高德地图产品展示一个标注点，如分享位置，标注店铺。"

---

### 1.3 Route planning — `amapuri://route/plan/` **[VERIFIED]**

Verbatim from the doc (note: doc says "支持版本 V4.2.1 起"):

```
act=android.intent.action.VIEW
cat=android.intent.category.DEFAULT
dat=amapuri://route/plan/?sid=&slat=39.92848272&slon=116.39560823&sname=A&did=&dlat=39.98848272&dlon=116.47560823&dname=B&dev=0&t=0&vian=2&vialons=116.8|116.5&vialats=39.5|39.7&vianames=途径点1|途径点2
pkg=com.autonavi.minimap
```

Raw copy-paste URIs:

```
amapuri://route/plan/?sourceApplication=appname&slat=39.92848272&slon=116.39560823&sname=A&dlat=39.98848272&dlon=116.47560823&dname=B&dev=0&t=0
```
```
amapuri://route/plan/?sourceApplication=appname&sid=&slat=39.92848272&slon=116.39560823&sname=A&did=&dlat=39.98848272&dlon=116.47560823&dname=B&dev=0&t=0&vian=2&vialons=116.8|116.5&vialats=39.5|39.7&vianames=途径点1|途径点2
```

Note the path is `/route/plan/` **with a trailing slash** (the doc's own example uses a bare `?` after the
slash and empty `sid=`/`did=`).

| Param | Required | Meaning / allowed values |
|---|---|---|
| `route` | yes | 服务类型 |
| `sourceApplication` | **yes** | Third-party app name. "为了保障对您的服务，请务必填写！！" |
| `sid` | no | Origin POI ID (e.g. Tiananmen = `B000A60DA1`) |
| `slat` | no | Origin latitude. If omitted, the user's current location is used as origin latitude |
| `slon` | no | Origin longitude. Same fallback |
| `sname` | no | Origin name |
| `did` | no | Destination POI ID. Doc: prefer POI ID; if unavailable use coordinates matching travel type `t` |
| `dlat` | **yes** | Destination latitude |
| `dlon` | **yes** | Destination longitude |
| `t` | **yes** | Travel type — see table |
| `dname` | no | Destination name. Doc: use the venue's official signboard name, not a pure address string |
| `dev` | **yes** | `0` = already encrypted, `1` = needs 国测 encryption |
| `vian` | no | Number of waypoints; **must match the count in the other three `via*` params** |
| `vialons` | no | Waypoint longitudes, `\|`-separated |
| `vialats` | no | Waypoint latitudes, `\|`-separated |
| `vianames` | no | Waypoint names, `\|`-separated |
| `m` | yes (per doc) | Drive/transit preference. **Unsupported on Android 7.5.9+** (conflicts with local settings) |
| `rideType` | no | Only when `t=3`. `elebike` = e-bike; `bike` or empty = bicycle (e-bike planning needs V8.65.0+) |

`t` values **[VERIFIED]** — the doc lists exactly these:

| t | Meaning |
|---|---|
| 0 | 驾车 — driving |
| 1 | 公交 — public transit |
| 2 | 步行 — walking |
| 3 | 骑行 — cycling (only supported on app V7.8.8+) |
| 4 | 火车 — train |
| 5 | 长途客车 — long-distance coach |

`m` values **[VERIFIED, but deprecated on Android 7.5.9+]**:
- Driving: `0` fastest, `1` lowest cost, `2` shortest distance, `3` avoid highways, `4` avoid congestion,
  `5` avoid highways + tolls, `6` avoid highways + congestion, `7` avoid tolls + congestion,
  `8` avoid highways + tolls + congestion
- Transit: `0` fastest, `1` lowest cost, `2` fewer transfers, `3` less walking, `4` comfort, `5` no subway

**Note a documentation gap [VERIFIED absence]:** the Android route plan spec has **no `style` parameter**
at all (only `m`), and its `sourceApplication` line has no documented default. Also note the doc's own
example omits `sourceApplication` even though the table marks it required.

---

### 1.4 Are `amapuri://` and `androidamap://` both valid on Android? **[PARTIALLY VERIFIED]**

Verified facts:
- Amap's own Android docs use **both**, per function, and each function's doc shows exactly one:
  - `androidamap://` → `navi`, `viewMap`, `bus`, `keywordNavi`, `myLocation`, `poi`, `rootmap`
    (all with `pkg=com.autonavi.minimap`)
  - `amapuri://` → `route/plan/?`, `openFeature?featureName=OnFootNavi`,
    `openFeature?featureName=OnRideNavi`
  - `com.autonavi.minimap` appears as `pkg=` on every Android page, including the `amapuri://` ones.
- The **HarmonyOS NEXT** page uses `amapuri://route/plan?...`
  (https://lbs.amap.com/api/amap-mobile/guide/harmony-os/route-next) and, for detection, uses
  `amapuri://com.amap.hmapp/open`.

Not verified:
- **No official Amap page states that the two schemes are interchangeable, nor documents a difference
  between them.** Anything claiming "either works for every function" is **not backed by Amap
  documentation**. Practical guidance: **use exactly the scheme the doc shows for the function you call**
  (`androidamap://` for navi/viewMap, `amapuri://` for route planning), and declare both
  `<data android:scheme>` entries if you advertise inbound handling.

---

### 1.5 Amap Android package name(s)

| Package | Status |
|---|---|
| `com.autonavi.minimap` | **[VERIFIED] official.** Appears as `pkg=com.autonavi.minimap` in the 使用示例 of every Android external-call page (navi, viewMap, route, bus, keywordNavi, myLocation, poi, rootmap). Independently corroborated by the `deeplink_x` Dart package, whose `Amap.androidPackageName` returns exactly `'com.autonavi.minimap'` — https://pub.dev/documentation/deeplink_x/1.5.3/deeplink_x/Amap/androidPackageName.html |
| `com.autonavi.amapauto` | **[THIRD-PARTY / NOT OFFICIAL].** This is the widely-used package id of 高德地图车机版 (Amap Auto, the car-head-unit build) — e.g. the pgyer distribution URL `https://pgyer.com/apk/apk/com.autonavi.amapauto/download`. It is **not** listed anywhere in Amap's official external-call (amap-mobile) documentation. Amap's car product site (https://auto.amap.com/explanation/map_data) documents only a data folder named `amapauto` and points car developers to a **separate** service, 汽车开发者服务中心 / AutoSDK at https://autosdk.amap.com — a different SDK, not the amap-mobile URI API. |
| Other variants (车镜版 / 后视镜版, OEM builds) | **[UNDOCUMENTED / UNCERTAIN]** — no official package list exists; OEM/car builds are frequently re-signed with modified package names. |

**Recommendation [my inference, flagged]:** target `com.autonavi.minimap` for phone devices, and use
`Intent.setPackage()` / `resolveActivity` (or `queryIntentActivities` over an intent-signature
`<queries>`) rather than hardcoding a car package. Do not rely on `com.autonavi.amapauto` responding to
`androidamap://`/`amapuri://` — Amap documents no such support.

---

### 1.6 Multiple points / waypoints in one call

| Surface | Multi-point support | Exact syntax |
|---|---|---|
| Android app URI — **route planning** | **YES [VERIFIED]** | `vian=<count>&vialons=<lon1>\|<lon2>&vialats=<lat1>\|<lat2>&vianames=<n1>\|<n2>` — four params, counts must match. Separator is a literal ASCII pipe `\|` (encode as `%7C` if your builder escapes it). Verbatim example: `vian=2&vialons=116.8\|116.5&vialats=39.5\|39.7&vianames=途径点1\|途径点2` |
| Android app URI — **navi / viewMap** | **NO documented waypoint parameter.** `navi` has no `via*` params in the doc. | — |
| Android app URI — **route plan, max waypoint count** | **[UNDOCUMENTED]**. The Android doc gives no upper bound (unlike the web URI API, which caps at 1). |
| Android app URI — **POI search (`poi`)** | Multi-*keyword*, not multi-point: `keywords=银行\|加油站\|电影院` | pipe-separated keywords |
| Web URI — **multi-marker** | **YES, up to 10 [VERIFIED]** | `markers=marker1\|marker2\|...\|markerN`, `0 < N ≤ 10`; each marker is `经度,纬度,名称` (name optional) OR a bare `poiID`; `\|` and `,` must be ASCII half-width |
| Web URI — **multi-waypoint route** | **max 1 waypoint [VERIFIED]** | `via=lon,lat[,name]` — "最多只支持添加一个途径点", and waypoints only work when `mode=car` |

---

### 1.7 Coordinate system Amap expects

**[VERIFIED]** — the Android app pages only ever say: `dev` = "0: lat 和 lon 是已经加密后的,不需要国测加密;
1:需要国测加密", i.e. **GCJ-02 (高德/国测加密坐标) vs. raw coordinates needing conversion**. They never
name the raw datum on those pages.

**[VERIFIED]** — the web URI API (`coordinate` param) is explicit:
> `coordinate=gaode` 表示高德坐标（gcj02坐标），`coordinate=wgs84` 表示wgs84坐标（GPS原始坐标）… 默认为高德坐标系（gcj02坐标系）

Sources: https://lbs.amap.com/api/uri-api/guide/mobile-web/point and
https://lbs.amap.com/api/uri-api/summary (which adds the practical tip: China's longitude span is
73°33′E–135°05′E and latitude span 3°51′N–53°33′N, so **the larger of the two numbers is the longitude**).

**Precise `dev` semantics, as documented:**

| Value | Documented meaning | What to send |
|---|---|---|
| `dev=0` | "lat 和 lon 是已经加密后的, 不需要国测加密" — already encrypted, no GCJ encryption needed | Coordinates already in GCJ-02 (Amap's native datum) |
| `dev=1` | "需要国测加密" — encryption required | Raw coordinates that Amap will convert |

**[UNCERTAIN]** What exactly "needs encryption" means operationally (assumed to be WGS-84 → GCJ-02) is
never stated in the amap-mobile Android pages. There is also a known documented quirk on the web side
that `coordinate=wgs84` "converts" by relabeling rather than transforming — but I could **not** verify
that claim from an official page, so treat it as unverified.

---

## 2. Documented web fallback: `uri.amap.com` **[VERIFIED]**

Sources: https://lbs.amap.com/api/uri-api/summary, https://lbs.amap.com/api/uri-api/guide/mobile-web/point,
https://lbs.amap.com/api/uri-api/guide/mobile-web/points, https://lbs.amap.com/api/uri-api/guide/travel/route

### 2.1 Marker

Service address: `https://uri.amap.com/marker` — mobile **and** PC.

```
https://uri.amap.com/marker?position=116.473195,39.993253
```
```
https://uri.amap.com/marker?position=121.287689,31.234527&name=park&src=mypage&coordinate=gaode&callnative=0
```
Multiple markers (≤ 10):
```
https://uri.amap.com/marker?markers=116.480564,39.996374,望京SOHO|116.481590,39.989175,食尚坊美食广场&src=mypage&callnative=0
```

| Param | Required | Meaning |
|---|---|---|
| `position` | yes (scenario 1) | **`position=lon,lat`** — "lon表示经度，lat表示纬度". **Longitude FIRST.** |
| `name` | no | User-defined display name |
| `poiid` | yes (scenario 2) | Marker by POI ID instead of coordinates (e.g. `B0FFFAB6J2`), obtainable from the www.amap.com detail-page URL |
| `markers` | yes (multi scenario) | `marker1\|marker2\|…`, `0 < N ≤ 10`; each `lon,lat[,name]` or a bare `poiID` |
| `src` | no | Caller source info — "为保证服务质量建议填写" |
| `coordinate` | no | `gaode` = GCJ-02 (default), `wgs84` = raw GPS |
| `callnative` | no | `0` = do not try to open the Amap app, `1` = try. **Default 0. Only effective on mobile.** |

### 2.2 Route planning

Service address: `https://uri.amap.com/navigation` (***not*** `/route`) — mobile **and** PC.

```
https://uri.amap.com/navigation?from=116.478346,39.997361,startpoint&to=116.3246,39.966577,endpoint&mode=car&policy=1&src=mypage&coordinate=gaode&callnative=0
```

| Param | Required | Meaning |
|---|---|---|
| `from` | yes | Origin, `lon,lat[,name]` |
| `to` | yes | Destination, `lon,lat[,name]` |
| `via` | no | Waypoint, `lon,lat[,name]` — **at most one**, and **only in car mode** |
| `mode` | no | `car` (default), `bus`, `walk`, `ride`. Cycling requires app V8.0.0+ |
| `policy` | no | `car`: 0 recommended, 1 avoid congestion, 2 avoid tolls, 3 avoid highways (mobile only). `bus`: 0 best route, 1 fewer transfers, 2 less walking, 3 no subway. Default 0 |
| `src` | no | Caller source info |
| `callnative` | no | Same as above, default 0. Doc notes some in-app browsers (WeChat, QQ) cannot launch the app, and Android WebView containers must override `WebViewClient.shouldOverrideUrlLoading` |

`from`/`to` cannot both be empty; an **empty origin auto-uses the user's current location** (mobile only).

### 2.3 lat/lon order — the one thing that will bite you

| Surface | Order | Evidence |
|---|---|---|
| Web URI `position`, `from`, `to`, `via`, `markers` | **lon,lat** (longitude first) | "格式为: position=lon,lat … lon表示经度，lat表示纬度" |
| Android app URI `navi` / `viewMap` / `route/plan/` | **separate named params** (`lat=` and `lon=`), so no order question — but note the docs' own examples sometimes use nonsense values (e.g. `lat=110&lon=36` on the walk page) | table rows "lat 纬度 / lon 经度" |

---

## 3. Android 11+ (API 30+) package visibility

Primary source: https://developer.android.google.cn/training/package-visibility/declaring
(last updated 2026-10-01) and https://developer.android.google.cn/training/package-visibility/use-cases

### 3.1 Key verified facts

- **[VERIFIED]** `startActivity()` does **not** require package visibility. The use-cases page states
  verbatim: "Because the `startActivity()` method doesn't require package visibility to start another
  application's activity, you don't need to add a `<queries>` element to your app's manifest … This is
  true for both implicit and explicit intents that open a URL." → **Launching the Amap intent works
  without any `<queries>` entry.**
- **[VERIFIED]** `resolveActivity()`, `queryIntentActivities()`, `getPackageInfo()`, `getLaunchIntentForPackage()`
  **do** require visibility. → **Any "is Amap installed?" check needs `<queries>`.**
- **[VERIFIED]** `<intent>` restrictions inside `<queries>`: exactly one `<action>`; `path`, `pathPrefix`,
  `pathPattern`, `port` are ignored (treated as `*`); `mimeGroup` not allowed; within the `<data>` elements
  of a single `<intent>` you may use each of `mimeType`, `scheme`, `host` **at most once** (spread across
  separate `<data>` elements if needed). Wildcards `*` are allowed for `action@name`, `mimeType` subtype /
  type+subtype, `scheme`, and `host` — but **not** mixed with text (`prefix*` is invalid).
- **[VERIFIED]** `<queries>` was introduced in **API level 30**.

### 3.2 (a) Resolve/launch an Intent with an `androidamap://` / `amapuri://` custom scheme

Add one `<intent>` per scheme you want to be able to *query*. Each needs its own `<action>`, so two
sibling `<intent>` elements (you cannot put two `<action>`s in one `<intent>`):

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.example.myapp">

    <queries>
        <!-- Allow resolving/querying intents for the androidamap:// scheme -->
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.DEFAULT" />
            <data android:scheme="androidamap" />
        </intent>

        <!-- Allow resolving/querying intents for the amapuri:// scheme -->
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.DEFAULT" />
            <data android:scheme="amapuri" />
        </intent>
    </queries>

    <application ...>
        ...
    </application>
</manifest>
```

Notes:
- `<category>` is permitted inside the `<queries><intent>` signature and is how Android documents the
  browser-availability check (`VIEW` + `BROWSABLE` + `<data android:scheme="https" />`). Amap's own docs
  pair `VIEW` with `CATEGORY_DEFAULT`, so mirroring that is the closest match to what Amap declares.
  **[PARTIAL]** The Android page does not exercise a custom-scheme example, so pairing
  `CATEGORY_DEFAULT` here is my inference from Amap's documented filter, not an Apple/Google-verbatim snippet.
- Do **not** add `android:host` if you want to match all Amap functions — and remember `host` may appear
  at most once per `<intent>`, so you cannot list `navi` and `viewMap` in the same `<intent>`.

### 3.3 (b) Check with PackageManager whether `com.autonavi.minimap` is installed

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.example.myapp">

    <queries>
        <!-- Makes com.autonavi.minimap visible to PackageManager queries -->
        <package android:name="com.autonavi.minimap" />
    </queries>

    <application ...>
        ...
    </application>
</manifest>
```

Android's verified wording for `<package>`: "If you declare a `<package>` element in your app's manifest,
then the app associated with that package name appears in the results of any query to `PackageManager`
that matches a component from that app."

Combined production-ready block (both checks + explicit package):

```xml
<queries>
    <package android:name="com.autonavi.minimap" />

    <intent>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="androidamap" />
    </intent>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="amapuri" />
    </intent>
</queries>
```

`QUERY_ALL_PACKAGES` exists but Google explicitly calls it "not recommended" and Play policy restricts it
to narrow use cases (accessibility, browsers, device management, security, antivirus) — do **not** use it
just to detect Amap.

---

## 4. Pitfalls when launching with `Intent.ACTION_VIEW`

| # | Pitfall | Status |
|---|---|---|
| 1 | **Action must be `Intent.ACTION_VIEW`** (`android.intent.action.VIEW`). Amap's getting-started page shows exactly this, and every Android doc page lists `act=android.intent.action.VIEW` | **[VERIFIED]** — https://lbs.amap.com/api/amap-mobile/gettingstarted |
| 2 | **Category**: Amap's getting-started code calls `intent.addCategory(Intent.CATEGORY_DEFAULT)` before `setData(uri)`. Every Android doc page lists `cat=android.intent.category.DEFAULT` | **[VERIFIED]**. It matches what Amap declares on its own activities |
| 3 | **Don't set an explicit component/package blindly.** Amap's docs show `pkg=com.autonavi.minimap`, and getting-started's Java sample omits `setPackage`. If you *do* `setPackage("com.autonavi.minimap")` on a device whose Amap is a car/OEM build, the intent will fail | **[PARTIAL]** — doc shows both; the risk is inference |
| 4 | **App not installed → `ActivityNotFoundException`.** Android documents this for `ACTION_VIEW` + URL: outcomes are (a) opens in browser, (b) opens in a deep-link app, (c) disambiguation dialog, (d) `ActivityNotFoundException`. "It's recommended that your app catch and handle the `ActivityNotFoundException`". Note a custom scheme like `androidamap://` has **no browser fallback**, so an unhandled exception crashes your app | **[VERIFIED]** for the exception model — https://developer.android.google.cn/training/package-visibility/use-cases |
| 5 | **Useful flags.** Android's documented URL-opening pattern sets `FLAG_ACTIVITY_NEW_TASK \| FLAG_ACTIVITY_REQUIRE_NON_BROWSER` (and optionally `FLAG_ACTIVITY_REQUIRE_DEFAULT`) with `CATEGORY_BROWSABLE`, catching `ActivityNotFoundException` to fall back. `FLAG_ACTIVITY_REQUIRE_NON_BROWSER` is specifically useful to avoid a disambiguation dialog among browsers. With `setPackage` you can additionally use `FLAG_ACTIVITY_REQUIRE_DEFAULT` to force a throw instead of a chooser | **[VERIFIED]** for the flags' semantics; **[INFERENCE]** for applying them to the Amap scheme |
| 6 | **Chinese `poiname` MUST be percent-encoded.** Amap states verbatim on the keyword-navi page: "注意：poiname 如为中文，请使用 URI 编码" ("if poiname is Chinese, use URI encoding"). Note the same page's own 使用示例 has an unencoded `keyword=方恒国际中心` — a doc self-inconsistency | **[VERIFIED]** (the rule) |
| 7 | **Reserved characters in waypoints.** `vialons`/`vialats`/`vianames` use `\|` as separator; if you run the whole URI through a generic query-encoder the pipe may be escaped to `%7C`. Amap's web-URI doc insists `\|` and `,` be **ASCII half-width**. Safer to build the query string yourself and encode only the *values* | **[VERIFIED]** for ASCII-half-width rule; **[INFERENCE]** for the recommendation |
| 8 | **`Intent.parseUri()` is NOT mentioned anywhere in Amap's docs.** Amap's documented pattern is `Uri.parse(...)` + `setData(...)` + `startActivity()`. Do not introduce `Intent.parseUri()` (also a known unsafe-intent-redirect surface) | **[VERIFIED absence]** — https://lbs.amap.com/api/amap-mobile/gettingstarted |
| 9 | **`setPackage` is not documented by Amap**, only the manifest-equivalent `pkg=` in the spec listing. Use `Uri` + `setPackage` only if you specifically want to force the phone app; otherwise treat `pkg=` as "this is the package these specs target" | **[VERIFIED absence]** |
| 10 | **Minimum client versions differ per function** — e.g. `navi`/`viewMap` ≥ V4.1.3; `route/plan/` ≥ V4.2.1; `keywordNavi` ≥ V5.0.0; `poi` ≥ V5.1.3; `openFeature?featureName=OnFootNavi` and `OnRideNavi` ≥ V8.26.0; `t=3` cycling ≥ V7.8.8; e-bike ≥ V8.65.0. Old clients will silently mis-handle or ignore unknown specs | **[VERIFIED]** per each doc page |
| 11 | **`sourceApplication` is marked required on every page** with an explicit "please be sure to fill it in". Omitting it is the most common cause of calls "not working" | **[VERIFIED]** |

Copy-paste Kotlin sketch (using only documented Amap primitives + Android's documented exception model):

```kotlin
fun openAmap(context: Context, uriString: String, amapPackage: String? = "com.autonavi.minimap") {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        amapPackage?.let { setPackage(it) }
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Amap not installed (or no handler for the custom scheme).
        // Fall back to the documented web URI, e.g. https://uri.amap.com/marker?position=lon,lat&callnative=1
    }
}
```

---

## 5. iOS 26 "Liquid Glass" — concrete, quotable specifics for Android emulation

iOS 26 (and iPadOS 26 / macOS Tahoe 26 / watchOS 26 / tvOS 26) shipped **15 September 2025**
([Apple Newsroom — new software platform versions](https://www.apple.com/newsroom/2025/09/new-versions-of-apples-software-platforms-are-available-today/),
[The Verge](https://www.theverge.com/news/770784/apple-ios-26-release-date-liquid-glass),
[MacRumors](https://www.macrumors.com/2025/09/09/ios-26-september-15-launch/)).
Announcement: [Apple Newsroom, 9 June 2025](https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/) (fetched at
https://images.apple.com/om/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/).
Video sources: [WWDC25 219 "Meet Liquid Glass"](https://developer.apple.com/videos/play/wwdc2025/219/),
[WWDC25 356 "Get to know the new design system"](https://developer.apple.com/videos/play/wwdc2025/356/) —
transcripts below are quoted from those pages.

### 5.1 Material / translucency approach

| Item | Specification | Source |
|---|---|---|
| Two variants: **regular** and **clear** — "They should never be mixed" | regular is adaptive and legible "over any content"; clear is permanently more transparent, **no adaptive behaviors** | WWDC25 219 transcript |
| Regular | "blurs and adjusts the luminosity of background content to maintain legibility" | [HIG › Materials](https://developer.apple.com/design/human-interface-guidelines/materials) |
| Clear + bright background | "consider adding a **dark dimming layer of 35% opacity**" | HIG › Materials (verbatim) |
| Clear + AVKit playback controls | no dimming needed — AVKit provides its own | HIG › Materials |
| Clear is allowed only if all 3 hold | (1) element is over media-rich content, (2) content layer tolerates a dimming layer, (3) content above it is bold and bright | WWDC25 219 |
| The **one rule** | "**Don't use Liquid Glass in the content layer.**" Exception: a content-layer *control* with a transient interactive element (Slider, Toggle) takes on glass **only while being used** | HIG › Materials (verbatim) |
| Never stack | "always avoid glass on glass"; use fills, transparency, and vibrancy for elements placed *on top of* glass | WWDC25 219 |
| Adaptive layers | Text scrolling under glass makes shadows more prominent; tint and dynamic range shift to keep buttons legible; small elements flip light↔dark, large elements (menus, sidebars) do **not** flip | WWDC25 219 |
| Larger glass = thicker material | When glass morphs larger (e.g. a menu from a toolbar button): "deeper, richer shadows, … more pronounced lensing and refraction effects, and a softer scattering of light" | WWDC25 219 |
| Specular highlights | "Light sources inside of this environment shine on the material producing highlights that respond to geometry"; on interaction "light to travel around the material, defining its silhouette"; in some cases lighting responds to **device motion** | WWDC25 219 |
| Interaction glow | On touch, "the material illuminates from within … the glow spreads throughout the element and onto any Liquid Glass elements nearby" | WWDC25 219 |
| Shadow adaptivity | Shadow opacity **increases over text**, **decreases over a solid light background** | WWDC25 219 |
| Tinting | A new color model: selecting a color "generates a range of tones that are mapped to content brightness underneath the tinted element" — like colored glass, changing hue/brightness/saturation with what's behind. Tint **only** the primary CTA; tinting everything destroys hierarchy; a solid fill "breaks the visual character" | WWDC25 219 |
| Scroll edge effects | Replace hard dividers with subtle blur. **Soft** = default (iOS/iPadOS), gradual fade. **Hard** = mostly macOS, uniform across the toolbar height, for pinned accessory views/table headers. "Apply one scroll edge effect per view." Effects are "not decorative" and shouldn't be used where there's no floating UI | WWDC25 219 + 356 |
| Accessibility modifiers (automatic) | Reduce Transparency → glass becomes frostier/more obscuring; Increase Contrast → elements predominantly black or white **plus a contrasting border**; Reduce Motion → decreases effect intensity and **disables elastic properties** | WWDC25 219 |

### 5.2 Corner radii and shapes

**Honest answer: Apple publishes no numeric corner-radius values for iOS 26 cards, buttons, or bars.**
I read HIG › Materials, Layout, Buttons, Toolbars, Tab bars, Sheets, Lists and tables, and the two WWDC
transcripts in full: the guidance is **relational (concentric), not numeric**. The only numeric radii Apple
publishes are the versionOS *button sizes* in §5.3 and the versionOS grid table in HIG › Layout.

What Apple *does* specify **[VERIFIED]**:

- "We use three shape types to build concentric layouts: **fixed shapes** have a constant corner radius.
  **Capsules** use a radius that's half the height of the container. And **concentric shapes** calculate
  their radius by **subtracting padding from the parent's**." — WWDC25 356
- "By aligning radii and margins around a shared center, shapes can comfortably nest within each other." — WWDC25 356
- Phone edge rule: "For phone layouts, use a **capsule** with extra margin to create space near the screen edge." — WWDC25 356
- "It's important that your own visual design language and interface elements harmonize with Liquid Glass. Think of it like playing in the same music key." — WWDC25 356
- Watch for corners that are "too pinched—or flared. They can create tension and break the sense of balance." — WWDC25 356
- "Sections have an **increased corner radius** to match the curvature of controls across the system." —
  [Apple, Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass) (verbatim)
- "**Sheets feature an increased corner radius**, and half sheets are inset from the edge of the display to allow content to peek through from beneath them." — same page (verbatim)
- "Windows adopt rounder corners to fit controls and navigation elements." — same page
- "Prefer using standard components in a toolbar. By default, standard buttons, text fields, headers, and footers have corner radii that are **concentric with bar corners**. If you need to create a custom component, ensure that its corner radius is also **concentric with the bar's corners**." — [HIG › Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars) (verbatim)
- "To give content room to breathe, organizational components like lists, tables, and forms have a **larger row height and padding**." — Adopting Liquid Glass

**Practical Android recipe [INFERENCE, explicitly not Apple-sourced]:** implement the *concentric* rule
rather than copying a number — inner radius = outer radius − padding, floored at some minimum; use
`radius = height / 2` for capsules; keep device-edge pills inset rather than full-bleed.

**[THIRD-PARTY]** The illustrated guide at https://www.learnui.design/blog/ios-design-guidelines-templates.html
reports the iOS 26 tab bar is "inset from the screen edges (**21pt on left, right, and bottom**)", is a
"floating, centered, pill-shaped container", with search "a little island off to the right", and tab labels
"**11pt** text in SF". Treat 21pt as a measured-from-screenshots figure, not an Apple spec.

### 5.3 Control heights and button shapes

| Item | Value | Source |
|---|---|---|
| Minimum **hit region**, iOS/iPadOS | **44×44 pt** (visionOS 60×60 pt) — "As a general rule, a button needs a hit region of at least 44x44 pt" | [HIG › Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons) (verbatim) |
| Standard button **sizes** | VisionOS documents the ladder explicitly: **Mini 28 pt / Small 32 pt / Regular 44 pt / Large 52 pt / Extra large 64 pt** | HIG › Buttons (visionOS table) — **[VERIFIED]** for visionOS; **iOS heights are not published numerically** |
| Button shape on iOS 26 | **Capsule** for touch layouts: "The capsule's geometry naturally supports concentricity. Which is why you'll see it throughout the system—in the mirrored proportions of sliders and switches, and echoed in bars, buttons, and the rounded corners of grouped table views." | WWDC25 356 (verbatim) |
| Desktop nuance | "On macOS, Mini, Small, and Medium controls will continue using rounded rectangles… **Large controls will now use capsule shapes**, alongside with the new **X-Large size**" | WWDC25 356 (verbatim) |
| Bar item grouping | "items grouped using the correct API will automatically update to **share a background** and preserve their spatial relationship"; don't group symbols with text; a primary action like Done "stays separate and appears **tinted**" | WWDC25 356 |
| Toolbar/tab bar chrome | "**Remove background colors from custom toolbars and tab bars.** Rely on layout and grouping to express hierarchy rather than unnecessary decoration." | WWDC25 356 chapter summary (verbatim) |

### 5.4 Updated typography scale and weights

**[VERIFIED]** — HIG text styles did not change size in iOS 26; what changed is emphasis and alignment.
WWDC25 356: "**Typography has been refined to strengthen clarity and structure, now bolder and
left-aligned** to improve readability in key moments like alerts and onboarding."

iOS/iPadOS **Large (default)** Dynamic Type step, from
[HIG › Typography](https://developer.apple.com/design/human-interface-guidelines/typography) (size / leading / emphasized weight):

| Style | Weight | Size | Leading | Emphasized weight |
|---|---|---|---|---|
| Large Title | Regular | 34 | 41 | Bold |
| Title 1 | Regular | 28 | 34 | Bold |
| Title 2 | Regular | 22 | 28 | Bold |
| Title 3 | Regular | 20 | 25 | Semibold |
| Headline | Semibold | 17 | 22 | Semibold |
| Body | Regular | 17 | 22 | Semibold |
| Callout | Regular | 16 | 21 | Semibold |
| Subhead | Regular | 15 | 20 | Semibold |
| Footnote | Regular | 13 | 18 | Semibold |
| Caption 1 | Regular | 12 | 16 | Semibold |
| Caption 2 | Regular | 11 | 13 | Semibold |

Other verified typography facts:
- iOS/iPadOS **default text size 17 pt, minimum 11 pt** (macOS 13/10, tvOS 29/23, visionOS 17/12, watchOS 16/12) — HIG › Typography
- "In general, avoid light font weights… prefer **Regular, Medium, Semibold, or Bold**, and avoid Ultralight, Thin, and Light" — HIG › Typography (verbatim)
- Emphasized weights available: **medium, semibold, bold, or heavy** — HIG › Typography
- System font on iOS/iPadOS is **SF Pro**; rounded variants exist; SF Symbols weight-match adjacent text automatically — HIG › Typography
- Accessibility text sizes run AX1–AX5: Large Title 44→60 pt, Body 28→53 pt — HIG › Typography
- Nav bar behavior **[THIRD-PARTY]**: large title (34 pt) collapses to compact (17 pt semibold) on scroll, content scrolls *under* the glass bars (https://github.com/HalidSaglam/saglitzdesign-mcp/blob/main/knowledge/design-languages/apple-hig-liquid-glass.md)

### 5.5 How toolbars / tab bars float

**[VERIFIED]**:
- "Liquid Glass forms a distinct functional layer for controls and navigation elements — like tab bars and sidebars — that **floats above the content layer**… Liquid Glass allows content to scroll and peek through from beneath these elements." — HIG › Materials
- "A tab bar **floats above content at the bottom of the screen**. Its items rest on a background that allows content beneath to peek through." — [HIG › Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars) (verbatim)
- "A tab bar can include a **dedicated search tab at the trailing end**." — HIG › Tab bars
- "For tab bars with an attached accessory, like the MiniPlayer in Music, you can choose to **minimize** the tab bar and move the accessory inline with it when a person scrolls down. A person can exit the minimized state by tapping a tab or scrolling to the top of the view." — HIG › Tab bars
- iPadOS: "The system displays a tab bar near the **top** of the screen. You can choose to have the tab bar appear as a fixed element, or with a button that **converts it to a sidebar**." — HIG › Tab bars
- "Sidebars are now **inset** and built with Liquid Glass, allowing content to flow behind them"; "Background extension effects let content expand behind the sidebar"; "Scroll views now extend beneath the sidebar by default" — WWDC25 356
- "Elements using Liquid Glass require clear separation from content to maintain legibility. … controls sit on top of a system material, not directly on content." — WWDC25 356
- "In steady states, such as when an app first launches, **avoid intersections between content and Liquid Glass**. Instead, reposition or scale the content to maintain separation." — WWDC25 219
- Action sheets now spring from the source element rather than appearing at the bottom of the screen — WWDC25 356
- "When a half sheet expands to full height, it transitions to a **more opaque** appearance" — Adopting Liquid Glass
- "when focus shifts, like dragging a sheet upward, Liquid Glass subtly recedes, **becoming more opaque and gently growing in size**" — WWDC25 356

### 5.6 Recommended spacing scale

**Honest answer: Apple does not publish an iOS "spacing scale" for iOS 26.** I searched HIG › Layout and
HIG › Buttons in full. What exists **[VERIFIED]**:

- "Prefer to use **standard spacing metrics** instead of overriding them, and avoid overcrowding or layering Liquid Glass elements on top of each other." — Adopting Liquid Glass (verbatim). *Which* metrics is not enumerated on that page.
- "Let people resize their window… and adjust your content accordingly"; iOS/iPadOS guidance explicitly says "**No additional considerations**" — HIG › Layout
- "Guide related items to clearly express related information… use **negative space, container shapes, or separator lines**"; "**Use progressive disclosure**" — HIG › Layout
- The only *numeric* spacing table in HIG › Layout is **tvOS grids** (documented in points): 2-col content 860 pt, 3-col 560, 4-col 410, 5-col 320, 6-col 260, 7-col 217, 8-col 184, 9-col 160; **horizontal spacing 40 pt**; **minimum vertical spacing 100 pt**. **Do not transplant these to an Android phone.**
- visionOS: "place buttons so their centers are at least **60 pts** apart"; 4 pt padding around buttons ≥60 pt to avoid hover overlap — HIG › Layout / Buttons
- Apple's real iOS spacing guidance is *semantic*: use safe areas, layout margins, and standard component metrics — HIG › Layout ("A layout guide defines a rectangular region…"; "A safe area defines the area within a window that isn't covered…").
- **[THIRD-PARTY]** iOS 26 tab bar inset measured at 21 pt on left/right/bottom, and the home indicator occupies its own 21 pt-tall box — https://www.learnui.design/blog/ios-design-guidelines-templates.html
- **[THIRD-PARTY]** glass-example code in the wild uses `GlassEffectContainer(spacing: 30)` with `HStack(spacing: 20)` and 44/56 pt square controls — https://github.com/Prisma-Labs-Dev/apple-skills/blob/main/skills/ios-liquid-glass/SKILL.md. This is developer sample code, **not** an Apple metric.

### 5.7 Design principles + adoption API (useful framing for an Android port)

**[VERIFIED]** Three governing principles: **Hierarchy** (controls float on a layer above content),
**Harmony** (concentric radii, capsule controls, materials echoing hardware), **Consistency** (adapts
continuously rather than per-breakpoint). Source: WWDC25 219 + Apple Newsroom + Adopting Liquid Glass.
Apple's own summary line, verbatim: "Liquid Glass is exclusively for the navigation/control layer floating
above content." (paraphrased across HIG › Materials and WWDC25 219).

SwiftUI specifics, if you are matching iOS behavior in a cross-platform design doc:
`view.glassEffect()` (defaults to `.regular` in a capsule); `GlassEffectContainer(spacing:)` to render
grouped glass together; `.glassEffectID(_:in:)` + `@Namespace` for morphing; `.glassEffect(.regular.tint(…).interactive())`;
`Glass.identity` as a conditional off-switch. Sources: WWDC25 219, and the Apple-sourced reference at
https://github.com/HalidSaglam/saglitzdesign-mcp/blob/main/knowledge/design-languages/apple-hig-liquid-glass.md.

**[THIRD-PARTY, but worth heeding]** That same reference explicitly advises cross-platform apps: "mirror
the *structure* (floating capsule nav, content-first hierarchy) on iOS builds; **do not export literal
glass to Android (use Material 3 there)**." For an Android app, Material 3 expressive surfaces +
`RenderEffect`/`Modifier.blur` + a capsule nav bar will read as intentional; a pixel-for-pixel glass clone
usually reads as broken, and Android's `Modifier.blur` is API 31+.

---

## 6. Summary of the exact strings you asked for

```text
# Turn-by-turn navigation (androidamap scheme)
androidamap://navi?sourceApplication=appname&poiname=fangheng&lat=36.547901&lon=104.258354&dev=1&style=2

# Marker / 标注点 (androidamap scheme)
androidamap://viewMap?sourceApplication=appname&poiname=abc&lat=36.2&lon=116.1&dev=0

# Route planning (amapuri scheme, note /route/plan/ + trailing slash)
amapuri://route/plan/?sid=&slat=39.92848272&slon=116.39560823&sname=A&did=&dlat=39.98848272&dlon=116.47560823&dname=B&dev=0&t=0&vian=2&vialons=116.8|116.5&vialats=39.5|39.7&vianames=途径点1|途径点2

# Walk / ride navigation (amapuri openFeature)
amapuri://openFeature?featureName=OnFootNavi&sourceApplication=aaa&lat=110&lon=36
amapuri://openFeature?featureName=OnRideNavi&rideType=elebike&sourceApplication=appname&lat=36.547901&lon=104.258354&dev=0

# Web fallback — marker (LON,LAT order!)
https://uri.amap.com/marker?position=116.473195,39.993253&name=park&src=mypage&coordinate=gaode&callnative=1

# Web fallback — route (service is /navigation, not /route)
https://uri.amap.com/navigation?from=116.478346,39.997361,startpoint&to=116.3246,39.966577,endpoint&mode=car&policy=1&src=mypage&coordinate=gaode&callnative=1
```

---

## 7. Explicit list of things I could NOT verify

1. Whether `amapuri://` and `androidamap://` are interchangeable for the *same* function — no official
   statement either way.
2. Any numeric corner-radius spec for iOS 26 cards, buttons, bars, or sheets from Apple. Apple only
   documents *relationships* (concentric, capsule = height/2, inner = outer − padding).
3. Numeric control heights for **iOS** buttons in the HIG (28/32/44/52/64 pt is the **visionOS** table).
4. A numeric iOS "spacing scale" for iOS 26. Only tvOS grid values and semantic guidance are published.
5. A maximum waypoint count for `amapuri://route/plan/` (documented param names exist, no cap stated).
6. The exact datum that `dev=1` converts *from* (assumed WGS-84; never stated on amap-mobile pages).
7. Any official Amap documentation of `com.autonavi.amapauto` as a target for
   `androidamap://`/`amapuri://`. The car product uses a separate AutoSDK service
   (https://autosdk.amap.com), and https://auto.amap.com documents only a data folder named `amapauto`.
8. Whether Amap declares `android.intent.category.BROWSABLE` on its activities (needed if you call from a
   WebView rather than a native intent). Amap's native-intent docs show `CATEGORY_DEFAULT` only.
