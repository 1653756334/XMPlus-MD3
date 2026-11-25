# XMPlus-MD3 Music Design

Status: Draft · 2026-11-25

## Goal

Add a music platform to XMPlus-MD3 next to 听书:

- The app ships no music sources. Users import MusicFree plugins, which supply search, links, lyrics, rankings and sheets.
- Plugins written for MusicFree run unchanged. No MD3-specific plugin API, no re-adaptation by plugin authors.
- 听书 and 音乐 share one player, one library tab, one download manager and one visual language.

## Decisions

| Topic | Decision |
|---|---|
| Plugin format | MusicFree plugins (one JS file each), as of MusicFree 0.6.2 (`../MusicFree`, commit `d118b18`) |
| Behaviour source | `../MusicFree` source. Plugin runtime, install and update rules, media-source order, cache control, lyric order and backup format are ported one to one. When unsure, read the TypeScript, do not guess |
| JS engine | Embedded QuickJS through `quickjs-kt` (stable, coroutine-friendly for the http bridge). Not WebView: it cannot set Referer, User-Agent or Cookie and is bound by CORS, which breaks most plugins |
| Bundled packages | Built once with npm and esbuild into one asset that is committed; the app build does not need Node |
| Version reported to plugins | MusicFree's version (`0.6.2`), not MD3's, because plugins declare `appVersion` ranges against MusicFree. Raised only after checking the newer MusicFree source |
| Copyright | No built-in plugin, no default subscription, no link to any plugin source, in the app or in the docs. Without plugins the app plays no music |
| 听时 | Music never touches UsageLedger or Ximalaya accounts |
| Navigation | 听书 · 音乐 · 听过 · 下载 · 我的 (发现 is renamed 听书) |
| License | MusicFree is AGPL; ported code stays AGPL-3.0-or-later, credited in 关于 |

## 1. Feature checklist

### 插件 Plugins

- [ ] Runtime: the injected environment and bundled packages exactly as MusicFree (see 2)
- [ ] Install from local files (several at once) or a link, as MusicFree does
- [ ] Subscriptions: a list of `.js` / `.json` links, updated on demand
- [ ] Update one plugin from its `srcUrl`, or all; same-name plugins replace older versions only
- [ ] Enable / disable, drag to reorder, user variables, alternative plugin (替代插件), description, uninstall
- [ ] Incompatible or unreadable plugins are listed with the reason and never called

### 音乐 Music tab

- [ ] 推荐: sheet square (歌单广场) per plugin, with pinned and grouped tags
- [ ] 榜单: ranking groups per plugin
- [ ] Search: per plugin, by type (单曲 / 专辑 / 歌手 / 歌单 / 歌词 where supported), paged, with history
- [ ] Empty state when no plugin is installed, with one 导入插件 action

### 详情 Detail pages

- [ ] 歌单 / 榜单 / 专辑: header, 播放全部, paged songs, multi-select
- [ ] 歌手: profile, 单曲 and 专辑 tabs
- [ ] Star a plugin sheet into 收藏歌单

### 播放 Player

- [x] Media source in MusicFree's order: local file → cache → alternative plugin → plugin, one retry
- [x] Quality: 低品质 / 标准 / 高品质 / 超高品质, default and fallback direction as in MusicFree
- [x] Play modes: 列表循环 / 单曲循环 / 随机
- [x] Lyrics: synced, translation, tap a line to seek, lyric search when a song has none
- [x] Comments when the plugin supports them
- [ ] Editable queue: reorder, remove, clear, play next
- [ ] Shared with 听书: mini player, sleep timer, notification, lock screen, Bluetooth, last session

### 听过 Library (both kinds of content)

- [ ] One overview page instead of seven swipe tabs (see 5 · 听过)
- [ ] 最近播放: 听书 episodes and songs in one list, filter 全部 / 听书 / 音乐, resume from where each stopped
- [ ] 我喜欢, a built-in sheet
- [ ] 自建歌单: create, rename, delete, reorder songs, move and copy between sheets
- [ ] 收藏歌单
- [ ] Import a sheet or a song from a link through a plugin (`importMusicSheet`, `importMusicItem`)
- [ ] 听书 sections (云历史, 听更新, 订阅, 收藏, 书签, 关注的主播) as pages opened from the overview, content unchanged
- [ ] Search across the library

