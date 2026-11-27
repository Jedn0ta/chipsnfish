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
 * Classic playing-card artwork drawn in code: standard pip layouts and original
 * double-headed court figures in the traditional red / blue / gold / black palette.
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

    // pip positions: x in {0, .5, 1}, y 0..1 (pips below the middle are drawn upside down)
    private val PIPS: Array<List<Pair<Float, Float>>> = arrayOf(
        listOf(.5f to 0f, .5f to 1f),
        listOf(.5f to 0f, .5f to .5f, .5f to 1f),
        listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f),
        listOf(0f to 0f, 1f to 0f, .5f to .5f, 0f to 1f, 1f to 1f),
        listOf(0f to 0f, 1f to 0f, 0f to .5f, 1f to .5f, 0f to 1f, 1f to 1f),
        listOf(0f to 0f, 1f to 0f, .5f to .25f, 0f to .5f, 1f to .5f, 0f to 1f, 1f to 1f),
        listOf(0f to 0f, 1f to 0f, .5f to .25f, 0f to .5f, 1f to .5f, .5f to .75f, 0f to 1f, 1f to 1f),
        listOf(0f to 0f, 1f to 0f, 0f to 1 / 3f, 1f to 1 / 3f, .5f to .5f, 0f to 2 / 3f, 1f to 2 / 3f, 0f to 1f, 1f to 1f),
        listOf(0f to 0f, 1f to 0f, .5f to 1 / 6f, 0f to 1 / 3f, 1f to 1 / 3f, 0f to 2 / 3f, 1f to 2 / 3f, .5f to 5 / 6f, 0f to 1f, 1f to 1f),
    )

    /** Centre artwork of a card face (the corner indices are drawn by CardView). */
    fun DrawScope.face(r: Int, s: Int, col: Color) {
        val w = size.width; val h = size.height
        when {
            r == 12 -> {
                val big = if (s == 0) w * 0.5f else w * 0.36f
                if (s == 0) {
                    drawCircle(col.copy(alpha = 0.12f), w * 0.3f, center)
                    drawCircle(col, w * 0.3f, center, style = Stroke(w * 0.012f))
                    drawCircle(GOLD, w * 0.27f, center, style = Stroke(w * 0.01f))
                }
                suit(s, center, big, col)
            }
            r <= 8 -> {
                val pip = w * 0.19f
                val x0 = w * 0.33f; val x1 = w * 0.67f
                val y0 = h * 0.17f; val y1 = h * 0.83f
                PIPS[r].forEach { (px, py) ->
                    suit(s, Offset(x0 + (x1 - x0) * px, y0 + (y1 - y0) * py), pip, col, flip = py > 0.5f)
                }
            }
            else -> court(r, s, col)
        }
    }

    /** Double-headed court card: the top half is drawn, then repeated rotated by 180°. */
    private fun DrawScope.court(r: Int, s: Int, col: Color) {
        val w = size.width; val h = size.height
        val fr = Rect(w * 0.2f, h * 0.09f, w * 0.8f, h * 0.91f)
        val frameCol = if (s == 1 || s == 2) RED else BLUE
        drawRect(CREAM, fr.topLeft, fr.size)
        val tilt = fr.height * 0.05f
        val half = Path().apply {
            moveTo(fr.left, fr.top); lineTo(fr.right, fr.top); lineTo(fr.right, fr.center.y - tilt); lineTo(fr.left, fr.center.y + tilt); close()
        }
        for (k in 0..1) {
            rotate(k * 180f, fr.center) {
                clipPath(half) { translate(fr.left, fr.top) { figure(r, s, Size(fr.width, fr.height / 2f), col) } }
            }
        }
        drawLine(frameCol, Offset(fr.left, fr.center.y + tilt), Offset(fr.right, fr.center.y - tilt), strokeWidth = w * 0.012f)
        drawRect(frameCol, fr.topLeft, fr.size, style = Stroke(w * 0.016f))
    }

    private fun DrawScope.figure(r: Int, s: Int, b: Size, col: Color) {
        val u = b.width
        val hy = b.height * 0.36f              // head centre
        val hx = u * 0.5f
        val hr = u * 0.135f
        val robeA = if (s == 1 || s == 2) RED else BLUE
        val robeB = if (s == 1 || s == 2) BLUE else RED

        // attribute held behind the figure: sword (K), flower (Q), halberd (J)
        val ax = u * 0.18f
        when (r) {
            11 -> {
                drawLine(Color(0xFF9AA3AD), Offset(ax, b.height * 1.1f), Offset(ax, b.height * 0.1f), strokeWidth = u * 0.035f)
                drawLine(GOLD, Offset(ax - u * 0.07f, b.height * 0.62f), Offset(ax + u * 0.07f, b.height * 0.62f), strokeWidth = u * 0.03f)
            }
            10 -> {
                drawLine(Color(0xFF2E7D32), Offset(ax, b.height * 1.1f), Offset(ax, b.height * 0.32f), strokeWidth = u * 0.022f)
                for (i in 0 until 6) {
                    val a = Math.toRadians(i * 60.0)
                    drawCircle(RED, u * 0.04f, Offset(ax + (u * 0.05f * Math.cos(a)).toFloat(), b.height * 0.28f + (u * 0.05f * Math.sin(a)).toFloat()))
                }
                drawCircle(GOLD, u * 0.035f, Offset(ax, b.height * 0.28f))
            }
            else -> {
                drawLine(Color(0xFF7A5230), Offset(ax, b.height * 1.1f), Offset(ax, b.height * 0.14f), strokeWidth = u * 0.03f)
                val tip = Path().apply { moveTo(ax, b.height * 0.02f); lineTo(ax + u * 0.05f, b.height * 0.16f); lineTo(ax - u * 0.05f, b.height * 0.16f); close() }
                drawPath(tip, Color(0xFF9AA3AD))
            }
        }

        // robe: shoulders down to the split, two-colour with gold trims
        val top = hy + hr * 1.15f
        val robe = Path().apply {
            moveTo(hx - u * 0.18f, top); lineTo(hx + u * 0.18f, top)
            cubicTo(hx + u * 0.38f, top + u * 0.03f, u * 0.98f, b.height * 0.8f, u * 0.98f, b.height * 1.1f)
            lineTo(u * 0.02f, b.height * 1.1f)
            cubicTo(u * 0.02f, b.height * 0.8f, hx - u * 0.38f, top + u * 0.03f, hx - u * 0.18f, top)
            close()
        }
        drawPath(robe, robeA)
        clipPath(robe) {
            drawRect(robeB, Offset(hx, top), Size(u, b.height))
            // gold bands with dots
            var y = top + u * 0.1f
            while (y < b.height * 1.1f) {
                drawLine(GOLD, Offset(0f, y), Offset(u, y), strokeWidth = u * 0.035f)
                var x = u * 0.06f
                while (x < u) { drawCircle(INK, u * 0.009f, Offset(x, y)); x += u * 0.08f }
                y += u * 0.16f
            }
            drawLine(GOLD, Offset(hx, top), Offset(hx, b.height * 1.1f), strokeWidth = u * 0.05f)
            drawLine(INK, Offset(hx, top), Offset(hx, b.height * 1.1f), strokeWidth = u * 0.01f)
        }
        drawPath(robe, INK, style = Stroke(u * 0.012f))

        // suit emblem on the chest (opposite to the held attribute)
        suit(s, Offset(u * 0.77f, b.height * 0.72f), u * 0.15f, col)

        // hair / beard
        when (r) {
            11 -> {
                val beard = Path().apply { moveTo(hx - hr * 0.95f, hy); quadraticTo(hx, hy + hr * 2.4f, hx + hr * 0.95f, hy); close() }
                drawPath(beard, Color(0xFFE8C46A)); drawPath(beard, INK, style = Stroke(u * 0.008f))
            }
            10 -> {
                drawRoundRect(Color(0xFFE8C46A), Offset(hx - hr * 1.3f, hy - hr * 0.6f), Size(hr * 2.6f, hr * 2.3f), androidx.compose.ui.geometry.CornerRadius(hr))
            }
            else -> drawCircle(Color(0xFFB5712E), hr * 1.12f, Offset(hx, hy - hr * 0.1f))
        }
        // face
        drawOval(SKIN, Offset(hx - hr * 0.85f, hy - hr), Size(hr * 1.7f, hr * 2f))
        drawOval(INK, Offset(hx - hr * 0.85f, hy - hr), Size(hr * 1.7f, hr * 2f), style = Stroke(u * 0.008f))
        drawCircle(INK, hr * 0.11f, Offset(hx - hr * 0.33f, hy - hr * 0.1f))
        drawCircle(INK, hr * 0.11f, Offset(hx + hr * 0.33f, hy - hr * 0.1f))
        drawLine(RED, Offset(hx - hr * 0.22f, hy + hr * 0.5f), Offset(hx + hr * 0.22f, hy + hr * 0.5f), strokeWidth = hr * 0.14f)
        if (r == 11) drawLine(INK, Offset(hx - hr * 0.4f, hy + hr * 0.3f), Offset(hx + hr * 0.4f, hy + hr * 0.3f), strokeWidth = hr * 0.12f)

        // headwear
        val cb = hy - hr * 0.85f
        when (r) {
            11 -> {
                val crown = Path().apply {
                    moveTo(hx - hr, cb); lineTo(hx - hr * 1.05f, cb - hr * 1.2f); lineTo(hx - hr * 0.5f, cb - hr * 0.6f)
                    lineTo(hx, cb - hr * 1.4f); lineTo(hx + hr * 0.5f, cb - hr * 0.6f); lineTo(hx + hr * 1.05f, cb - hr * 1.2f); lineTo(hx + hr, cb); close()
                }
                drawPath(crown, GOLD); drawPath(crown, INK, style = Stroke(u * 0.008f))
                listOf(-1.05f, 0f, 1.05f).forEach { drawCircle(RED, hr * 0.16f, Offset(hx + hr * it, cb - hr * (if (it == 0f) 1.4f else 1.2f))) }
            }
            10 -> {
                val crown = Path().apply {
                    moveTo(hx - hr * 0.95f, cb); quadraticTo(hx - hr * 0.6f, cb - hr * 1.1f, hx, cb - hr * 0.7f)
                    quadraticTo(hx + hr * 0.6f, cb - hr * 1.1f, hx + hr * 0.95f, cb); close()
                }
                drawPath(crown, GOLD); drawPath(crown, INK, style = Stroke(u * 0.008f))
                drawCircle(BLUE, hr * 0.17f, Offset(hx, cb - hr * 0.35f))
                drawCircle(CREAM, hr * 0.13f, Offset(hx, cb - hr * 0.95f))
            }
            else -> {
                drawOval(robeB, Offset(hx - hr * 1.15f, cb - hr * 0.55f), Size(hr * 2.3f, hr * 0.8f))
                drawOval(INK, Offset(hx - hr * 1.15f, cb - hr * 0.55f), Size(hr * 2.3f, hr * 0.8f), style = Stroke(u * 0.008f))
                val feather = Path().apply { moveTo(hx + hr * 0.6f, cb - hr * 0.3f); quadraticTo(hx + hr * 1.9f, cb - hr * 1.6f, hx + hr * 2.1f, cb - hr * 0.4f); quadraticTo(hx + hr * 1.5f, cb - hr * 0.6f, hx + hr * 0.6f, cb - hr * 0.3f); close() }
                drawPath(feather, GOLD)
            }
        }
        // collar
        drawLine(GOLD, Offset(hx - u * 0.2f, top + u * 0.02f), Offset(hx + u * 0.2f, top + u * 0.02f), strokeWidth = u * 0.06f)
        var x = hx - u * 0.17f
        while (x <= hx + u * 0.18f) { drawCircle(INK, u * 0.012f, Offset(x, top + u * 0.02f)); x += u * 0.07f }
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
