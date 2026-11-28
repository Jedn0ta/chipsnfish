package com.onoseto.chipsnfish.ui

import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onoseto.chipsnfish.R

/** Accent blue of the glass style. */
val LobbyBlue = Color(0xFF7FC4FF)
val GlassInk = Color.White
val GlassMuted = Color.White.copy(alpha = 0.62f)

/** The lobby picture (blurred) and where the app sits on screen, so glass panels can show it behind them. */
class Backdrop(val img: ImageBitmap) {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(): Backdrop {
    val img = ImageBitmap.imageResource(R.drawable.lobby_bg_blur)
    return remember(img) { Backdrop(img) }
}

/** Frosted glass: the blurred card room behind, a light sheen and a thin bright edge. */
fun Modifier.glass(shape: Shape, tint: Color = Color(0x2A2A5CA8), lift: Dp = 14.dp): Modifier = composed {
    val bd = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .shadow(lift, shape, ambientColor = Color(0xFF020814), spotColor = Color(0xFF000000))
        .clip(shape)
        .drawBehind {
            if (bd != null && bd.size.width > 0) {
                val iw = bd.img.width; val ih = bd.img.height
                val sc = kotlin.math.max(bd.size.width / iw.toFloat(), bd.size.height / ih.toFloat())
                val dw = iw * sc; val dh = ih * sc
                val ox = bd.origin.x + (bd.size.width - dw) / 2 - pos.x
                val oy = bd.origin.y + (bd.size.height - dh) / 2 - pos.y
                drawImage(bd.img, IntOffset.Zero, IntSize(iw, ih), IntOffset(ox.toInt(), oy.toInt()), IntSize(dw.toInt(), dh.toInt()))
            } else drawRect(Color(0xF00B1630))
            drawRect(tint)
            drawRect(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f), Color.White.copy(alpha = 0.07f)),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                ),
            )
        }
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f), Color(0x667FC4FF))), shape)
}

/** Dark blue suede with slowly drifting smoke. */
@Composable
fun LobbyBackground() {
    val tile = remember {
        // fine suede grain, tiled
        val n = 96
        val px = IntArray(n * n) { val v = (Math.random() * 255).toInt(); val a = (8 + Math.random() * 22).toInt(); (a shl 24) or (v shl 16) or (v shl 8) or v }
        android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val grain = remember(tile) { androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(tile, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)) }
    val t = rememberInfiniteTransition(label = "smoke").animateFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "s")
    val puffs = remember { List(7) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.35f + Math.random().toFloat() * 0.35f, Math.random().toFloat(), 0.05f + Math.random().toFloat() * 0.06f) } }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF16346F), Color(0xFF09183A), Color(0xFF030814)), Offset(size.width * 0.5f, size.height * 0.42f), size.width * 0.62f))
        // smoke: big soft drifting puffs
        puffs.forEach { p ->
            val ph = (t.value + p[3]) % 1f
            val a = ph * 6.283f
            val x = (p[0] + 0.12f * kotlin.math.sin(a) + 0.25f * ph) % 1.2f * size.width - size.width * 0.1f
            val y = (p[1] + 0.08f * kotlin.math.cos(a * 1.3f)) * size.height
            val r = p[2] * size.width * (0.9f + 0.15f * kotlin.math.sin(a * 2f))
            val alpha = p[4] * (0.6f + 0.4f * kotlin.math.sin(ph * 3.1416f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8DB8FF).copy(alpha = alpha), Color(0xFF8DB8FF).copy(alpha = alpha * 0.35f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        }
        drawRect(grain, alpha = 0.55f)
        deluxe(t.value)
        // vignette
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xB3020610)), Offset(size.width * 0.5f, size.height * 0.45f), size.width * 0.75f))
        // thin gold frame with art-deco corners, on top of everything
        decoFrame()
    }
}

private val GOLD_L = Color(0xFFF3D68A)

