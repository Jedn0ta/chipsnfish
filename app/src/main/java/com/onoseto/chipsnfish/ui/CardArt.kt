package com.onoseto.chipsnfish.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * Card backs and suit shapes drawn in code (faces use the bitmap artwork in CardImg).
 */
object CardArt {
    private val RED = Color(0xFFC0222E)
    private val BLUE = Color(0xFF1F3E8C)
    private val GOLD = Color(0xFFE6B437)
    private val INK = Color(0xFF16161A)
    private val SKIN = Color(0xFFF9DCC0)
    private val CREAM = Color(0xFFFFFBF2)

    /** Suit shape in a unit box centred on 0,0 (height 1). 0 ♠, 1 ♥, 2 ♦, 3 ♣. */
    private val suitPaths: Array<Path> = Array(4) { s ->
        Path().apply {
            when (s) {
                1 -> {
                    moveTo(0f, 0.48f)
                    cubicTo(-0.25f, 0.25f, -0.52f, 0.05f, -0.5f, -0.2f)
                    cubicTo(-0.48f, -0.46f, -0.12f, -0.53f, 0f, -0.27f)
                    cubicTo(0.12f, -0.53f, 0.48f, -0.46f, 0.5f, -0.2f)
                    cubicTo(0.52f, 0.05f, 0.25f, 0.25f, 0f, 0.48f)
                    close()
                }
                2 -> { moveTo(0f, -0.5f); quadraticTo(0.18f, -0.2f, 0.38f, 0f); quadraticTo(0.18f, 0.2f, 0f, 0.5f); quadraticTo(-0.18f, 0.2f, -0.38f, 0f); quadraticTo(-0.18f, -0.2f, 0f, -0.5f); close() }
                0 -> {
                    moveTo(0f, -0.5f)
                    cubicTo(-0.25f, -0.26f, -0.52f, -0.1f, -0.5f, 0.12f)
                    cubicTo(-0.48f, 0.36f, -0.15f, 0.4f, -0.04f, 0.2f)
                    quadraticTo(-0.06f, 0.42f, -0.17f, 0.5f)
                    lineTo(0.17f, 0.5f)
                    quadraticTo(0.06f, 0.42f, 0.04f, 0.2f)
                    cubicTo(0.15f, 0.4f, 0.48f, 0.36f, 0.5f, 0.12f)
                    cubicTo(0.52f, -0.1f, 0.25f, -0.26f, 0f, -0.5f)
                    close()
                }
                else -> {
                    addOval(Rect(Offset(0f, -0.25f), 0.215f))
                    addOval(Rect(Offset(-0.25f, 0.07f), 0.215f))
                    addOval(Rect(Offset(0.25f, 0.07f), 0.215f))
                    moveTo(-0.12f, -0.05f); lineTo(0.12f, -0.05f); lineTo(0.12f, 0.12f); lineTo(-0.12f, 0.12f); close()
                    moveTo(-0.04f, 0.1f); quadraticTo(-0.06f, 0.42f, -0.17f, 0.5f); lineTo(0.17f, 0.5f); quadraticTo(0.06f, 0.42f, 0.04f, 0.1f); close()
                }
            }
        }
    }

    fun DrawScope.suit(s: Int, c: Offset, h: Float, col: Color, flip: Boolean = false) {
        withTransform({
            translate(c.x, c.y)
            if (flip) rotate(180f, Offset.Zero)
            scale(h, h, Offset.Zero)
        }) { drawPath(suitPaths[s], col) }
    }

    /** Ornamental card back. Every size is proportional so it reads the same on the table and in the large preview. */
    fun DrawScope.back(a: Color, b: Color, trim: Color) {
        val w = size.width; val h = size.height
        drawRect(Brush.radialGradient(listOf(a, b), center, size.maxDimension * 0.7f))
        // fine lattice
        val step = w / 11f
        val line = Color.White.copy(alpha = 0.13f)
        val sw = maxOf(0.7f, w * 0.008f)
        var x = -h
        while (x < w + h) {
            drawLine(line, Offset(x, h), Offset(x + h, 0f), strokeWidth = sw)
            drawLine(line, Offset(x, 0f), Offset(x + h, h), strokeWidth = sw)
            x += step
        }
        // small dots in the lattice cells
        var yy = step / 2f
        var row = 0
        while (yy < h) {
            var xx = if (row % 2 == 0) step / 2f else step
            while (xx < w) { drawCircle(Color.White.copy(alpha = 0.16f), w * 0.012f, Offset(xx, yy)); xx += step }
            yy += step / 2f; row++
        }
        // double frame
        val i1 = w * 0.08f; val i2 = w * 0.13f
        drawRect(trim.copy(alpha = 0.9f), Offset(i1, i1), Size(w - i1 * 2, h - i1 * 2), style = Stroke(maxOf(1f, w * 0.022f)))
        drawRect(trim.copy(alpha = 0.55f), Offset(i2, i2), Size(w - i2 * 2, h - i2 * 2), style = Stroke(maxOf(0.6f, w * 0.01f)))
        // corner diamonds
        listOf(Offset(i1, i1), Offset(w - i1, i1), Offset(i1, h - i1), Offset(w - i1, h - i1)).forEach { c ->
            val d = w * 0.05f
            drawPath(Path().apply { moveTo(c.x, c.y - d); lineTo(c.x + d, c.y); lineTo(c.x, c.y + d); lineTo(c.x - d, c.y); close() }, trim)
        }
        // central rosette medallion
        val r = w * 0.26f
        drawCircle(b, r, center)
        drawCircle(trim, r, center, style = Stroke(maxOf(1f, w * 0.02f)))
        for (k in 0 until 8) {
            rotate(k * 45f, center) {
                val p = Path().apply {
                    moveTo(center.x, center.y)
                    quadraticTo(center.x + r * 0.32f, center.y - r * 0.45f, center.x, center.y - r * 0.85f)
                    quadraticTo(center.x - r * 0.32f, center.y - r * 0.45f, center.x, center.y)
                    close()
                }
                drawPath(p, trim.copy(alpha = if (k % 2 == 0) 0.95f else 0.6f))
            }
        }
        drawCircle(a, r * 0.2f, center)
        drawCircle(trim, r * 0.2f, center, style = Stroke(maxOf(0.6f, w * 0.01f)))
    }
}
