# XMPlus-MD3 Design

Status: Draft · 2026-11-17

## Goal

Rebuild XMPlus (`../XMPlus`, Flutter) as a native Android app that follows Material 3:

- Full feature parity with XMPlus 1.2.3 on Android
- Simplified Chinese and English UI
- Natural motion and a polished UI, styled after QQ Music and Ximalaya

## Decisions

| Topic | Decision |
|---|---|
| Toolkit | Kotlin, Jetpack Compose, Material 3 (including Expressive components and motion) |
| Platforms | Android only. Windows stays on XMPlus |
| SDK levels | minSdk 26, compileSdk and targetSdk 36 |
| App ID | `com.xuleo.xmplus.md3`, a separate app installed alongside XMPlus. Users move data by importing an XMPlus backup |
| Project identity | Maintained by GitHub user 1653756334 at `github.com/1653756334/XMPlus-MD3`. Links, updates and feedback point only to this repository |
| Navigation | 发现 · 听过 · 下载 · 我的 |
| Tests | No test code in git. Temporary tests only, deleted after use |
| License | AGPL-3.0-or-later, as a port of XMPlus |

## Scope

XMPlus 1.2.3 has about 76k lines of Dart: 14 API modules, 37 services, about 40 screens, about 1,040 UI strings, about 800 service messages and 134 error codes.

The hard parts are not the UI. These must be ported faithfully:

- Ximalaya protocol: request signing, AES, rebuilding paid audio links
- 听时 billing ledger: never double-charge, never lose recorded usage
- Download pipeline, whose cache is shared with the player

Not ported (Windows only): mini window, tray, taskbar buttons, window-close preferences, WebView2 checks, desktop FFmpeg paths, data-directory picker and command-line arguments.

Not carried over (not needed): 莘应用 update source and the update-source switch, Telegram link, Sentry error reporting.

## 1. Feature parity checklist

### 发现 Discover

- [ ] Channels with tabs and recommended sections
- [ ] Ranking preview and full rankings with all tabs
- [ ] 全部专辑 per channel, sortable (综合 / 最多播放 / 最近更新)
- [ ] Categories: groups → channels → albums with filters and sort
- [ ] Hot words, can be turned off in settings
- [ ] Search: all, albums, tracks, anchors, with paging
- [ ] Locate a track inside its album (定位)

### 专辑 Album

- [ ] Header with stats, 连载 / 完结, paid and 白金 labels
- [ ] Episode list: sort order remembered per album, range chips, jump to page, search inside the album
- [ ] 继续播放 第N集, per-episode progress, downloaded markers
- [ ] Full HTML introduction, similar albums
- [ ] Reviews: score, sort, replies, copy
- [ ] Subscribe, play, play next, add to queue
- [ ] Download one episode, a range such as `1,3,5-8`, or the whole album (up to 5,000 episodes), with multi-select
- [ ] Per-album speed, volume and intro/outro skip, with the server's suggestion

### 主播 Anchor

- [ ] Profile, verification badge, follow / unfollow
- [ ] Albums and tracks tabs, sorted by 默认 / 最新 / 最早 / 最多播放
- [ ] Followed anchors list

### 播放 Player

- [ ] Queue built from an album or the updates feed, continuing across album pages
- [ ] Queue editing: drag to reorder, reverse, remove, clear, check the album for new episodes
- [ ] Modes: sequential, list loop, single loop, shuffle without repeats
- [ ] Speed (global and per album), master volume and mute, per-album volume, seek intervals
- [ ] Intro/outro skip
- [ ] Sleep timer: after a duration or at the end of the episode; then pause or exit; optionally keep downloading
- [ ] Like, favorite, with counts
- [ ] Comments: replies, likes, posting, deleting your own
- [ ] Transcript (文稿)
- [ ] Bookmarks with notes
- [ ] Resume from local and cloud progress; optional upload with consent; start over above 95%
- [ ] Downloaded episodes play locally without using 听时
- [ ] Listen while downloading (边听边下), prefetch the next episode
- [ ] System controls: notification, lock screen, Bluetooth, headset

