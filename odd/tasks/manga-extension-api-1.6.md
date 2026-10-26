# Manga extension API 1.6 compatibility

## Objective

Make the app compatible with manga extensions built against tachiyomix 1.6
(`getMangaUpdate`, suspend `getPopularManga`/`getLatestUpdates`/`getSearchManga`,
`getPageList`), so Keiyoushi extensions load details, chapters and pages instead of
throwing `AbstractMethodError`.

## Problem

Extensions compiled against the 1.6 stub (`keiyoushi/extensions-lib`) extend the
app-provided `HttpSource` and implement only the new API. The fork still calls the
legacy `getMangaDetails`/`getChapterList`, which route to abstract helpers
(`mangaDetailsParse`, ...) that the extension never implements.

Evidence:

- `adb dumpsys dropbox --print data_app_crash`:
  `AbstractMethodError: abstract method "...HttpSource.mangaDetailsParse(okhttp3.Response)"`
  on `keiyoushi.source.Generated`.
- Dex dump of `eu.kanade.tachiyomi.extension.es.manhwaweb`:
  `Generated` extends `HttpSource`, declares `getMangaUpdate`, `getPageList`,
  `getPopularManga`; it does not declare `mangaDetailsParse`.

## Scope

- `source-api` (manga side only; anime sources untouched).
- App call sites that fetch manga details / chapter lists.
- Manga "Open in WebView" parity with anime (`getHomeUrl`).
- Extensions list globe fallback that opens a 404 repo URL.

## Tasks

- [x] T1 Add `SMangaUpdate` model (manga + chapters) in `source-api`.
- [x] T2 Add `MangaSource.getMangaUpdate` with a legacy fallback default.
- [x] T3 `HttpSource`: make legacy request/parse helpers `open` + throw instead of
      `abstract`; add `getHomeUrl()`.
- [x] T4 Migrate app call sites to `getMangaUpdate` (details/chapters).
- [x] T5 Use `getHomeUrl()` in the manga browse WebView (parity with anime).
- [x] T6 Fix extensions-list globe URL fallback (no 404 repo URL).
- [x] T7 Build, install on device, capture logcat, verify details/chapters/pages.
- [x] T8 Re-check Cloudflare handling with real requests after T7.

## Acceptance criteria

- Opening a Keiyoushi 1.6 manga source shows details and chapters without
  `AbstractMethodError`.
- Reading a chapter resolves pages.
- No crash when entering a source.
- Manga WebView button opens the source home URL like anime does.
- Verified on the connected device (Android 14, `xyz.jmir.tachiyomi.mi`).

## Constraints

- Do not break extensions built for lib 1.4/1.5 (`ParsedHttpSource` path).
- Do not modify anime sources.
- Commit only after device verification.

## Result

- Extensions built for lib 1.4/1.5 keep working through the legacy fallback.
- Extensions built for 1.6 (ManhwaWeb, ZonaTMO) load details and chapters; the
  `AbstractMethodError` and the concurrent `getMangaUpdate` error are gone.
- The manga extensions list no longer shows the dead web icon: Keiyoushi's
  `index.pb` has no `sources` field (only fields 1-8), so there is no URL to open.
- Cloudflare: ported Mihon's `CloudflareInterceptor` (cf-mitigated detection,
  per-host locks, isolated-world listener, cf_clearance re-check). LectorXD's
  challenge is interactive (`interactiveBegin`), which Mihon also aborts on; it
  requires solving the challenge once via the site's WebView. LectorXD remains
  non-functional until then.
