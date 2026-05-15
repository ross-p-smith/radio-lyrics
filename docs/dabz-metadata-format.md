# DAB-Z MediaSession metadata format

Captured live from `com.zoulou.dab` v2.0.239 on a Mekede DUDU7 (Android 13, `uis7870sc_2h10`)
in May 2026 while the unit was tuned to BBC/UK DAB+ ensembles. This document records the
actual layout DAB-Z publishes through its exported `MediaBrowserService`
(`com.zoulou.dab.service.DabMediaBrowserService`), which is what
[`DabzMediaBrowserClient`](../app/src/main/kotlin/com/example/radiolyric/bridge/DabzMediaBrowserClient.kt)
consumes.

## Field layout

DAB-Z does **not** populate `MediaMetadataCompat` the way a typical music app does. The keys
mean different things from their names:

| `MediaMetadataCompat` key       | Actual content in this DAB-Z release                                                                                                  |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `METADATA_KEY_ARTIST`           | **Station name** (e.g. `Heart UK`) — NOT the song artist                                                                              |
| `METADATA_KEY_ALBUM`            | **DAB ensemble** (e.g. `D1 National`) — NOT a song album                                                                              |
| `METADATA_KEY_TITLE`            | Variable; see [Title formats](#title-formats) below                                                                                   |
| `METADATA_KEY_DISPLAY_SUBTITLE` | Not populated                                                                                                                         |
| `METADATA_KEY_RATING`           | A `Rating` Parcelable; calling `getString()` on it throws `Bundle: Attempt to cast generated internal exception` (use `bundle.get()`) |
| `PlaybackStateCompat.activeQueueItemId` | Always `-1` (`PLAYBACK_POSITION_UNKNOWN`) → station-change detection via queue-item discontinuity will never fire             |

## Title formats

`METADATA_KEY_TITLE` carries the now-playing payload, but in three different shapes that the
mapper has to disambiguate:

| Sample TITLE                                                                | Meaning                              | Mapper outcome |
| --------------------------------------------------------------------------- | ------------------------------------ | -------------- |
| `On Air Now on Heart UK: Noah Kahan with Stick Season`                      | Song (current DAB-Z 2.0.239 form)    | `DLPLUS`       |
| `Now on Heart UK: Rudimental feat. James Arthur with Sun Comes Up`          | Song (older prefix variant, still seen) | `DLPLUS`    |
| `On Air Now on Heart UK: Alex Warren and Meghan Trainor with Ordinary`      | Song with multi-artist credit; `and` keeps the artists together because the regex splits on the first ` with ` | `DLPLUS` |
| `On Air Now on Heart UK: Ben Atkinson`                                      | Show / DJ name between songs         | `EMPTY`        |
| `Heart UK - turn up the feel good!`                                         | Station slogan or jingle             | `EMPTY`        |

## Mapper rules

Implemented in
[`DabzMetadataMapper.kt`](../app/src/main/kotlin/com/example/radiolyric/bridge/DabzMetadataMapper.kt)
and covered by
[`DabzMetadataMapperTest.kt`](../app/src/test/kotlin/com/example/radiolyric/bridge/DabzMetadataMapperTest.kt):

1. If TITLE matches `^(?:On Air )?Now on [^:]+:\s*(.+?)\s+with\s+(.+)$`, emit
   `NowPlaying(artist, title, source = DLPLUS)`. The non-greedy artist group means the **first**
   ` with ` wins, which matches the empirical DAB-Z output where multi-artist credits use ` and `
   between performers and ` with ` only as the artist/title separator.
2. If TITLE has the `(?:On Air )?Now on …:` prefix but no ` with ` payload, emit `EMPTY`. This
   suppresses DJ names between songs from polluting downstream LRCLIB queries.
3. If TITLE starts with `<ARTIST> -` (where ARTIST is the station name from `METADATA_KEY_ARTIST`),
   emit `EMPTY`. This suppresses station slogans and jingles.
4. **Never read `METADATA_KEY_ALBUM`.** It contains the DAB ensemble label and would corrupt the
   LRCLIB query if used as the album field.
5. The plain DL+ fast path (use ARTIST and TITLE as-is) only fires when none of rules 1–3 apply,
   which in practice means a future DAB-Z release that switches to a more standard layout, or any
   non-DAB-Z `MediaBrowserService` that someone might point this client at.

## Playback ownership

Audio is owned by uid 10145 (`com.zoulou.dab`). This app is a strictly read-only consumer of
DAB-Z's session: it does not call `requestAudioFocus`, does not co-publish a `MediaSession`, and
emits an empty audio flow in DAB-Z mode (see
[`DabzBridgeRadioSource`](../app/src/main/kotlin/com/example/radiolyric/data/radio/DabzBridgeRadioSource.kt)
and the DAB-Z hard gate in
[`PlaybackService`](../app/src/main/kotlin/com/example/radiolyric/playback/PlaybackService.kt)).

## Re-capturing the raw fields

`DabzMediaBrowserClient.logMetadata()` dumps every key on each `onMetadataChanged` and on the
initial snapshot. To capture the full set on a fresh device:

```bash
make install-dabz
adb shell am force-stop com.example.radiolyric.debug
adb shell am start-foreground-service \
  -n com.example.radiolyric.debug/com.example.radiolyric.playback.PlaybackService
PID=$(adb shell pidof com.example.radiolyric.debug | tr -d '\r\n')
adb logcat -d -v time --pid=$PID | grep DabzMediaBrowserClient
```

If a future DAB-Z release changes the TITLE shape again, add a regex / test fixture rather than
loosening the existing rules — the suppression rules for DJ names and slogans matter for downstream
lyrics quality.
