# XMPlus-MD3 Test Cases

Status: Draft · 2026-11-17

## How to use

- Each case gives an input or action and the expected result.
- Logic cases are run as temporary unit tests once the code exists, then deleted (see `AGENTS.md`). UI cases are checked by hand on a device or emulator.
- Sections marked with an XMPlus source must behave exactly like that source. If the source and this document disagree, the source wins and this document is corrected.
- Cases for phases 4, 6 and 7 are added when those phases start.

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
- STD-13 Predictive back previews the destination and can be cancelled midway with no state change.
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
- P0-12 Default theme uses seed color #245C56 (松绿); dark mode follows the system.
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

### XMPlus backup import

Source: XMPlus `lib/storage/config_archive.dart`.

- BK-01 Importing an XMPlus backup (`meta.json` type `data_archive`, format major version 1) shows a preview before anything changes.
- BK-02 A backup that includes logins restores accounts that work without SMS login.
- BK-03 Over 64 MiB, empty, not a ZIP, wrong type or wrong major version → rejected as an invalid backup; existing data unchanged.
- BK-04 Cancelling at the preview changes nothing.
- BK-05 After restoring, nothing plays or downloads by itself; unfinished tasks stay paused.
- BK-06 If the backup's download folder is unavailable, the user is asked to choose a new one.

## Phase 2 — Browsing

- BR-01 Search with an empty keyword or over 200 characters is not sent and shows a message.
- BR-02 Search results switch between 全部, 专辑, 声音 and 主播, each with its own paging.
- BR-03 Hot words turned off in settings → no hot words on 发现 or in search.

## Phase 3 — Playback rules

Source: XMPlus `lib/services/playback_policy.dart`.

Settings

- PB-01 Album settings override global ones; global overrides defaults. Defaults: speed 1.0, intro 0, outro 0, back 15 s, forward 30 s, auto-next on, sequential order, listen-while-downloading off, volume 100, master volume 100, not muted.
- PB-02 Ranges: speed 0.5–3.0; intro and outro 0–3600 whole seconds; back and forward 1–300 whole seconds; volume and master volume 0–100. Invalid input is rejected and the old value kept.
- PB-03 Output volume is 0 when muted, otherwise album volume × master volume / 100.

Intro and outro

- PB-10 Starting at 0 with a 30 s intro → playback starts at 30 s.
- PB-11 Resuming at 10 s with a 30 s intro → starts at 30 s; resuming at 45 s → starts at 45 s.
- PB-12 Intro + outro ≥ episode length → nothing is skipped in that episode and a notice is shown.
- PB-13 Reaching length − outro completes the episode exactly once, even if the real end follows.
- PB-14 After a manual seek or replay, intro and outro skipping stop for the rest of that episode.
- PB-15 Raising the intro setting while playing before the new intro point (with no manual seek) jumps to it.

Cloud resume

- PB-20 Cloud progress beyond 95% of the playable length (length − outro when skipping applies) → start from the beginning, then intro skipping applies. Local progress is not affected by this rule.
- PB-21 A manual replay ignores the 95% rule.

Shuffle

- PB-30 No episode repeats until every other episode has played; then it reshuffles, excluding the current one.
- PB-31 Previous returns to the episode actually played before; next then moves forward through that history before picking new ones.
- PB-32 Changing the queue resets shuffle history; a single-item queue keeps playing that item.
- PB-33 Shuffle history keeps at most 5,000 entries.

Sleep timer

- PB-40 Duration must be 1–1440 minutes. Defaults: 30 minutes, pause.
- PB-41 After the duration, the chosen action (pause or exit) happens.
- PB-42 With "wait for the episode to end" on, playback continues past the deadline and the action happens when the episode ends.
- PB-43 End-of-episode mode acts when the current episode ends.
- PB-44 Timer and episode end arriving together → the action happens once and the next episode does not start.
- PB-45 Cancelling clears the timer. The preset (minutes, action, mode) is saved, but a running timer is not restored after a restart.

## Phase 5 — Downloads

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
