#!/usr/bin/env python3
"""Generate the debug-only stub content pack under app/src/debug/assets.

Every value is an obvious placeholder: no real letter data, glyphs, names or audio. The
release build refuses to package without real content in src/main/assets/content, and a
release unit test fails if the string PLACEHOLDER reaches release assets.

Requires ffmpeg built with libopus. Run from anywhere: python3 tools/gen_stub_pack.py
"""
import json
import pathlib
import shutil
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "debug" / "assets"

LETTERS = 28
TIER_SIZE = 4
VOICES = ["v1", "v2", "v3", "v4"]
HARAKAT = ["fatha", "kasra", "damma"]
HARAKAH_VOICES = ["v1", "v2"]
MAKHRAJ = 4
GLYPH = "؟"  # Arabic question mark: visibly not a letter.


def silent_opus(path: pathlib.Path) -> None:
    # Opus, 24 kbps, mono, 16 kHz, 1 s of silence: matches the real pack's format.
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "anullsrc=r=16000:cl=mono",
         "-t", "1", "-c:a", "libopus", "-b:a", "24k", "-ac", "1", "-ar", "16000", str(path)],
        check=True,
    )


def main() -> None:
    for sub in ("content", "audio", "makhraj"):
        shutil.rmtree(OUT / sub, ignore_errors=True)
        (OUT / sub).mkdir(parents=True)

    ids = [f"placeholder_{i:02d}" for i in range(1, LETTERS + 1)]
    tiers = [ids[i:i + TIER_SIZE] for i in range(0, LETTERS, TIER_SIZE)]
    makhraj_ids = [f"placeholder_makhraj_{i}" for i in range(1, MAKHRAJ + 1)]

    letters = []
    for n, lid in enumerate(ids):
        tier = n // TIER_SIZE
        # Every seventh letter has a single voice, to exercise graceful degradation.
        voices = VOICES[:1] if n % 7 == 6 else VOICES
        letters.append({
            "id": lid,
            "glyph": GLYPH,
            "forms": {"isolated": GLYPH, "initial": GLYPH, "medial": "ـ" + GLYPH, "final": "ـ" + GLYPH},
            "nameEn": f"PLACEHOLDER {n + 1:02d}",
            "nameMl": f"PLACEHOLDER {n + 1:02d}",
            "makhrajId": makhraj_ids[n % MAKHRAJ],
            "makhrajNoteEn": "PLACEHOLDER",
            "makhrajNoteMl": "PLACEHOLDER",
            "confusableWith": [o for o in tiers[tier] if o != lid],
            "audio": {"isolated": [f"{lid}_isolated_{v}.opus" for v in voices],
                      **{h: [f"{lid}_{h}_{v}.opus" for v in HARAKAH_VOICES] for h in HARAKAT}},
            "unlockTier": tier + 1,
        })

    makhraj = [{"id": m, "diagram": f"makhraj/{m}.svg", "labelEn": "PLACEHOLDER", "labelMl": "PLACEHOLDER"}
               for m in makhraj_ids]

    dump = lambda name, data: (OUT / "content" / name).write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    dump("letters.json", letters)
    dump("confusion_sets.json", tiers)
    dump("makhraj.json", makhraj)

    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">'
           '<!-- PLACEHOLDER --><rect x="5" y="5" width="90" height="90" fill="none" stroke="#888"/></svg>\n')
    for m in makhraj_ids:
        (OUT / "makhraj" / f"{m}.svg").write_text(svg)

    with tempfile.TemporaryDirectory() as tmp:
        silence = pathlib.Path(tmp) / "silence.opus"
        silent_opus(silence)
        for l in letters:
            for files in l["audio"].values():
                for f in files:
                    shutil.copyfile(silence, OUT / "audio" / f)

    print(f"stub pack: {len(letters)} letters, {len(tiers)} tiers, "
          f"{sum(len(f) for l in letters for f in l['audio'].values())} audio files -> {OUT}")


if __name__ == "__main__":
    main()
