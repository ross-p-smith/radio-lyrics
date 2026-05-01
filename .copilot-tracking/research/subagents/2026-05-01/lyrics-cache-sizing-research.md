<!-- markdownlint-disable-file -->
# Lyrics Cache Sizing Research

Date: 2026-05-01
Status: Complete
Scope: Sizing and eviction policy for the on-device LRCLIB lyric cache backing the `radio-lyric` Android head-unit app on Heart UK (DAB+ on Digital One). Inputs: actual LRCLIB payloads for a representative Heart-FM rotation sample, the existing `LyricsCacheEntity` schema in .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md, and a 200 MB user-set storage budget.

## Headline answers

- Recommended **per-track storage figure** (plain + synced lyrics + Room/SQLite per-row overhead): **~4.7 KB/track** (plan with **5 KB/track** for headroom).
- **200 MB budget capacity:** ~**42 000 tracks** at the mean, ~**26 000 tracks** at p95 — comfortably **two orders of magnitude larger** than Heart FM's weekly hot set (~300 tracks) and ~14× the annual rotation (~1500–3000 tracks). The 200 MB budget is **massively overkill** for content but appropriate as a "never-think-about-it" upper bound on a vehicle head unit.
- Recommended **eviction policy:** **LFU with periodic decay** (every row carries `playCount`, decayed by ÷2 every 30 days at app start). Simplest single-column ORDER BY in Room, behaves correctly when Heart's playlist refreshes, never re-fetches LRCLIB rows in the steady state.

## Topic 1 — Real LRCLIB payload sizes for Heart FM UK rotation

### Method

For each of 12 representative Heart-UK-rotation tracks, called the public LRCLIB search API:

```bash
curl -s -A 'RadioLyric-Research/0.1 (+https://github.com/example/radio-lyric)' \
  --data-urlencode "track_name=$title" \
  --data-urlencode "artist_name=$artist" \
  -G 'https://lrclib.net/api/search'
```

