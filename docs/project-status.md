# Project Status — radio-lyric

Snapshot **2026-05-07**. Authoritative source for what works today, what's
pinned where, and what's blocked. Updated by hand at the end of each
planning sprint; not auto-generated.

## 1. Build & pin state

| Item                      | Value                                                                                                                         |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Parent repo HEAD          | `f0fa5a3` on `main` (== `origin/main`)                                                                                        |
| Submodule URL             | `https://github.com/ross-p-smith/omri-usb.git` (fork)                                                                         |
| Submodule pin             | `899799d` on `radio-lyric` branch                                                                                             |
| `:omri-usb:compileDebug…` | **BUILD SUCCESSFUL** (verified 2026-05-07)                                                                                    |
| `:app:assembleDebug`      | **BUILD SUCCESSFUL** (verified 2026-05-07; only the expected `libc++_shared.so`/`libfec.so`/`libirtdab.so` strip-skip notice) |

The `omri-usb` submodule carries five commits on top of upstream `master`:

1. `2d8d534` — Android 12/13 USB permission fix (PR opened upstream).
2. `86cdcb1` — `.gitignore` for `.cxx` and `.kotlin`.
3. `2470c04` — wRadio C100 power-up fix (drain `setRegister` ack byte).
4. `0e4518e` — JUsbDevice direct-bulk-transfer fast path (`USBDEVFS_BULK` ioctl).
5. `d103ebc` + `899799d` — IRT IP/Shoutcast subsystem removal.

See `docs/dab-driver-bringup.md` for the narrative behind commits 3-5.

## 2. What works end-to-end today

- **Hardware-free dev loop.** `./gradlew :app:installDebug` produces an APK
  bound to `FakeRadioSource` that emits a scripted DLS/DL+ timeline so the
  Compose UI, lyrics fetch, and Media3 plumbing all work without a dongle.
- **Real-dongle bind.** `omri-usb` recognises the yonghx `0x16C0:0x05DC`
  dongle on the DUDU7 (UNISOC UMS9620, Android 13, SDK 33). Permission
  broadcast lands; `openDevice` returns; `RaonUsbTuner` reaches
  `TUNER_STATUS_INITIALIZED` and starts the FIC service scan.
- **Service scan.** `Starting service scan!` traverses the UK band-III plan
  (174.928–194.064 MHz). Phase 2B verification on 2026-05-06 logged 11
  ensemble transitions in 35 s.
- **Foreground-service contract.** `PlaybackService` no longer trips
  `ForegroundServiceDidNotStartInTimeException` (WI-05 in the wRadio C100
  bring-up log was applied inline).
- **Submodule traceability.** Fork URL flipped to `ross-p-smith/omri-usb`,
  pin matches `radio-lyric` branch HEAD, all commits pushed.

## 3. What's blocked on hardware / RF coverage

These are the only items that actively need a human + the head unit to make
forward progress. Each is tracked in detail in its source plan; they are
re-listed here so a returning maintainer can pick the next on-device session
without rummaging.

| ID    | Item                                                            | Source plan                                                                                         |
| ----- | --------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| HW-V1 | Emulator smoke-test of `FakeRadioSource` end-to-end timeline    | `.copilot-tracking/plans/2026-05-01/dab-radio-lyrics-app-plan.instructions.md` Step 10.2            |
| HW-V2 | DUDU7 verify of `POST_NOTIFICATIONS` + `ACTION_POWER_CONNECTED` | `.copilot-tracking/plans/2026-05-05/device-facts-followups-plan.instructions.md` Steps 5.2-5.3      |
| HW-V3 | DUDU7 verify of direct-bulk-transfer + scan-timeout triage      | `.copilot-tracking/plans/2026-05-06/wradio-c100-direct-bulk-transfer-plan.instructions.md` Phase 4  |
| HW-V4 | Live audio render path once a DAB ensemble actually locks       | `.copilot-tracking/plans/logs/2026-05-06/omri-usb-wradio-c100-bringup-log.md` (WI-PHASE2A-DEFERRED) |

