package com.onoseto.chipsnfish.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Main PLAY button: two aces held in a fan, wrapped in crackling electric magic; a tap makes it flare. */
@Composable
fun PlayCards(title: String, sub: String, tilt: Offset, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val inf = rememberInfiniteTransition(label = "magic")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t")
    var seed by remember { mutableIntStateOf(1) }
    val flare = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    // lightning flickers: new bolts several times a second
    LaunchedEffect(Unit) { while (true) { delay(70L + Random.nextLong(90)); seed++ } }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(170.dp).clickable(src, null) {
                if (busy) return@clickable
                busy = true
                scope.launch {
                    flare.snapTo(0f); flare.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                    onClick(); flare.snapTo(0f); busy = false
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            val f = flare.value
            // aura behind
            Canvas(Modifier.fillMaxSize()) {
                val pulse = 0.55f + 0.2f * sin(t * 2f * PI.toFloat() * 2f) + 0.4f * f
                drawCircle(Brush.radialGradient(listOf(Color(0xFF6FD3FF).copy(alpha = 0.45f * pulse), Color(0xFF7B4DFF).copy(alpha = 0.22f * pulse), Color.Transparent), center, size.minDimension * 0.5f), size.minDimension * 0.5f)
            }
            // the two aces
            val dn = androidx.compose.ui.platform.LocalDensity.current.density
            val press = if (pressed) 0.94f else 1f
            val spread = 1f + 0.35f * sin(f * PI.toFloat())
            Box(Modifier.graphicsLayer(scaleX = press, scaleY = press, rotationY = tilt.x * 10f, rotationX = -tilt.y * 8f, cameraDistance = 12f * dn)) {
                CardView(48, 62.dp, "picture", glow = true, modifier = Modifier.graphicsLayer(rotationZ = -13f * spread, translationX = -22f * spread * dn, translationY = 4f * dn))
                CardView(49, 62.dp, "picture", glow = true, modifier = Modifier.graphicsLayer(rotationZ = 11f * spread, translationX = 22f * spread * dn))
            }
            // electricity and sparks in front
            Canvas(Modifier.fillMaxSize()) {
                val rnd = Random(seed)
                val c = center
                val rIn = size.minDimension * 0.27f
                val rOut = size.minDimension * (0.44f + 0.05f * f)
                val bolts = 3 + (f * 4).toInt()
                repeat(bolts) {
                    val a = rnd.nextFloat() * 2f * PI.toFloat()
                    val a2 = a + (rnd.nextFloat() - 0.5f) * 0.9f
                    val p0 = Offset(c.x + cos(a) * rIn * (0.8f + 0.3f * rnd.nextFloat()), c.y + sin(a) * rIn * 1.15f)
                    val p1 = Offset(c.x + cos(a2) * rOut, c.y + sin(a2) * rOut)
                    bolt(p0, p1, rnd, 0.55f + 0.45f * f)
                }
                // an arc jumping between the two cards
                if (rnd.nextFloat() < 0.6f + 0.4f * f) bolt(Offset(c.x - 30f * density, c.y - 34f * density), Offset(c.x + 30f * density, c.y - 38f * density), rnd, 0.7f)
                // orbiting sparks
                for (i in 0 until 14) {
                    val ph = (t * (1f + i % 3 * 0.4f) + i / 14f) * 2f * PI.toFloat()
                    val rr = rOut * (0.78f + 0.18f * sin(ph * 2f + i))
                    val p = Offset(c.x + cos(ph) * rr, c.y + sin(ph) * rr * 0.9f)
                    val tail = Offset(c.x + cos(ph - 0.18f) * rr, c.y + sin(ph - 0.18f) * rr * 0.9f)
                    drawLine(Color(0xFF9EE6FF).copy(alpha = 0.35f), tail, p, strokeWidth = 2f * density, cap = StrokeCap.Round)
                    drawCircle(Color.White, (1.3f + (i % 3)) * density * 0.6f, p)
                }
            }
        }
        T(title, 22.sp, Color.White, num = true, letter = 3.sp, maxLines = 1, lineHeight = 24.sp)
        T(sub, 11.5.sp, LobbyBlue, FontWeight.ExtraBold, letter = 1.5.sp, maxLines = 1)
    }
}

/** A jagged lightning bolt (midpoint displacement) with a soft glow. */
private fun DrawScope.bolt(a: Offset, b: Offset, rnd: Random, strength: Float) {
    var pts = listOf(a, b)
    var disp = (b - a).getDistance() * 0.28f
    repeat(5) {
        val n = ArrayList<Offset>(pts.size * 2)
        for (i in 0 until pts.size - 1) {
            val p = pts[i]; val q = pts[i + 1]
            val m = Offset((p.x + q.x) / 2f, (p.y + q.y) / 2f)
            val d = q - p
            val len = d.getDistance().coerceAtLeast(0.001f)
            val nx = -d.y / len; val ny = d.x / len
            val k = (rnd.nextFloat() - 0.5f) * 2f * disp
            n.add(p); n.add(Offset(m.x + nx * k, m.y + ny * k))
        }
        n.add(pts.last()); pts = n; disp *= 0.5f
    }
    val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
    drawPath(path, Color(0xFF4FC3FF).copy(alpha = 0.22f * strength), style = Stroke(7f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Color(0xFF8FE0FF).copy(alpha = 0.6f * strength), style = Stroke(3f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Color.White.copy(alpha = 0.95f * strength), style = Stroke(1.1f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
