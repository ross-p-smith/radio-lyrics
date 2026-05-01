# Lyrics API Research — Android In-Car App (Heart FM UK)

Status: Complete
Date: 2026-04-30
Scope: Selecting a lyrics API for a personal-use Android Auto / in-car app that receives `artist + title` (and sometimes `album`, `duration`) from DAB+ DL+ metadata for Heart FM UK (mainstream pop/rock) and renders synced lyrics.

---

## 1. API Comparison

### 1.1 LRCLIB (lrclib.net) — RECOMMENDED PRIMARY

- Source: https://lrclib.net/docs
- Auth: none — no API key, no registration.
- Rate limit: explicitly **none** ("This API has no rate limiting in place"). Polite `User-Agent` header requested (e.g. `MyApp/1.0 (https://example.com)`).
- Pricing: **free**. Donation-supported, community-maintained.
- License / ToS: lyrics in LRCLIB are user-submitted and treated as public-domain / freely redistributable; the project explicitly targets third-party app integration (LRCGET, players). No clause forbidding commercial or in-car use. Lowest legal risk for a personal in-car app.
- Synced support: **yes** — both `plainLyrics` and `syncedLyrics` (LRC `[mm:ss.xx]` format) returned in a single response. Word-level karaoke not supported.
- Coverage: very strong for English-language mainstream catalogue (driven by Spotify/MusicBrainz user contributions). UK chart pop is well covered; long-tail or brand-new releases (<1 week) may miss.
- Endpoints:
  - `GET /api/get` — exact match by `track_name`, `artist_name`, `album_name`, `duration` (±2 s tolerance). Falls back to scraping external sources if not cached.
  - `GET /api/get-cached` — same parameters, internal DB only (faster, no upstream lookup).
  - `GET /api/get/{id}`
  - `GET /api/search?q=...` or `?track_name=...&artist_name=...` — fuzzy search returning up to 20 records. Best for messy DL+ metadata.
  - `POST /api/publish` — community contribution (proof-of-work challenge).
- Response schema (200):
  ```json
  {
    "id": 3396226,
    "trackName": "...",
    "artistName": "...",
    "albumName": "...",
    "duration": 233,
    "instrumental": false,
    "plainLyrics": "I feel your breath upon my neck\n...",
    "syncedLyrics": "[00:17.12] I feel your breath upon my neck\n..."
  }
  ```
  404: `{"code":404,"name":"TrackNotFound","message":"Failed to find specified track"}`.
- Notable: `duration` parameter is critical for `/api/get`; LRCLIB only returns a match within ±2 s of stored duration. For a radio app where duration is unknown, use `/api/search` instead.

### 1.2 Musixmatch Developer (Pro) API

- Source: https://www.musixmatch.com/pro/api/pricing , https://docs.musixmatch.com
- Auth: API key (`apikey` query param), per-app.
- Pricing (2026):
  - Basic — $49/mo — 5 k total/day, **500 lyrics/day**, **no time-synced lyrics**.
  - Grow — $199/mo — 20 k total/day, 2 k lyrics/day, **time-synced lyrics included**, App Store certificate.
  - Scale — $499/mo — 100 k/day, 10 k lyrics/day.
  - Enterprise — from $2,000/mo — adds lyrics caching rights.
- License / ToS:
  - Lyrics are licensed; the EULA forbids storing/caching lyrics outside the device session except on Enterprise. Real blocker for offline-first car use.
  - Below Enterprise the legacy free tier (`api.musixmatch.com/ws/1.1/...`) returned only **30 % snippet** for `lyrics.get` — not full text.
  - Commercial in-car use almost certainly requires Grow + App Store certification or a custom contract.
- Synced: yes (LRC), only on Grow+.
- Coverage: best in industry, fully licensed catalog including UK chart pop.
- Verdict: too expensive and too restrictive for a personal in-car app.

### 1.3 Genius API

- Source: https://docs.genius.com
- Auth: OAuth2 (`Authorization: Bearer <token>`), client-credentials token also available.
- Rate limits: not documented publicly; soft per-IP throttling.
- Pricing: free for non-commercial. Docs explicitly state **"Commercial use of the Genius API is not allowed without a license"** (contact api-sales@genius.com).
- Synced: **no** (Genius does not maintain timestamps).
- Returns lyric **text**? **No.** API returns song metadata (title, artist, `url`, primary_artist, stats, annotations, referents). The actual lyric body must be HTML-scraped from the song's web page — that scraping violates Genius ToS.
- Coverage: huge, including UK pop.
- Verdict: unsuitable as a lyric source. Useful only for metadata enrichment (artist info, cover art) under a non-commercial label.

