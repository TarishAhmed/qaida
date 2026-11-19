#!/usr/bin/env bash
# Rasterises the legacy (pre-Android 8) launcher icon from tools/icon/ic_launcher_legacy.svg.
# Android 8+ uses the adaptive icon in res/mipmap-anydpi-v26. Requires rsvg-convert (librsvg).
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
for spec in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  dpi="${spec%%:*}"; px="${spec##*:}"
  mkdir -p "$root/app/src/main/res/mipmap-$dpi"
  rsvg-convert -w "$px" -h "$px" "$root/tools/icon/ic_launcher_legacy.svg" -o "$root/app/src/main/res/mipmap-$dpi/ic_launcher.png"
done
