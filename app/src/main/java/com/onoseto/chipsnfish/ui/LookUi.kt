package com.onoseto.chipsnfish.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
private fun Tile(on: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(TILE).clip(Sq)
            .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.16f), Sq)
            .tap(onClick),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

/** The whole "look" tab: table colour + tone, table size, card backs, card faces, chips. */
@Composable
fun LookSettings() {
    watch()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) { FeltPicker(); TableSize() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Head(tr("Rub karet"), tr(C.BACKS[Game.look.back] ?: ""))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                C.BACKS.keys.forEach { k ->
                    Tile(Game.look.back == k, { Game.look.back = k; saveLook() }) {
                        if (k == "fish") Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF173A52), Color(0xFF0B1A26)))), contentAlignment = Alignment.Center) { FishLogo(Modifier.size(TILE * 0.7f)) }
                        else Canvas(Modifier.fillMaxSize()) { val (a, b, t) = backCols(k); with(CardArt) { back(a, b, t) } }
                    }
                }
            }
            Head(tr("Líc karet"), tr(C.FACES[Game.look.face] ?: ""))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                C.FACES.keys.forEach { k ->
                    // a crop of the K♦ corner shows the style (index size, colours, court art) without a whole card
                    Tile(Game.look.face == k, { Game.look.face = k; saveLook() }) { CardView(46, TILE, k) }
                }
            }
        }
    }
    ChipValues()
}

@Composable
private fun FeltPicker() {
    val cur = remember(Game.look.felt) { C.feltHue(Game.look.felt) }
    var tone by remember(Game.look.felt) { mutableFloatStateOf(cur.third) }
    Head(tr("Barva stolu"))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        C.FELT_PRESETS.forEach { (h, i) ->
            val v = remember(h, i) { C.felt(C.feltKey(h, i)) }
            val on = abs(h - cur.first) < 3f && abs(i - cur.second) < 0.03f
            Tile(on, { Game.look.felt = C.feltKey(h, i, tone); saveLook() }) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(v[0]), Color(v[1]), Color(v[2])))))
            }
        }
    }
    // tone of the chosen colour: darker … lighter
    val track = remember(cur.first, cur.second) { Brush.horizontalGradient(listOf(0f, 0.5f, 1f).map { Color(C.felt(C.feltKey(cur.first, cur.second, it))[1]) }) }
    ThinSlider(tone, track) { t, done ->
        tone = t
        Game.look.felt = C.feltKey(cur.first, cur.second, t)
        if (done) Game.saveLook()
        Game.bump()
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

/** Chips: tap a chip to use / not use it, type its value under it. */
@Composable
private fun ChipValues() {
    val g = Game
    Head(L("Žetony", "Chips", "Chips"), L("klepnutím vypneš / zapneš", "tap to turn off / on", "antippen = an / aus"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        g.chips.forEach { c ->
            var v by remember(c.k) { mutableStateOf(c.v.toString()) }
            Column(Modifier.weight(1f).alpha(if (c.off) 0.35f else 1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(32.dp).tap { c.off = !c.off; g.saveChips(); g.bump(); Ui.redraw() }, contentAlignment = Alignment.Center) { ChipIcon(c, 28.dp) }
                BasicTextField(
                    v, { t -> v = t.filter { it.isDigit() }.take(6); v.toIntOrNull()?.let { if (it >= 1) { c.v = it; g.saveChips(); g.bump() } } },
                    Modifier.fillMaxWidth().wrapContentHeight(),
                    enabled = !c.off, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = F.num, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(LobbyBlue),
                    decorationBox = { inner ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            inner()
                            Box(Modifier.width(46.dp).height(1.dp).background(Color.White.copy(alpha = if (c.off) 0.1f else 0.3f)))
                        }
                    },
                )
            }
        }
    }
}
