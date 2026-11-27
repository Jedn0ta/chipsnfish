@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.tr
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val LeafS = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp)

@Composable
private fun LookPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(LeafS)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), LeafS)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        T(title, 12.5.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true, modifier = Modifier.fillMaxWidth())
        content()
    }
}

private fun saveLook() { Game.saveLook(); Game.bump() }

/** Table colour palette, card back carousel and card face carousel — the "look" tab of the settings. */
@Composable
fun LookPanels() {
    watch()
    Row(Modifier.fillMaxWidth().height(232.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeltPanel(Modifier.weight(1.15f).fillMaxSize())
        LookPanel(tr("Rub karet"), Modifier.weight(0.95f).fillMaxSize()) {
            val keys = remember { C.BACKS.keys.toList() }
            LookCarousel(keys, Game.look.back, 70.dp, 124.dp, { if (Game.look.back != it) { Game.look.back = it; saveLook() } }) { k -> CardBack(70.dp, k) }
            T(tr(C.BACKS[Game.look.back] ?: ""), 13.sp, LobbyBlue, FontWeight.Bold, maxLines = 1)
            Dots(keys.size, keys.indexOf(Game.look.back))
        }
        LookPanel(tr("Líc karet"), Modifier.weight(1.1f).fillMaxSize()) {
            val keys = remember { C.FACES.keys.toList() }
            LookCarousel(keys, Game.look.face, 118.dp, 124.dp, { if (Game.look.face != it) { Game.look.face = it; saveLook() } }) { k -> CardFan(k) }
            T(tr(C.FACES[Game.look.face] ?: ""), 13.sp, LobbyBlue, FontWeight.Bold, maxLines = 1, fit = true)
            Dots(keys.size, keys.indexOf(Game.look.face))
        }
    }
}

@Composable
private fun CardFan(face: String) {
    Box(Modifier.size(118.dp, 112.dp), contentAlignment = Alignment.Center) {
        listOf(Triple(23, -12f, -34), Triple(45, 0f, 0), Triple(48, 12f, 34)).forEach { (c, rot, dx) ->
            CardView(c, 52.dp, face, modifier = Modifier.offset(x = dx.dp, y = if (dx == 0) (-3).dp else 5.dp).rotate(rot))
        }
    }
}

@Composable
private fun Dots(n: Int, sel: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(n) { i -> Box(Modifier.size(if (i == sel) 14.dp else 5.dp, 5.dp).clip(CircleShape).background(if (i == sel) LobbyBlue else Color.White.copy(alpha = 0.25f))) }
    }
}

/** Swipe (or press, hold and slide) to pick; neighbours peek in at the sides. A tap on a neighbour picks it too. */
@Composable
private fun LookCarousel(keys: List<String>, selected: String, itemW: Dp, h: Dp, onPick: (String) -> Unit, content: @Composable (String) -> Unit) {
    val state = rememberPagerState(keys.indexOf(selected).coerceAtLeast(0)) { keys.size }
    val pick by rememberUpdatedState(onPick)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) {
        snapshotFlow { state.currentPage }.drop(1).collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); pick(keys[it]) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(h)) {
        val pad = ((maxWidth - itemW) / 2).coerceAtLeast(0.dp)
        HorizontalPager(
            state, Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = pad),
            pageSize = PageSize.Fixed(itemW),
            pageSpacing = 4.dp,
        ) { page ->
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val off = abs((state.currentPage - page) + state.currentPageOffsetFraction).coerceIn(0f, 1f)
                        val sc = 1f - 0.3f * off
                        scaleX = sc; scaleY = sc; alpha = 1f - 0.55f * off
                    }
                    .tap { scope.launch { state.animateScrollToPage(page) } },
                contentAlignment = Alignment.Center,
            ) { content(keys[page]) }
        }
    }
}