/** Casino-lounge extras: a warm spotlight, art-deco sunburst, faint embossed suits and twinkling gold glints. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.deluxe(t: Float) {
    val w = size.width; val h = size.height
    // warm spotlight from above
    drawOval(Brush.radialGradient(listOf(Color(0x33FFD98A), Color(0x14FFC85A), Color.Transparent), Offset(w * 0.5f, -h * 0.05f), w * 0.45f),
        topLeft = Offset(w * 0.1f, -h * 0.45f), size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 1.1f))
    // art-deco sunburst rising from the bottom centre
    val o = Offset(w * 0.5f, h * 1.08f)
    for (i in 0..36) {
        val a = Math.toRadians(180.0 + i * 5.0).toFloat()
        val len = h * 1.25f
        drawLine(GOLD_L.copy(alpha = if (i % 2 == 0) 0.05f else 0.025f), o, Offset(o.x + len * kotlin.math.cos(a), o.y + len * kotlin.math.sin(a)), strokeWidth = 1.2f * density)
    }
    for (k in 1..3) drawCircle(GOLD_L.copy(alpha = 0.05f), h * (0.42f + k * 0.16f), o, style = androidx.compose.ui.graphics.drawscope.Stroke(1f * density))
    // faint embossed suits in the corners
    val suits = listOf("♠", "♥", "♦", "♣")
    val pos = listOf(Offset(w * 0.07f, h * 0.3f), Offset(w * 0.93f, h * 0.3f), Offset(w * 0.07f, h * 0.82f), Offset(w * 0.93f, h * 0.82f))
    val tp = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = h * 0.16f; color = android.graphics.Color.argb(14, 255, 230, 170) }
    drawIntoCanvas { c -> pos.forEachIndexed { i, p -> c.nativeCanvas.drawText(suits[i], p.x, p.y + tp.textSize * 0.35f, tp) } }
    // twinkling gold glints
    for (i in 0 until 26) {
        val sx = ((i * 0.6180339f) % 1f) * w
        val sy = ((i * 0.3819660f + 0.13f) % 1f) * h
        val tw = kotlin.math.sin((t * 40f + i * 0.77f) * 6.283f).coerceAtLeast(0f)
        if (tw < 0.05f) continue
        val r = (1.2f + (i % 3)) * density * tw
        drawCircle(GOLD_L.copy(alpha = 0.55f * tw), r, Offset(sx, sy))
        drawLine(GOLD_L.copy(alpha = 0.35f * tw), Offset(sx - r * 3, sy), Offset(sx + r * 3, sy), strokeWidth = 0.8f * density)
        drawLine(GOLD_L.copy(alpha = 0.35f * tw), Offset(sx, sy - r * 3), Offset(sx, sy + r * 3), strokeWidth = 0.8f * density)
    }
}

/** Double thin gold frame with stepped art-deco corners. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.decoFrame() {
    val w = size.width; val h = size.height; val d = density
    val i1 = 6f * d; val i2 = 10f * d; val c = 26f * d
    val st1 = androidx.compose.ui.graphics.drawscope.Stroke(1.2f * d); val st2 = androidx.compose.ui.graphics.drawscope.Stroke(0.7f * d)
    drawRect(GOLD_L.copy(alpha = 0.35f), Offset(i1, i1), androidx.compose.ui.geometry.Size(w - 2 * i1, h - 2 * i1), style = st1)
    drawRect(GOLD_L.copy(alpha = 0.18f), Offset(i2, i2), androidx.compose.ui.geometry.Size(w - 2 * i2, h - 2 * i2), style = st2)
    listOf(Offset(i2, i2) to Offset(1f, 1f), Offset(w - i2, i2) to Offset(-1f, 1f), Offset(i2, h - i2) to Offset(1f, -1f), Offset(w - i2, h - i2) to Offset(-1f, -1f)).forEach { (p, dir) ->
        for (k in 1..3) {
            val q = c * k / 3f
            drawLine(GOLD_L.copy(alpha = 0.4f), Offset(p.x + dir.x * q, p.y), Offset(p.x + dir.x * q, p.y + dir.y * (c - q + 4f * d)), strokeWidth = 0.9f * d)
            drawLine(GOLD_L.copy(alpha = 0.4f), Offset(p.x, p.y + dir.y * q), Offset(p.x + dir.x * (c - q + 4f * d), p.y + dir.y * q), strokeWidth = 0.9f * d)
        }
        val m = Offset(p.x + dir.x * 5f * d, p.y + dir.y * 5f * d)
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(m.x, m.y - 4f * d); lineTo(m.x + 4f * d, m.y); lineTo(m.x, m.y + 4f * d); lineTo(m.x - 4f * d, m.y); close() }, GOLD_L.copy(alpha = 0.7f))
    }
}

/** Slowly drifting cool light specks. */
@Composable
fun LobbyBokeh() {
    val t = rememberInfiniteTransition(label = "bokeh").animateFloat(0f, 1f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "b")
    val dots = remember { List(16) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat() * 0.6f, 4f + Math.random().toFloat() * 10f, Math.random().toFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        dots.forEach { d ->
            val ph = (t.value + d[3]) % 1f
            val x = (d[0] + ph * 0.08f) % 1f * size.width
            val y = d[1] * size.height + kotlin.math.sin(ph * 6.283f) * 8f
            val a = 0.04f + 0.09f * kotlin.math.sin(ph * 3.1416f)
            drawCircle(Color(0xFF9ED2FF).copy(alpha = a), d[2] * density, Offset(x, y))
        }
    }
}

