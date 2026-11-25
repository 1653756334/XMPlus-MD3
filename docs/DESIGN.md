# XMPlus-MD3 Design

Status: Draft · 2026-11-17 · Paused 2026-11-25

> 听书 is paused at the end of phase 5. Unchecked items and phases 6–7 resume after the music phases. Music is designed in `docs/MUSIC.md`.

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
| Navigation | 听书 · 音乐 · 听过 · 下载 · 我的 (发现 is renamed 听书; 音乐 is in `docs/MUSIC.md`) |
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

### 听书 Discover (former 发现)

- [x] Channels with tabs and recommended sections
- [x] Ranking preview and full rankings with all tabs
- [x] 全部专辑 per channel, sortable (综合 / 最多播放 / 最近更新)
- [x] Categories: groups → channels → albums with filters and sort
- [ ] Hot words, can be turned off in settings
- [x] Search: all, albums, tracks, anchors, with paging
- [ ] Locate a track inside its album (定位)

### 专辑 Album

- [ ] Header with stats, 连载 / 完结, paid and 白金 labels
- [ ] Episode list: sort order remembered per album, range chips, jump to page, search inside the album
- [ ] 继续播放 第N集, per-episode progress, downloaded markers
- [ ] Full HTML introduction, similar albums
- [ ] Reviews: score, sort, replies, copy
- [ ] Subscribe, play, play next, add to queue
- [x] Download one episode, a range such as `1,3,5-8`, or picked episodes (up to 5,000 at a time), with multi-select
- [x] Per-album speed, volume and intro/outro skip, with the server's suggestion

### 主播 Anchor

- [x] Profile, verification badge, follow / unfollow
- [x] Albums and episodes tabs in server order, loading the next page as the list is scrolled. The albums tab has a search bar for that anchor's albums only. Episodes are the anchor's sounds, newest first, up to 5,000
- [x] Followed anchors list

### 播放 Player

- [ ] Queue built from an album or the updates feed, continuing across album pages
- [ ] Queue: check the album for new episodes. No drag to reorder, reverse, remove or clear: the app is for novels, which play in order
- [ ] Order: sequential only. No shuffle, list loop or single loop, for the same reason
- [ ] Speed and volume remembered per album, a mute switch, the phone's keys as master volume; the skip interval stays 15 s with no setting
- [x] Intro/outro skip, per album only and never global, seeded from the server's suggestion (see Intro and outro skip)
- [ ] Sleep timer: after a duration or at the end of the episode; then pause or exit; optionally keep downloading
- [x] Like and favorite; the counts are not shown yet
- [x] Comments: reading, posting, liking, deleting your own; replies are not built yet
- [x] Transcript (文稿)
- [x] Bookmarks with notes
- [x] Resume from local and cloud progress; optional upload with consent; an episode that played to its end is recorded as finished (see Episode end)
- [x] Downloaded episodes play locally without using 听时
- [ ] Listen while downloading (边听边下), prefetch the next episode
- [x] System controls: notification, lock screen, Bluetooth, headset (see Full player)

### 听过 Library

- [x] Seven swipeable tabs: 历史 (this device), 云历史, 听更新, 订阅, 收藏, 书签, 关注的主播
- [ ] Combined across all accounts; the account lists read the primary account only
- [ ] Searchable

### 下载 Downloads

- [x] Task list and downloaded library, grouped by album
- [x] Pause / resume / cancel all, live speed, storage used and free
- [ ] Search, multi-select, task details
- [ ] Retry, re-fetch the link, move to a new folder, delete audio (size shown first)
- [ ] Account choice and account rotation (轮询)
- [x] Cost preview before starting (estimated 听时)
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
- [x] Multiple accounts, primary account, restore and remove
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

- Bottom navigation bar: 听书 · 音乐 · 听过 · 下载 · 我的. 听书 is the former 发现 tab.
- Navigation rail at 600dp and up; two panes at 840dp and up.
- Floating mini player above the navigation bar: rounded card with cover, progress ring around the play button, swipe left/right to change episode. Tap or drag up to open the full player.

### 听书 Discover (former 发现)

- One header row of mode headings (推荐 / 分类 / 排行) with a search icon at the end; scrollable channel tabs below. The header collapses on scroll.
- Featured carousel, then rows of album covers.
- Ranking cards with the top 3 numbers highlighted; chip grid of categories. No banners.