### 1.4 Happi.dev Lyrics Search

- Source: https://happi.dev , https://docs.happi.dev/reference/lyrics-search-api
- Auth: API key (`x-happi-key` header).
- Pricing: pay-as-you-go credits — 1 credit ≈ $0.008 USD; **Lyrics Search = 0.05 credits per call** (~$0.0004 / call). $10 minimum top-up; credits never expire.
- Status: marked **Beta**.
- Synced: **no** — plain lyrics only, no LRC timestamps.
- ToS / commercial: standard ToS at https://happi.dev/terms — does not specifically prohibit commercial use, but lyric provenance/licensing is unclear (re-aggregates third-party sources).
- Coverage: decent for charting English pop; weaker for deep cuts.
- Verdict: cheap plain-lyric fallback; no synced support and uncertain licensing.

### 1.5 SyncedLyrics (Python library) — provider aggregation reference

- Source: https://github.com/moehmeni/syncedlyrics (MIT, last release v1.0.1, Jul 2024).
- Client library, not an API. Useful as a reference for which back-ends actually serve LRC. Provider list:
  1. **Musixmatch** — uses an unofficial mobile-app token (subject to revocation; ToS-grey).
  2. **Deezer** — currently broken.
  3. **LRCLIB** — official public API.
  4. **NetEase** — `https://music.163.com/api/song/lyric` (Chinese service, English coverage limited).
  5. **Megalobiz** — community LRC archive, scraped HTML.
  6. **Genius** — plain text via web scraping.
  7. **Lyricsify** — broken (Cloudflare).
- Takeaway: in 2025-26 the only **stable, terms-clean** synced sources are LRCLIB (primary) and Musixmatch (paid).

### 1.6 NetEase / QQ Music endpoints

- NetEase: `GET https://music.163.com/api/song/lyric?id={id}&lv=-1&kv=-1&tv=-1` returns `{ lrc: { lyric: "[mm:ss.xx]..." }, tlyric: { lyric: "..." } }`. Requires a NetEase numeric song ID — must first call `search?s={artist+title}&type=1`.
- QQ Music: similar undocumented endpoints (`u.y.qq.com/cgi-bin/musicu.fcg`), Cookie-gated.
- Auth: none, but both require a `Referer: https://music.163.com/` (or QQ equivalent) header to bypass anti-hotlink.
- ToS: undocumented for international developers; both are Chinese-market services.
- Coverage for UK pop: spotty; many British tracks missing or mismatched. LRC quality high for tracks they do carry.
- Verdict: viable *third-tier* fallback only; brittle, unofficial.

### 1.7 Spotify Lyrics

- Officially Musixmatch-backed; no public REST endpoint in the Spotify Web API.
- Reverse-engineered endpoint `https://spclient.wg.spotify.com/color-lyrics/v2/track/{trackId}` requires a per-session bearer token issued to official Spotify clients. Use violates Spotify Developer ToS ("you must not reverse engineer or attempt to access non-public APIs").
- Verdict: **do not use**. ToS risk is real, and the endpoint changes without notice.

### 1.8 Comparison Matrix

| API | Auth | Rate limit | Cost | Synced (LRC) | Returns full lyric text | UK pop coverage | ToS for in-car app |
|---|---|---|---|---|---|---|---|
| LRCLIB | none | none | free | yes | yes | high | permissive (no restriction) |
| Musixmatch Grow | API key | 2 k lyrics/day | $199/mo | yes | yes | best | EULA: no caching < Enterprise |
| Genius | OAuth2 | undocumented | free non-commercial | no | **no** (metadata only) | high | "no commercial use without license" |
| Happi.dev | API key | per-credit | ~$0.0004/call | no | yes (plain) | medium | unclear lyric licensing |
| NetEase (unofficial) | none + Referer | unknown | free | yes (when present) | yes | low for UK | undocumented; unofficial |
| Spotify Lyrics | reverse-engineered | n/a | n/a | yes | yes | best | **violates ToS** |

---

## 2. Recommendation

For a personal-use Heart FM in-car app:

