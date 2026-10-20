#!/bin/sh
# mqa: every overlay call site (dialog, sheet, menu, popup) carries a tag, or is inside a tagged wrapper.
# Prints the untagged call sites; exit 1 when there is one. Wrappers and known-dead code are listed in SKIP.
SRC="${SRC:-app/src/main/java}"
# SponsorDialog.kt: its ModalBottomSheet takes the tag through modifier, at its one call site (TipsAndSupportPage).
SKIP='component/base/(RYDialog|RadioDialog|TextFieldDialog|BottomDrawer|AnimatedPopup|Menu)\.kt|component/menu/AnimatedDropdownMenu\.kt|reading/drawer/FeedOptionDrawer\.kt|tips/SponsorDialog\.kt'
OUT=$(find "$SRC" -name '*.kt' | grep -vE "$SKIP" | while IFS= read -r f; do
  awk -v f="$f" '{ l[NR] = $0 } END {
    for (i = 1; i <= NR; i++) if (l[i] ~ /(^|[^A-Za-z.])(Dialog|AlertDialog|ModalBottomSheet|BottomDrawer|DropdownMenu|AnimatedDropdownMenu|Popup|RYDialog|RadioDialog|TextFieldDialog|RenameDialog|ChangeUrlDialog)\(/ && l[i] !~ /^ *(\/\/|\*|\/\*)/ && l[i] !~ /fun |import /) {
      ok = 0; for (j = i; j <= i + 8 && j <= NR; j++) if (l[j] ~ /overlayTag|overlayRoot|sheetTag/) ok = 1
      if (!ok) printf "%s:%d: overlay with no tag: %s\n", f, i, l[i] } }' "$f"
done)
[ -z "$OUT" ] && { echo "overlay audit: every overlay call site is tagged"; exit 0; }
echo "$OUT"; exit 1