### 听过 Library

- [ ] Tabs: 听更新, 订阅, 历史 (cloud), 收藏, 书签, 关注的主播
- [ ] Combined across all accounts, searchable, removal from the owning account

### 下载 Downloads

- [ ] Task list and downloaded library, grouped by album
- [ ] Search, multi-select, pause / resume / cancel all, task details
- [ ] Retry, re-fetch the link, move to a new folder, delete audio (size shown first)
- [ ] Account choice and account rotation (轮询)
- [ ] Cost preview before starting (estimated 听时)
- [ ] Whole albums downloaded page by page, with waits and retries when rate-limited
- [ ] Minimum free space, concurrent downloads, quality (highest first by default)
- [ ] Auto-download new episodes of subscribed albums, without back-filling
- [ ] File-name template, per-album folders, duplicate-name policy (skip after check / replace / add number)
- [ ] FFmpeg: output format (original, MP3, M4A, M4B, FLAC), bitrate, tags, cover art, duplicate detection
- [ ] Background: progress notification, battery-optimization exemption
- [ ] Option: pause downloads when playback pauses

### 账号 Accounts

- [x] SMS login with Geetest verification
- [x] Cookie / request-header import (verified by a listening-time query before saving)
- [ ] Multiple accounts, primary account, restore and remove
- [x] 听时 balance, claim once
- [ ] Auto-claim: top up to 4 h when below 2 h; pauses after an uncertain result until confirmed manually
- [ ] Usage ledger: charges for playback and download prepayment; states prepared, submitting, confirmed, uncertain, rejected; settles before switching account or exiting; no charge while the balance is above 20 h

### 设置 Settings and system

- [ ] Appearance: theme mode, 7 preset colors, custom HEX / HSV color, OLED black, text size
- [ ] Backup and restore: ZIP (meta, setting, library, account), optional logins, preview before restoring, remap the download folder
- [ ] Import XMPlus backups
- [ ] In-app update from this project's GitHub releases; ignore a version; download and install the APK
- [ ] Developer: logs (detail level, export, copy), feedback prompt that opens this project's GitHub Issues
- [ ] About: licenses and links to this project
- [ ] New: language (跟随系统 / 简体中文 / English), color from wallpaper (Android 12+)

## 2. Tech stack

| Area | Choice | Replaces in XMPlus |
|---|---|---|
| UI | Compose + Material 3 (Expressive), Navigation 3, adaptive scaffolds | Flutter material |
| State and DI | ViewModel + StateFlow (one-way data flow), Hilt, coroutines | ChangeNotifier |
| Network | OkHttp + kotlinx.serialization, hand-written client (custom signing and retry rules) | dio |
| Playback | Media3 ExoPlayer + MediaSessionService | media_kit + audio_service |
| Shared cache | Custom Media3 DataSource reading the download cache directly | Local HTTP proxy |
| Storage | Room (library, tasks, ledger journal, album cache, queue), DataStore (settings) | state.json |
| Logins | Android Keystore AES-GCM | flutter_secure_storage |
| Images and color | Coil 3, color scheme extracted from the cover | — |
| Background downloads | User-initiated data transfer jobs on API 34+, dataSync foreground service below | Custom plugin |
| Media processing | Same source-built FFmpeg / FFprobe / LAME, reusing `tool/build_android_media.sh` and the CI workflow | Same |
| Web content | WebView for Geetest, transcripts and introductions | flutter_inappwebview |

Android 15 caps dataSync foreground services at 6 hours per 24 hours, which would kill long album downloads. User-initiated data transfer jobs have no such cap.

## 3. Modules

```
app/                 Activity, navigation, adaptive shell
core/model           Album, Track, Quota, error codes (no user-facing text)
core/protocol        XmlyClient, signing/AES, paid link rebuild, retry policy
core/account         login, accounts, quota, rewards, UsageLedger
core/media           playback service, queue, speed/skip/sleep, progress sync
core/download        tasks, AudioCache, FFmpeg, paths, auto-download
core/data            Room, DataStore, Keystore
core/designsystem    theme, type, shapes, motion tokens, shared components
feature/             discover · search · album · anchor · player · library · downloads · account · settings
```

