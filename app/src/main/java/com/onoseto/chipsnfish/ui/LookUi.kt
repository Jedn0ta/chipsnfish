@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.tr
import kotlin.math.abs
import kotlin.math.roundToInt

private val Sq = RoundedCornerShape(9.dp)
private val TILE = 34.dp

private fun saveLook() { Game.saveLook(); Game.bump() }

@Composable
private fun Head(title: String, value: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(title)
        if (value.isNotEmpty()) T(value, 11.sp, LobbyBlue, FontWeight.Bold, maxLines = 1)
    }
}

/** One square choice: selected = white outline. */
@Composable
private fun Tile(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier.size(TILE), content: @Composable () -> Unit) {
    Box(
        modifier.clip(Sq)
            .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.16f), Sq)
            .tap(onClick),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

/** The whole "look" tab: table colour + size, card back and card face carousels. */
@Composable
fun LookSettings() {
    watch()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(212.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FeltPicker(); TableSize()
            Label(L("Moje karty", "My cards", "Meine Karten"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(false to L("Lícem nahoru", "Face up", "Offen"), true to L("Skryté · podržet", "Hidden · hold", "Verdeckt · halten")).forEach { (v, l) ->
                    val on = Game.look.hide == v
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) LobbyBlue.copy(alpha = 0.25f) else Color.Transparent)
                            .border(1.dp, if (on) LobbyBlue else Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                            .tap { Game.look.hide = v; saveLook() }.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) { T(l, 11.5.sp, if (on) Color.White else GlassMuted, FontWeight.Bold, maxLines = 1) }
                }
            }
        }
        Column(Modifier.weight(0.85f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(tr("Rub karet"))
            val keys = remember { C.BACKS.keys.toList() }
            LookCarousel(keys, Game.look.back, 74.dp, 128.dp, { if (Game.look.back != it) { Game.look.back = it; saveLook() } }) { k -> CardBack(74.dp, k) }
            T(tr(C.BACKS[Game.look.back] ?: ""), 13.sp, LobbyBlue, FontWeight.Bold, maxLines = 1)
            Dots(keys.size, keys.indexOf(Game.look.back))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(tr("Líc karet"))
            val keys = remember { C.FACES.keys.toList() }
            LookCarousel(keys, Game.look.face, 120.dp, 128.dp, { if (Game.look.face != it) { Game.look.face = it; saveLook() } }) { k -> CardFan(k) }
            T(tr(C.FACES[Game.look.face] ?: ""), 13.sp, LobbyBlue, FontWeight.Bold, maxLines = 1, fit = true)
            Dots(keys.size, keys.indexOf(Game.look.face))
        }
    }
}

@Composable
private fun CardFan(face: String) {
    Box(Modifier.size(120.dp, 116.dp), contentAlignment = Alignment.Center) {
        listOf(Triple(23, -12f, -34), Triple(45, 0f, 0), Triple(48, 12f, 34)).forEach { (c, rot, dx) ->
            CardView(c, 54.dp, face, modifier = Modifier.offset(x = dx.dp, y = if (dx == 0) (-3).dp else 5.dp).rotate(rot))
        }
    }
}

@Composable
private fun Dots(n: Int, sel: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(n) { i -> Box(Modifier.size(if (i == sel) 14.dp else 5.dp, 5.dp).clip(CircleShape).background(if (i == sel) LobbyBlue else Color.White.copy(alpha = 0.25f))) }
    }
}

/** Swipe, or press-hold-and-slide, to pick; neighbours peek in at the sides. Tapping a neighbour picks it too. */
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
        HorizontalPager(state, Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = pad), pageSize = PageSize.Fixed(itemW), pageSpacing = 2.dp) { page ->
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val off = abs((state.currentPage - page) + state.currentPageOffsetFraction).coerceIn(0f, 1f)
                        val sc = 1f - 0.32f * off
                        scaleX = sc; scaleY = sc; alpha = 1f - 0.6f * off
                    }
                    .tap { scope.launch { state.animateScrollToPage(page) } },
                contentAlignment = Alignment.Center,
            ) { content(keys[page]) }
        }
    }
}

@Composable
private fun FeltPicker() {
    val cur = remember(Game.look.felt) { C.feltHue(Game.look.felt) }
    Label(tr("Barva stolu"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        C.FELT_PRESETS.forEach { (h, i) ->
            val v = remember(h, i) { C.felt(C.feltKey(h, i)) }
            val on = abs(h - cur.first) < 3f && abs(i - cur.second) < 0.03f
            Tile(on, { Game.look.felt = C.feltKey(h, i); saveLook() }, Modifier.weight(1f).aspectRatio(1f)) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(v[0]), Color(v[1]), Color(v[2])))))
            }
        }
    }
}

@Composable
private fun TableSize() {
    val g = Game
    var z by remember { mutableFloatStateOf((2.1 - g.zoom).toFloat()) }
    Head(tr("Velikost stolu"), "${(z * 100).roundToInt()} %")
    val track = remember { Brush.horizontalGradient(listOf(LobbyBlue.copy(alpha = 0.25f), LobbyBlue)) }
    ThinSlider((z - 0.6f) / 0.9f, track) { t, done ->
        z = 0.6f + t * 0.9f
        g.zoom = ((2.1 - z) * 100).roundToInt() / 100.0
        if (done) g.saveZoom()
        g.bump()
    }
    T(L("Obnovit rozmístění hráčů", "Reset seat layout", "Sitzordnung zurücksetzen"), 11.5.sp, GlassMuted, FontWeight.Bold,
        modifier = Modifier.padding(top = 2.dp).tap { g.st.pos = g.defaultPos(g.st.n); g.saveLayout(); Ui.close(); g.update(); g.toast(tr("Rozmístění obnoveno")) })
}

/** Slim slider used across the look tab; reports (value 0..1, finished). */
@Composable
private fun ThinSlider(value: Float, track: Brush, onChange: (Float, Boolean) -> Unit) {
    val change by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(22.dp)
            .pointerInput(Unit) { detectTapGestures { change(((it.x - size.height / 2f) / (size.width - size.height)).coerceIn(0f, 1f), true) } }
            .pointerInput(Unit) {
                var last = value
                detectHorizontalDragGestures(onDragEnd = { change(last, true) }, onDragCancel = { change(last, true) }) { ch, _ ->
                    ch.consume()
                    last = ((ch.position.x - size.height / 2f) / (size.width - size.height)).coerceIn(0f, 1f)
                    change(last, false)
                }
            }
    ) {
        val r = size.height / 2f
        val th = 6.dp.toPx()
        drawRoundRect(track, Offset(0f, center.y - th / 2), Size(size.width, th), CornerRadius(th / 2))
        val x = r + value.coerceIn(0f, 1f) * (size.width - 2 * r)
        drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.8f, Offset(x, center.y + 1.5f))
        drawCircle(Color.White, r * 0.76f, Offset(x, center.y))
    }
}