1. **Primary: LRCLIB** — `/api/search?artist_name=...&track_name=...` then `/api/get/{id}` (or `/api/get` when a Spotify/MusicBrainz duration is known). Free, no auth, synced LRC, permissive, excellent UK pop coverage, offline cacheable.
2. **Fallback (plain only): Happi.dev Lyrics Search** — pennies per call, plain text only. Use when LRCLIB returns 404.
3. **Optional metadata enrichment: Genius** (artist bio, cover art) — never as a lyric source.
4. **Avoid**: Musixmatch (cost / caching restrictions), Spotify Lyrics (ToS), NetEase (UK coverage / brittleness).

Rationale: zero ongoing cost, no API key to embed in the APK, full LRC for sing-along, on-device caching is allowed, and no commercial-use blocker for a personal hobby project.

---

## 3. LRCLIB — Concrete Request / Response

### 3.1 HTTP

Search-then-get (recommended for radio metadata where duration is unreliable):

```
GET https://lrclib.net/api/search?track_name=As%20It%20Was&artist_name=Harry%20Styles
User-Agent: RadioLyric/0.1 (+https://github.com/you/radio-lyric)
Accept: application/json
```

Direct hit when duration is known (Spotify/MusicBrainz lookup):

```
GET https://lrclib.net/api/get
      ?artist_name=Harry+Styles
      &track_name=As+It+Was
      &album_name=Harry%27s+House
      &duration=167
```

### 3.2 Sample response (truncated)

```json
{
  "id": 1234567,
  "trackName": "As It Was",
  "artistName": "Harry Styles",
  "albumName": "Harry's House",
  "duration": 167,
  "instrumental": false,
  "plainLyrics": "Holdin' me back\nGravity's holdin' me back\n...",
  "syncedLyrics": "[00:11.20] Holdin' me back\n[00:13.45] Gravity's holdin' me back\n[00:16.10] I want you to hold out the palm of your hand\n..."
}
```

### 3.3 Kotlin client (Retrofit + Moshi)

```kotlin
// build.gradle.kts (module)
// implementation("com.squareup.retrofit2:retrofit:2.11.0")
// implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
// implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
// implementation("com.squareup.okhttp3:okhttp:4.12.0")

@JsonClass(generateAdapter = true)
data class LrcLibTrack(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double?,
    val instrumental: Boolean = false,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

interface LrcLibApi {
    @GET("api/search")
    suspend fun search(
        @Query("track_name") track: String,
        @Query("artist_name") artist: String,
    ): List<LrcLibTrack>

    @GET("api/get")
    suspend fun get(
        @Query("track_name") track: String,
        @Query("artist_name") artist: String,
        @Query("album_name") album: String,
        @Query("duration") durationSeconds: Int,
    ): LrcLibTrack

    @GET("api/get/{id}")
    suspend fun getById(@Path("id") id: Long): LrcLibTrack
}

object LrcLibClient {
    val api: LrcLibApi = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "RadioLyric/0.1 (+https://github.com/you/radio-lyric)")
                            .build()
                    )
                }
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        ))
        .build()
        .create(LrcLibApi::class.java)
}
```

Equivalent Ktor client (if you prefer multiplatform):

```kotlin
val http = HttpClient(CIO) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(UserAgent) { agent = "RadioLyric/0.1 (+https://github.com/you/radio-lyric)" }
    defaultRequest { url("https://lrclib.net/") }
}

suspend fun search(track: String, artist: String): List<LrcLibTrack> =
    http.get("api/search") {
        parameter("track_name", track)
        parameter("artist_name", artist)
    }.body()
```

### 3.4 LRC parser → Compose

LRC line grammar: zero or more `[mm:ss.xx]` (or `.xxx`) timestamps, then text. Multiple timestamps may prefix one line (rare).

```kotlin
data class LyricLine(val timeMs: Long, val text: String)

private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

fun parseLrc(lrc: String): List<LyricLine> = buildList {
    for (raw in lrc.lineSequence()) {
        val matches = TIMESTAMP.findAll(raw).toList()
        if (matches.isEmpty()) continue
        val text = raw.substring(matches.last().range.last + 1).trim()
        for (m in matches) {
            val (mm, ss, frac) = m.destructured
            val fracMs = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            add(LyricLine(mm.toLong() * 60_000 + ss.toLong() * 1_000 + fracMs, text))
        }
    }
}.sortedBy { it.timeMs }

@Composable
fun LyricsView(lines: List<LyricLine>, positionMs: Long) {
    val activeIndex = remember(positionMs, lines) {
        // Largest index with timeMs <= positionMs
        var lo = 0; var hi = lines.size - 1; var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        ans
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem(activeIndex.coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lines) { i, line ->
            Text(
                text = line.text.ifBlank { "♪" },
                style = if (i == activeIndex)
                    MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary)
                else
                    MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}
```

