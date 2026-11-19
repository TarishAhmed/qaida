#!/usr/bin/env python3
"""Build the real content pack in app/src/main/assets from authoritative or openly licensed sources.

Nothing here is written from memory:
  * letters, glyphs, English names: the Unicode Character Database (Python's unicodedata)
  * positional forms: Unicode's ArabicShaping.txt joining types (downloaded, cached)
  * look-alike letters: the bundled DigitalKhatt IndoPak font, which builds each letter from a shared
    body glyph plus dots; letters that share a body are visual confusables
  * sound-alike letters: the product spec's own confusion sets
  * audio: recordings by Ruaa GHAREEB on Wikimedia Commons (CC BY-SA 4.0), matched to letters by file
    name, downloaded with SHA-1 checks, loudness-normalised and encoded to the spec's Opus format

Every recording ships UNVERIFIED. Ambiguous file names are attached to every letter they could be. A
fluent reader marks each clip right or wrong on the in-app review screen, exports the result, and
  python3 tools/build_content_pack.py --apply-review qaida-review.json
records the verdicts in content/verification.json and removes clips marked wrong. Release builds refuse
to package until every remaining clip is verified.

Not built: makhraj notes and diagrams, Malayalam letter names (English names are used), boss lines.
They need a qualified source.

Requires: ffmpeg with libopus, and Python packages fonttools and uharfbuzz (pip install fonttools uharfbuzz).
"""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import unicodedata
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
# QAIDA_ASSETS points the tool at another assets folder (used to test --apply-review on a copy).
OUT = pathlib.Path(__import__("os").environ.get("QAIDA_ASSETS", ROOT / "app" / "src" / "main" / "assets"))
CACHE = ROOT / "content-src"
FONT = ROOT / "app" / "src" / "main" / "res" / "font" / "indopak_nastaleeq.ttf"
UA = "QaidaContentBuild/0.1 (+https://github.com/TarishAhmed/qaida)"

# The 29 letters of the Qaida alphabet as Unicode code points, in Unicode (hija'i) order.
CODEPOINTS = [0x0627, *range(0x0628, 0x063B), *range(0x0641, 0x064B), 0x0621]
CODEPOINTS = [c for c in CODEPOINTS if c not in (0x0629, 0x0649)]  # teh marbuta and alef maksura are not separate letters

ZWJ = "‍"

# Sound confusion sets from the product spec, in the spec's transliteration, mapped to Unicode letter names.
SPEC_SOUND_SETS = [["tha", "sin", "sad"], ["dhal", "zay", "za"], ["ha", "haa"], ["dad", "dal", "za"],
                   ["qaf", "kaf"], ["ayn", "hamza"], ["ta", "tah"]]
SPEC_TO_UNICODE = {"tha": "theh", "sin": "seen", "sad": "sad", "dhal": "thal", "zay": "zain", "za": "zah",
                   "ha": "hah", "haa": "heh", "dad": "dad", "dal": "dal", "qaf": "qaf", "kaf": "kaf",
                   "ayn": "ain", "hamza": "hamza", "ta": "teh", "tah": "tah"}

# Commons recordings by file name. Where the name cannot tell two letters apart the clip is offered to both;
# the review keeps it under the right one and removes it from the other.
RECORDINGS = {
    "alef": ["Alifp.wav", "Alif A.wav"],
    "beh": ["Bapron.wav", "Baaudio.wav"],
    "teh": ["Tapron.wav", "Taaudio.wav"],
    "theh": ["Thep.wav", "Thaaudio.wav"],
    "jeem": ["Jeemp.wav", "Jeemaudio.wav"],
    "hah": ["Hap.wav", "Haaudio.wav", "Hpron.wav", "Haaaudio.wav"],
    "khah": ["Khap.wav", "Khaaudio.wav"],
    "dal": ["Dalp.wav", "Dalaudio.wav"],
    "thal": ["Dhalp.wav", "Dzalaudio.wav"],
    "reh": ["RAPRON.wav", "RAAUDIO.wav"],
    "zain": ["Zaip.wav", "ZaiAUDIO.wav"],
    "seen": ["Senp.wav", "Senaudio.wav"],
    "sheen": ["Shenp.wav", "Shenaudio.wav"],
    "sad": ["Sadp.wav", "Sadaudio.wav"],
    "dad": ["Ddapro.wav", "Ddadaudio.wav", "Dddp.wav", "Dddaudio.wav"],
    "tah": ["Ttapron.wav", "Ttaaudio.wav"],
    "zah": ["Ddapro.wav", "Ddadaudio.wav", "Dddp.wav", "Dddaudio.wav"],
    "ain": ["Aap.wav", "Aaaudio.wav"],
    "ghain": ["Ghp.wav", "Ghaudio.wav"],
    "feh": ["Fap.wav", "Faaudio.wav"],
    "qaf": ["Qafp.wav", "Qafaudio.wav"],
    "kaf": ["Kafp.wav", "Kafaudio.wav"],
    "lam": ["Lampronn.wav", "Lamudio.wav"],
    "meem": ["Mpron.wav", "Maudio.wav"],
    "noon": ["Npron.wav", "Naudio.wav"],
    "heh": ["Hap.wav", "Haaudio.wav", "Hpron.wav", "Haaaudio.wav"],
    "waw": ["Wapron.wav", "Wawaudio.wav"],
    "yeh": ["Yapron.wav", "Yaaudio.wav"],
    "hamza": ["Hamzap.wav", "Hamza h.wav"],
}
ARTIST = "Ruaa GHAREEB"
LICENCE = "CC BY-SA 4.0"


