package `in`.qaida.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import `in`.qaida.app.R

enum class ScriptFace { NASTALEEQ, NASKH }

val NastaleeqFamily = FontFamily(Font(R.font.indopak_nastaleeq))
val NaskhFamily = FontFamily(Font(R.font.indopak_naskh))

fun ScriptFace.family() = when (this) {
    ScriptFace.NASTALEEQ -> NastaleeqFamily
    ScriptFace.NASKH -> NaskhFamily
}

/**
 * All Arabic text goes through here: always right-to-left, never hyphenated, never upper-cased, and with
 * room above and below for marks so nothing clips.
 *
 * [compact] is for a single letter in a tile or grid cell: the Indo-Pak face reserves 2.2 em of line for
 * mushaf marks, which would push a lone glyph far below centre.
 */
@Composable
fun ArabicText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    face: ScriptFace = ScriptFace.NASTALEEQ,
    color: Color = LocalContentColor.current,
    textAlign: TextAlign = TextAlign.Center,
    compact: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontFamily = face.family(),
            fontSize = size,
            lineHeight = if (compact) 1.4.em else 1.9.em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            platformStyle = PlatformTextStyle(includeFontPadding = !compact),
            textDirection = TextDirection.Rtl,
            textAlign = textAlign,
            hyphens = Hyphens.None,
        ),
    )
}

/** A line of mushaf text: wraps, centred, with room for the marks above and below. */
@Composable
fun ArabicLine(text: String, face: ScriptFace, modifier: Modifier = Modifier, size: TextUnit = 34.sp) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = face.family(),
            fontSize = size,
            lineHeight = 2.1.em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            platformStyle = PlatformTextStyle(includeFontPadding = true),
            textDirection = TextDirection.Rtl,
            textAlign = TextAlign.Center,
            hyphens = Hyphens.None,
        ),
    )
}