@Composable
private fun FeltPanel(modifier: Modifier) {
    val start = remember { C.feltHue(Game.look.felt) }
    var hue by remember { mutableFloatStateOf(start.first) }
    var inten by remember { mutableFloatStateOf(start.second) }
    fun apply(save: Boolean) {
        Game.look.felt = C.feltKey(hue, inten)
        if (save) Game.saveLook()
        Game.bump()
    }
    LookPanel(tr("Barva stolu"), modifier) {
        FeltPreview(C.felt(C.feltKey(hue, inten)), Modifier.fillMaxWidth().height(86.dp))
        val hueBrush = remember { Brush.horizontalGradient((0..12).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 0.8f, 0.7f))) }) }
        val cur = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.8f * inten, 0.4f + 0.3f * inten)))
        SliderRow(L("Odstín", "Hue", "Farbton")) {
            PaletteSlider(hue / 360f, hueBrush, cur, { hue = it * 360f; apply(false) }) { apply(true) }
        }
        val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.7f)))
        SliderRow(L("Sytost", "Colour", "Sättigung")) {
            PaletteSlider(inten, Brush.horizontalGradient(listOf(Color(0xFF2A2C30), pure)), cur, { inten = it; apply(false) }) { apply(true) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(150f to 1f, 213f to 1f, 350f to 1f, 268f to 1f, 41f to 1f, 213f to 0f).forEach { (ph, pi) ->
                val v = C.felt(C.feltKey(ph, pi))
                val on = abs(ph - hue) < 3f && abs(pi - inten) < 0.03f
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(v[0]), Color(v[2]))))
                        .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
                        .tap { hue = ph; inten = pi; apply(true) }
                )
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, slider: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(label, 10.5.sp, GlassMuted, FontWeight.Bold, maxLines = 1, fit = true, modifier = Modifier.width(46.dp))
        Box(Modifier.weight(1f)) { slider() }
    }
}

@Composable
private fun PaletteSlider(value: Float, track: Brush, thumb: Color, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    Canvas(
        Modifier.fillMaxWidth().height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { change(((it.x - size.height / 2f) / (size.width - size.height)).coerceIn(0f, 1f)); done() }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { done() }, onDragCancel = { done() }) { ch, _ ->
                    ch.consume()
                    change(((ch.position.x - size.height / 2f) / (size.width - size.height)).coerceIn(0f, 1f))
                }
            }
    ) {
        val r = size.height / 2f
        val th = size.height * 0.46f
        drawRoundRect(track, Offset(0f, center.y - th / 2), Size(size.width, th), CornerRadius(th / 2))
        drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(0f, center.y - th / 2), Size(size.width, th), CornerRadius(th / 2), style = Stroke(1.dp.toPx()))
        val x = r + value * (size.width - 2 * r)
        drawCircle(Color.Black.copy(alpha = 0.4f), r * 0.92f, Offset(x, center.y + 1.5f))
        drawCircle(Color.White, r * 0.88f, Offset(x, center.y))
        drawCircle(thumb, r * 0.62f, Offset(x, center.y))
    }
}

/** Little table with the chosen felt, a card back and a face card lying on it. */
@Composable
private fun FeltPreview(felt: List<Long>, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val rim = size.height * 0.11f
            val cr = CornerRadius(size.height / 2)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A2A20), Color(0xFF140D09))), cornerRadius = cr)
            val inner = CornerRadius(size.height / 2 - rim)
            drawRoundRect(
                Brush.radialGradient(listOf(Color(felt[0]), Color(felt[1]), Color(felt[2])), Offset(center.x, center.y * 0.8f), size.width * 0.55f),
                Offset(rim, rim), Size(size.width - rim * 2, size.height - rim * 2), inner,
            )
            drawRoundRect(Color(felt[3]).copy(alpha = 0.7f), Offset(rim, rim), Size(size.width - rim * 2, size.height - rim * 2), inner, style = Stroke(rim * 0.5f))
            val l = rim * 2.1f
            drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(l, l), Size(size.width - l * 2, size.height - l * 2), CornerRadius(size.height / 2 - l), style = Stroke(1.dp.toPx()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CardBack(28.dp)
            CardView(50, 28.dp)
            CardView(45, 28.dp)
        }
    }
}

/** Table size slider and layout reset, as one compact strip. */
@Composable
fun LookTableSize() {
    watch()
    val g = Game
    var z by remember { mutableStateOf((2.1 - g.zoom).toFloat()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1.5f).clip(LeafS).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.15f), LeafS).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T(tr("Velikost stolu"), 12.sp, Color.White, FontWeight.Bold, maxLines = 1)
            Slider(z, { z = it; g.zoom = ((2.1 - it) * 100).roundToInt() / 100.0; g.saveZoom(); g.bump() }, Modifier.weight(1f).height(30.dp), valueRange = 0.6f..1.5f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LobbyBlue, inactiveTrackColor = Color.White.copy(alpha = 0.18f)))
            T("${(z * 100).roundToInt()} %", 14.sp, LobbyBlue, FontWeight.Bold, num = true)
        }
        Btn(L("Obnovit rozmístění hráčů", "Reset seat layout", "Sitzordnung zurücksetzen"), BK.GHOST, Modifier.weight(1f), height = 40.dp) {
            g.st.pos = g.defaultPos(g.st.n); g.saveLayout(); Ui.close(); g.update(); g.toast(tr("Rozmístění obnoveno"))
        }
    }
    Spacer(Modifier.height(2.dp))
}