def fetch(url: str, dest: pathlib.Path) -> pathlib.Path:
    if not dest.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req) as r, open(dest, "wb") as f:
            shutil.copyfileobj(r, f)
    return dest


def letters():
    shaping = fetch("https://www.unicode.org/Public/UCD/latest/ucd/ArabicShaping.txt", CACHE / "ArabicShaping.txt")
    joining = {}
    for line in shaping.read_text().splitlines():
        if line and not line.startswith("#"):
            cp, _, jt, _ = [p.strip() for p in line.split(";")]
            joining[int(cp, 16)] = jt
    out = []
    for cp in CODEPOINTS:
        ch = chr(cp)
        name = unicodedata.name(ch).removeprefix("ARABIC LETTER ")
        dual = joining.get(cp) == "D"
        right = joining.get(cp) == "R"
        out.append({
            "id": name.lower(),
            "glyph": ch,
            "nameEn": name.title(),
            "forms": {
                "isolated": ch,
                "initial": ch + ZWJ if dual else ch,
                "medial": ZWJ + ch + ZWJ if dual else (ZWJ + ch if right else ch),
                "final": ZWJ + ch if (dual or right) else ch,
            },
        })
    return out


def shape_groups(ls):
    """Letters drawn on the same body glyph in the bundled font (ب ت ث share a body, differing only in dots)."""
    import uharfbuzz as hb
    font = hb.Font(hb.Face(FONT.read_bytes()))
    body = {}
    for l in ls:
        buf = hb.Buffer()
        buf.add_str(l["glyph"])
        buf.guess_segment_properties()
        hb.shape(font, buf)
        body[l["id"]] = tuple(sorted(i.codepoint for i, p in zip(buf.glyph_infos, buf.glyph_positions) if p.x_advance != 0))
    groups = {}
    for lid, b in body.items():
        groups.setdefault(b, []).append(lid)
    return [g for g in groups.values() if len(g) > 1]


def tiers(ls, shape, sound):
    """Tiers that introduce whole confusion groups, never the alphabet in order (spec)."""
    order = [l["id"] for l in ls]
    groups = [list(g) for g in shape]
    grouped = {x for g in groups for x in g}
    # A letter with no look-alike joins the group it is most easily confused with by sound, if any.
    for lid in order:
        if lid in grouped:
            continue
        partners = {y for s in sound if lid in s for y in s if y != lid}
        home = next((g for g in groups if partners & set(g) and len(g) < 5), None)
        if home is not None:
            home.append(lid)
            grouped.add(lid)
    # Letters still alone form groups with their sound partners (qaf and kaf), else stand alone. They go
    # after the look-alike groups.
    loners = [x for x in order if x not in grouped]
    loner_groups = []
    for lid in loners:
        partners = {y for s in sound if lid in s for y in s}
        home = next((g for g in loner_groups if partners & set(g)), None)
        if home is not None:
            home.append(lid)
        else:
            loner_groups.append([lid])
    groups.sort(key=lambda g: min(order.index(x) for x in g))
    out, current = [], []
    for g in groups + loner_groups:  # pack into tiers of at most five without splitting a group
        if current and len(current) + len(g) > 5:
            out.append(current)
            current = []
        current = current + sorted(g, key=order.index)
    if current:
        out.append(current)
    return out


def encode(src: pathlib.Path, dst: pathlib.Path):
    # Opus, 24 kbps, mono, 16 kHz, loudness-normalised to -16 LUFS (spec).
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(src), "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
                    "-ac", "1", "-ar", "16000", "-c:a", "libopus", "-b:a", "24k", str(dst)], check=True)


def commons_meta(titles):
    import urllib.parse
    meta = {}
    titles = sorted(set(titles))
    for i in range(0, len(titles), 40):
        q = urllib.parse.urlencode({"action": "query", "titles": "|".join("File:" + t for t in titles[i:i + 40]),
                                    "prop": "imageinfo", "iiprop": "url|sha1|extmetadata", "format": "json"})
        req = urllib.request.Request("https://commons.wikimedia.org/w/api.php?" + q, headers={"User-Agent": UA})
        for p in json.load(urllib.request.urlopen(req))["query"]["pages"].values():
            ii = p["imageinfo"][0]
            lic = ii["extmetadata"]["LicenseShortName"]["value"]
            if lic != LICENCE:
                sys.exit(f"{p['title']}: licence {lic}, expected {LICENCE}")
            meta[p["title"].removeprefix("File:")] = {"url": ii["url"].split("?")[0], "sha1": ii["sha1"],
                                                      "page": ii["descriptionurl"]}
    return meta