Rules:

- Feature modules depend only on core modules.
- The protocol layer returns error codes, never user-facing text. The UI translates them.
- All billing goes through UsageLedger. The rules documented in the XMPlus Dart comments are ported one-to-one.

## 4. Chinese and English

- English is the fallback (`values/`); Simplified Chinese lives in `values-zh/`.
- In-app language switch (跟随系统 / 简体中文 / English) using Android's per-app language API, so it also appears in system settings.
- Locale-aware formatting: counts (12.3万 / 123K), relative time (3 小时前 / 3 hours ago), English plurals.
- The 134 error codes map to translated strings in one place.
- Content from Ximalaya (titles, categories, comments, hot words) stays as delivered.
- Lint fails the build on hard-coded text or a missing translation.
- Copy is shortened and free of technical wording. Near-duplicate strings (重试, 加载失败 variants) are merged.

## 5. UI (Material 3, styled after QQ Music and Ximalaya)

### Shell

#### Visual and layout baseline

- Reference QQ Music's clear content hierarchy, not its promotional features or player skins; use official Material 3 components.
- Neutral white surfaces in light mode, restrained green accents. Readable light-mode primary is `#007F57`; `#0E9F6E` remains a decorative brand color.
- Use Material 3 typography roles consistently. Page gutters 16dp, related-item gaps 8–12dp, section gaps 24dp; shared corner radii 4 / 8 / 12 / 16 / 24dp.
- Content is centered and limited to 640dp; account forms to 560dp. Text and controls wrap at large font sizes; no fixed-height text rows.
- Show one prominent primary action per section. Hide operations that are not connected yet; use text and icons, not color alone, to convey state.
- Verify Chinese/English, light/dark, 200% font, keyboard, empty/loading/error states and accessibility as each screen is built, not only in phase 7.

#### Navigation

- Bottom navigation bar: 发现 · 听过 · 下载 · 我的.
- Navigation rail at 600dp and up; two panes at 840dp and up.
- Floating mini player above the navigation bar: rounded card with cover, progress ring around the play button, swipe left/right to change episode. Tap or drag up to open the full player.

### 发现 Discover

- Search bar whose placeholder rotates through hot words; scrollable channel tabs.
- Featured carousel, then rows of album covers.
- Ranking cards with the top 3 numbers highlighted; chip grid of categories. No banners.

### 专辑 Album

- Collapsing header: blurred cover tinted with the cover's color; the cover animates in from the previous screen; anchor chip with follow button; stats row.
- Main actions: 继续播放 第N集, 订阅, download icon.
- Tabs 简介 / 节目 / 评价, with a sticky toolbar: sort, range chips (1–50, 51–100…), search, multi-select.
- Episode rows: number, duration, 已听 35%, download state, animated equalizer on the one playing. Long-press opens a multi-select action bar.

### Full player

- Color scheme taken from the cover, changing smoothly between episodes.
- Swipeable pages: 播放 / 文稿 / 评论.
- The cover shrinks slightly with a spring when paused.
- Progress bar is wavy while playing and flat when paused.
- Main controls: previous · seek back · play/pause (shape-morph button) · seek forward · next. Labels follow the configured intervals (defaults: back 15s, forward 30s).
- Second row: 倍速, 定时, 列表, 下载, 更多.
- Queue sheet: play mode, reverse, drag handles, 回到当前.
- Sleep timer sheet: 15 / 30 / 60 / 90 min, end of episode, custom; countdown chip on the player.
- Tablets and landscape: cover and controls on the left, transcript or comments on the right.

### 听过 Library

- The 6 tabs, with account filter chips.
- History grouped 今天 / 昨天 / 更早 with sticky headers.
- Swipe to remove, with an undo snackbar.

### 下载 Downloads

- Storage usage bar at the top.
- 下载中: live speed, pause / resume all.
- 已下载: grid of album covers, each opening an offline album page.