HW-V4 needs DAB band-III RF coverage at the test location (BBC National DAB
or a strong local multiplex). Phase 2B's 2026-05-06 verification ran in a
location with `LockStat: 0` on every channel, which is an antenna/RF
question, not a code question.

## 4. Forward backlog (medium priority)

Each WI traces back to a planning log under `.copilot-tracking/plans/logs/`.
None of these block a release; they are the next work to plan and pick up
once the hardware-blocked items above are unblocked.

| WI         | One-liner                                                                                       | Source log                                           |
| ---------- | ----------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| WI-DBT-01  | Settings UI toggle for direct-bulk-transfer (mirror DAB-Z `alternativeUsbComm`)                 | `2026-05-06/wradio-c100-direct-bulk-transfer-log.md` |
| WI-DBT-06  | Restructure `OmriUsbRadioSource.tune()` to await first lock vs. full scan                       | same                                                 |
| WI-DBT-07  | Disable `com.zoulou.dab` USB-attach intent filter on shipped devices                            | same                                                 |
| WI-DEVX-01 | Promote `radio.source` Gradle property to a Settings UI toggle                                  | `2026-05-07/devx-improvements-log.md`                |
| WI-DUD-08  | On-device E2E test exercising `RealRadioSourceProvider.open()` with the real dongle             | `2026-05-05/duduauto-bringup-followups-log.md`       |
| WI-DEVF-01 | Audio-focus contention smoke-test against `com.syu.carradio` + `com.dudu.btmusic`               | `2026-05-05/device-facts-followups-log.md`           |
| WI-DEVF-06 | Capture a second device snapshot after a real ACC cycle (we lost ADB mid-session last time)     | same                                                 |
| WI-FORK-A  | Apply Git tag `radio-lyric/2026-05-06` on the upstream-PR SHA for immutability                  | `2026-05-06/submodule-flip-to-fork-log.md`           |
| WI-FORK-C  | CI workflow that fails when `.gitmodules` URL changes without a matching pin update             | same                                                 |
| WI-FORK-D  | Document the fork-merge protocol in `omri-usb/.copilot-build-notes.md`                          | same                                                 |
| WI-IRT-03  | Audit `RadioServiceManager.java` for further IP/Shoutcast leftover state (parallel-array audit) | `2026-05-07/irt-ip-shoutcast-cleanup-log.md`         |

## 5. Low-priority backlog

Tracked but not summarised here — see the source planning logs:

- `.copilot-tracking/plans/logs/2026-05-05/device-facts-followups-log.md`
  (WI-DEVF-02..05).
- `.copilot-tracking/plans/logs/2026-05-05/duduauto-bringup-followups-log.md`
  (WI-DUD-05..07).
- `.copilot-tracking/plans/logs/2026-05-06/submodule-flip-to-fork-log.md`
  (WI-FORK-B).
- `.copilot-tracking/plans/logs/2026-05-06/wradio-c100-direct-bulk-transfer-log.md`
  (WI-DBT-02..04).
- `.copilot-tracking/plans/logs/2026-05-07/devx-improvements-log.md`
  (WI-DEVX-02..05).
- `.copilot-tracking/plans/logs/2026-05-07/irt-ip-shoutcast-cleanup-log.md`
  (WI-IRT-01..02).

## 6. Adding new outstanding work

When a planning sprint closes:

1. The plan's checkboxes record what shipped.
2. The plan's planning log carries any leftover Suggested Follow-On Work as
   `WI-NN` entries with priority + dependency.
3. When the next audit-and-tidy pass runs, medium-priority WIs are promoted
   into §4 of this file with a one-liner; low-priority WIs are referenced
   from §5 only.

This file is the entry point for "what should I work on next?" — start at
§3 (hardware-blocked), then §4 (medium backlog), then the source logs.
