#!/usr/bin/env python3
"""Convert a (variable) CFF/CFF2 OpenType font to a static TrueType font for res/font.

Android 7 (API 24-25) cannot load CFF2 outlines, and res/font needs a file every supported
release can draw. Variation axes are pinned at their defaults, cubic curves are converted to
quadratics with cu2qu, and GSUB/GPOS/GDEF are kept untouched so shaping is unchanged.

Usage: python3 -I tools/otf_to_ttf.py <input.otf> <output.ttf>
"""
import sys

from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont, newTable
from fontTools.varLib import instancer

# Max curve approximation error in font units (UPM 1000): invisible at any on-screen size.
MAX_ERR = 0.5


def main(src: str, dst: str) -> None:
    font = TTFont(src)
    cff_tag = "CFF2" if "CFF2" in font else "CFF "
    if cff_tag not in font:
        sys.exit("no CFF outlines; nothing to convert")

    glyph_set = font.getGlyphSet()
    glyf = newTable("glyf")
    glyf.glyphOrder = font.getGlyphOrder()
    glyf.glyphs = {}
    for name in glyf.glyphOrder:
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(pen, MAX_ERR, reverse_direction=True))
        glyf.glyphs[name] = pen.glyph()

    del font[cff_tag]
    for t in ("VORG", "DSIG"):
        if t in font:
            del font[t]
    font["glyf"] = glyf
    font["loca"] = newTable("loca")
    font["maxp"].tableVersion = 0x00010000
    for attr in ("maxZones", "maxTwilightPoints", "maxStorage", "maxFunctionDefs", "maxInstructionDefs",
                 "maxStackElements", "maxSizeOfInstructions", "maxComponentElements"):
        setattr(font["maxp"], attr, 0)
    font["maxp"].maxZones = 1
    font["head"].glyphDataFormat = 0
    font["post"].formatType = 2.0
    font["post"].extraNames = []
    font["post"].mapping = {}
    font.sfntVersion = "\x00\x01\x00\x00"

    # Outlines were drawn at the default location above; now flatten the remaining variation
    # data (GDEF/GPOS anchors, HVAR metrics) to that same default. The instancer cannot do the
    # CFF2 outlines of some fonts itself, which is why it runs after the glyf conversion.
    if "fvar" in font:
        font = instancer.instantiateVariableFont(font, {a.axisTag: a.defaultValue for a in font["fvar"].axes})
    font.save(dst)

    check = TTFont(dst)
    print(f"wrote {dst}: {len(check.getGlyphOrder())} glyphs, tables {' '.join(sorted(check.keys()))}")


if __name__ == "__main__":
    main(*sys.argv[1:3])