### 专辑 Album

- Collapsing header: blurred cover running up behind the status bar; cover on the left, title (two lines) and anchor on the right; stats and tags below. It collapses on scroll and comes back only when its list is at the top. The top-left button goes back at the top; once collapsed it brings the header back without moving the list.
- Main actions: 继续播放 第N集. 订阅 is an icon at the end of the top bar, so it stays in reach once the header has collapsed; it uses the icons of the 订阅 list in 听过 (outlined to add, filled in the accent colour when subscribed). Downloads start from the episode list rather than the header.
- Tabs 简介 / 节目 / 评价, swipeable, with no divider. The episode toolbar sticks under the tabs: the count and order as text (共765集 · 从旧到新), then icons to reverse the order, search, and pick episodes to download. Search and picking each open in place of it. Episodes load a page at a time as the list is scrolled down, so 从新到旧 reverses the whole album.
- Episode rows: number, duration, 已听 35%, download state, animated equalizer on the one playing. The download state is one outlined 24 dp control at the end of the row: an arrow to download, a dashed arrow while waiting, a ring that fills while downloading (tap to pause, tap again to resume), a check once downloaded, a warning sign after a failure (tap to retry).
- Picking: the toolbar's download icon, or a long press on a row, turns the rows' download controls into checkboxes (episodes already downloading or downloaded show their state instead). The toolbar then reads close · 已选 N 集 · pick by number (`1,3,5-8`, reading only the pages those numbers are on) · pick all loaded · download. Another tab or back ends picking.

### Full player