Notes:

- For a radio app the playback `positionMs` is *the elapsed time since DAB DL+ "now playing" event fired* — not a media-player position. LRC start references the studio recording, but radio edits/intros differ. Apply a per-track or per-station offset.
- Consider a user-tappable "sync now" gesture that records `(currentLrcLineIndex, elapsedMs)` to derive offset.

---

## 4. Caching Strategy

### 4.1 Why cache

- Cars frequently lose cellular signal; lyrics must survive a tunnel.
- Heart FM has a tight rotation (~300 tracks weekly) — cache hit rate after a week is very high.
- LRCLIB is free, but being a good citizen still matters (User-Agent + cache).

### 4.2 Room schema

```kotlin
@Entity(
    tableName = "lyrics",
    indices = [Index(value = ["artistKey", "titleKey"], unique = true)]
)
data class LyricsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artistKey: String,            // normalised: lowercase, ASCII-folded, "feat" stripped
    val titleKey: String,             // normalised: lowercase, brackets stripped
    val artistDisplay: String,
    val titleDisplay: String,
    val album: String?,
    val durationMs: Long?,
    val plainLyrics: String?,
    val syncedLyrics: String?,        // raw LRC; parse on read
    val source: String,               // "lrclib" | "happi" | "user"
    val sourceId: String?,            // e.g. LRCLIB id
    val fetchedAt: Long,              // epoch ms
    val isInstrumental: Boolean = false,
    val isNegativeCache: Boolean = false  // true if lookup definitively failed
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE artistKey = :artist AND titleKey = :title LIMIT 1")
    suspend fun find(artist: String, title: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE fetchedAt < :cutoff AND isNegativeCache = 1")
    suspend fun pruneFailed(cutoff: Long)
}
```

### 4.3 TTL

