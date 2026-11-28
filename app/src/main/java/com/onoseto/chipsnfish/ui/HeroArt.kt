package com.onoseto.chipsnfish.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onoseto.chipsnfish.R
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Display font for the big lobby title (Cinzel Decorative, OFL). */
val Lucky = FontFamily(Font(R.font.cinzel_deco_black, androidx.compose.ui.text.font.FontWeight.Black), Font(R.font.cinzel_deco_bold, androidx.compose.ui.text.font.FontWeight.Bold))

/** Big title with a thick dark outline and a golden fill. */
@Composable
fun StrokeTitle(text: String, size: TextUnit, modifier: Modifier = Modifier, bold: Boolean = false) {
    val d = LocalDensity.current.density
    val fw = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Black
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontFamily = Lucky, fontWeight = fw, fontSize = size, textAlign = TextAlign.Center, color = Color(0xFF0A1530), letterSpacing = 1.sp,
            drawStyle = Stroke(width = 3.5f * d, join = StrokeJoin.Round)))
        Text(text, style = TextStyle(fontFamily = Lucky, fontWeight = fw, fontSize = size, textAlign = TextAlign.Center, letterSpacing = 1.sp,
            brush = Brush.verticalGradient(listOf(Color(0xFFFFF4C2), Color(0xFFFFD24A), Color(0xFFE59A12))),
            shadow = Shadow(Color(0x99000000), Offset(0f, 3f * d), 6f * d)))
    }
}

/** Main PLAY button: two aces that gently breathe and float, with the title written over them. */
@Composable
fun PlayCards(title: String, sub: String, tilt: Offset, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val inf = rememberInfiniteTransition(label = "cards")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "t")
    val glow by inf.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
    val flare = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val dn = LocalDensity.current.density
    val ph = t * 2f * PI.toFloat()
    Box(
        Modifier.size(190.dp, 176.dp).clickable(src, null) {
            if (busy) return@clickable
            busy = true
            scope.launch {
                flare.snapTo(0f); flare.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
                onClick(); flare.snapTo(0f); busy = false
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val f = flare.value
        // soft light behind the cards
        Canvas(Modifier.fillMaxSize()) {
            val a = glow + 0.4f * f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF6FB6FF).copy(alpha = 0.5f * a), Color(0xFF2F7BFF).copy(alpha = 0.18f * a), Color.Transparent), center, size.minDimension * 0.55f), size.minDimension * 0.55f)
        }
        val press = if (pressed) 0.94f else 1f
        val spread = 1f + 0.35f * sin(f * PI.toFloat())
        Box(Modifier.offset(y = (-14).dp).graphicsLayer(scaleX = press, scaleY = press, rotationY = tilt.x * 10f, rotationX = -tilt.y * 8f, cameraDistance = 12f * dn)) {
            // each card breathes (bigger / smaller) and floats up and down, a little out of step
            val s1 = 1f + 0.05f * sin(ph); val y1 = 5f * sin(ph + 0.6f)
            val s2 = 1f + 0.05f * sin(ph + 1.9f); val y2 = 5f * sin(ph + 2.5f)
            CardView(48, 66.dp, "picture", glow = true, modifier = Modifier.graphicsLayer(scaleX = s1, scaleY = s1, rotationZ = -12f * spread, translationX = -24f * spread * dn, translationY = (4f + y1) * dn))
            CardView(49, 66.dp, "picture", glow = true, modifier = Modifier.graphicsLayer(scaleX = s2, scaleY = s2, rotationZ = 10f * spread, translationX = 24f * spread * dn, translationY = y2 * dn))
        }
        // the title, written over the cards
        Column(Modifier.align(Alignment.BottomCenter).graphicsLayer(scaleX = 1f + 0.12f * f, scaleY = 1f + 0.12f * f), horizontalAlignment = Alignment.CenterHorizontally) {
            StrokeTitle(title, 38.sp)
            StrokeTitle(sub, 17.sp, Modifier.offset(y = (-4).dp), bold = true)
        }
    }
}
