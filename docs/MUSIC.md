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
| JS engine | Embedded QuickJS. Not WebView: it cannot set Referer, User-Agent or Cookie and is bound by CORS, which breaks most plugins |
| Version reported to plugins | MusicFree's version (`0.6.2`), not MD3's, because plugins declare `appVersion` ranges against MusicFree. Raised only after checking the newer MusicFree source |
| Copyright | No built-in plugin, no default subscription, no link to any plugin source, in the app or in the docs. Without plugins the app plays no music |
| 听时 | Music never touches UsageLedger or Ximalaya accounts |
| Navigation | 听书 · 音乐 · 听过 · 下载 · 我的 (发现 is renamed 听书) |
| License | MusicFree is AGPL; ported code stays AGPL-3.0-or-later, credited in 关于 |

## 1. Feature checklist

### 插件 Plugins

- [ ] Runtime: the injected environment and bundled packages exactly as MusicFree (see 2)
- [ ] Install from a local file or a link; confirm with name, version and author first
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

- [ ] Media source in MusicFree's order: local file → cache → alternative plugin → plugin, one retry
- [ ] Quality: 低品质 / 标准 / 高品质 / 超高品质, default and fallback direction as in MusicFree
- [ ] Play modes: 列表循环 / 单曲循环 / 随机
- [ ] Lyrics: synced, translation, tap a line to seek, lyric search when a song has none
- [ ] Comments when the plugin supports them
- [ ] Editable queue: reorder, remove, clear, play next
- [ ] Shared with 听书: mini player, sleep timer, notification, lock screen, Bluetooth, last session

### 听过 · 音乐 Library

- [ ] 最近播放 (this device)
- [ ] 我喜欢, a built-in sheet
- [ ] 我的歌单: create, rename, delete, reorder songs, move and copy between sheets
- [ ] 收藏歌单
- [ ] Import a sheet or a song from a link through a plugin (`importMusicSheet`, `importMusicItem`)
- [ ] Search across the music library

### 下载 Downloads

- [ ] Songs in the shared download manager, with quality choice
- [ ] Tags, cover and lyrics written to the file (shared FFmpeg pipeline)
- [ ] Downloaded songs play offline

### 迁移 Migration (last)

- [ ] Import a MusicFree backup: sheets, and plugins reinstalled from their links after confirmation

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

### Safety

- Only the environment above is exposed: no file, storage or device access.
- Each plugin runs off the main thread with a memory limit and an interrupt for runaway code. A failing or hung call fails that request only; the UI shows a short error with 重试.
- Installing shows the plugin's name, version and author, and a one-line warning to install only plugins from sources the user trusts.

## 3. Modules

```
core/plugin      QuickJS host, bundled packages, http bridge, PluginManager, plugin meta
core/music       music items, media-source and lyric resolution, media cache, sheets, history
core/media       one player for both kinds of content (extended)
core/download    one download manager for both (extended)
```

- The bundled JS packages are built into one asset by a script kept in the repository; the asset is versioned with the app.
- `core/plugin` returns error codes, never user-facing text, as `core/protocol` does.

## 4. UI

One pattern everywhere: a header row of mode headings (as 听书's 推荐 / 分类 / 排行) and scrollable tabs under it.

### Shell

- Bottom bar: 听书 · 音乐 · 听过 · 下载 · 我的. Rail at 600dp and up with the same five.
- Back on any other tab goes to 听书; back on 听书 leaves the app.
- One mini player for both kinds of content. Swiping changes episode or song.
- One queue at a time: playing a song replaces a 听书 queue and the reverse. Progress of the 听书 episode is saved as usual, and the 听书 last session comes back when 听书 is played again.

### 音乐

- Header: 推荐 · 榜单, search icon at the end. Under it, the enabled plugins that support the current mode as scrollable tabs, in plugin order. A plugin without that ability is not shown.
- 推荐: tag chips (pinned tags first, 全部 opens a sheet of grouped tags), then a 2- or 3-column grid of sheet covers with play counts where given.
- 榜单: groups with titles; each ranking a row with cover, name and its first three songs.
- Without plugins: centred message 还没有音乐来源 and one 导入插件 button, which opens 插件. No other content.
- Without plugins supporting the mode: 当前插件不支持 and a link to 插件.

### Search (音乐)

- Search field with history and clear. Results: plugin tabs on top, type chips under them (only types the plugin supports, its `defaultSearchType` first). Paged by scrolling.
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

- Header: 听书 · 音乐, remembered. 听书 keeps its seven tabs.
- 音乐 tabs: 最近 · 我喜欢 · 歌单 · 收藏.
  - 最近: this device's songs, newest first, with select and remove like 历史.
  - 我喜欢: songs, newest first, with 播放全部.
  - 歌单: the user's sheets as rows with cover (from the first song), name and count; 新建歌单 and 导入歌单 in the top bar. 导入歌单 asks for a link and a plugin that can import, showing the plugin's hints.
  - 收藏: starred plugin sheets; they open the live sheet.
- Search in the top bar covers the current kind of content.

### 下载

- 下载中 holds both kinds of content in one list.
- 已下载 gets filter chips 听书 · 音乐 when both exist. 音乐 lists songs, newest first, with 播放全部 and delete with the size shown first.
- Downloading a song uses the download quality setting, falling back as in playback.

### 我的 and 插件

- 我的: the 听书 account summary, then 插件 (with the count), then the settings groups; music settings (default play and download quality, quality fallback) join them.
- 插件 page: one card per plugin with name, version, author and an enable switch; tapping opens its menu: 设置 (user variables), 替代插件, 更新, 说明, 复制链接, 卸载. Broken plugins show the reason in place of the switch.
- Top bar: 安装 (from a file or a link), 全部更新, and 更多 → 排序, 订阅. Sorting is a mode with drag handles.
- No sample plugin, list or website is shown anywhere.

### Motion

- As in 听书: shared covers between lists, detail pages and the player; predictive back; skeletons instead of spinners.
- Lyric scrolling uses `MaterialTheme.motionScheme` and stops when animations are removed.

## 5. Phases

听书's remaining work (its phases 6 and 7 and unchecked items in `DESIGN.md`) is paused and resumes after M6.

- M1 Plugin runtime: QuickJS host, bundled packages, http bridge, plugin management page, subscriptions; the five-tab shell with the 音乐 empty state. Checked against a set of public plugins, which are never added to the app or the docs.
- M2 Browsing: 推荐, 榜单, search, sheet, album and artist pages.
- M3 Playback: media source, quality, play modes, lyrics, music player pages, editable queue, comments.
- M4 Library: 听过 · 音乐, 我喜欢, sheets, starred sheets, import from links.
- M5 Downloads: songs in the download manager, tags and lyrics, offline play.
- M6 Migration: MusicFree backup import.

## 6. Risks

- Engine differences: plugins were run on Hermes in MusicFree. QuickJS gaps (for example `Intl`) may break some plugins; they are filled in M1 where plugins need them.
- Plugin quality varies and plugins change often. Failures stay inside the call and never block the app.
- Plugins run arbitrary code with network access. The environment stays limited to what MusicFree exposes.
- Copyright: the app stays a neutral player; nothing in it points to a source.
- MusicFree updates its plugin API. Changes are ported only from its source, and the reported version is raised with them.