### 下载 Downloads

- [ ] Songs in the shared download manager, with quality choice
- [ ] Tags, cover and lyrics written to the file (shared FFmpeg pipeline)
- [ ] Downloaded songs play offline

### 迁移 Migration (last)

- [ ] Import a MusicFree backup: sheets, and plugins reinstalled from their links, as MusicFree restores them

## 2. Plugin runtime

Source: MusicFree `src/core/pluginManager/plugin.ts`, `index.ts`, `meta.ts`, `src/types/plugin.d.ts`.

### Loading

- The plugin's code runs as the body of `function(require, __musicfree_require, module, exports, console, env, URL, process)` in strict mode. `module.exports.default` wins over `module.exports`.
- `require` serves only the bundled packages: `cheerio`, `crypto-js`, `axios`, `dayjs`, `big-integer`, `qs`, `he`, `webdav`, and `@react-native-cookies/cookies` as the deprecated no-op stub. Each package's `default` is the package itself.
- `env`: `getUserVariables()`, the `userVariables` getter, `appVersion`, `os: "android"`, `lang: "zh-CN"`. `process`: `platform: "android"`, `version`, `env`. `lang` stays `zh-CN` whatever the UI language, as in MusicFree.
- Other globals that plugins rely on in MusicFree's React Native runtime (timers, `fetch`, `atob` / `btoa`, `TextEncoder`, `URLSearchParams` and the like) are provided to the same extent. The exact set is fixed in M1 from the MusicFree runtime and a survey of public plugins.
- `axios` is the real package with a request adapter backed by OkHttp: no CORS, every header passes as given. Defaults as in MusicFree: 2 s timeout, and a single `set-cookie` header split on commas with the original kept in `x-set-cookie`.
- After loading: `userVariables` without `key` are dropped; `appVersion` is checked with `compare-versions` `satisfies` against the reported version; a missing `platform` is an error. The plugin's hash is SHA-256 of its code.
- Plugins are mounted lazily on first use, each in its own context.

### Calls

- The 14 methods (`search`, `getMediaSource`, `getMusicInfo`, `getLyric`, `getAlbumInfo`, `getMusicSheetInfo`, `getArtistWorks`, `importMusicSheet`, `importMusicItem`, `getTopLists`, `getTopListDetail`, `getRecommendSheetTags`, `getRecommendSheetsByTag`, `getMusicComments`) are wrapped as MusicFree's `PluginMethodsWrapper` does: missing methods return empty results, `isEnd` defaults to true, and returned items get the plugin's `platform`.
- Songs, albums, artists and sheets are kept as the plugin returned them, unknown fields included, and handed back unchanged on later calls. Identity is `platform` + `id`.
- `getMediaSource`: local file → media cache under `cacheControl` (`cache` always, `no-cache` only offline, `no-store` never stored) → alternative plugin when it has `getMediaSource` → the plugin; without `getMediaSource`, `qualities[q].url` or `url`. User and password in a link become a Basic `Authorization` header. One retry after 150 ms unless the plugin returned no link.
- `getLyric`: MusicFree's order (associated lyric, local file, cache, plugin, deprecated `lrc` link, `rawLrc`), with lyric and translation cached as files.

### Install, update, remove

- Same hash as an installed plugin → already installed, nothing changes. Same `platform`, higher installed version → refused. Otherwise the new file replaces the old one of that `platform`.
- Meta per `platform`: order, disabled, user variables, alternative plugin. Kept across updates, removed when the last plugin of that `platform` is uninstalled.
- Subscriptions accept links ending in `.js` or `.json`; `.json` lists are read in MusicFree's format.

### Threads and errors