/** Little things happening on the lobby table: a drink put on the rail, a chip tossed, a card dealt. */
@Composable
fun LobbyLife() {
    class Ev(val id: Long, val kind: Int, val x: Float, val y: Float, val icon: String)
    val evs = remember { androidx.compose.runtime.mutableStateListOf<Ev>() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var n = 0L
        kotlinx.coroutines.delay(1800)
        while (true) {
            val k = listOf(0, 0, 1, 1, 2).random()
            val e = when (k) {
                // drink on the far rail (visible between the top bar and the panels)
                0 -> Ev(n++, 0, 300f + Math.random().toFloat() * 200f, 64f, listOf("🥃", "🍸", "🍺", "🍷", "☕", "🥂").random())
                // chip tossed onto the felt
                1 -> Ev(n++, 1, if (Math.random() < 0.5) 268f + Math.random().toFloat() * 30f else 500f + Math.random().toFloat() * 50f, 300f + Math.random().toFloat() * 30f, "")
                // card dealt from the dealer's side
                else -> Ev(n++, 2, if (Math.random() < 0.5) 275f else 515f, 290f + Math.random().toFloat() * 30f, "")
            }
            evs.add(e)
            if (evs.size > 5) evs.removeAt(0)
            kotlinx.coroutines.delay(4500L + (Math.random() * 4500).toLong())
        }
    }
    Box(Modifier.fillMaxSize()) {
        evs.toList().forEach { e ->
            androidx.compose.runtime.key(e.id) {
                val t = remember { androidx.compose.animation.core.Animatable(0f) }
                val fade = remember { androidx.compose.animation.core.Animatable(1f) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    t.animateTo(1f, androidx.compose.animation.core.tween(if (e.kind == 0) 1400 else 900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    kotlinx.coroutines.delay(if (e.kind == 0) 9000 else 6000)
                    fade.animateTo(0f, androidx.compose.animation.core.tween(1200))
                    evs.remove(e)
                }
                val v = t.value
                when (e.kind) {
                    0 -> {   // slides in from the side along the rail
                        val from = if (e.x < 400f) -40f else 840f
                        val x = from + (e.x - from) * v
                        Box(Modifier.offset((x - 14).dp, (e.y - 16).dp).size(28.dp).graphicsLayer(alpha = fade.value), contentAlignment = Alignment.Center) {
                            Box(Modifier.offset(y = 9.dp).size(20.dp, 6.dp).background(Brush.radialGradient(listOf(Color(0x99000000), Color.Transparent)), CircleShape))
                            T(e.icon, 19.sp, lineHeight = 22.sp)
                        }
                    }
                    1 -> {   // chip flies in from below with a little arc and spin
                        val x = e.x + (1 - v) * 40f
                        val y = 380f + (e.y - 380f) * v - kotlin.math.sin(v * 3.1416f) * 60f
                        Box(Modifier.offset((x - 9).dp, (y - 9).dp).graphicsLayer(alpha = fade.value, rotationX = (1 - v) * 540f, scaleX = 0.8f + 0.2f * v, scaleY = 0.8f + 0.2f * v)) {
                            ChipDisc(listOf(C.gold, Color(0xFF2F7BFF), Color(0xFFC62828), Color(0xFF1B1B1B))[(e.id % 4).toInt()], 18.dp)
                        }
                    }
                    else -> { // card slides from the top centre
                        val x = 400f + (e.x - 400f) * v
                        val y = 40f + (e.y - 40f) * v
                        Box(Modifier.offset((x - 11).dp, (y - 15).dp).graphicsLayer(alpha = fade.value, rotationZ = (1 - v) * 220f + (if (e.x < 400f) -12f else 12f) * v)) {
                            CardBack(22.dp)
                        }
                    }
                }
            }
        }
    }
}
