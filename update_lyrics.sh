#!/bin/bash
set -euo pipefail

SOURCE_URL="${SOURCE_URL:-https://www.heart.co.uk/radio/last-played-songs/}"
SEED_FILE="${SEED_FILE:-app/src/main/assets/lyrics/seed_lyrics_cache.jsonl}"
PAIR_LIMIT="${PAIR_LIMIT:-60}"

tmp_html=$(mktemp)
tmp_pairs=$(mktemp)
tmp_new=$(mktemp)
tmp_merged=$(mktemp)
cleanup() {
    rm -f "$tmp_html" "$tmp_pairs" "$tmp_new" "$tmp_merged"
}
trap cleanup EXIT

curl -k -s "$SOURCE_URL" -o "$tmp_html"

# Heart page exposes track/artist in adjacent span tags.
perl -0777 -ne '
  while (/<span itemprop="name" class="track">\s*(.*?)\s*<\/span>\s*<span itemprop="byArtist" class="artist">\s*(.*?)\s*<\/span>/gs) {
    my ($title, $artist) = ($1, $2);
    $title =~ s/<[^>]+>//g;
    $artist =~ s/<[^>]+>//g;
    $title =~ s/^\s+|\s+$//g;
    $artist =~ s/^\s+|\s+$//g;
    $title =~ s/&#39;/'\''/g;
    $artist =~ s/&#39;/'\''/g;
    $title =~ s/&amp;/&/g;
    $artist =~ s/&amp;/&/g;
    print "$artist|$title\n" if length($artist) && length($title);
  }
' "$tmp_html" | awk '!seen[tolower($0)]++' | head -n "$PAIR_LIMIT" > "$tmp_pairs"

extracted_count=$(wc -l < "$tmp_pairs")

matched_count=0
while IFS='|' read -r artist title; do
    [[ -z "$artist" || -z "$title" ]] && continue

    encoded_artist=$(printf %s "$artist" | jq -sRr @uri)
    encoded_title=$(printf %s "$title" | jq -sRr @uri)
    first_row=$(curl -k -s "https://lrclib.net/api/search?artist_name=$encoded_artist&track_name=$encoded_title" | jq -c 'first(.[]?) // empty')

    if [[ -n "$first_row" ]]; then
        jq -c --arg artist "$artist" --arg title "$title" \
            '{artist:$artist,title:$title,syncedLyrics:.syncedLyrics,plainLyrics:.plainLyrics,provider:"lrclib"}' \
            <<<"$first_row" >> "$tmp_new"
        matched_count=$((matched_count + 1))
    fi
done < "$tmp_pairs"

mkdir -p "$(dirname "$SEED_FILE")"
touch "$SEED_FILE"
before_count=$(wc -l < "$SEED_FILE")

# Keep existing rows first; append only new unique artist/title rows.
cat "$SEED_FILE" "$tmp_new" | jq -cs '
  map(select(.artist and .title))
  | reduce .[] as $i ([];
      if any(.[]; ((.artist|ascii_downcase)==($i.artist|ascii_downcase) and (.title|ascii_downcase)==($i.title|ascii_downcase)))
      then . else . + [$i] end
    )
  | .[]
' > "$tmp_merged"

after_count=$(wc -l < "$tmp_merged")
added_count=$((after_count - before_count))
mv "$tmp_merged" "$SEED_FILE"

echo "Extracted pairs: $extracted_count"
echo "LRCLIB matches: $matched_count"
echo "Added to seed: $added_count"
echo "Final seed lines: $after_count"
echo "First 10 tracks now in seed:"
head -n 10 "$SEED_FILE" | jq -r '"- \(.artist) | \(.title)"'