- Plugins run off the main thread, so the UI never waits on them. Nothing is added beyond MusicFree: no install confirmation, no warnings, no extra limits.
- A failing call fails that request only; the page shows its error state with 重试.
- Install results as in MusicFree: success, or the failure reason; several at once → how many succeeded and failed, with the failed ones listed. A batch (a link listing several plugins, 全部更新, 从订阅更新) shows a sheet with a progress bar, n/total and the plugin being installed, then the result of each, failures first, and 完成. One plugin shows the progress line and a snackbar. 从链接安装 starts with the link on the clipboard.

## 3. Modules

```
core/plugin      QuickJS host, bundled packages, http bridge, PluginManager, plugin meta
core/music       music items, media-source and lyric resolution, media cache, sheets, history
core/media       one player for both kinds of content (extended)
core/download    one download manager for both (extended)
```

- The bundled JS packages are built into one asset by a script kept in the repository; the asset is versioned with the app.
- `core/plugin` returns error codes, never user-facing text, as `core/protocol` does.

## 4. Data structure

One set of types for both kinds of content, so the player, mini player, notification, 最近播放 and downloads never branch on where an item came from.

### Keys

- 听书: `xmly:<id>`, as today. Stored data keeps working unchanged.
- 音乐: `mf:<platform>:<id>`. A plugin whose `platform` happens to be `xmly` cannot collide with 听书.
- A song is identified by `platform` + `id`, as in MusicFree.

### Playable items

- Two kinds only: 听书 episode and song.
- Both expose title, subtitle, cover and duration; shared UI uses only these.
- 听书 episodes keep the existing `Track` and `Album`.

### Plugin items

- Song, sheet, album and artist keep the fields the UI shows, plus the plugin's full object stored as is.
- Calls to a plugin pass that full object back unchanged, so unknown fields survive.

### Queues

- The queue is an interface with two implementations: the existing 听书 album queue (paging rules unchanged) and a music list queue that loads further pages when the list has them.
- The saved session records which kind of queue it was.

### History and downloads

- 最近播放 and download tasks get a content kind; existing rows are 听书.
- 听时 fields (receipt, confirmed seconds, account identity) belong to 听书 only. Deduction runs only for 听书.

### Music library

- A separate store for 我喜欢, 自建歌单 and 收藏歌单, as in MusicFree: songs are kept as full objects keyed by `platform` + `id`.

## 5. UI

### Comfort baseline

The whole app, 听书 included, follows these; 听书 screens are brought in line as they are touched.

