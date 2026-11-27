package com.onoseto.chipsnfish.ui

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

/** The blue card room with drifting lights, darkened top and bottom. */
@Composable
fun LobbyBackground() {
    Box(Modifier.fillMaxSize().background(Color(0xFF050B1A))) {
        Image(painterResource(R.drawable.lobby_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        LobbyBokeh()
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99030814), 0.35f to Color.Transparent, 1f to Color(0xCC030814))))
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