### 我的 Me

- One primary-account summary first: identity, last verified listening-time balance and its update time; manage accounts and refresh balance. Unknown balances are never shown as zero. No fabricated nickname or avatar.
- Before an account is added, show a short welcome and one Add account action. Read failures preserve the saved data and offer Retry.
- Account management is a separate, scrollable list. Mark the primary account; keep refresh, update login and confirmed removal in each account's menu. Removing a login retains its account records.
- Import is a separate page with a masked, pasteable field, a primary-account checkbox and one Import action. Verify online before saving; on update require the original account/device and preserve the primary selection. Show errors inline; never save the pasted text in restored UI state.
- Login information is saved encrypted with Android Keystore, outside system backup. Storage failure never resets the account list. Queries do not claim or spend listening time.
- Claim actions are added when rewards are implemented, not as inactive placeholders.
- Then grouped settings: playback, downloads, media processing, appearance, language, backup, updates, developer, about.
- Appearance page with a live preview.

### Login

- Phone number and SMS code; Geetest in a dialog.
- 导入登录信息 as the secondary option.

## 6. Motion

- Spring-based M3 Expressive motion throughout; honors the system "remove animations" setting.
- Shared-element transitions: cover between a list and the album page; cover between the mini player and the full player; avatar into the anchor page.
- Predictive back on every screen, including collapsing the player as the finger moves.
- Lists: items animate when added, removed or reordered; skeleton placeholders instead of spinners; pull to refresh.
- Small touches: play/pause morph, burst on like, equalizer on the playing episode, haptics on long-press and toggles.
- Tabs keep their own state when switching.

## 7. Changes from XMPlus

- Error codes translated in the UI instead of Chinese text in the API layer.
- Media3 DataSource instead of the local HTTP proxy.
- User-initiated jobs for downloads, avoiding the Android 15 cap.
- Paging for long lists (subscriptions, favorites and updates stop at 5,000 today).
- New: wallpaper color on Android 12+, per-app language, car and Bluetooth metadata. Android Auto browsing and a home-screen widget may follow later.
- XMPlus backup ZIPs remain importable.

## 8. Phases

Each phase ends with a side-by-side check against XMPlus on the same account.

0. Skeleton: Gradle setup, modules, design system, translation lint, CI (checks and debug build; release signing with this project's own keystore).
1. Protocol and accounts: API layer port; SMS / Geetest / Cookie login; quota, rewards, usage ledger; XMPlus backup import so testers can bring their accounts.
2. Browsing: discover, categories, rankings, search, album, anchor, reading reviews and comments.
3. Playback: Media3 service, mini and full player, queue, settings, sleep timer, bookmarks, progress, 听时 metering.
4. Library and interactions: the 6 tabs; like, favorite, subscribe, follow; posting, liking and deleting comments.
5. Downloads: shared cache, billing receipts, FFmpeg (source-built in CI, reusing `tool/build_android_media.sh` and the XMPlus workflow), background work, auto-download, offline library.
6. System: settings, backup export and restore, in-app update, logs and feedback, about.
7. Polish: motion pass, tablets and foldables, TalkBack, startup performance, translation review, full comparison against XMPlus.

## 9. Verification

- Protocol, crypto and ledger: temporary tests comparing Kotlin results with values produced by the XMPlus Dart code. Deleted before each commit and re-created whenever that code changes.
- CI: build and lint only.
- Each phase: side-by-side comparison with XMPlus on the same account.

## 10. Risks

- Billing correctness: a double charge or lost usage costs users 听时.
- Protocol changes on Ximalaya's side. MD3 imitates the same Lite app version (3.4.10) as XMPlus, so exposure is unchanged.
- Geetest misbehaving in the WebView on some phone brands.
- Running XMPlus and MD3 on the same account: keep auto-claim on in only one app, or both may send a claim.
- Licensing: as a port of AGPL code, MD3 stays AGPL-3.0-or-later. The PiliPlus-derived UI is not carried over.