- Color scheme taken from the cover, changing smoothly between episodes.
- Top bar: minimize on the left, the page headings 播放 / 文稿 / 评论 in the middle (styled like 听书's mode headings). 更多 is the last button in the 点赞 · 收藏 · 书签 · 下载 row under the title. 播放, 文稿 and 评论 are three pages that swipe sideways under the top bar; 文稿 and 评论 sit on the same backdrop and keep to the same column (480 dp, 24 dp margins) as the cover and controls. 评论: avatar, name, the comment, then the date, replies and a compact like with its count on one quiet line; the box to write one is a filled pill with the send icon in the accent colour.
- The cover takes the largest square left above the controls and shrinks slightly with a spring when paused.
- Under the cover, top to bottom:
  - Episode title (two lines), then the album name, which opens the album.
  - Episode actions, icon only: 点赞 · 收藏 · 书签 · 下载. 下载 is the same download control as on episode rows, showing the episode's state.
  - Progress bar with the elapsed and total time in a pill riding on it (as in Ximalaya). Drag the pill or tap the bar to seek. The played part is wavy while playing and flat when paused.
  - Transport: seek back · previous · play/pause (shape-morph button) · next · seek forward. The seek buttons show the fixed 15 s interval.
  - Listening tools, icon over label: 播放列表 · 倍速 · 定时关闭. A setting away from its default shows its value in the accent color (1.5×, the sleep countdown). There is no play mode: episodes play in order, and 随机播放, 列表循环 and 单集循环 are left out on purpose, because this is for listening to novels, not music.
- 更多 sheet: one list of rows, each a round icon, a title and, where it helps, a line under it: 查看专辑, 查看主播, 跳过片头片尾 (showing its current setting, see Intro and outro skip) 音量 (the album's volume, or 已静音) and 回到开头 (dimmed when already at the start; it goes to where the episode begins, past any intro). Rows that open something end in a chevron.
- Unlike other screens, the player shows its planned actions before they work: until one is connected, a tap says 即将推出.
- Queue sheet: the album's episodes in episode-number order with the current one marked, and a locate icon to scroll back to it. Scrolling toward either end loads the album page beyond it, so the list runs from the first episode to the last. No reverse or drag handles.
- Setting sheets (speed, volume, sleep timer, intro/outro skip) share one pattern: a title with a reset icon where there is a default, − and + steppers in place of sliders, and the common values as a row of one-tap cells. A tap on − or + moves one step; holding runs on, faster the longer it is held, and the value it stops on applies a moment later, so a held button does not apply every step. Whatever was set is kept when the sheet closes.
- Speed sheet: a large stepper from 0.5× to 3× in 0.1× steps, with 0.8× / 1× / 1.2× / 1.5× / 2× as cells under it. The sheet stays open while you listen; the reset icon (恢复 1×) appears when the speed differs from 1×. The speed is remembered for the album only; an album that was never adjusted plays at 1×, whatever another album is set to.
  - Volume sheet: a large stepper for the album's volume from 0 to 100% in 5% steps, with 25 / 50 / 75 / 100% as cells, remembered per album, plus a switch that mutes every album until the app is closed (the level is dimmed while muted). The phone's volume keys stay the master volume, so there is no second master slider; there is no boost above 100%.
  - Playlist sheet: when the list reaches the end of the album, 检查新节目 sits under the last row (nothing is offered earlier, since there is nothing to check for) and re-reads the end of the album and adds episodes published since the queue was built, then says 新增 N 集 or 没有新节目. Playing continues undisturbed.
- Sleep timer sheet: the header says what is running (12:34 后暂停播放, or 本集播完后暂停播放). A grid of 15 / 30 / 45 / 60 / 90 分钟 and 本集播完 starts that timer with one tap and closes the sheet. 自定义 is a stepper for 1–120 minutes in whole minutes, opening at the length last started; 关闭定时 and 开始计时 sit side by side under it, 关闭定时 enabled only while a timer is running. The countdown replaces the 定时关闭 label.
- Episode end. One rule decides what happens when an episode finishes, and the same rule serves the sleep timer, the next episode and progress:
  - An episode is finished when playback reaches its end, or reaches the outro skip point when the album has one. A position the user only seeks to never finishes it.
  - When it finishes, in this order: 本集播完后 is on → pause, clear the timer, stay on the episode; otherwise there is a next episode → play it; otherwise → pause at the end.
  - Progress of a finished episode is saved as finished, not as an end position. Opening it again starts from the beginning (after the intro skip), and so does tapping it in the album or queue.
  - The play button on a finished episode plays the next episode when there is one, and replays the last one from the beginning.
  - Finished is a recorded state, not a position: playing to the end saves the episode as finished, and the next listen clears it. A position the user only seeks to, such as the last seconds, is never finished. There is no percentage threshold.
  - A bookmark or a drag to the end plays from that point and does not change this.
- Last session. The loaded episode, its album and the queue around it are kept on the device. After the app is swiped away, killed by the system or the phone restarts, the mini player and the player come back paused on that episode at its saved progress; nothing is resolved or charged until play. Play then works as reopening the episode does: newer cloud progress still applies, and a finished episode moves on to the next one. Changing the primary account forgets the session, and one saved under another account is never shown.
- Intro and outro skip. Per album only; there is no global setting. The values are the album's 片头 and 片尾 in whole seconds, 0 to 3600, and 0 means off.
  - Seeding: as in XMPlus, the server's suggestion for the album comes from `/mobile-settings/v1/skipsegment/get/{ts}` with `albumId` and `isShowRecommend=true`. `recommendSkip.recommendHeadSkip` and `recommendHeadTail` are milliseconds, rounded to seconds; a missing `recommendSkip` means there is no suggestion. The suggestion is only offered, never applied by itself.
  - Playback rules, from XMPlus: an episode that starts at the beginning, or resumes before the intro skip point, starts at the skip point; a resumed position past it is kept. At or after `duration − outro` the episode counts as finished by the rule above. If intro + outro is not shorter than the episode, skipping is off for that episode and the sheet says so. A deliberate seek into the outro, or a start inside it such as a bookmark, keeps the outro for that episode until it is opened again; a seek anywhere earlier leaves the outro skip on. Changing the intro while listening never overrides a position the user chose.
  - UI, in the 更多 sheet: a 跳过片头片尾 row, icon on the left, the current setting under it (片头 30 秒 · 片尾 15 秒, or 未设置) and in the accent color when set. It opens a sheet in the style of the speed and sleep sheets: two rows, 片头 and 片尾, each with − and + steppers from 0 to 3600 s in 1 s steps, applied once the buttons settle. Above them, when the server has a suggestion that differs from the current values, a card reads 喜马拉雅建议 片头 X 秒 · 片尾 Y 秒 with a 使用建议 button; with no suggestion the card is absent, and a failed lookup shows a short retry line without blocking the steppers. 恢复为不跳过 sets both to 0. The sheet applies to every episode of the album and says so.
- Tablets and landscape: cover and controls on the left, transcript or comments on the right.
- System controls:
  - The playback notification, lock screen, Bluetooth and headset controls all come from the one media session and offer play / pause, previous, next and 15 s back and forward. Next is offered only while the album has a later episode. Previous restarts an episode that is more than 3 s in and goes to the earlier episode otherwise.
  - Audio is declared as speech, so a notification sound or a navigation prompt from another app pauses the story and playback resumes afterwards instead of talking over it. Unplugging the headset or losing Bluetooth pauses.
  - Android 13 and later hide the notification until it is allowed, so the permission is requested once, the first time something plays.

### 听过 Library

- Being redesigned as one overview page for 听书 and 音乐 (`docs/MUSIC.md` · 听过). The sections below are kept as pages opened from it. Seven sections: 历史 (this device), 云历史, 听更新, 订阅, 收藏, 书签 and 关注的主播.
- 历史 is this device's own record, one row per album: the latest episode, where playback stopped and when. 选择 in the top bar selects rows (全选 selects all) and removes them with an undo snackbar. Swiping never removes, so the tabs still swipe.
- Account sections load the first time they are shown. Subscriptions and favourites can be removed in place; other rows open their album or anchor page.
- 书签 lists the device's bookmarks across all albums, whichever account is signed in, with the album and episode names kept when each bookmark was made. Tapping one plays its episode from the saved position in the player. Bookmarks made before the names were kept are completed from the catalogue, cover included, once per launch.
- 历史, 云历史 and 书签 rows play their episode from the saved position. A finished episode starts from the beginning. Other rows open their album or anchor page.

### 下载 Downloads

- Storage bar at the top: the space downloads use against what is left.
- 已下载 and 下载中 swipe, each with its count. Icons in the top bar act on everything in 下载中: pause all or resume all, and cancel all (confirmed, since charged 听时 is not refunded).
- 已下载: albums, newest first, with cover, title, episode count and size; a row opens in place into its episodes (tap to play, delete with the size shown first). The album's menu opens the album page or deletes its downloads.
- 下载中: cover, title and album, a thin progress bar, bytes so far and live speed; pause / resume (retry after a failure) and cancel. Cancelling an episode that was already charged asks first.
- Cost preview: before anything is queued, each picked episode the list does not mark as free is looked up the way playback resolves it. When any need 听时, a dialog shows how many, the total, the balance and the rules (charged per episode before it downloads, no refund on failure or cancel, a retry never charges twice), and the confirm button is off when the balance is short. Free downloads start at once. Only the confirmed amount is ever charged: an episode the preview took for free that turns out to need 听时 stops and asks to be downloaded again.

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
- Back is exactly the opening slide in reverse, whatever starts it: the back button, or a back swipe from either edge. A page opens by sliding in from the end side (the right, left to right) while the one below drifts a third of the way out; on back it slides off to that same side and the page below comes back from the other. The page follows the finger during the swipe; cancelling puts it back. Sideways pages (tabs, the player's 播放 / 文稿 / 评论) ease onto their place without overshooting, and the first and last stop flat instead of stretching. Sheets (the player, queue, speed and other sheets) keep Material's own sheet back. Switching from another tab back to 听书 is instant.
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

Phases 0–5 are done. Phases 6 and 7 are paused until the music phases M1–M6 (`docs/MUSIC.md`) are finished.

0. Skeleton: Gradle setup, modules, design system, translation lint, CI (checks and debug build; release signing with this project's own keystore).
1. Protocol and accounts: API layer port; SMS / Geetest / Cookie login; quota, rewards, usage ledger; XMPlus backup import so testers can bring their accounts.
2. Browsing: discover, categories, rankings, search, album, anchor, reading reviews and comments.
3. Playback: Media3 service, mini and full player, queue, settings, sleep timer, bookmarks, progress, 听时 metering.
4. Library and interactions: 听过 with its seven sections; like, favorite, subscribe, follow; comments in the player.
5. Downloads, all of it: the 下载 tab, the download buttons and the download engine (until then they are placeholders): shared cache, billing receipts, FFmpeg (source-built in CI, reusing `tool/build_android_media.sh` and the XMPlus workflow), background work, auto-download, offline library.
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
