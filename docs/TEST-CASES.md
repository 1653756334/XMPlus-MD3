# XMPlus-MD3 Test Cases

Status: Draft · 2026-11-17

## How to use

- Each case gives an input or action and the expected result.
- Logic cases are run as temporary unit tests once the code exists, then deleted (see `AGENTS.md`). UI cases are checked by hand on a device or emulator.
- Sections marked with an XMPlus source must behave exactly like that source. If the source and this document disagree, the source wins and this document is corrected.
- Cases for phases 6 and 7 are added when those phases start.

## Shared standards (every phase)

### Language

- STD-01 System language Chinese → all UI text is Simplified Chinese.
- STD-02 System language English or any other non-Chinese language → all UI text is English.
- STD-03 In-app language changed (跟随系统 / 简体中文 / English) → UI switches immediately, the current screen and inputs are kept, and the choice survives a restart. On Android 13+ the choice also appears in the system's per-app language settings.
- STD-04 Every string exists in both `values/` and `values-zh/`; the build fails if either is missing.
- STD-05 Kotlin source contains no UI text: the build fails on a Chinese string literal or a literal passed to `Text` or `contentDescription`.
- STD-06 Counts: 12,345 → 1.2万 / 12K; 123,456,789 → 1.2亿 / 123M.
- STD-07 Relative time: under a minute → 刚刚 / just now; then minutes, hours and days ago in the current language.
- STD-08 English plurals: 1 episode, 2 episodes.

### Look and motion

- STD-10 Light, dark and OLED black (dark only, pure black background) are all readable; body text meets WCAG AA contrast.
- STD-11 System font size at 200%: no clipped or overlapping text on any screen.
- STD-12 System "remove animations" on → transitions complete instantly, nothing gets stuck mid-animation.
- STD-13 Predictive back previews the destination and can be cancelled midway with no state change. On every page the page shrinks into a rounded card and is pulled in from the edge the swipe began at (left or right), with the previous page visible behind; the back button plays the same motion.
- STD-14 Edge-to-edge in portrait and landscape: nothing tappable sits under the status bar, navigation bar or display cutout.
- STD-15 Touch targets are at least 48dp; every icon-only button has a TalkBack label in the current language.
- STD-16 Rotation, window resize and dark mode changes keep the current tab, scroll position and inputs.
- STD-17 UI text contains no technical terms (URL, cache, HTTP codes, tokens, exception messages).

## Phase 0 — Skeleton