def voice_id(file: str) -> str:
    return "".join(c for c in file.removesuffix(".wav").lower() if c.isalnum())


def build():
    ls = letters()
    ids = [l["id"] for l in ls]
    sound = [[SPEC_TO_UNICODE[x] for x in s] for s in SPEC_SOUND_SETS]
    shape = shape_groups(ls)
    tier_list = tiers(ls, shape, sound)
    tier_of = {lid: t + 1 for t, members in enumerate(tier_list) for lid in members}

    meta = commons_meta([f for files in RECORDINGS.values() for f in files])
    for d in ("content", "audio"):
        shutil.rmtree(OUT / d, ignore_errors=True)
        (OUT / d).mkdir(parents=True)

    sources = []
    for l in ls:
        lid = l["id"]
        related = {y for s in shape + sound if lid in s for y in s}
        confusable = sorted(related - {lid}, key=ids.index)
        if len(confusable) < 3:  # top up from the letter's own tier so listening cards have choices
            confusable += [x for x in tier_list[tier_of[lid] - 1] if x != lid and x not in confusable][: 3 - len(confusable)]
        clips = []
        for f in RECORDINGS[lid]:
            m = meta[f]
            raw = fetch(m["url"], CACHE / "commons" / f)
            if hashlib.sha1(raw.read_bytes()).hexdigest() != m["sha1"]:
                sys.exit(f"{f}: SHA-1 mismatch against Commons")
            out_name = f"{lid}_isolated_{voice_id(f)}.opus"
            encode(raw, OUT / "audio" / out_name)
            clips.append(out_name)
            sources.append({"file": out_name, "letter": lid, "source": f, "page": m["page"], "sha1": m["sha1"],
                            "artist": ARTIST, "licence": LICENCE, "shared": sum(f in v for v in RECORDINGS.values()) > 1})
        l.update({
            "nameMl": l["nameEn"],
            "makhrajId": None,
            "makhrajNoteEn": "",
            "makhrajNoteMl": "",
            "confusableWith": confusable,
            "audio": {"isolated": clips},
            "unlockTier": tier_of[lid],
        })

    letters_sorted = sorted(ls, key=lambda l: (l["unlockTier"], ids.index(l["id"])))
    dump = lambda name, data: (OUT / "content" / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    dump("letters.json", letters_sorted)
    dump("confusion_sets.json", [sorted(g, key=ids.index) for g in shape] + sound)
    dump("makhraj.json", [])
    dump("sources.json", sources)
    (OUT / "content" / "credits.txt").write_text(
        f"Letter recordings: {ARTIST}, {LICENCE}, via Wikimedia Commons (commons.wikimedia.org).\n", encoding="utf-8")
    print(f"{len(ls)} letters in {len(tier_list)} tiers: " + " | ".join(" ".join(t) for t in tier_list))
    print(f"shape groups from the font: {shape}")
    print(f"{len(sources)} clips, all unverified until reviewed")


def apply_review(path: str):
    """Records a review exported from the app and removes every clip marked wrong."""
    review = json.loads(pathlib.Path(path).read_text())
    verdicts = review["clips"]  # file -> true (right letter) / false (wrong)
    letters_path = OUT / "content" / "letters.json"
    ls = json.loads(letters_path.read_text())
    for l in ls:
        keep = [f for f in l["audio"]["isolated"] if verdicts.get(f) is not False]
        for f in set(l["audio"]["isolated"]) - set(keep):
            (OUT / "audio" / f).unlink(missing_ok=True)
        l["audio"]["isolated"] = keep
    letters_path.write_text(json.dumps(ls, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    remaining = {f for l in ls for f in l["audio"]["isolated"]}
    verification = {"reviewer": review.get("reviewer", ""), "reviewedAt": review.get("reviewedAt"),
                    "clips": {f: bool(verdicts.get(f)) for f in sorted(remaining)}}
    (OUT / "content" / "verification.json").write_text(json.dumps(verification, indent=2) + "\n", encoding="utf-8")
    pending = [f for f, ok in verification["clips"].items() if not ok]
    empty = [l["id"] for l in ls if not l["audio"]["isolated"]]
    print(f"verified {len(remaining) - len(pending)} of {len(remaining)} clips; unreviewed: {len(pending)}; letters without audio: {empty}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply-review", metavar="REVIEW_JSON")
    a = ap.parse_args()
    apply_review(a.apply_review) if a.apply_review else build()
