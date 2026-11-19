#!/usr/bin/env bash
# Convert a .woff2 font to .ttf for res/font (Android cannot load woff2).
# Requires fonttools with brotli:  pip install fonttools brotli
#
# Usage: tools/convert_font.sh <input.woff2> <nastaleeq|naskh>
#
# Every glyph and every GSUB/GPOS feature is kept (no subsetting): Nastaliq shaping
# depends on lookups that a subsetter can silently drop.
set -euo pipefail

in="${1:?input .woff2}"
face="${2:?nastaleeq or naskh}"
case "$face" in
  nastaleeq|naskh) ;;
  *) echo "face must be nastaleeq or naskh" >&2; exit 2 ;;
esac

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/app/src/main/res/font/indopak_${face}.ttf"

python3 -I - "$in" "$out" <<'PY'
import sys
from fontTools.ttLib import TTFont

src, dst = sys.argv[1], sys.argv[2]
font = TTFont(src)
if "CFF " in font or "CFF2" in font:
    sys.exit("CFF outlines: save as .otf and rename, or convert outlines first; refusing to write a broken .ttf")
font.flavor = None
font.save(dst)

check = TTFont(dst)
tables = sorted(check.keys())
print(f"wrote {dst}")
print("tables:", " ".join(tables))
for t in ("GSUB", "GPOS"):
    if t not in check:
        print(f"WARNING: no {t} table; Indo-Pak shaping will be wrong", file=sys.stderr)
    else:
        feats = sorted({fr.FeatureTag for fr in check[t].table.FeatureList.FeatureRecord})
        print(f"{t} features:", " ".join(feats))
PY
