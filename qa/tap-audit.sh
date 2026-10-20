#!/bin/sh
# mqa control-id source scan (skill stable-control-ids, untagged-tap-check sample 3). Exit 1 on a suspect.
SRC="${SRC:-app/src/main/java}"; TAG_RE="${TAG_RE:-testTag[(]|controlTag[(]|overlayRoot[(]|buttonModifier = }"; OUT=${OUT:-/tmp/readyou-tap-audit.txt}; : > "$OUT"
find "$SRC" -name '*.kt' | while IFS= read -r f; do
  awk -v f="$f" -v tag="$TAG_RE" '{ l[NR] = $0 } END {
    for (i = 1; i <= NR; i++) if (l[i] ~ /(\.clickable[( {]|onClick = |\.toggleable\(|\.selectable\(|DropdownMenuItem\(|combinedClickable\(|Clickable\()/ && l[i] !~ /^ *(\/\/|\*|import)/ && l[i] !~ /fun / && l[i] !~ /onClick = null/) {
      ok = 0; for (j = i - 4; j <= i + 4; j++) if (l[j] ~ tag) ok = 1
      if (!ok) printf "%s:%d: tap target with no tag: %s\n", f, i, l[i] } }' "$f" >> "$OUT"
done
grep -rlE 'detectTapGestures' --include='*.kt' "$SRC" | while IFS= read -r f; do
  grep -q 'onClick(' "$f" || echo "$f: detectTapGestures with no semantics { onClick }" >> "$OUT"
done
grep -rnE 'setOnTouchListener' --include='*.kt' --include='*.java' "$SRC" | sed 's/$/  <- needs a click action/' >> "$OUT"
if [ -s "$OUT" ]; then cat "$OUT"; exit 1; fi
