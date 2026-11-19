package `in`.qaida.app.ui.fonttest

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.qaida.app.R

private val SIZES_SP = listOf(48, 32, 20)
private val FACES = listOf(
    "indopak_nastaleeq" to R.string.fonttest_face_nastaleeq,
    "indopak_naskh" to R.string.fonttest_face_naskh,
)

/**
 * Milestone 0 throwaway: renders the supplied Indo-Pak sample strings in both faces at
 * 48/32/20 sp so shaping can be checked on a low-end device. Delete once the gate passes.
 */
@Composable
fun FontTestScreen() {
    val context = LocalContext.current
    val samples = remember {
        context.assets.open("fonttest/samples.txt").bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for ((fontName, label) in FACES) {
            Text(stringResource(label), color = Color(0xFFB0B0B0), fontSize = 14.sp)
            val family = rememberFontFamily(fontName)
            if (family == null) {
                Text(stringResource(R.string.fonttest_font_missing, fontName), color = Color(0xFFE0A050))
            } else {
                for (size in SIZES_SP) {
                    Text(stringResource(R.string.fonttest_size, size), color = Color(0xFF808080), fontSize = 12.sp)
                    for (sample in samples) {
                        Text(
                            text = sample,
                            color = Color(0xFFEDEDED),
                            modifier = Modifier.fillMaxWidth(),
                            style = arabicStyle(family, size),
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF2A2A2A))
        }
    }
}

private fun arabicStyle(family: FontFamily, sizeSp: Int) = TextStyle(
    fontFamily = family,
    fontSize = sizeSp.sp,
    // Nastaliq stacks vertically; give it room and never let marks clip.
    lineHeight = (sizeSp * 2.0).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    textDirection = TextDirection.Rtl,
    textAlign = TextAlign.Start,
    hyphens = Hyphens.None,
)

// Looked up by name so the project builds before the converted fonts are dropped into res/font.
@SuppressLint("DiscouragedApi")
@Composable
private fun rememberFontFamily(name: String): FontFamily? {
    val context = LocalContext.current
    return remember(name) {
        val id = context.resources.getIdentifier(name, "font", context.packageName)
        if (id == 0) null else FontFamily(Font(id))
    }
}