For each response, picked the first result that has non-empty `syncedLyrics` (falling back to `[0]` if none), then measured UTF-8 byte length of `plainLyrics` and `syncedLyrics` and counted timestamped lines via the regex `\[\d+:\d+`. Source: lrclib.net public API (no auth, no rate limit; <https://lrclib.net/docs>).

### Per-track results

| Artist | Track | Duration (s) | plain bytes | synced bytes | timestamped lines |
|---|---|---:|---:|---:|---:|
| Calvin Harris | One Kiss | 215 | 1438 | 2132 | 64 |
| Coldplay | Yellow | 267 | 958 | 1449 | 45 |
| Doja Cat | Paint The Town Red | 296 | 3361 | 4342 | 91 |
| Dua Lipa | Houdini | 186 | 1636 | 2276 | 59 |
| Ed Sheeran | Shape of You | 234 | 3190 | 4203 | 93 |
| Harry Styles | As It Was | 167 | 1123 | 1652 | 49 |
| Miley Cyrus | Flowers | 201 | 1638 | 2146 | 47 |
| Olivia Rodrigo | vampire | 220 | 1965 | 2585 | 57 |
| Sabrina Carpenter | Espresso | 175 | 1858 | 2499 | 59 |
| Sam Smith | Unholy | 157 | 1834 | 2313 | 44 |
| Taylor Swift | Cruel Summer | 178 | 2082 | 2788 | 65 |
| The Weeknd | Blinding Lights | 200 | 1240 | 1675 | 40 |

### Aggregate (n = 12)

| Stat | plain (bytes) | synced (bytes) | plain + synced (bytes) |
|---|---:|---:|---:|
| min | 958 | 1449 | 2407 |
| mean | 1860 | 2505 | **4365** |
| median | 1736 | 2294 | 4030 |
| p95 | 3190 | 4203 | 7393 |
| max | 3361 | 4342 | 7703 |

Observations:

- Synced lyrics are consistently ~30–35 % larger than plain (the `[mm:ss.xx] ` prefix adds ~10 bytes per line; ~50–90 lines per song).
- Range is tight: max payload (Doja Cat — Paint The Town Red, a rap track with dense lyrics) is **3.2× the min** (Coldplay — Yellow). Heart's ballad/pop weighting keeps the central tendency near 4 KB.
- No track exceeds 8 KB end-to-end; **sizing the cache by row count is safe** when the per-row planning figure is ≥ 5 KB.

**Recommended planning figure for lyric content alone: 4.4 KB (mean) — round to 4.5 KB.** Combined with row overhead from Topic 2, the full **per-track planning figure is ~4.7 KB; use 5 KB to bake in p95 headroom.**

## Topic 2 — SQLite/Room per-row overhead for `LyricsCacheEntity`

### Schema (from .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md, Step 6.1)

```kotlin
@Entity(
  tableName = "lyrics_cache",
  primaryKeys = ["artist", "title"],
  indices = [Index(value = ["artist", "title"], unique = true)]
)
data class LyricsCacheEntity(
  @ColumnInfo(collate = ColumnInfo.NOCASE) val artist: String,
  @ColumnInfo(collate = ColumnInfo.NOCASE) val title: String,
  val syncedLyrics: String?,
  val plainLyrics: String?,
  val provider: String,
  val fetchedAt: Long,
)
```

7 logical columns. Composite primary key on `(artist, title)`; the explicit unique `Index` on the same columns is redundant against the PK (Room emits both, but SQLite will use the implicit auto-index for the PK in a `WITHOUT ROWID`-less table; the second one is genuine extra cost — see "Schema delta" below for the simplification).

### SQLite storage model (cite https://www.sqlite.org/fileformat.html §1.6 "B-tree pages", §2.6 "Cell payload")

Every row in a regular `rowid` table is one cell on a leaf b-tree page:

```text
cell = payload-size varint (1-3 B)
     + rowid varint (1-9 B; ~3 B for typical row counts)
     + record header
       = header-length varint (1-2 B)
       + N serial-type varints, one per column (1-2 B each)
     + record body
       = sum of value sizes per serial type
```

Per the SQLite "Record Format" section (§2.1, "A record is a sequence … prefixed by a header that is itself a sequence of varints …"), each column contributes a 1–2 byte serial-type varint. For 7 columns: header is 7–14 B plus the 1–2 B header-length varint, call it ~12 B.

Per-row storage of the table cell (excluding lyrics text):

| Component | Bytes |
|---|---:|
| Cell payload-size varint | 2–3 |
| Rowid varint (~10⁴–10⁵ rows) | 3 |
| Record header (length varint + 7 serial-type varints) | 12 |
| `artist` text body (mean ~14 B + ~1 B serial type already counted) | 14 |
| `title` text body (~22 B) | 22 |
| `provider` text body (`"lrclib"` ≈ 6 B) | 6 |
| `fetchedAt` INTEGER (epoch ms, ~6-byte serial) | 6 |
| Cell pointer in page array | 2 |
| Page-level slack (b-tree page header amortised ≤ 12 B per page, free-block fragmentation) | ~5 |
| **Subtotal — table row, excluding lyrics text** | **~73 B** |

The unique compound index `(artist, title)` (PK auto-index — SQLite §1.5 "Internal Schema", "the PRIMARY KEY of a rowid table is implemented by a unique index named `sqlite_autoindex_…`"):

| Component | Bytes |
|---|---:|
| Index cell payload-size + rowid varints | 5 |
| Index record header (length + 3 serial types: artist, title, rowid) | 6 |
| `artist` body | 14 |
| `title` body | 22 |
| `rowid` body | 3 |
| Cell pointer + page slack | 7 |
| **Subtotal — auto-index entry** | **~57 B** |

The explicitly-declared `Index(value = ["artist", "title"], unique = true)` is **a duplicate** of the PK auto-index and adds another ~57 B — recommend dropping it (see Topic 5).

**Total per-row overhead (table cell + PK auto-index, no redundant index): ~130 B.**

Adding round-up for SQLite's WAL journaling (`-wal` file holds uncommitted frames; <https://www.sqlite.org/walformat.html>) and the page-fill factor (default 90 % effective per <https://www.sqlite.org/lang_vacuum.html>): inflate by ~1.5× to **~200 B per row** as the planning figure.

**Single number: ~200 bytes per row of fixed overhead beyond the lyric text.**

If the redundant secondary index is left in, add ~85 B for ~**285 B/row**.

### Per-track storage figure (recommended)

`per_track_bytes ≈ 4365 B (mean lyrics) + 200 B (overhead) ≈ 4565 B → round to 4.7 KB.` Use **5 KB/track** as the conservative planning constant in code (handles p95 + WAL + fragmentation).

## Topic 3 — 200 MB budget vs Heart FM rotation

`tracks_capacity = 200 × 1024 × 1024 / per_track_bytes`

| Per-track planning size | Capacity (rows) |
|---|---:|
| 4.5 KB (mean lyrics + overhead) | **~46 600** |
| 5 KB (recommended planning constant) | **~42 000** |
| 7.7 KB (p95 + overhead) | **~27 000** |
| 8.0 KB (max + overhead) | **~26 200** |

Heart FM rotation reference (from the user's brief and corroborated by .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §A — single national feed since 21 Feb 2025, contemporary CHR-ish playlist):

- Weekly hot set: **~300 unique tracks** (Heart's tight gold + current playlist).
- Annual rotation incl. seasonal/promo: **~1500–3000 unique tracks**.

| Window | Heart unique tracks | 200 MB capacity / required | Verdict |
|---|---:|---:|---|
| Weekly | ~300 | ~140× | Massively oversized |
| Annual | ~1500–3000 | ~14–28× | Still very oversized |
| 5-year cumulative incl. genre drift | ~6000–10000 | ~4–7× | Comfortable headroom |
| Theoretical worst-case (every UK chart entry for 20 years) | ~30 000 | ~1.4× | Approaches the ceiling |

**Conclusion: 200 MB is overkill for content but reasonable as an upper-bound user-facing knob.** Practical eviction will *never* fire under normal Heart FM listening — the cache will saturate at the rotation size (~3 000 rows ≈ 15 MB) and stay there indefinitely. The cap exists to bound failure modes (a buggy DL+ stream feeding garbage artist/title pairs, a long-tail Heart Dance / Heart 80s mix-in, or experimentation with other UK pop stations on the same head unit).

Recommendation: **keep the 200 MB cap as the hard ceiling, but trigger eviction at 90 % (180 MB) and drop to 80 % (160 MB)** — small absolute deletions, no thrashing.

## Topic 4 — Eviction policy options

User intent: "based on times played rather than date." Hot-set size ≈ 300 (weekly) → 3000 (yearly). Budget ceiling ≈ 42 000 slots — eviction will be rare.

### a) Pure LFU (least-frequently-used)

- Mechanic: row with lowest `playCount` evicted first.
- Pro: trivial SQL (`ORDER BY playCount ASC LIMIT N`), single column.
- **Con — cache pollution / new-song starvation (Megiddo & Modha 2003, ARC paper; Caffeine docs <https://github.com/ben-manes/caffeine/wiki/Efficiency>):** A track that was huge 6 months ago and now dropped from the playlist accumulates a sky-high `playCount` and never evicts. Genuinely new chart entries arrive with `playCount = 1` and get killed first under cache pressure.
- Verdict: dangerous in isolation for a music-rotation cache.

### b) Window-TinyLFU (Caffeine's algorithm)

- Mechanic: an admission filter (CountMin sketch) admits a candidate only if its frequency exceeds the victim's; an LRU window protects new entries; main region is segmented LRU. Reference: <https://github.com/ben-manes/caffeine>, paper <https://dl.acm.org/doi/10.1145/3149371>.
- Pro: state-of-the-art hit ratio, solves new-song starvation by design.
- Con: stateful in-memory sketch + decoupled from SQLite; reimplementing in Room is non-trivial — you'd need a CountMin sketch in a separate table, periodic aging, and a maintenance coroutine. Massive engineering for a 300-track hot set that won't even fill the cache.
- Verdict: overkill for this app.

### c) Frecency (LFU + LRU hybrid; Firefox URL bar — <https://wiki.mozilla.org/User:Mconnor/PastWork/PlacesFrecency>)

- Mechanic: `score = playCount * w1 + (now - lastPlayedAt) * -w2`. Bias is tunable.
- Pro: matches user intent ("times played") while still giving recency a vote.
- Con: requires both `playCount` AND `lastPlayedAt` columns; `ORDER BY` on a computed expression cannot use a single index, so eviction scans all rows. At 42 000 rows max this is a few-ms scan — acceptable, but more code than (d).
- Verdict: viable but heavier than necessary.

### d) LFU with periodic decay (RECOMMENDED)

- Mechanic: keep a single integer `playCount`. Increment on every successful lookup. Periodically (e.g. every 30 days, gated at app start) execute `UPDATE lyrics_cache SET playCount = playCount / 2`. Eviction order is `ORDER BY playCount ASC, fetchedAt ASC`.
- Pro:
  - Single column, indexable, simplest possible eviction SQL.
  - Decay solves new-song starvation: a 6-month-old former hit halves four times → 1/16 of its peak; a current hit catches up within a week.
  - Decay is O(rows) but runs once per month off the critical path.
  - Naturally matches the user's "based on times played" framing.
  - Behaves correctly when Heart refreshes its rotation: dropped tracks decay to zero and are evicted only if/when storage pressure exists.
- Con: need a "last decay timestamp" preference key in DataStore so decay runs at most once per period.
- Verdict: best fit for this app.

**Recommended: option (d) — LFU with periodic ÷2 decay every 30 days.**

Re-fetching: LRCLIB lyric text is essentially immutable per `(artist, title)` (community-curated — corrections happen but are rare). **Evicted rows are not worth proactive re-fetching;** the next live play will trigger a fresh cache-miss → API fetch path. Keep eviction cheap.

## Topic 5 — Concrete Room schema delta (v1 → v2)

### 5.1 New / changed columns on `lyrics_cache`

```kotlin
@Entity(
  tableName = "lyrics_cache",
  primaryKeys = ["artist", "title"],
  // Drop the redundant explicit Index — the PK already gives a unique auto-index on (artist, title).
  indices = [Index(value = ["playCount"])] // supports ORDER BY playCount ASC for eviction
)
data class LyricsCacheEntity(
  @ColumnInfo(collate = ColumnInfo.NOCASE) val artist: String,
  @ColumnInfo(collate = ColumnInfo.NOCASE) val title: String,
  val syncedLyrics: String?,
  val plainLyrics: String?,
  val provider: String,
  val fetchedAt: Long,
  // --- v2 additions ---
  @ColumnInfo(defaultValue = "0")  val playCount: Long,        // incremented on every successful lookup
  @ColumnInfo(defaultValue = "0")  val firstPlayedAt: Long,    // set on insert
  @ColumnInfo(defaultValue = "0")  val lastPlayedAt: Long,     // updated on every increment
  @ColumnInfo(defaultValue = "0")  val byteSize: Long,         // cached LENGTH(syncedLyrics)+LENGTH(plainLyrics) for fast SUM()
)
```

Rationale for `byteSize`:

- `SELECT SUM(LENGTH(plainLyrics) + LENGTH(syncedLyrics))` is `O(rows × text)`; storing `byteSize` once at insert collapses the headroom check to `SELECT SUM(byteSize)` — a single pass over a small INTEGER column.
- `LENGTH()` in SQLite returns characters for TEXT, **bytes for BLOB**, per <https://www.sqlite.org/lang_corefunc.html#length>. For accurate byte accounting use `LENGTH(CAST(plainLyrics AS BLOB))`. Caching `byteSize` sidesteps the footgun.

### 5.2 Migration v1 → v2

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("""
      ALTER TABLE lyrics_cache
      ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0
    """.trimIndent())
    db.execSQL("""
      ALTER TABLE lyrics_cache
      ADD COLUMN firstPlayedAt INTEGER NOT NULL DEFAULT 0
    """.trimIndent())
    db.execSQL("""
      ALTER TABLE lyrics_cache
      ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0
    """.trimIndent())
    db.execSQL("""
      ALTER TABLE lyrics_cache
      ADD COLUMN byteSize INTEGER NOT NULL DEFAULT 0
    """.trimIndent())
    // Backfill byteSize for existing rows (cheap: cache is at most a few MB at v1).
    db.execSQL("""
      UPDATE lyrics_cache
      SET byteSize = COALESCE(LENGTH(CAST(plainLyrics AS BLOB)), 0)
                   + COALESCE(LENGTH(CAST(syncedLyrics AS BLOB)), 0)
    """.trimIndent())
    // Drop the redundant secondary unique index Room created at v1; create the playCount index.
    db.execSQL("DROP INDEX IF EXISTS index_lyrics_cache_artist_title")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_lyrics_cache_playCount ON lyrics_cache(playCount)")
  }
}
```

Wired in `RadioDatabase` builder: `.addMigrations(MIGRATION_1_2)`. Bump `@Database(version = 2)` and run `./gradlew :app:assembleDebug` — Room will validate via the exported schema JSON in `app/schemas/`.

### 5.3 DAO additions

```kotlin
@Dao
interface LyricsCacheDao {

  // Existing queries omitted.

  /**
   * Upsert with byteSize materialisation. Use over the bare @Insert when
   * caching a fresh LRCLIB hit so byteSize is always in sync.
   */
  @Query("""
    INSERT INTO lyrics_cache(
      artist, title, syncedLyrics, plainLyrics, provider, fetchedAt,
      playCount, firstPlayedAt, lastPlayedAt, byteSize
    )
    VALUES(
      :artist, :title, :synced, :plain, :provider, :now,
      0, :now, :now,
      COALESCE(LENGTH(CAST(:plain AS BLOB)), 0) + COALESCE(LENGTH(CAST(:synced AS BLOB)), 0)
    )
    ON CONFLICT(artist, title) DO UPDATE SET
      syncedLyrics = excluded.syncedLyrics,
      plainLyrics  = excluded.plainLyrics,
      provider     = excluded.provider,
      fetchedAt    = excluded.fetchedAt,
      byteSize     = excluded.byteSize
  """)
  suspend fun upsert(
    artist: String, title: String,
    synced: String?, plain: String?,
    provider: String, now: Long,
  )

  /**
   * Increment play count on every successful lookup that returned lyrics
   * to the user. Cheap: O(1) on the PK index.
   */
  @Query("""
    UPDATE lyrics_cache
    SET playCount = playCount + 1,
        lastPlayedAt = :now
    WHERE artist = :artist AND title = :title
  """)
  suspend fun incrementPlayCount(artist: String, title: String, now: Long): Int

  /** Total bytes of lyric content currently cached. */
  @Query("SELECT COALESCE(SUM(byteSize), 0) FROM lyrics_cache")
  suspend fun totalBytes(): Long

  /**
   * Evict lowest-score rows until total bytes drops to <= :targetBytes.
   * Score = (playCount, fetchedAt). Lowest playCount first; ties broken by
   * oldest fetchedAt (which the user has never re-heard since cache fill).
   *
   * Implemented as a subquery rather than ORDER BY ... LIMIT in DELETE
   * (which SQLite supports only when compiled with SQLITE_ENABLE_UPDATE_DELETE_LIMIT;
   * Android's bundled SQLite does NOT enable this flag — see
   * https://www.sqlite.org/compile.html#enable_update_delete_limit).
   */
  @Query("""
    DELETE FROM lyrics_cache
    WHERE rowid IN (
      SELECT rowid FROM lyrics_cache
      ORDER BY playCount ASC, fetchedAt ASC
      LIMIT :rowsToDelete
    )
  """)
  suspend fun evictRows(rowsToDelete: Int): Int

  /** Periodic decay (call once per month). */
  @Query("UPDATE lyrics_cache SET playCount = playCount / 2")
  suspend fun decayAllPlayCounts(): Int
}
```

### 5.4 Eviction policy implementation

```kotlin
@Singleton
class LyricsCachePolicy @Inject constructor(
  private val dao: LyricsCacheDao,
  private val settings: SettingsRepository,
) {
  // 200 MB hard cap, evict to 80 % to amortise.
  private val capBytes = 200L * 1024 * 1024
  private val highWaterBytes = (capBytes * 0.9).toLong()  // 180 MB trigger
  private val lowWaterBytes  = (capBytes * 0.8).toLong()  // 160 MB target
  private val avgRowBytes    = 5_000L                      // see Topic 1+2

  /** Call from a background coroutine after every Nth cache write (e.g. N = 50). */
  suspend fun evictIfNeeded() {
    val total = dao.totalBytes()
    if (total < highWaterBytes) return
    val excess = total - lowWaterBytes
    val rowsToDelete = (excess / avgRowBytes).coerceAtLeast(1L).toInt()
    dao.evictRows(rowsToDelete)
  }

  /** Call from RadioLyricApp.onCreate; gated to once per 30 days via DataStore. */
  suspend fun maybeDecay(now: Long) {
    val last = settings.lastDecayAt.first()
    if (now - last < 30L * 24 * 60 * 60 * 1000) return
    dao.decayAllPlayCounts()
    settings.setLastDecayAt(now)
  }
}
```

### 5.5 When to call `incrementPlayCount`

Per the user's intent ("times **played**"), increment **on every successful lookup that returned lyrics to the UI**, not on every API fetch. Concretely in `LyricsRepository.lookup(artist, title)`:

```kotlin
suspend fun lookup(artist: String, title: String): Lyrics {
  val cached = dao.find(artist, title)
  if (cached != null) {
    dao.incrementPlayCount(artist, title, now = clock.nowMillis())
    return cached.toLyrics(parser)
  }
  val fetched = runCatching {
    withTimeout(3.seconds) { api.search(title, artist).firstOrNull() }
  }.getOrNull() ?: return Lyrics.None
  dao.upsert(artist, title, fetched.syncedLyrics, fetched.plainLyrics, "lrclib", clock.nowMillis())
  // Initial play counts as 1.
  dao.incrementPlayCount(artist, title, now = clock.nowMillis())
  policy.evictIfNeeded()
  return fetched.toLyrics(parser)
}
```

Notes:

- This treats a cache hit and a fresh fetch identically as "1 play."
- The DL+ pipeline already debounces by 2 s and dedupes consecutive identical `(artist, title)` (per .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §A.2 and Phase 5.2 of the plan), so each playback equals one increment — no double-counting from cross-fade flapping.

### 5.6 Where the "200 MB" measurement comes from — `SUM(byteSize)` vs file size

Two candidate measurements:

1. **`SELECT SUM(byteSize) FROM lyrics_cache`** (or `SUM(LENGTH(CAST(... AS BLOB)))` if `byteSize` is dropped).
   - Measures **content bytes only** — what the user thinks of as "lyrics I've cached".
   - Excludes b-tree page overhead, indices, free pages awaiting reuse, the WAL file (`-wal`), and the shared-memory file (`-shm`).
   - Stable, predictable, fast (with the cached `byteSize` column).
2. **`File(database.openHelper.writableDatabase.path).length()`** (and `+ "-wal"` / `+ "-shm"` siblings).
   - Measures **on-disk reality** — what the OS sees and what occupies head-unit eMMC.
   - Includes overhead, indices, fragmentation, and WAL frames not yet checkpointed.
   - File never shrinks below the high-water mark unless `VACUUM` runs (<https://www.sqlite.org/lang_vacuum.html>); after eviction the file size is *unchanged* until vacuum.

**Pick `SUM(byteSize)` for the eviction trigger.** Justification:

- Users (and the spec) reason about "cache size" as content. Showing 200 MB in a settings UI driven by file size would be confusing — the file is sticky at its high-water mark even after deleting rows.
- Eviction needs a *deterministic* signal: deleting N rows must reduce the metric monotonically. `SUM(byteSize)` does; file size doesn't (it stays flat post-DELETE until VACUUM).
- The fixed-overhead component (~200 B/row) is bounded — at the 42 000-row ceiling that's only ~8 MB, a 4 % accounting error vs the 200 MB cap, which is comfortably absorbed by the 10 % high-water headroom (180 MB trigger).
- Periodic `VACUUM` (e.g. monthly, alongside decay) keeps file size loosely tracking content size if the user inspects it externally.

For the **settings screen** display, prefer `SUM(byteSize)` formatted as MiB; optionally show file size in an "advanced" disclosure.

## Open questions

- Confirm Heart FM's annual rotation size (the 1500–3000 figure is a reasoned estimate from the brief; not directly cited in the heartfm research doc). If the actual figure is much higher (say 10 000+ for a station that mixes in deep cuts), the 200 MB cap remains comfortable but the "overkill" framing weakens.
- User preference: should the settings UI expose the 200 MB number as a slider (50 / 100 / 200 / 500 MB) or hard-code it? If exposed, keep `evictRows` and `evictIfNeeded` parameterised on `capBytes`.
- Should DAB+ "advert" or "news" segments — where DL+ may report a stale or null artist/title — be filtered out of the increment path? Current plan: rely on the upstream debounce in Phase 5.2, but if `playCount` inflation becomes visible in testing, add a per-station cooldown (don't increment the same `(artist, title)` more than once per 90 s).

## Recommended next research (not completed in this session)

- [ ] Empirically measure SQLite-on-Android per-row overhead for `LyricsCacheEntity` by inserting 1000 synthetic rows in an instrumented test on a real Android 7 head unit, then comparing `database.openHelper.writableDatabase.path` file size before/after to validate the ~200 B/row estimate from §Topic 2.
- [ ] Survey Heart FM "Recently Played" feed (heart.co.uk) over 14 days to derive an actual unique-tracks-per-week number rather than the ~300 estimate.
- [ ] Compare LRCLIB hit rate vs Happi.dev plain-only fallback for Heart's most-frequently-missed tracks to decide whether the negative-cache TTL (currently undecided) should be 7 vs 30 days.

## References

- LRCLIB API docs — <https://lrclib.net/docs>
- SQLite file format — <https://www.sqlite.org/fileformat.html> (§1.6 b-tree, §2.1 record, §2.6 cell payload)
- SQLite varint encoding — <https://www.sqlite.org/fileformat.html#varint>
- SQLite WAL format — <https://www.sqlite.org/walformat.html>
- SQLite VACUUM — <https://www.sqlite.org/lang_vacuum.html>
- SQLite LENGTH() semantics — <https://www.sqlite.org/lang_corefunc.html#length>
- SQLite compile-time options (`SQLITE_ENABLE_UPDATE_DELETE_LIMIT`) — <https://www.sqlite.org/compile.html#enable_update_delete_limit>
- Caffeine cache (Window-TinyLFU) — <https://github.com/ben-manes/caffeine> ; paper <https://dl.acm.org/doi/10.1145/3149371>
- Mozilla "Frecency" algorithm — <https://wiki.mozilla.org/User:Mconnor/PastWork/PlacesFrecency>
- ARC paper (LFU cache pollution) — Megiddo & Modha 2003, "ARC: A Self-Tuning, Low Overhead Replacement Cache" — <https://www.usenix.org/conference/fast-03/arc-self-tuning-low-overhead-replacement-cache>
- Existing internal docs:
  - .copilot-tracking/research/subagents/2026-04-30/lyrics-api-research.md (LRCLIB integration, §3 schema, §4 cache)
  - .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md (§A Heart UK on D1, §B architecture)
  - .copilot-tracking/plans/2026-05-01/dab-radio-lyrics-app-plan.instructions.md (Phase 6, Phase 7)
  - .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Phase 6 schema, Phase 7 repository)