- P0-01 `gradlew assembleDebug` succeeds on a clean checkout with JDK 17+ and Android SDK 36.
- P0-02 `gradlew lint` passes with zero errors, including the translation checks (STD-04, STD-05).
- P0-03 CI runs build and lint on pushes and pull requests to `main` and `dev`, and fails when either fails.
- P0-04 The app installs as `com.xuleo.xmplus.md3` and runs alongside XMPlus (`teikengstudio.xmplus`) on one device.
- P0-05 The launcher icon renders correctly in circle and squircle masks; on Android 13+ the themed (monochrome) icon works.
- P0-06 Cold start shows the splash icon, then 发现, with no white flash in dark mode.
- P0-07 Phone portrait (under 600dp): bottom bar with 发现 · 听过 · 下载 · 我的, icons and labels, selected tab highlighted.
- P0-08 600dp and wider (tablet, landscape, unfolded): navigation rail instead of the bottom bar; changing between them keeps the selected tab.
- P0-09 Switching tabs fades through; each tab keeps its own state and scroll position.
- P0-10 Back on 听过, 下载 or 我的 goes to 发现; back on 发现 leaves the app.
- P0-11 The idle mini player floats above the bottom bar (or at the bottom of the content beside the rail) and never covers the last list item.
- P0-12 Default theme uses neutral white surfaces and readable green emphasis (#007F57); dark mode follows the system.
- P0-13 Language switch in 我的 behaves as STD-03.
- P0-14 After process death (Don't keep activities), the selected tab is restored.

## Phase 1 — Protocol and accounts

### Protocol parity

Source: XMPlus `lib/api/xmly_crypto.dart`, `lib/api/xmly_client.dart`. Expected values are produced by running the XMPlus Dart functions on the same inputs with a temporary script.

- PR-01 AES encrypt/decrypt with each XMPlus key matches Dart byte for byte: empty map, ASCII text, Chinese text, payload just under the size limit.
- PR-02 Payload over the size limit → `invalid_encoding`; malformed ciphertext → decode error, no crash.
- PR-03 Paid audio link rebuild: same inputs → identical URL as Dart; malformed inputs → `invalid_paid_media`.
- PR-04 Activity signature: the same parameters in any order → the same signature as Dart.
- PR-05 Fresh ticket: the same credentials and attributes → the same ticket as Dart.
- PR-06 Mobile requests send version `3.4.10`, channel `and-f5`, impl `com.ximalaya.ting.lite` and User-Agent `ting_3.4.10.3(Android,30)`; the Cookie is sent only on authenticated requests.
- PR-07 Every error code XMPlus uses has a translated message in both languages; an unknown code shows a generic message.

### 听时 ledger

Source: XMPlus `lib/services/usage_ledger.dart`.

Metering

- LG-01 No metering for tracks that don't need 听时, the anonymous account, or prepaid (downloaded) tracks.
- LG-02 Only playing time counts — not buffering or paused time. 60 s at 1.0x → 60 s; 60 s at 2.0x → 120 s.
- LG-03 Seeking forward produces no billable time.
- LG-04 Speed outside 0.5–3.0 → `invalid_speed`; nothing recorded.
- LG-05 Usage is saved to disk as it accrues; it is settled when playback pauses or stops, and every 120 s while playing.
- LG-06 Only whole seconds are reported; the fraction stays as debt for the next settlement.
- LG-07 Starting a metered track with balance − debt ≤ 0 → `insufficient_quota`, playback does not start. Exception: a new device with balance 0 may play.
- LG-08 Balance above 20 h (72,000 s, the free-day state): no debit request is sent; the record is confirmed with the balance unchanged.

Journal

- LG-10 An entry is saved as `submitting` before the debit request is sent.
- LG-11 Server confirms → `confirmed`, balance after = server balance, debt reduced by the reported seconds (never below 0).
- LG-12 Definitive rejection → `rejected`; no balance change assumed.
- LG-13 Failure after sending → the balance is re-read from the server; if that also fails → `uncertain` and the account is blocked.
- LG-14 App killed while `submitting` → on next launch the entry becomes `uncertain` and is never sent again automatically.
- LG-15 Blocked account → the next operation first re-reads the server balance, adopts it and marks pending entries confirmed; no request is replayed.
- LG-16 Network errors, rate limiting, invalid balance data or uncertain results defer settlement by 30 s; other errors are shown.
- LG-17 A balance query that takes over 10 s fails with a "sync timed out, try later" message.

Download prepayment

- LG-20 Prepaid seconds = track duration rounded up; a missing or invalid duration → `invalid_duration`.
- LG-21 Without user confirmation → `confirmation_required`; nothing is debited.
- LG-22 The user confirmed N seconds at balance B; if either differs at debit time → `confirmation_changed`; nothing is debited.
- LG-23 Balance below the seconds needed → `insufficient_quota`.
- LG-24 Retrying a task that already has a confirmed receipt reuses it; no second debit.
- LG-25 A receipt whose track doesn't match the task → `task_mismatch`.
- LG-26 A definitive rejection is retried at most 4 times, waiting 1, 2, 4, 8 s (or the server's Retry-After if longer), and only while the balance is unchanged. Never retried for `invalid_duration`, `invalid_ledger`, `login_expired`, `login_required`, `account_mismatch`, `invalid_endpoint`, `insufficient_quota` or `rate_limited`.

Boundaries

- LG-30 Before switching account or exiting, playback is paused and usage settled; exiting never replays uncertain reports.
- LG-31 Corrupted ledger data (negative seconds, wrong types, a receipt for another account) → `invalid_ledger`; the data is kept, not overwritten.

### Accounts and rewards

Source: XMPlus `lib/pages/account_page.dart`, `lib/api/xmly_login.dart`, `lib/api/xmly_rewards.dart`.

- AC-01 Phone number must be 6–18 digits, optionally starting with +.
- AC-02 SMS code must be 4–8 digits; valid for 10 minutes, at most 5 attempts.
- AC-03 After sending, resend is locked for 180 s with a countdown.
- AC-04 Geetest must pass before the SMS is sent; closing or failing it allows a retry or switching to import.
- AC-05 Pasting a multi-line Cookie or request headers extracts the token and device ID.
- AC-06 Adding an account with 设为主账号 unchecked keeps the current primary account.
- AC-07 Removing the primary account makes a remaining usable account primary; usage records are kept.
- AC-08 Auto-claim: balance under 2 h (7,200 s) → claim until at least 4 h (14,400 s) or 64 claims; at 2 h or more → no claim.
- AC-09 A claim with an unknown result pauses auto-claim for that account until the user confirms they checked the official app.
- AC-10 Logins are stored encrypted with the Android Keystore and are not included in Android's system backup.
- AC-11 Import is verified with a listening-time query before saving. Failed verification leaves the previous accounts, balance and primary selection unchanged; no claim or debit is sent.
- AC-12 Reimporting the same account/device updates the login without duplicating the account. Updating from an account's menu requires that original identity and preserves the primary selection, including after process recreation.
- AC-13 Read failure, corrupted ciphertext or missing encryption key preserves the original file and blocks account mutations; Retry never resets the saved data. A write failure is not published as success.
- AC-14 Import text is masked and absent from restored UI state. Returning cancels verification and clears the text; rotation preserves it in memory, while process death requires pasting again.
- AC-15 Refresh failure retains the last successful balance and update time. An expired login is identified and can be updated. A missing result is never displayed as zero.
- AC-16 Account screens in both languages, light/dark mode and at 200% font remain scrollable; the checkbox exposes its state, errors are announced, and menus have account-specific labels.

### XMPlus backup import

Source: XMPlus `lib/storage/config_archive.dart`.

- BK-01 Importing an XMPlus backup (`meta.json` type `data_archive`, format major version 1) shows a preview before anything changes.
- BK-02 A backup that includes logins restores accounts that work without SMS login.
- BK-03 Over 64 MiB, empty, not a ZIP, wrong type or wrong major version → rejected as an invalid backup; existing data unchanged.
- BK-04 Cancelling at the preview changes nothing.
- BK-05 After restoring, nothing plays or downloads by itself; unfinished tasks stay paused.
- BK-06 If the backup's download folder is unavailable, the user is asked to choose a new one.

## Phase 2 — Browsing

### Search

- BR-01 Search with an empty keyword or over 200 characters is not sent and shows a message.
- BR-02 Search results switch between 全部, 专辑, 声音 and 主播, each with its own paging.
- BR-03 Hot words turned off in settings → no hot words on 发现 or in search.
- BR-04 The same album, track or anchor never appears twice on a page; the last page shows there is nothing more.
- BR-05 Search history keeps the last 10 keywords on this device, newest first; searching a kept keyword again moves it to the front. An empty or over-long keyword is neither searched nor kept.
- BR-06 History shows one line until expanded; a long press removes one keyword, and clearing all asks first. Popular searches are a ranked list that scrolls with the history.

### 发现, categories and rankings

- BR-10 发现 shows the server's channel tabs; switching tabs keeps each tab's scroll position; a channel with sub-tabs shows them and switching works.
- BR-11 A section row shows cover, title, anchor and, when the server gives them, play count and score; a section that has a category opens 全部专辑 for it.
- BR-12 Ranking preview highlights the top 3 numbers; 完整榜单 opens the full rankings.
- BR-13 全部专辑: category chips and 综合 / 最近更新 / 最多播放; changing either returns to the first page; 加载更多 appends without duplicates; the end of the list says so.
- BR-14 Categories: group → channel → albums, 60 per page, with sort. Choosing a filter that has sub-filters shows its child rows; clearing or changing the parent removes the children's selection.
- BR-15 Rankings: each category lists its own tabs; an albums chart shows numbered albums, an anchors chart shows numbered anchors.
- BR-16 Signed in or not, 发现 loads; switching the primary account reloads it.

### Loading and errors (all browsing screens)

- BR-20 While loading, a list shows placeholder rows, not a lone spinner; content already on screen stays while a refresh runs.
- BR-21 Network down or server error → a short message with a retry button; retrying works and nothing half-filled is shown.
- BR-22 Rate limited (429) → the message asks to wait; nothing retries on its own faster than the server allows.
- BR-23 Pull down refreshes the current list.
- BR-24 Rotation and window resize keep the tab, scroll position and paging.
- BR-25 A response with the wrong shape (page number not the one asked for, more rows than the page size, a duplicated id in a ranking, a missing list) is rejected whole and shows the generic error.

### 专辑 and 主播

- BR-30 Album header shows cover, title, anchor, stats, 连载 / 完结 and paid labels; the intro shows the full text.
- BR-31 Episodes: sort order reverses the whole album, from the first episode or the last; the list loads the next page as it is scrolled down until the last episode; search inside the album; 定位 waits for playback.
- BR-32 Similar albums are listed and open their own pages.
- BR-33 Reviews: score, sort, replies and copy; an album with no reviews says so.
- BR-34 Anchor page: profile, verification badge, albums and episodes tabs in server order, loading the next page as the list is scrolled; episodes stop at the first 5,000. The albums search shows only that anchor's albums, never another anchor's, and a blank search shows the whole list again.
- BR-35 Controls that need download or multi-select stay hidden until those features exist. 订阅 and 关注 show only for a signed-in account.

## Phase 3 — Playback rules

Source: XMPlus `lib/services/playback_policy.dart`.

Playback integration

- PB-00a Pick a free, previously authorized, and listening-time episode through the same album list. Only the last one meters; catalog paid labels do not determine the result.
- PB-00b The mini player, full player, and system controls all act on the same episode. Returning to Discover or locking the screen keeps active audio playing.
- PB-00c Rapid episode selection or a primary-account change during authorization cannot start a stale source. When switching accounts, close the old watch before replacing the credentials; retain any unsettled usage under the old account.
- PB-00d Unavailable or preview-only audio never starts. An expired stream is reauthorized for the same account and track and resumes at the current position.
- PB-00e During a settlement retry cooldown, pausing or stopping still saves every final local listening sample. Retrying an unknown debit must not resend it.

Album playback queue

- PB-04 Select an episode from a descending album or its search results. The queue starts at that exact episode and follows ascending official episode numbers.
- PB-05 From the first or last episode of a page, Previous or Next reads the adjacent page without duplicating rows. Next is disabled on the album's last episode. Natural completion still re-reads the last page, so an episode appended since the queue was built plays on.
- PB-06 Reject a moved catalogue anchor or a repeated episode ID. A failed page read leaves the current episode and queue intact; selecting a different episode or changing the primary account cancels a pending read. After a failed read, tapping the loaded episode rebuilds the queue without restarting playback.
- PB-07 Top to bottom the player reads: 播放 / 文稿 / 评论 at the top; the cover; the title and the album name (tapping it opens the album); 点赞 · 收藏 · 书签 · 下载 · 更多 (更多 opens the 更多 sheet); the progress bar with the time pill; seek back · previous · play/pause · next · seek forward; 播放列表 · 倍速 · 定时关闭. Open the play queue, jump to the current row, select a different episode, and dismiss the sheet without stopping playback. The 15-second seek buttons move within the current episode; the previous and next episode buttons change episodes, and Next is disabled on the last episode. Dragging the pill or tapping the bar seeks, and the pill shows the drag position before release. 文稿 is a page of the cover area that swipes with it. 点赞 and 收藏 are connected for a signed-in account; 评论 shows the track's comments; 下载 says 即将推出 and changes nothing.
- PB-08 Let a metered episode end at the page boundary; the next episode opens through normal authorization, and a free next episode does not start a meter. Pause while the page or settlement is pending; the next episode must not start.
- PB-09 Pause mid-episode, then swipe the app away from recents (also: force-stop, let the system kill it, reboot) and reopen. The mini player shows the same episode paused at the same position, with the same queue, speed, volume and intro/outro skip. Nothing plays, resolves or uses 听时 until play, and play continues from that spot. Before play the seek buttons, the seek bar, 回到开头 and bookmarks work as they do while playing; a position set before play is where play starts. A finished episode comes back finished and play moves to the next. Changing the primary account clears it.

Settings

Speed and volume are per album, and mute covers every album, all in Phase 3. There are no global defaults and no master volume slider: the phone's volume keys are the master volume.

- PB-01 Speed and volume are remembered per album. Each album keeps its own: one never adjusted plays at 1× and volume 100, whatever another album is set to, and changing one album never changes another. Mute covers every album until the app is closed. Back and forward are a fixed 15 s with no setting; episodes always play in order and the next one follows automatically.
- PB-02 Ranges: speed 0.5–3.0 in 0.1 steps, and a speed outside it is rejected with the old value kept; volume 0–100 % in 5 % steps; intro and outro 0–3600 whole seconds (the sliders reach 180 s).
- PB-03 Output volume is 0 when muted, otherwise the album's volume.

Intro and outro

Per album only, with no global setting.

- PB-10 Starting at 0 with a 30 s intro → playback starts at 30 s.
- PB-11 Resuming at 10 s with a 30 s intro → starts at 30 s; resuming at 45 s → starts at 45 s.
- PB-12 Intro + outro ≥ episode length → nothing is skipped in that episode and a notice is shown.
- PB-13 Reaching length − outro completes the episode exactly once, even if the real end follows.
- PB-14 A seek into the outro, or a start inside it such as a bookmark, keeps the outro for the rest of that episode. A seek anywhere earlier, or a start earlier such as a retry, a resume after the playback service restarted or a bookmark, leaves the outro skip on. A replay from the beginning skips both again.
- PB-15 Raising the intro setting while playing before the new intro point (with no manual seek) jumps to it. A start the listener chose, such as a bookmark or a retry, is not moved.

Cloud resume

- PB-20 Cloud progress is used only when it is newer than the local progress. Cloud progress at or past the end of the episode → start from the beginning, then intro skipping applies. There is no percentage threshold.
- PB-21 A position the listener chooses (a replay, a bookmark, a seek) is never replaced by cloud progress.

Play order

- PB-30 Episodes play in official episode-number order only; there is no shuffle, list loop or single loop. (PB-31–PB-33, shuffle history, were dropped with shuffle.)

Sleep timer

Phase 3 (player sheet): 15, 30, 60 or 90 minutes, or the end of this episode, then pause; the button shows the time left. Moved to Phase 6 with the settings store: a custom duration, the exit action, wait-for-end and the saved preset (PB-40 custom values, PB-41 exit, PB-42, PB-45 preset).

- PB-40 Duration must be 1–1440 minutes. Defaults: 30 minutes, pause.
- PB-41 After the duration, the chosen action (pause or exit) happens.
- PB-42 With "wait for the episode to end" on, playback continues past the deadline and the action happens when the episode ends.
- PB-43 End-of-episode mode acts when the current episode ends.
- PB-44 Timer and episode end arriving together → the action happens once and the next episode does not start.
- PB-45 Cancelling clears the timer. The preset (minutes, action, mode) is saved, but a running timer is not restored after a restart.

## Phase 4 — Library and interactions

### 听过 (this device's history and the account's lists)

- LH-01 Playing an episode records its album on this device, with the episode last played from it, where playback stopped and when. Pausing, stopping or moving to another episode saves the latest position.
- LH-02 Each album appears once, most recently played first, with its cover, album title, the latest episode, the progress (听到 x / y, or 已听完) and the date. Playing another episode of the album moves the entry to that episode.
- LH-03 Tapping a row plays its episode from the saved position, in the player, without opening the album. An episode that finished starts from the beginning.
- LH-04 选择 in the top bar selects rows: tapping a row selects it instead of playing it, 全选 selects every row, and the delete button removes the selected albums and shows 已从听过中移除 with 撤销. Undo restores them with their episodes, positions and times, unless an album has been played again since; then the newer entry stays. Swiping a row does not remove it, so the tabs still swipe. The back button or 关闭 ends selection.
- LH-05 There is no separate clear action: selecting every row and deleting clears this device's history, with the same undo. Cloud history is not changed.
- LH-06 At most 5,000 albums are kept; the least recently played are dropped first.
- LH-07 With no history the tab shows the empty message.
- LH-08 Interrupted playback (app killed mid-episode) may lose up to about five seconds of position; the last saved position is shown.
- LH-09 The seven sections swipe left and right, and their tabs follow. An account section is read the first time it is shown. Signed out, it says 登录后查看 and asks the server for nothing.
- LH-10 云历史 lists the account's cloud history, one row per episode, newest first, a page at a time as the list is scrolled. An episode played again appears once, at its latest listen. Tapping plays that episode from its saved position, as LH-03 does.
- LH-11 听更新 lists episodes published by followed albums, newest first, with the album name and date. Tapping opens the album.
- LH-12 订阅 lists the subscribed albums with the latest episode and the unplayed count. 取消订阅 removes the row once the server confirms. A refused change shows 操作失败，请重试 and keeps the row.
- LH-13 收藏 lists favourite episodes. 取消收藏 works as in LH-12.
- LH-14 书签 lists this device's bookmarks across all albums, newest first, with the album and episode names, position and note. Bookmarks are the same whichever account is signed in. Bookmarks made before the names were kept get their album, episode and cover from the catalogue the first time 书签 is opened in each launch. Tapping a bookmark plays that episode from its saved position in the player, without opening the album.
- LH-15 关注的主播 lists the anchors the account follows. Tapping opens the anchor page.
- LH-16 A section that fails shows the error with 重试. Scrolling to the end loads the next page once; a failed page is not retried in a loop.

### 文稿 transcript

- TR-01 文稿 shows the episode's official manuscript: the lyric, or else the draft. An episode without one says 这一集没有文稿.
- TR-02 The promotional introduction is never shown as the transcript.
- TR-03 A failed read shows the error with 重试; retry reads the transcript again.
- TR-04 文稿 is the second page of the cover area. Swiping left or right moves between the cover and 文稿, the top tabs follow the swipe, and tapping a tab slides to its page.

### 评论 comments (player)

- CM-01 评论 lists the track's comments, newest first, 20 at a time. Scrolling to the end loads the next page.
- CM-02 Reading needs no account. Posting, liking and deleting need one; without it the player says 登录后评论 and changes nothing.
- CM-03 Blank text is not sent. A posted comment appears once the server confirms it.
- CM-04 A like changes the icon and count only after the server confirms; a second tap takes it back.
- CM-05 Only the account's own comments show 删除, and deleting asks first.
- CM-06 A failed write shows 评论操作失败，请重试 and leaves the list as it was.
- CM-07 Not built yet: replies, sorting, and the replies under a comment.

### 点赞, 收藏, 订阅, 关注

- IN-01 点赞 toggles the like for the episode. It starts off for each episode, as in XMPlus, and does not read the server's state.
- IN-02 收藏 is off until the account's favourites are read, then shows the true state. A tap changes the button only after the server confirms.
- IN-03 订阅 sits beside the album title and appears only for a signed-in account. Its state comes from the subscription list. An "already subscribed" answer counts as done.
- IN-04 关注 sits beside the anchor's name and appears only for a signed-in account, never on the account's own page. Its state comes from the anchor's card.
- IN-05 A failed change shows 操作失败，请重试 and leaves the state as it was.
- IN-06 Each change is sent once; a failure is not retried automatically.
## Phase 5 — Downloads

All download work waits for this phase: until it ships, the 下载 tab and the download buttons are placeholders that change nothing.

Source: XMPlus `lib/api/xmly_catalogue.dart`, `lib/services/download_paths.dart`, `lib/services/download_service.dart`.

Episode ranges

- DL-01 `1-3` → 1, 2, 3; `1,3,5-8` → 1, 3, 5, 6, 7, 8; full-width comma `1，3` and spaces around numbers are accepted.
- DL-02 Results are sorted and de-duplicated: `1-3,2` → 1, 2, 3.
- DL-03 Invalid: empty, `a`, `3-1`, `0`, `1-5001`, `1--2`, or an expression over 40,000 characters → error.
- DL-04 A number missing from the album → error; nothing is queued.
- DL-05 Two different episodes with the same official number in the range → ambiguity error.
- DL-06 An album over 5,000 episodes or a batch over 5,000 → refused with the limit message.

File names

- DL-10 Default pattern `{index} - {title} [{trackId}]`. Index is zero-padded to max(3, digits of the album total): episode 7 of 120 → `007`; of 12,000 → `00007`.
- DL-11 Only `{index}`, `{title}`, `{trackId}`, `{album}` and `{albumId}` are allowed. Unknown variables, stray braces, `/`, `\`, `.` or `..` → rejected.
- DL-12 `<>:"/\|?*` and control characters become `_`; trailing dots and spaces are removed; Windows reserved names (CON, NUL, COM1…) are avoided.
- DL-13 Long titles are shortened so the name fits in 255 UTF-8 bytes; the `[trackId]` suffix is always kept.
- DL-14 `{index}` with an episode that has no official number → error asking to load the whole album or change the pattern.
- DL-15 Per-album folders are on by default.

Settings

- DL-20 Concurrent downloads 1–6 (default 3). Rate-limit retries 0–1000 (default 3; 0 = no automatic retry), waiting at least 5 minutes after a rate limit. Minimum free space 512–1,048,576 MiB (default 512).
- DL-21 Duplicate names: skip (after checking the existing file), overwrite, or add a number.
- DL-22 A downloaded episode plays from the local file without using 听时 (LG-01).

## Phase M1 — Plugin runtime

Source: MusicFree `src/core/pluginManager/plugin.ts`, `index.ts`, `meta.ts`, `src/types/plugin.d.ts`. Design: `docs/MUSIC.md`. Plugins used for checking stay in a local temporary folder and are never committed.

Loading

- PL-01 A plugin exporting `platform` and `version` loads; `module.exports.default` is used when present, otherwise `module.exports`.
- PL-02 A plugin without `platform` → listed as unreadable with the reason, never called.
- PL-03 `appVersion` `>0.4.0` loads; `>1.0.0` → listed as incompatible (reported version is MusicFree 0.6.2).
- PL-04 `require` returns `axios`, `cheerio`, `crypto-js`, `dayjs`, `big-integer`, `qs`, `he`, `webdav`; `@react-native-cookies/cookies` returns the no-op stub; any other name → `undefined`, no crash.
- PL-05 `env.getUserVariables()` returns the saved values; `env.os` = `android`, `env.lang` = `zh-CN` in both UI languages; `process.platform` = `android`.
- PL-06 `userVariables` entries without `key` are dropped.
- PL-07 The plugin's hash equals SHA-256 of its code, as computed by MusicFree for the same file.
- PL-08 Syntax error or exception at load → unreadable with the reason; other plugins keep working.

HTTP

- PL-10 `axios.get` with custom `Referer`, `User-Agent` and `Cookie` → the server receives them unchanged.
- PL-11 No timeout given → the request fails after 2 s.
- PL-12 A single `set-cookie` header is split on commas; the original stays in `x-set-cookie`.
- PL-13 JSON, text and binary (`responseType: arraybuffer`) responses match MusicFree's results for the same request.

Calls and safety

- PL-20 A method the plugin lacks → empty result (`isEnd: true`), no error.
- PL-21 Returned items get the plugin's `platform`; unknown fields survive a round trip to the next call.
- PL-22 A call that throws or never answers → only that request fails, with 重试; the UI stays responsive.
- PL-23 A plugin cannot read files, app storage or other plugins' variables.

Install, update, remove

- PL-30 Install from several files at once or a link → installed without an extra confirmation; the result says how many succeeded and lists failures with their reasons, as MusicFree does.
- PL-31 Same hash as installed → already installed, nothing changes.
- PL-32 Same `platform`, lower version than installed → refused; higher → replaces, keeping order, enabled state, user variables and alternative plugin.
- PL-33 Uninstall the last plugin of a `platform` → its meta is removed.
- PL-34 Subscription `.js` link installs one plugin; `.json` list installs each entry in MusicFree's format; other links → refused.
- PL-35 全部更新 updates each plugin from its `srcUrl`; one failure does not stop the others and is reported.
- PL-36 Disable, reorder and set user variables survive an app restart.

Shell

- PL-40 Bottom bar shows 听书 · 音乐 · 听过 · 下载 · 我的 in both languages; the rail shows the same at 600dp and up.
- PL-41 Back on 音乐, 听过, 下载 or 我的 → 听书; back on 听书 leaves the app.
- PL-42 No plugin installed → 音乐 shows only the empty message and 导入插件, which opens 插件.
- PL-43 No plugin, subscription link or plugin website appears anywhere in the app.