- One navigation pattern: a header row of mode headings (as 听书's 推荐 / 分类 / 排行), scrollable tabs under it when needed. Never more than two levels of tabs on one screen.
- One row style for every list: 64dp minimum, 48dp cover (8dp corners, circle for people), title on one line, one quiet meta line, a single trailing action. Grids: 12dp corners, title up to two lines, no meta under 2 columns of text.
- Covers carry the colour: placeholders are tinted surfaces with an icon, never grey boxes; pages that open from a cover keep the shared transition.
- Calm surfaces: no dividers between rows, no outlined cards on lists; spacing groups content. One filled button per screen at most.
- Text is short: no section subtitles, no explanations beside controls; counts in labels (歌单 12) instead of separate badges.
- States: skeletons while loading, one line plus one action when empty or failed, a snackbar (with 撤销 where it undoes) after an action. No toasts, no blocking dialogs except to confirm deletion.
- Touch: 48dp targets, long press for multi-select everywhere, haptics on toggles; the mini player never covers the last row.
- Reachability: main actions in the lower half (播放全部 under the header, actions in sheets), top bar only for page-level actions.

### Shell

- Bottom bar: 听书 · 音乐 · 听过 · 下载 · 我的. Rail at 600dp and up with the same five.
- Back on any other tab goes to 听书; back on 听书 leaves the app.
- One mini player for both kinds of content. Swiping changes episode or song.
- One queue at a time: playing a song replaces a 听书 queue and the reverse. Progress of the 听书 episode is saved as usual, and the 听书 last session comes back when 听书 is played again.

### 音乐

- Header: 推荐 · 榜单, then the current source (plugin name with ▾) and the search icon at the end. The source opens a menu of the enabled plugins that support the current mode, in plugin order, the current one checked; a plugin without that ability is not listed. The choice is kept and shared by both modes; a mode it cannot show uses the first plugin that can, without changing the choice. With one plugin only its name shows, without ▾.
- 推荐: tag chips (pinned tags first, 全部 opens a sheet of grouped tags), then a 2- or 3-column grid of sheet covers with play counts where given.
- 榜单: groups with titles; each ranking a row with cover, name and its first three songs.
- Without plugins: centred message 还没有音乐来源 and one 导入插件 button, which opens 插件. No other content.
- Without plugins supporting the mode: 当前插件不支持 and a link to 插件.

### Search (音乐)

- Search field with history and clear. Results: one row of type tabs (only types the plugin supports, its `defaultSearchType` first; scrolls sideways when they do not fit) with the source (plugin name with ▾) at its end, the same source and menu as 音乐. Paged by scrolling.
- Tapping a song plays it now and inserts it after the current song; the queue is kept.

### Song rows

- Cover 48dp, title, artist · album, trailing 更多. The playing row shows the equalizer.
- 更多 sheet: 下一首播放, 加入歌单, 下载, 查看歌手, 查看专辑, 来源 (the plugin's name).
- Songs whose plugin is missing or disabled are dimmed; tapping explains that the source is not installed and offers 插件.
- Long press starts multi-select: 播放, 下一首播放, 加入歌单, 下载, and in the user's own sheets 移除.

### Detail pages

- 歌单, 榜单 and 专辑 reuse the album page: collapsing header with blurred cover, title, creator and count; 播放全部 as the main action; 收藏 (plugin sheets) at the end of the top bar.
- 歌手: avatar header, tabs 单曲 · 专辑.
- Tapping a song plays the list from that song. The queue is the list as loaded and continues into further pages, as 听书 queues do.

### Player (music)

Same player shell as 听书; only the content differs.

- Pages: 播放 · 歌词 · 评论. 评论 is shown only when the plugin supports comments.
- 播放: cover, title, artist (opens the artist), and the current lyric line under them; tapping it opens 歌词.
- Action row: 喜欢 · 加入歌单 · 下载 · 更多. 喜欢 adds to or removes from 我喜欢.
- Progress bar as in 听书, with the playing quality as a small tag that opens the quality sheet.
- Transport: 播放模式 · 上一首 · 播放/暂停 · 下一首 · 播放列表. No 15 s seek buttons and no 倍速 row.
- 更多: 查看歌手, 查看专辑, 音质, 定时关闭, 倍速, 来源.
- 歌词: lines centred, the current one larger and in the accent colour, auto-scrolling. Dragging shows a time line with a play button to jump there; auto-scroll returns after a few seconds. A 译 toggle when there is a translation. No lyrics → 暂无歌词 with 搜索歌词.
- Queue sheet: play mode and count in the header, 清空; drag handles, remove per row, locate the current song.
- System controls: previous, play / pause, next. Audio is declared as music, so short sounds from other apps duck it instead of pausing.

### 听过

One page for everything the user has listened to or kept, styled after QQ 音乐's 我的: a short overview that scrolls, with each collection one tap away. It replaces 听书's seven swipe tabs; their content and rules (`DESIGN.md` · 听过) are kept, each as its own page.

Top bar: title 听过, search (covers the whole library: episodes, albums, songs, sheets), and 更多 → 管理歌单.

From top to bottom:

1. 继续听 — a horizontal row of the latest 10 items, both kinds mixed, newest first. Card: cover (square, 12dp corners), title on two lines; episodes show a thin progress bar on the cover, the playing item shows the equalizer. Tap resumes: an episode from its saved position, a song plays and the queue becomes the list it was played from when that list is still known, otherwise just the song. The row's title opens 最近播放. Hidden when there is nothing yet.
2. Shortcut grid — 4 columns of icon tiles, each with a label and count (or a dot for news):
   - 我喜欢 · 听更新 · 订阅 · 书签
   - 收藏专辑 · 关注 · 云历史 · 下载 (opens the 下载 tab)
   - Tiles that need a Ximalaya account (听更新, 订阅, 收藏专辑, 关注, 云历史) are left out when no account is signed in; the grid closes up, never shows empty slots.
3. 歌单 — headings 自建 n · 收藏 n (switch in place, remembered), trailing icons 新建 and 导入. Rows: cover from the first song (or the sheet's own), name, song count. Empty 自建 → one line and 新建歌单. 我喜欢 is not repeated here.

Pages opened from the overview:

- 最近播放: filter chips 全部 · 听书 · 音乐; rows of both kinds (episode rows show where playback stopped, song rows the artist). Long press selects; remove with an undo snackbar. This replaces 历史 and adds songs.
- 我喜欢: the sheet page (header with cover mosaic, 播放全部), newest first.
- 听更新, 订阅, 收藏专辑, 书签, 关注, 云历史: the existing section lists, unchanged, each on its own page.
- 歌单 rows open the sheet page; 收藏 rows open the live plugin sheet.
- 新建: a dialog with one name field. 导入: a sheet with a link field and the plugins that can import (chips), showing the chosen plugin's hints; the result opens as a new 自建 sheet.
- 管理歌单: drag to reorder, multi-select delete, in place on the overview.

Why this layout: the queue holds one kind of content at a time, so 继续听 is the way back to whatever was replaced; the overview fits both kinds without a second row of tabs; collections the user opens rarely stay one tap away without taking space.

### 下载

- 下载中 holds both kinds of content in one list.
- 已下载 gets filter chips 听书 · 音乐 when both exist. 音乐 lists songs, newest first, with 播放全部 and delete with the size shown first.
- Downloading a song uses the download quality setting, falling back as in playback.

### 我的 and 插件

- 我的: the 听书 account summary, then 插件 (with the count), then the settings groups; music settings (default play and download quality, quality fallback) join them.
- 插件 page: one card per plugin with name, version, author and an enable switch; tapping opens its menu: 设置 (user variables), 替代插件, 更新, 说明, 复制链接, 卸载. Broken plugins show the reason in place of the switch.
- Top bar: 安装 (from a file or a link), 全部更新, and 更多 → 选择, 排序, 订阅. Sorting is a mode with drag handles.
- Selection: a long press on a plugin, or 更多 → 选择, starts it with check boxes in place of the switches; the bar shows the count and 全选. A floating toolbar acts on the selected plugins at once: 启用, 停用, 更新 (those with a link, in the batch sheet) and 卸载 (after a confirmation). Back or ✕ ends it.
- No sample plugin, list or website is shown anywhere.

### Motion

- As in 听书: shared covers between lists, detail pages and the player; predictive back; skeletons instead of spinners.
- Lyric scrolling uses `MaterialTheme.motionScheme` and stops when animations are removed.

## 6. Phases

听书's remaining work (its phases 6 and 7 and unchecked items in `DESIGN.md`) is paused and resumes after M6.

- M1 Plugin runtime: QuickJS host, bundled packages, http bridge, plugin management page, subscriptions; the five-tab shell with the 音乐 empty state. Checked against a set of public plugins, which are never added to the app or the docs.
- M2 Browsing: 推荐, 榜单, search, sheet, album and artist pages.
- M3 Playback: media source, quality, play modes, lyrics, music player pages, editable queue, comments.
- M4 Library: 听过 · 音乐, 我喜欢, sheets, starred sheets, import from links.
- M5 Downloads: songs in the download manager, tags and lyrics, offline play.
- M6 Migration: MusicFree backup import.

## 7. Risks

- Engine differences: plugins were run on Hermes in MusicFree. QuickJS gaps (for example `Intl`) may break some plugins; they are filled in M1 where plugins need them.
- Plugin quality varies and plugins change often. Failures stay inside the call and never block the app.
- Copyright: the app stays a neutral player; nothing in it points to a source.
- MusicFree updates its plugin API. Changes are ported only from its source, and the reported version is raised with them.