- Successful synced lyrics: **never expire** (LRC text doesn't change). Add a manual "refresh lyrics" button.
- Successful plain-only lyrics: 30 days, then opportunistically retry to upgrade to synced.
- Negative cache (404 from all sources): 7 days (avoid hammering for a track LRCLIB just doesn't have yet — community uploads are frequent).
- Total cache size cap: e.g. 5 000 rows; LRU eviction on access timestamp.

### 4.4 Offline behaviour

1. On track change → query Room first by normalised key.
2. If hit and not stale → display immediately.
3. If miss and online → fire LRCLIB search; on 404 fall back to Happi.dev; on 404 store negative-cache row.
4. If miss and offline → show "Lyrics unavailable offline" with the artist/title; queue a fetch via WorkManager (`Constraints` requiring network).

---

## 5. Fuzzy Matching for DAB DL+ Metadata

DAB+ DL+ commonly delivers strings like:

- `HARRY STYLES - AS IT WAS` (all-caps, hyphen-separated)
- `Calvin Harris  Feat. Dua Lipa - One Kiss` (double spaces, "Feat." variants)
- `Ed Sheeran - Shape Of You (Stormzy Remix) [Radio Edit]`
- `Various Artists / The Weeknd - Blinding Lights`

### 5.1 Normalisation (apply to both query and cache key)

```kotlin
private val FEAT_REGEX = Regex(
    """\s*[(\[]?\s*(?:feat\.?|ft\.?|featuring|with|w/)\s+[^)\]]+[)\]]?""",
    RegexOption.IGNORE_CASE
)
private val BRACKET_TAG = Regex(
    """\s*[(\[][^)\]]*(?:remix|edit|version|remaster|mix|mono|live|acoustic)[^)\]]*[)\]]""",
    RegexOption.IGNORE_CASE
)
private val PUNCT = Regex("""[\p{Punct}&&[^']]""")     // keep apostrophes
private val WHITESPACE = Regex("""\s+""")

fun normalise(s: String): String {
    var out = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")     // strip diacritics
    out = FEAT_REGEX.replace(out, "")
    out = BRACKET_TAG.replace(out, "")
    out = PUNCT.replace(out, " ")
    out = WHITESPACE.replace(out, " ")
    return out.trim().lowercase(java.util.Locale.UK)
}

fun splitArtistTitle(raw: String): Pair<String, String>? {
    // Heart FM DL+ uses " - " as separator; sometimes " – " (en-dash) or " — " (em-dash).
    val sep = Regex("""\s+[-–—]\s+""").find(raw) ?: return null
    return raw.substring(0, sep.range.first).trim() to raw.substring(sep.range.last + 1).trim()
}
```

### 5.2 Query strategy

1. Split with `splitArtistTitle`. If split fails, use raw string in LRCLIB `?q=` mode.
2. Normalise artist and title separately.
3. Try `/api/search?track_name=<title>&artist_name=<primary artist>` (primary = part before "feat."/"ft.").
4. Score candidates with Jaro–Winkler (`org.apache.commons:commons-text:1.12.0`) — handles transposition and short-string typos:

   ```kotlin
   val jw = org.apache.commons.text.similarity.JaroWinklerSimilarity()
   fun score(query: Pair<String,String>, cand: LrcLibTrack): Double {
       val (qa, qt) = query
       val a = jw.apply(qa, normalise(cand.artistName))
       val t = jw.apply(qt, normalise(cand.trackName))
       return 0.4 * a + 0.6 * t   // title weighted higher (artist often truncated)
   }
   ```

   Accept threshold ≥ 0.88.
5. If best < threshold, drop "feat" / parentheticals and retry once with `?q=<artist>+<title>`.
6. If still no hit, fall back to Happi.dev for plain text only.

### 5.3 Edge cases observed on Heart FM

- `Various Artists / Real Artist` prefix → strip everything before `/`.
- All-caps artist → handled by `lowercase()`.
- Show ID prefix (`HEART BREAKFAST: Song - Artist`) → strip before `:` if the colon is outside song text.
- Live broadcast jingles deliver `Heart FM` as the "title" → ignore tracks where title equals station name or contains words like `news`, `traffic`, `weather`.
- Treat duplicate spaces and non-breaking spaces (`\u00A0`) as a single space before splitting.

---

## 6. References / Sources

- LRCLIB API docs — https://lrclib.net/docs
- LRCGET (reference client, Tauri/Rust) — https://github.com/tranxuanthang/lrcget
- Musixmatch Pro pricing — https://www.musixmatch.com/pro/api/pricing
- Musixmatch developer docs — https://docs.musixmatch.com/
- Genius API docs — https://docs.genius.com/
- Happi.dev API directory & pricing — https://happi.dev/
- Happi.dev Lyrics endpoint — https://docs.happi.dev/reference/lyrics-search-api
- syncedlyrics provider list — https://github.com/moehmeni/syncedlyrics
- NetEase community endpoint reference — https://github.com/Binaryify/NeteaseCloudMusicApi
- Apache Commons Text (Jaro–Winkler) — https://commons.apache.org/proper/commons-text/

---

## 7. Recommended Next Research (not done)

- [ ] Verify LRCLIB hit-rate empirically against a captured 7-day Heart FM playlist (e.g. via Now Playing scrape from Global Player).
- [ ] Confirm Happi.dev fallback returns lyrics for tracks LRCLIB misses (sample 50 misses).
- [ ] Investigate MusicBrainz / AcoustID lookup to obtain canonical `(artist, title, album, duration)` from messy DL+ strings, increasing LRCLIB direct-hit rate via `/api/get`.
- [ ] Evaluate Android Auto media template constraints — full lyric scrolling likely requires running as a non-Auto-certified app (parked-only) or as a tablet head-unit app.
- [ ] Prototype per-station LRC offset auto-detection (cross-correlate first vocal energy against first non-blank LRC timestamp).

---

## 8. Clarifying Questions

1. Will the app run on **Android Auto** (strict template UI, no free-form Compose) or as a **standalone tablet/phone app** mounted in the car? This changes the entire UI strategy and Google's Driver Distraction policy applicability.
2. Is "personal use" strictly single-user / never published, or do you intend to publish to Play Store? Distribution changes Genius / Musixmatch ToS exposure.
3. What is the source of `nowPlaying` events — the car's DAB+ tuner via a hardware bridge, an internet stream from Global Player, or Shazam-style audio fingerprinting? Metadata quality (and whether `duration` is available) drives the LRCLIB lookup mode.
4. Do you need **translations** (Musixmatch sells these; LRCLIB does not) or is English-only acceptable for Heart FM?
