package `in`.qaida.app.ui.common

import android.content.Context
import android.util.Xml
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import org.xmlpull.v1.XmlPullParser

/**
 * A small SVG subset for makhraj diagrams: path, rect, circle, ellipse, line, polyline and polygon with
 * fill, stroke and stroke-width, scaled from the viewBox. Black and currentColor follow the theme's text
 * colour so diagrams read in dark mode; anything unsupported is skipped rather than crashing.
 */
data class SvgShape(val path: Path, val fill: Color?, val stroke: Color?, val strokeWidth: Float)

data class SvgImage(val width: Float, val height: Float, val shapes: List<SvgShape>)

object SvgParser {
    /** Marker colour meaning "use the theme's content colour". */
    val CURRENT = Color(0x01000000)

    fun parse(xml: String): SvgImage {
        val p = Xml.newPullParser()
        p.setInput(xml.reader())
        var w = 100f
        var h = 100f
        val shapes = mutableListOf<SvgShape>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            fun a(name: String) = p.getAttributeValue(null, name)
            fun f(name: String) = a(name)?.removeSuffix("px")?.toFloatOrNull() ?: 0f
            val path = Path()
            when (p.name) {
                "svg" -> {
                    a("viewBox")?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }
                        ?.let { w = it[2]; h = it[3] }
                    continue
                }
                "path" -> PathParser().parsePathString(a("d") ?: continue).toPath(path)
                "rect" -> path.addRect(androidx.compose.ui.geometry.Rect(f("x"), f("y"), f("x") + f("width"), f("y") + f("height")))
                "circle" -> path.addOval(androidx.compose.ui.geometry.Rect(Offset(f("cx"), f("cy")), f("r")))
                "ellipse" -> path.addOval(androidx.compose.ui.geometry.Rect(f("cx") - f("rx"), f("cy") - f("ry"), f("cx") + f("rx"), f("cy") + f("ry")))
                "line" -> { path.moveTo(f("x1"), f("y1")); path.lineTo(f("x2"), f("y2")) }
                "polyline", "polygon" -> {
                    val pts = a("points")?.trim()?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }?.chunked(2) ?: continue
                    pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    if (p.name == "polygon") path.close()
                }
                else -> continue
            }
            val isLine = p.name == "line" || p.name == "polyline"
            val fill = colour(a("fill"), default = if (isLine) null else CURRENT)
            val stroke = colour(a("stroke"), default = null)
            shapes += SvgShape(path, fill, stroke, a("stroke-width")?.toFloatOrNull() ?: 1f)
        }
        return SvgImage(w, h, shapes)
    }

    private fun colour(v: String?, default: Color?): Color? = when {
        v == null -> default
        v == "none" -> null
        v == "currentColor" || v == "#000" || v == "#000000" || v == "black" -> CURRENT
        v.startsWith("#") && v.length == 7 -> Color(0xFF000000 or v.substring(1).toLong(16))
        v.startsWith("#") && v.length == 4 -> Color(0xFF000000 or v.substring(1).map { "$it$it" }.joinToString("").toLong(16))
        else -> default
    }
}

@Composable
fun SvgDiagram(assetPath: String, modifier: Modifier = Modifier) {
    val context: Context = LocalContext.current
    val image = remember(assetPath) {
        runCatching { SvgParser.parse(context.assets.open(assetPath).bufferedReader().use { it.readText() }) }.getOrNull()
    } ?: return
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val s = minOf(size.width / image.width, size.height / image.height)
        translate((size.width - image.width * s) / 2, (size.height - image.height * s) / 2) {
            scale(s, s, pivot = Offset.Zero) {
                for (shape in image.shapes) {
                    shape.fill?.let { drawPath(shape.path, if (it == SvgParser.CURRENT) ink else it) }
                    shape.stroke?.let { drawPath(shape.path, if (it == SvgParser.CURRENT) ink else it, style = Stroke(shape.strokeWidth)) }
                }
            }
        }
    }
}
