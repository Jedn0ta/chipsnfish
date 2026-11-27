@file:OptIn(ExperimentalFoundationApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import kotlin.math.sqrt
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.onoseto.chipsnfish.I18n
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Cards
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.game.Reads
import com.onoseto.chipsnfish.tr
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Table geometry in design dp (800×360 screen), as renderTable() in the original. */
class Geo(val w: Float, val h: Float, n: Int, zoom: Double, playing: Boolean) {
    val sc: Float
    val x0: Float; val x1: Float; val y0: Float; val y1: Float; val r: Float
    val cx: Float; val cy: Float
    val cw: Float
    val zone: FloatArray?
    /** Bottom-right corner kept free for the round action buttons. */
    val zone2: FloatArray?

    init {
        val sc0 = (zoom * 0.8).toFloat()
        val per0 = 2 * (w + h)
        var ss = min(1f, (per0 * 0.62f) / (max(1, n) * 150 * sc0))
        ss = max(0.7f, ss)
        val rcol = 112f
        val cardW = 150 * sc0 * ss
        val cardH = 66 * sc0 * ss
        x0 = cardW / 2 + 4; x1 = w - rcol - cardW / 2 - 2
        y0 = cardH / 2 + 2; y1 = h - cardH / 2 - 2
        r = min(x1 - x0, y1 - y0) * 0.42f
        cx = (x0 + x1) / 2; cy = (y0 + y1) / 2
        val availH = (y1 - y0 - cardH - 44) / 1.4f
        val availW = (x1 - x0 - cardW - 16) / 5.25f
        cw = min(64f, max(40f, min(availH, availW))).roundToInt().toFloat()
        sc = sc0 * ss
        // top-right corner: my hole cards; bottom-right corner: action buttons — seats keep out of both
        zone = if (playing) floatArrayOf(w - 182, -20f, w + 20, 140f) else null
        zone2 = if (playing) floatArrayOf(w - 204, h - 176, w + 20, h + 20) else null
    }

    fun pt(s0: Double): Offset {
        val q = (PI * r / 2).toFloat()
        val cxx = (x0 + x1) / 2
        // segments: L(x,y→x,y) or A(cx,cy,a0,a1)
        data class Seg(val arc: Boolean, val a: Float, val b: Float, val c: Float, val d: Float)
        val segs = listOf(
            Seg(false, cxx, y1, x0 + r, y1), Seg(true, x0 + r, y1 - r, 90f, 180f),
            Seg(false, x0, y1 - r, x0, y0 + r), Seg(true, x0 + r, y0 + r, 180f, 270f),
            Seg(false, x0 + r, y0, x1 - r, y0), Seg(true, x1 - r, y0 + r, 270f, 360f),
            Seg(false, x1, y0 + r, x1, y1 - r), Seg(true, x1 - r, y1 - r, 0f, 90f),
            Seg(false, x1 - r, y1, cxx, y1),
        )
        fun len(g: Seg) = if (!g.arc) hypot(g.c - g.a, g.d - g.b) else q
        val total = segs.sumOf { len(it).toDouble() }.toFloat()
        var d = ((((s0 % 1) + 1) % 1) * total).toFloat()
        for (g in segs) {
            val l = len(g)
            if (d <= l) {
                val f = if (l > 0) d / l else 0f
                if (!g.arc) return Offset(g.a + (g.c - g.a) * f, g.b + (g.d - g.b) * f)
                val a = ((g.c + (g.d - g.c) * f) * PI / 180).toFloat()
                return Offset(g.a + r * cos(a), g.b + r * sin(a))
            }
            d -= l
        }
        return Offset(cxx, y1)
    }

    fun project(p: Offset): Double {
        var best = 0.0; var bd = Float.MAX_VALUE
        for (i in 0 until 720) {
            val s = i / 720.0
            val q = pt(s)
            val dd = (q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y)
            if (dd < bd) { bd = dd; best = s }
        }
        return best
    }

    /** Like project(), but only looks near the previous spot, so the seat never jumps to the other side of the table. */
    fun projectNear(p: Offset, s0: Double): Double {
        var best = s0; var bd = Float.MAX_VALUE
        for (i in -90..90) {
            val s = s0 + i * 0.002
            val q = pt(s)
            val dd = (q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y)
            if (dd < bd) { bd = dd; best = s }
        }
        return ((best % 1) + 1) % 1
    }

    /** Keep seat k between its neighbours (seat order must not change during a game), with room for the seat. */
    fun between(k: Int, s: Double): Double {
        fun m(x: Double) = ((x % 1) + 1) % 1
        val st = Game.st
        val n = st.n
        if (n < 3) return m(s)
        val pos = st.pos!!
        val prev = pos[(k - 1 + n) % n]
        val next = pos[(k + 1) % n]
        val span = m(next - prev).let { if (it == 0.0) 1.0 else it }
        val perim = 2 * (x1 - x0 - 2 * r) + 2 * (y1 - y0 - 2 * r) + 2 * PI.toFloat() * r
        val gap = minOf((120 * sc / perim).toDouble(), span / 2)
        var d = m(s - prev)
        if (d > span) d = if (d - span < 1 - d) span - gap else gap
        return m(prev + d.coerceIn(gap, span - gap))
    }

    private fun clashes(k: Int, s: Double): Boolean {
        val st = Game.st
        val p = pt(s)
        val sw = 158 * sc; val sh = 70 * sc
        val z = zone
        if (z != null && k > 0 && p.x + sw * 0.42f > z[0] && p.y - sh * 0.5f < z[3]) return true
        val z2 = zone2
        if (z2 != null && k > 0 && p.x + sw * 0.42f > z2[0] && p.y + sh * 0.5f > z2[1]) return true
        val pos = st.pos!!
        for (j in 0 until st.n) {
            if (j == k) continue
            val q = pt(pos[j])
            if (abs(q.x - p.x) < sw && abs(q.y - p.y) < sh) return true
        }
        return false
    }

    fun clampS(k: Int, s0: Double): Double {
        fun m(x: Double) = ((x % 1) + 1) % 1
        val st = Game.st
        val n = st.n
        val s = m(s0)
        if (n < 2) return s
        val pos = st.pos!!
        val prev = pos[(k - 1 + n) % n]
        val next = pos[(k + 1) % n]
        val span = m(next - prev).let { if (it == 0.0) 1.0 else it }
        var d = m(s - prev)
        if (n > 2 && d > span) d = if (d - span < 1 - d) span - 0.004 else 0.004
        for (stp in 0..400) {
            for (sg in if (stp > 0) intArrayOf(1, -1) else intArrayOf(1)) {
                val dd = d + sg * stp * 0.0025
                if (n > 2 && (dd <= 0.002 || dd >= span - 0.002)) continue
                val c = m(prev + dd)
                if (!clashes(k, c)) return c
            }
        }
        return m(prev + d)
    }
}

private class Drag(val k: Int, val start: Offset) {
    var cur by mutableStateOf(start)
    var s by mutableStateOf<Double?>(null)
    var swap by mutableStateOf<Int?>(null)
}

@Composable
fun TableView() {
    watch()
    @Suppress("UNUSED_VARIABLE") val rev = Game.rev
    val st = Game.st
    val playing = Game.playing()
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp)) {
        val w = maxWidth.value; val h = maxHeight.value
        Game.ensurePos()
        val geo = remember(w, h, st.n, Game.zoom, playing) { Geo(w, h, st.n, Game.zoom, playing) }
        var drag by remember { mutableStateOf<Drag?>(null) }
        // keep seats apart (as the original did on every render)
        LaunchedEffect(st.n, w, h, Game.zoom, st.handNo) {
            val pos = st.pos ?: return@LaunchedEffect
            repeat(4) { for (k in 1 until st.n) pos[k] = geo.clampS(k, pos[k]) }
            Game.bump()
        }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val myTurn = playing && st.turn == 0 && st.done == null && Game.gm()
        LaunchedEffect(myTurn, st.handNo, st.betStreet) {
            if (myTurn && Game.online()) { Sound.turn(); haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }
        }
        val sc = geo.sc
        // rail & velvet felt
        FeltCanvas(geo)
        // watermark on the felt
        Box(Modifier.offset((geo.cx - 150).dp, (geo.cy + geo.cw * 0.7f + 30).dp).width(300.dp), contentAlignment = Alignment.Center) {
            if (Game.online() && com.onoseto.chipsnfish.online.Online.cup && playing)
                T("🏆 " + com.onoseto.chipsnfish.online.Online.cupName.uppercase(), 15.sp, C.gold.copy(alpha = 0.3f), num = true, letter = 3.sp, maxLines = 1)
            else T("CHIPS & FISH", 15.sp, Color.White.copy(alpha = 0.07f), num = true, letter = 6.sp)
        }
        // center
        Center(geo)
        // bets
        if (playing) st.seats.forEachIndexed { k, s ->
            if (s.bet <= 0) return@forEachIndexed
            val p = geo.pt(st.pos!![k])
            val dx = geo.cx - p.x; val dy = geo.cy - p.y
            val dd = hypot(dx, dy).coerceAtLeast(1f)
            val off = min(dd * 0.58f, 104 * sc)
            val bx = p.x + dx / dd * off; val by = p.y + dy / dd * off
            Row(
                Modifier.offset((bx - 40).dp, (by - 12).dp).width(80.dp).zIndex(2f),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipPile(s.bet, 1, (15 * sc / 0.8f).dp)
                Box(Modifier.offset(x = (-3).dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(start = 6.dp, end = 7.dp)) {
                    T(Game.fm(s.bet), (14 * sc / 0.8f).sp, C.gold, num = true, maxLines = 1)
                }
            }
        }
        // seats
        val (sb, bb) = Game.positions()
        val lead = st.seats.maxOf { it.stack + it.bet }
        val leadCnt = st.seats.count { it.stack + it.bet == lead }
        st.seats.forEachIndexed { k, _ ->
            key(k, st.seats[k].pid) {
                val p0 = geo.pt(st.pos!![k])
                SeatView(k, p0, geo, sb, bb, lead, leadCnt, moving = drag?.k == k && drag?.s != null,
                    drop = drag?.swap == k,
                    onDragStart = { drag = Drag(k, p0) },
                    onDrag = { delta ->
                        val d = drag ?: return@SeatView
                        d.cur = d.cur + delta
                        d.swap = null
                        if (st.phase == "setup" && k != 0) {
                            for (j in 1 until st.n) {
                                if (j == k) continue
                                val q = geo.pt(st.pos!![j])
                                if (hypot(q.x - d.cur.x, q.y - d.cur.y) < 34 * sc) { d.swap = j; break }
                            }
                        }
                        // follow the finger smoothly: stay near the last spot and between the neighbours;
                        // the final, collision-free spot is picked when the finger is lifted
                        val near = geo.projectNear(d.cur, d.s ?: st.pos!![k])
                        d.s = if (d.swap != null) null else if (st.phase == "setup") near else geo.between(k, near)
                    },
                    onDragEnd = {
                        val d = drag
                        drag = null
                        if (d != null) {
                            val sw = d.swap
                            val s = d.s
                            if (sw != null) {
                                val a = st.seats[d.k]; st.seats[d.k] = st.seats[sw]; st.seats[sw] = a
                                Game.update(); Game.toast(tr("Hráči prohozeni"))
                            } else if (s != null) {
                                st.pos!![d.k] = geo.clampS(d.k, s)
                                Game.saveLayout(); Game.update()
                                Game.toast(tr(if (d.k == 0) "Tvoje místo uloženo" else "Umístění uloženo"))
                            }
                        }
                    },
                )
            }
        }
        // a friend asks me to show one card: pick it, or decline
        val ask = com.onoseto.chipsnfish.online.Online.asksIn.firstOrNull()
        if (Game.online() && ask != null && !st.hero.contains(null)) {
            Column(
                Modifier.offset((geo.cx - 150).dp, (geo.cy - 100).dp).width(300.dp).zIndex(24f).shadow(12.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF0101A28)).border(1.dp, LobbyBlue.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                T(ask.second + L(" chce vidět jednu tvoji kartu", " wants to see one of your cards", " möchte eine deiner Karten sehen"), 14.sp, Color.White, FontWeight.Bold, align = TextAlign.Center)
                T(L("Klepni na kartu, kterou ukážeš všem u stolu", "Tap the card you'll show to the table", "Tippe auf die Karte, die du zeigst"), 11.sp, C.muted, align = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0..1) CardView(st.hero[i]!!, 64.dp, modifier = Modifier.tap { com.onoseto.chipsnfish.online.Online.answerAsk(ask.first, i) })
                }
                Btn(L("Neukážu", "Decline", "Ablehnen"), BK.DARK, Modifier.fillMaxWidth(), height = 38.dp) { com.onoseto.chipsnfish.online.Online.answerAsk(ask.first, -1) }
            }
        }
        // fold zone: appears in the middle of the table while my cards are being dragged
        if (Ui.foldDrag != null) {
            val hot = Ui.foldHot
            Box(
                Modifier.offset((geo.cx - 56).dp, (geo.cy - 56).dp).size(112.dp).zIndex(15f)
                    .onGloballyPositioned { Ui.foldZone = it.boundsInRoot().center; Ui.foldR = it.size.width / 2f }
                    .clip(CircleShape).background(if (hot) C.lose.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f))
                    .border(2.dp, if (hot) Color.White else C.lose.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T("FOLD", 22.sp, Color.White, num = true, letter = 2.sp)
                    T(if (hot) L("pusť", "release", "loslassen") else L("sem přetáhni", "drop here", "hierher ziehen"), 11.sp, Color.White.copy(alpha = 0.8f), FontWeight.Bold)
                }
            }
        }
        // dealer / small blind / big blind buttons lying on the felt in front of the player
        if (st.n >= 2) {
            val dpos = { k: Int -> drag?.takeIf { it.k == k }?.s ?: st.pos!![k] }
            PosButton("D", st.dealer, dpos, geo, drag != null)
            if (sb >= 0 && sb != st.dealer) PosButton("SB", sb, dpos, geo, drag != null)
            if (bb >= 0) PosButton("BB", bb, dpos, geo, drag != null)
        }
        // drag marker
        drag?.let { d ->
            val s = d.s
            if (s != null) {
                val m = geo.pt(s)
                val gs = 50 * geo.sc / 0.8f
                Box(Modifier.offset((m.x - gs / 2).dp, (m.y - gs / 2).dp).size(gs.dp).zIndex(6f).graphicsLayer(scaleX = 1.08f, scaleY = 1.08f)) {
                    Avatar(Game.pl(st.seats[d.k]), gs.dp, ring = C.gold, border = (3 * geo.sc / 0.8f).dp)
                }
            }
        }
        // quick actions for a tapped player
        if (Ui.seatMenu in 1 until st.n) SeatMenu(Ui.seatMenu, geo, w)
        // mood popup
        if (Ui.moodSeat in 0 until st.n) MoodPop(geo, w, h)
        // fish bubble, swimming chip, chip rain
        FishFx(geo, w, h)
        // emoji bubbles, flying drinks, emoji/drink panel
        SocialFx(geo, w, h)
        if (com.onoseto.chipsnfish.online.Social.available()) {
            SocialBtn(Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 2.dp).zIndex(7f))
            if (com.onoseto.chipsnfish.online.Social.open) Box(Modifier.align(Alignment.TopStart).padding(start = 46.dp, top = 2.dp).zIndex(21f)) { SocialPop() }
        }
        // right column
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 0.dp, bottom = 0.dp).width(200.dp).fillMaxHeight().zIndex(8f)) { ActBox() }
        if (playing && Game.raiseOpen && st.turn >= 0) Box(Modifier.align(Alignment.BottomEnd).padding(end = 178.dp, bottom = 2.dp).width(360.dp).zIndex(9f)) { RaisePop() }
        // beginner coach (practice game)
        if (playing && Game.bot() && Game.gameCfg.coach) Box(Modifier.align(Alignment.BottomStart).padding(start = 46.dp, bottom = 2.dp).zIndex(7f)) { CoachPanel() }
        // advisor
        if (playing && Game.advisorOn()) {
            Box(
                Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = if (com.onoseto.chipsnfish.online.Social.available()) 48.dp else 2.dp).size(40.dp).zIndex(7f).clip(RoundedCornerShape(10.dp))
                    .background(C.panel.copy(alpha = 0.92f)).border(1.dp, C.line, RoundedCornerShape(10.dp))
                    .tap {
                        Game.ensureEq()
                        if (Game.playing()) Game.nextCache = Reads.nextStats(Game.st)
                        Ui.open("analysis")
                    },
                contentAlignment = Alignment.Center,
            ) { BulbIcon(C.gold, 24.dp) }
        }
    }
}

private val bulbPath by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12 3a6 6 0 0 0-3.6 10.8c.7.6 1.1 1.3 1.1 2.2h5c0-.9.4-1.6 1.1-2.2A6 6 0 0 0 12 3z").toPath() }

@Composable
fun BulbIcon(col: Color, size: Dp) {
    watch()
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, Offset.Zero) {
            drawLine(col, Offset(9f, 18f), Offset(15f, 18f), 2f, StrokeCap.Round)
            drawLine(col, Offset(10f, 21f), Offset(14f, 21f), 2f, StrokeCap.Round)
            drawPath(bulbPath, col, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun Center(geo: Geo) {
    watch()
    val st = Game.st
    val cw = geo.cw.dp
    Box(Modifier.offset((geo.cx - 200).dp, (geo.cy - 80).dp).size(400.dp, 160.dp).zIndex(1f), contentAlignment = Alignment.Center) {
        if (Game.playing()) {
            val scl = min(1f, (geo.x1 - geo.x0 - 30) / (5 * geo.cw + 24 + 10))
            Column(Modifier.graphicsLayer(scaleX = scl, scaleY = scl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val pm = Game.potMiddle(); val tot = Game.potTotal()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.42f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChipPile(pm, 5, 18.dp)
                    T("POT", 11.sp, Color.White.copy(alpha = 0.6f), FontWeight.ExtraBold, letter = 1.3.sp)
                    T(Game.fm(pm), 21.sp, C.gold, num = true)
                    if (tot > pm && pm > 0) T(L("celkem ", "total ", "gesamt ") + Game.fm(tot), 11.sp, Color.White.copy(alpha = 0.6f), FontWeight.SemiBold)
                }
                // whose turn it is, right next to the pot
                if (st.done == null && st.turn >= 0 && st.over == null) TurnWho(st.turn)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 2..6) {
                        val v = Game.slotVal(i)
                        if (Game.gm()) {
                            if (v == null) CardSlot(cw, "", locked = true) else DealIn("${Game.st.handNo}-$i-$v") { CardView(v, cw) }
                        } else if (v == null && !Game.slotOpen(i)) {
                            CardSlot(cw, if (i < 5) "FLOP" else if (i == 5) "TURN" else "RIVER", locked = true, modifier = Modifier.tap { Game.toast(Game.lockMsg(i)) })
                        } else if (v == null) {
                            CardSlot(cw, "+", next = Game.slotOpen(i) && i == Game.nextBoardSlot(), modifier = Modifier.tap { openCardSlot(i) })
                        } else CardView(v, cw, modifier = Modifier.tap { openCardSlot(i) })
                    }
                }
                if (Game.gm()) GameResult()
            }
        } else if (Game.online()) {
            OnlineLobbyCenter()
        } else {
            val msg = if (st.phase == "ready") {
                L("Hand #1", "Hand #1", "Hand #1") to L("Hráči sedí, blindy ${Cards.lvlStr(st.level)}. Až budete připraveni, dej START.", "Players are seated, blinds ${Cards.lvlStr(st.level)}. When you're ready, press START.", "Spieler sitzen, Blinds ${Cards.lvlStr(st.level)}. Wenn ihr bereit seid, START drücken.")
            } else tr("Příprava hry") to when {
                Game.regCount() == 0 -> tr("Nejdřív si v záložce Hráči vytvoř seznam hráčů.")
                st.n < 2 -> L("Klepni na hráče na lavičce vpravo a posaď ho ke stolu.", "Tap a player on the bench on the right to seat him.", "Tippe rechts auf einen Spieler auf der Bank, um ihn zu setzen.")
                !st.cfgDone -> tr("Hráči sedí. Zkontroluj Nastavení, nebo přidej další hráče.")
                else -> tr("Vše připraveno. Klepni na Připravit.")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(msg.first.uppercase(), 26.sp, Color.White.copy(alpha = 0.85f), num = true, letter = 2.sp, align = TextAlign.Center)
                T(msg.second, 14.sp, Color.White.copy(alpha = 0.65f), FontWeight.SemiBold, align = TextAlign.Center, modifier = Modifier.widthIn(max = 340.dp))
            }
        }
    }
}

fun openCardSlot(i: Int) {
    val g = Game
    val v = g.slotVal(i)
    if (v == null && !g.slotOpen(i)) { g.toast(g.lockMsg(i)); return }
    val target = if (v == null) g.slotGroup(i).firstOrNull { g.slotVal(it) == null } ?: i else i
    Ui.open("card", CardArg(target))
}

@Composable
private fun GameResult() {
    watch()
    val d = Game.st.done ?: return
    val st = Game.st
    fun nm(k: Int) = if (k == 0) tr("Ty") else Game.pl(st.seats[k]).name
    val heroWon = 0 in d.w
    val t: String; var sub = ""
    if (d.w.size > 1) {
        t = tr("Dělba potu")
        sub = d.w.joinToString(" · ") { nm(it) + " +" + Game.fm(d.amt[it]) }
    } else {
        val k = d.w[0]
        t = if (k == 0) L("Vyhráváš ", "You win ", "Du gewinnst ") + Game.fm(d.amt[k]) else nm(k) + L(" vyhrává ", " wins ", " gewinnt ") + Game.fm(d.amt[k])
    }
    val c = if (d.sd) I18n.cat(Cards.CAT[d.cat[d.w[0]] ?: 0]) else tr("ostatní složili")
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (heroWon) C.win.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.55f))
            .border(1.dp, if (heroWon) C.win else Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(t, 17.sp, if (heroWon) C.win else C.ink, num = true)
        T((if (sub.isNotEmpty()) "$sub · " else "") + c, 12.sp, Color.White.copy(alpha = 0.75f), FontWeight.SemiBold)
    }
}

/** Tap on a player during the game: what I can do with him, instead of his statistics. */
@Composable
private fun SeatMenu(k: Int, geo: Geo, w: Float) {
    watch()
    val g = Game
    val st = g.st
    val s = st.seats.getOrNull(k) ?: return
    val p = g.pl(s)
    val pt = geo.pt(st.pos!![k])
    val social = com.onoseto.chipsnfish.online.Social
    val shop = social.available() && com.onoseto.chipsnfish.online.SupaCfg.configured
    val items = buildList<Triple<String, String, () -> Unit>> {
        if (g.playing() && !s.out && !s.folded && st.done == null && st.one?.get(k) == null && (g.online() || st.hole[k] != null))
            add(Triple("🂠", L("Ukaž kartu", "Show a card", "Karte zeigen"), { g.askCard(k) }))
        if (shop) add(Triple("🍹", L("Drink", "Drink", "Drink"), { social.openPanel("drink", s.pid) }))
        if (shop) add(Triple("🎁", L("Darovat", "Give", "Schenken"), { social.openPanel("hat", s.pid) }))
        if (social.available()) add(Triple("💬", L("Napsat", "Message", "Schreiben"), { social.openPanel("chat", s.pid) }))
        if (social.available()) add(Triple("😀", L("Emoji", "Emoji", "Emoji"), { social.openPanel("emoji", s.pid) }))
        add(Triple("ⓘ", L("Profil", "Profile", "Profil"), { Ui.open("seat", k) }))
    }
    val mw = items.size * 64f + 16f
    val x = (pt.x - mw / 2).coerceIn(4f, w - mw - 4f)
    val y = if (pt.y < geo.cy) pt.y + 46f else pt.y - 118f
    // tap anywhere else closes it
    Box(Modifier.fillMaxSize().zIndex(22f).tap { Ui.seatMenu = -1 })
    Column(
        Modifier.offset(x.dp, y.dp).zIndex(23f).shadow(12.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(Color(0xF0101A28)).border(1.dp, LobbyBlue.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        T(p.name, 12.sp, LobbyBlue, FontWeight.ExtraBold, maxLines = 1)
        Row {
            items.forEach { (ic, label, fn) ->
                Column(
                    Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).tap { Ui.seatMenu = -1; fn() }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                        T(ic, 18.sp, Color.White, FontWeight.Bold)
                    }
                    T(label, 10.5.sp, Color.White, FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** The table itself; reads the felt colour on its own, so a colour change in the settings shows at once. */
@Composable
private fun FeltCanvas(geo: Geo) {
    watch()
    val felt = remember(Game.look.felt) { C.felt(Game.look.felt) }
    val noise = remember { velvetNoise() }
    val dens = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize().pointerInput(geo) {
        // double tap on the felt = check (only when it's my turn and checking is free)
        detectTapGestures(onDoubleTap = { o ->
            val x = o.x / dens; val y = o.y / dens
            if (x < geo.x0 || x > geo.x1 || y < geo.y0 || y > geo.y1) return@detectTapGestures
            val g = Game; val s0 = g.st
            if (!g.gm() || !g.playing() || s0.turn != 0 || s0.done != null) return@detectTapGestures
            if (s0.curBet - s0.seats[0].bet <= 0) g.heroAction("call")
            else g.toast(L("Check teď nejde, je potřeba dorovnat", "Can't check now, there's a bet to call", "Check geht nicht, es gibt einen Einsatz"))
        })
    }) { drawVelvetTable(geo, felt, noise) }
}

/** Round D / SB / BB button placed between the player and the table centre (beside the bet), sliding when it moves. */
@Composable
private fun PosButton(tag: String, k: Int, pos: (Int) -> Double, geo: Geo, dragging: Boolean) {
    val st = Game.st
    if (k !in 0 until st.n || st.pos == null) return
    val sc = geo.sc / 0.8f
    val p = geo.pt(pos(k))
    // the faint gold betting line on the felt (same geometry as drawVelvetTable): a rounded rect inset from the felt
    val fl = geo.x0 + 2; val ft = geo.y0 + 2; val fw = geo.x1 - geo.x0 - 4; val fh = geo.y1 - geo.y0 - 4
    val bi = min(fw, fh) * 0.13f
    val lr = max(2f, geo.r - 2 - bi)
    val lL = fl + bi + lr; val lR = fl + fw - bi - lr; val lT = ft + bi + lr; val lB = ft + fh - bi - lr
    // closest point on the line to the seat: clamp to the inner rect, then step out by the corner radius
    val qx = p.x.coerceIn(lL, max(lL, lR)); val qy = p.y.coerceIn(lT, max(lT, lB))
    var nx = p.x - qx; var ny = p.y - qy
    val nd = hypot(nx, ny)
    if (nd < 0.5f) { nx = 0f; ny = if (p.y < geo.cy) -1f else 1f } else { nx /= nd; ny /= nd }
    // slide a bit along the line so the button sits beside the bet, not on it
    val side = 30f * sc
    val tx = qx + nx * lr - ny * side
    val ty = qy + ny * lr + nx * side
    val spec = if (dragging) snap<Float>() else tween<Float>(500, easing = FastOutSlowInEasing)
    val x by animateFloatAsState(tx, spec, label = "px$tag")
    val y by animateFloatAsState(ty, spec, label = "py$tag")
    val d = 21f * sc
    val (bg, ink) = when (tag) { "D" -> Color(0xFFF4F1E8) to Color(0xFF15181C); "BB" -> C.gold to C.goldInk; else -> Color(0xFF3E7CC9) to Color.White }
    Box(
        Modifier.offset((x - d / 2).dp, (y - d / 2).dp).size(d.dp).zIndex(2.5f)
            .shadow(3.dp, CircleShape).clip(CircleShape)
            .background(Brush.radialGradient(listOf(bg, bg.copy(alpha = 1f).let { Color(it.red * 0.8f, it.green * 0.8f, it.blue * 0.8f) })))
            .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { T(tag, (if (tag == "D") 11 * sc else 8.5f * sc).sp, ink, FontWeight.Black, maxLines = 1) }
}

@Composable
private fun SeatView(
    k: Int, pt: Offset, geo: Geo, sb: Int, bb: Int, lead: Int, leadCnt: Int, moving: Boolean, drop: Boolean,
    onDragStart: () -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit,
) {
    watch()
    val g = Game
    val st = g.st
    val s = st.seats[k]
    val p = g.pl(s)
    val sc = geo.sc / 0.8f
    val playing = g.playing()
    val isTurn = playing && st.turn == k && !(g.gm() && st.done != null)
    val folded = s.folded && playing
    val last = if (playing) s.actions.filter { it.st == st.betStreet && it.a != "sb" && it.a != "bb" }.lastOrNull() else null
    val isLead = playing && st.n > 1 && s.stack + s.bet == lead && leadCnt == 1
    val crit = playing && !s.allin && s.stack > 0 && s.stack <= 10 * st.bb
    val won = g.gm() && st.done != null && st.done!!.amt.getOrElse(k) { 0 } > 0
    val pulse = rememberInfiniteTransition(label = "turn").animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "g")
    val density = LocalDensity.current
    val seatW = 156f * sc
    Box(
        Modifier
            .offset((pt.x - seatW / 2).dp, (pt.y - 25 * sc).dp)
            .width(seatW.dp)
            .zIndex(if (isTurn) 5f else 3f)
            .graphicsLayer(
                alpha = if (moving) 0.3f else if (s.out) 0.4f else if (folded) 0.5f else if (playing && st.turn >= 0 && !isTurn && k != 0) 0.85f else 1f,
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha,
            )
            .pointerInput(k, st.phase) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { ch, amount -> ch.consume(); with(density) { onDrag(Offset(amount.x.toDp().value, amount.y.toDp().value)) } },
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = { Ui.moodSeat = -1; if (k != 0 && g.gm()) Ui.seatMenu = k else Ui.open("seat", k) },
                onLongClick = { Ui.moodSeat = k },
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                // stack chip
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Column(horizontalAlignment = Alignment.End) {
                        StackChip(if (s.allin && playing) "ALL-IN" else g.shortN(s.stack), s.allin && playing, crit, sc)
                        if (s.stack > 0) Box(Modifier.padding(top = (2 * sc).dp, end = (4 * sc).dp)) { ChipPile(s.stack, 3, (12 * sc).dp) }
                        SeatDrinks(s.pid, sc)
                    }
                }
                Spacer(Modifier.width((5 * sc).dp))
                // avatar
                Box(Modifier.size((50 * sc).dp)) {
                    if (isTurn) Box(Modifier.fillMaxSize().graphicsLayer(scaleX = 1.18f, scaleY = 1.18f).clip(CircleShape).background(C.gold.copy(alpha = pulse.value * 0.55f)))
                    if (isTurn && g.online()) TurnTimer(st.turnAt, Modifier.fillMaxSize().graphicsLayer(scaleX = 1.3f, scaleY = 1.3f))
                    if (drop) Box(Modifier.fillMaxSize().graphicsLayer(scaleX = 1.2f, scaleY = 1.2f).clip(CircleShape).background(C.gold.copy(alpha = 0.7f)))
                    Avatar(p, (50 * sc).dp, ring = if (isTurn || (k == 0 && g.online())) C.gold else null, border = (3 * sc).dp, dim = folded)
                    if (g.online() && com.onoseto.chipsnfish.online.Online.players.any { it.uid == s.pid && (!it.on || it.left) }) Box(Modifier.align(Alignment.BottomStart).size((12 * sc).dp).clip(CircleShape).background(C.lose).border(1.5.dp, Color.Black, CircleShape))
                    if (isLead) Crown(Modifier.align(Alignment.TopCenter).offset(y = (-14 * sc).dp).size((24 * sc).dp, (16 * sc).dp))
                    if (g.gm() && k > 0 && playing && !s.folded && !s.out && st.reveal?.get(k) == null && g.peekCards(k) == null && st.one?.get(k) == null) {
                        Row(Modifier.align(Alignment.TopEnd).offset((12 * sc).dp, (-4 * sc).dp)) {
                            CardBack((16 * sc).dp, modifier = Modifier.rotate(-8f))
                            CardBack((16 * sc).dp, modifier = Modifier.offset((-8 * sc).dp).rotate(10f))
                        }
                    }
                    if (s.mood.isNotEmpty()) T(s.mood.joinToString(""), (12 * sc).sp, modifier = Modifier.align(Alignment.BottomStart).offset((-8 * sc).dp, (6 * sc).dp))
                }
                Spacer(Modifier.width((5 * sc).dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val rv = st.reveal?.get(k)
                    val pk = g.peekCards(k)
                    if (g.gm() && rv != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { rv.forEach { CardView(it, (25 * sc).dp) } }
                    } else if (pk != null) {
                        // I folded and this player lets folded players look
                        Box {
                            Row(Modifier.alpha(0.85f), horizontalArrangement = Arrangement.spacedBy(2.dp)) { pk.forEach { CardView(it, (25 * sc).dp) } }
                            T("👁", (10 * sc).sp, modifier = Modifier.align(Alignment.TopEnd).offset((4 * sc).dp, (-6 * sc).dp))
                        }
                    } else if (g.gm() && st.one?.get(k) != null && !s.folded) {
                        // one card shown on request
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { CardView(st.one!!.getValue(k), (25 * sc).dp); CardBack((25 * sc).dp) }
                    }
                }
            }
            // name plate
            Row(
                Modifier.offset(y = (-13 * sc).dp).zIndex(3f).clip(RoundedCornerShape(99.dp)).background(Color(0xEB0A1015))
                    .border(1.dp, if (isTurn) C.gold else C.seatLine, RoundedCornerShape(99.dp))
                    .padding(horizontal = (8 * sc).dp, vertical = (1 * sc).dp)
                    .widthIn(max = (156 * sc).dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((4 * sc).dp),
            ) {
                T(if (k == 0 && p.id == "hero") p.name else p.name, (12.5f * sc).sp, if (isTurn) C.gold else C.ink, FontWeight.Bold, maxLines = 1, modifier = Modifier.widthIn(max = (90 * sc).dp))
                val stt: Pair<String, Color>? = when {
                    s.out -> "OUT" to C.fold
                    won -> ("+" + g.fm(st.done!!.amt[k])) to C.win
                    playing && s.folded -> "FOLD" to Color(0xFFE05548)
                    playing && last != null -> (Reads.actl(last.a) + if (s.bet > 0) " " + g.fm(s.bet) else "") to (if (Reads.agg(last.a)) C.gold else if (last.a == "call") C.win else Color(0xFFCFD8DC))
                    playing && s.bet > 0 -> g.fm(s.bet) to Color(0xFFCFD8DC)
                    playing && s.allin -> "ALL-IN" to C.gold
                    else -> null
                }
                if (stt != null) {
                    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(stt.second).padding(horizontal = (4 * sc).dp)) {
                        T(stt.first, (10 * sc).sp, if (stt.second == C.fold || stt.second == Color(0xFFE05548)) Color.White else Color(0xFF111111), FontWeight.Black, lineHeight = (14 * sc).sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Text2(t: String, size: Float, col: Color, underline: Boolean) {
    watch()
    androidx.compose.material3.Text(
        t, color = col, fontSize = size.sp, fontFamily = F.num, fontWeight = FontWeight.ExtraBold,
        style = TextStyle(textDecoration = if (underline) TextDecoration.Underline else null, shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 1f), 4f)),
    )
}

@Composable
fun StackChip(text: String, allin: Boolean, crit: Boolean, sc: Float) {
    watch()
    val shape = RoundedCornerShape(99.dp)
    Box(
        Modifier.clip(shape)
            .background(if (allin) Color(0xFFB3261E) else Color(0xF20B1217))
            .border((1.5f * sc).dp, if (allin) Color(0xFFFF8A80) else C.gold.copy(alpha = 0.85f), shape)
            .padding(horizontal = (7 * sc).dp, vertical = (1 * sc).dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, (15 * sc).sp, if (allin) Color.White else if (crit) Color(0xFFFF8A80) else C.gold, num = true, maxLines = 1, lineHeight = (17 * sc).sp)
    }
}

@Composable
fun Crown(modifier: Modifier) {
    watch()
    Canvas(modifier) {
        val sx = size.width / 24f; val sy = size.height / 16f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(2 * sx, 14 * sy); lineTo(22 * sx, 14 * sy); lineTo(23.5f * sx, 3 * sy); lineTo(17.5f * sx, 8 * sy)
            lineTo(12 * sx, 0f); lineTo(6.5f * sx, 8 * sy); lineTo(0.5f * sx, 3 * sy); close()
        }
        drawPath(p, C.gold)
        drawPath(p, Color(0xFF7A5408), style = Stroke(1.2f * sx))
        drawCircle(Color(0xFFD3121D), 1.6f * sx, Offset(12 * sx, 9.5f * sy))
    }
}

@Composable
private fun MoodPop(geo: Geo, w: Float, h: Float) {
    watch()
    val k = Ui.moodSeat
    val s = Game.st.seats.getOrNull(k) ?: return
    val p = geo.pt(Game.st.pos!![k])
    val x = max(4f, min(w - 284, p.x - 140))
    val y = if (p.y > h / 2) p.y - 58 else p.y + 44
    Row(
        Modifier.offset(x.dp, y.dp).zIndex(20f).width(280.dp).shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Column {
            Row { (Game.MOODS.take(7)).forEach { MoodBtn(it, s.mood.contains(it)) { toggleMood(k, it) } } }
            Row {
                Game.MOODS.drop(7).forEach { MoodBtn(it, s.mood.contains(it)) { toggleMood(k, it) } }
                MoodBtn("✕", false) { s.mood.clear(); Game.saveLocal(); Game.bump() }
                MoodBtn("✓", false) { Ui.moodSeat = -1 }
            }
        }
    }
}

private fun toggleMood(k: Int, m: String) {
    val s = Game.st.seats[k]
    if (s.mood.contains(m)) s.mood.remove(m) else s.mood.add(m)
    Game.saveLocal(); Game.bump()
}

@Composable
private fun MoodBtn(t: String, on: Boolean, onClick: () -> Unit) {
    watch()
    Box(
        Modifier.padding(1.dp).size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (on) C.gold.copy(alpha = 0.3f) else C.panel2).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { T(t, 18.sp) }
}

@Composable
private fun FishFx(geo: Geo, w: Float, h: Float) {
    watch()
    // speech bubble
    val txt = Ui.fishText
    if (txt != null) {
        val id = Ui.fishId
        var vis by remember(id) { mutableStateOf(true) }
        LaunchedEffect(id) { delay(3200); vis = false }
        if (vis) {
            Box(Modifier.offset((geo.cx - 150).dp, (geo.cy - 70).dp).width(300.dp).zIndex(15f), contentAlignment = Alignment.Center) {
                Box(Modifier.shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    T(txt, 14.sp, Color(0xFF1D4F7A), FontWeight.ExtraBold, align = TextAlign.Center)
                }
            }
        }
    }
    // swimming fish chip
    val sw = Ui.swimId
    if (sw > 0) {
        val x = remember(sw) { androidx.compose.animation.core.Animatable(-60f) }
        val y = remember(sw) { 0.15f + Math.random().toFloat() * 0.6f }
        LaunchedEffect(sw) { x.snapTo(-60f); x.animateTo(w + 60, tween(4200, easing = LinearEasing)) }
        if (x.value < w + 50) FishLogo(Modifier.offset(x.value.dp, (h * y).dp).size(44.dp).zIndex(14f).graphicsLayer(rotationZ = sin(x.value / 30f) * 8f))
    }
    // chip rain
    val rn = Ui.rainId
    if (rn > 0) {
        val t = remember(rn) { androidx.compose.animation.core.Animatable(0f) }
        val drops = remember(rn) { List(160) { floatArrayOf(Math.random().toFloat(), 16 + Math.random().toFloat() * 30, Math.random().toFloat() * 0.55f, 0.25f + Math.random().toFloat() * 0.3f, (Math.random().toFloat() * 1440 - 720)) } }
        LaunchedEffect(rn) { t.snapTo(0f); t.animateTo(1f, tween(5000, easing = LinearEasing)) }
        if (t.value < 1f) {
            drops.forEach { d ->
                val f = (t.value - d[2]) / d[3]
                if (f in 0f..1f) {
                    FishLogo(Modifier.offset((d[0] * w).dp, (-40 + f * (h + 80)).dp).size(d[1].dp).zIndex(16f).graphicsLayer(rotationZ = d[4] * f))
                }
            }
        }
    }
}

// ---------------- right column ----------------

@Composable
private fun ActBox() {
    watch()
    val g = Game
    val st = g.st
    @Suppress("UNUSED_VARIABLE") val rev = g.rev
    // armed check/fold: check when it's free, otherwise fold — as soon as it's my turn
    LaunchedEffect(st.turn, st.handNo, st.betStreet, g.preCF, g.preCall) {
        if (g.gm() && st.turn == 0 && st.done == null && (g.preCF == st.handNo || g.preCall == st.handNo)) {
            delay(450)
            if (st.turn == 0 && st.done == null) {
                val tc = max(0, st.curBet - st.seats[0].bet)
                when {
                    g.preCall == st.handNo -> { g.preCall = -1; g.heroAction("call") }
                    g.preCF == st.handNo -> { g.preCF = -1; g.heroAction(if (tc == 0) "call" else "fold") }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom), horizontalAlignment = Alignment.End) {
        if (st.phase == "ready") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(10.dp)).padding(6.dp)) {
                T(tr("Připraveno"), 15.sp, C.gold, num = true)
                T(L("${st.n} hráčů · blindy ${Cards.lvlStr(st.level)}", "${st.n} players · blinds ${Cards.lvlStr(st.level)}", "${st.n} Spieler · Blinds ${Cards.lvlStr(st.level)}"), 11.sp, C.muted)
                T(L("dealer: ", "dealer: ", "Dealer: ") + g.pl(st.seats[st.dealer]).name, 11.sp, C.muted, maxLines = 1)
            }
            ColBtn(tr("▶ START"), BK.CALL, h = 70.dp) {
                st.blindStart = System.currentTimeMillis(); st.phase = "play"; st.handNo = 0; st.sample = false
                g.startHand(); g.update(); g.toast(L("Hra začala · hand #1", "Game on · hand #1", "Spiel läuft · Hand #1"))
            }
            ColBtn(tr("Zpět k přípravě"), BK.DARK) { st.phase = "setup"; g.update() }
            return@Column
        }
        if (g.online()) { if (OnlineBox()) return@Column }
        if (!g.playing()) { SetupBench(); return@Column }
        // hole cards stay pinned at the top of the column; the buttons sit at the bottom, so the cards never jump
        val hc: @Composable ColumnScope.() -> Unit = { MyCards(); Spacer(Modifier.weight(1f)) }
        if (g.bot()) {
            val over = st.over
            if (over != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (over.win) C.win.copy(alpha = 0.2f) else C.panel).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(tr(if (over.win) "Vyhrál jsi celou hru!" else "Vypadl jsi"), 13.sp, if (over.win) C.win else C.ink, FontWeight.ExtraBold, align = TextAlign.Center)
                    T(if (over.win) "🏆" else L("${over.place}. místo", "${over.place}th place", "${over.place}. Platz"), 16.sp, C.gold, num = true)
                }
                if (!over.win && st.watchPlace == 0 && st.seats.count { it.stack > 0 } >= 2)
                    ColBtn("👀 " + L("Sledovat dál", "Keep watching", "Weiter zusehen"), BK.DARK, h = 44.dp) { g.watchOn() }
                ColBtn(tr("Nová hra"), BK.CALL) { Ui.openGameSetup() }
                ColBtn(tr("Menu"), BK.DARK) { Ui.showMenu() }
                return@Column
            }
            if (st.watchPlace > 0) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("👀 " + L("Sleduješ hru", "Watching", "Du schaust zu"), 12.sp, C.ink, FontWeight.ExtraBold, align = TextAlign.Center, maxLines = 1, fit = true)
                    T(L("vypadl jsi na ${st.watchPlace}. místě", "you finished ${st.watchPlace}th", "du bist ${st.watchPlace}. geworden"), 10.5.sp, C.muted, align = TextAlign.Center)
                }
                if (st.done != null) ColBtn(tr("Další hand") + " ▶", BK.CALL, h = 56.dp) { if (g.gameNext()) Ui.openGameSetup() }
                ColBtn(tr("Nová hra"), BK.DARK, h = 44.dp) { Ui.openGameSetup() }
                return@Column
            }
            if (st.done != null) {
                hc()
                ColBtn(tr("Další hand") + " ▶", BK.CALL, h = 64.dp) { if (g.gameNext()) Ui.openGameSetup() }
                return@Column
            }
        }
        if (g.gm()) {
            if (st.turn != 0) {
                hc()
                val me = st.seats[0]
                if (st.done == null && st.turn >= 0 && !me.folded && !me.out && !me.allin && me.stack > 0) {
                    Box(Modifier.fillMaxWidth().height(170.dp)) {
                        val cf = g.preCF == st.handNo; val ca = g.preCall == st.handNo
                        T(L("PŘEDVOLBA", "PRE-SELECT", "VORWAHL"), 9.5.sp, C.muted, FontWeight.ExtraBold, letter = 1.sp, modifier = Modifier.align(Alignment.BottomEnd).offset(y = (-60).dp))
                        GBtn(if (cf) BK.FOLD else BK.DARK, Modifier.align(Alignment.BottomEnd).offset(x = (-90).dp).size(70.dp, 54.dp), on = cf, onClick = { g.preCF = if (cf) -1 else st.handNo; if (g.preCF >= 0) g.preCall = -1; g.bump() }) {
                            T((if (cf) "✓ " else "") + "CHECK\nFOLD", 10.5.sp, Color.White, num = true, maxLines = 2, lineHeight = 12.sp, align = TextAlign.Center)
                        }
                        GBtn(if (ca) BK.CALL else BK.DARK, Modifier.align(Alignment.BottomEnd).size(86.dp, 54.dp), on = ca, onClick = { g.preCall = if (ca) -1 else st.handNo; if (g.preCall >= 0) g.preCF = -1; g.bump() }) {
                            T((if (ca) "✓ " else "") + "CALL\nANY", 11.sp, Color.White, num = true, maxLines = 2, lineHeight = 13.sp, align = TextAlign.Center)
                        }
                    }
                }
                return@Column
            }
        }
        val undo = g.playing() && g.canUndo() && !g.gm()
        if (g.live().size <= 1) {
            hc()
            if (undo) ColBtn(tr("↶ Zpět"), BK.DARK, h = 32.dp) { g.undo() }
            ColBtn(tr("Uložit handu"), BK.RAISE) { Ui.open("end", g.newEnd()) }
            return@Column
        }
        if (st.turn < 0) {
            hc()
            if (undo) ColBtn(tr("↶ Zpět"), BK.DARK, h = 32.dp) { g.undo() }
            val s = st.betStreet
            if (s < 3) {
                ColBtn(tr("Ukončit"), BK.DARK) { Ui.open("end", g.newEnd()) }
                ColBtn(tr("Rozdat " + arrayOf("flop", "turn", "river")[s]), BK.CALL) {
                    val slot = g.nextBoardSlot()
                    if (slot < 0) g.toast(tr("Teď se žádná karta nerozdává")) else Ui.open("card", CardArg(slot))
                }
            } else ColBtn(tr("Showdown"), BK.RAISE) { Ui.open("end", g.newEnd()) }
            return@Column
        }
        val k = st.turn
        val s = st.seats[k]
        val toCall = max(0, st.curBet - s.bet)
        val (_, mx) = g.minMax(s)
        var ring: Color? = null
        val e = g.eq
        if (k == 0 && g.advisorOn() && toCall > 0 && e != null && !e.allFolded && !st.hero.contains(null)) {
            val need = toCall.toDouble() / (g.potTotal() + toCall)
            ring = if (e.eq >= need) C.win else C.lose
        }
        hc()
        if (undo) ColBtn(tr("↶ Zpět"), BK.DARK, h = 32.dp) { g.undo() }
        // corner controls
        val (mn, _) = g.minMax(s)
        val canRaise = mx > st.curBet
        LaunchedEffect(k, st.betStreet, st.curBet) { if (g.raiseTo !in mn..mx) g.raiseTo = mn }
        var foldArm by remember(st.handNo, st.betStreet, st.turn) { mutableStateOf(false) }
        LaunchedEffect(foldArm) { if (foldArm) { delay(2500); foldArm = false } }
        // corner controls: CALL in the corner, RAISE beside it on the bottom edge (slide it up for more),
        // FOLD above CALL. Bet sizes appear only after tapping RAISE.
        var betOpen by remember(k, st.handNo, st.betStreet) { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            GBtn(BK.CALL, Modifier.align(Alignment.BottomEnd).size(86.dp, 54.dp), ring = ring, onClick = { g.heroAction("call") }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        toCall == 0 -> T("CHECK", 15.sp, Color.White, num = true, maxLines = 1)
                        toCall >= s.stack -> { T("ALL-IN", 12.sp, Color.White, num = true, lineHeight = 13.sp, maxLines = 1); T(g.fm(s.stack), 14.sp, Color.White, num = true, lineHeight = 15.sp, maxLines = 1) }
                        else -> { T("CALL", 12.sp, Color.White, num = true, lineHeight = 13.sp, maxLines = 1); T(g.fm(toCall), 14.sp, Color.White, num = true, lineHeight = 15.sp, maxLines = 1) }
                    }
                }
            }
            GBtn(if (toCall == 0 && !foldArm) BK.DARK else BK.FOLD, Modifier.align(Alignment.BottomEnd).offset(y = (-58).dp).size(86.dp, 44.dp), onClick = {
                if (toCall == 0 && !foldArm) foldArm = true else g.heroAction("fold")
            }) { T(if (foldArm) "FOLD?" else "FOLD", 13.sp, Color.White, num = true, maxLines = 1) }
            // RAISE: first tap shows the sizes, next tap bets; slide up / down = more / less; hold = exact amount
            Box(Modifier.align(Alignment.BottomEnd).offset(x = (-90).dp).size(82.dp, 54.dp)) {
                GBtn(BK.RAISE, Modifier.fillMaxSize(), enabled = canRaise, on = betOpen, onClick = { }) {
                    val to = g.raiseTo.coerceIn(mn, mx)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T((if (to >= mx) "ALL-IN" else if (st.curBet == 0) "BET" else "RAISE") + " ↕", 11.sp, C.goldInk, num = true, lineHeight = 12.sp, maxLines = 1)
                        T(g.fm(to), 15.sp, C.goldInk, num = true, lineHeight = 16.sp, maxLines = 1)
                    }
                }
                if (canRaise) {
                    val stepPx = with(LocalDensity.current) { 9.dp.toPx() }
                    Box(Modifier.matchParentSize()
                        .pointerInput(k, st.betStreet) {
                            detectTapGestures(
                                onTap = { if (!betOpen) betOpen = true else { betOpen = false; g.raiseOpen = false; g.heroAction("raise") } },
                                onLongPress = { g.heroAction("raiseopen") },
                            )
                        }
                        .pointerInput(k, st.betStreet) {
                            var acc = 0f
                            detectVerticalDragGestures(onDragStart = { acc = 0f; betOpen = true }) { ch, dy ->
                                ch.consume()
                                acc -= dy
                                val n = (acc / stepPx).toInt()
                                if (n != 0) {
                                    acc -= n * stepPx
                                    g.raiseTo = (g.raiseTo.coerceIn(mn, mx) + n * st.bb.coerceAtLeast(1)).coerceIn(mn, mx); g.bump()
                                }
                            }
                        })
                }
            }
            // bet sizes, shown above RAISE once it was tapped
            if (canRaise && betOpen) {
                val pt = g.potTotal()
                fun pre(f: Double) = (((st.curBet + f * (pt + toCall)) / g.step()).roundToInt() * g.step()).coerceIn(mn, mx)
                Column(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-90).dp, y = (-58).dp).width(82.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xE60E1622)).border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf(listOf("MIN" to mn, "½" to pre(0.5)), listOf("POT" to pre(1.0), "MAX" to mx)).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            row.forEach { (l, x) ->
                                val on = g.raiseTo == x
                                Box(
                                    Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(7.dp)).background(if (on) C.gold.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.07f))
                                        .border(1.dp, if (on) C.gold else Color.White.copy(alpha = 0.14f), RoundedCornerShape(7.dp)).tap { g.raiseTo = x; g.bump() },
                                    contentAlignment = Alignment.Center,
                                ) { T(l, 10.sp, if (on) C.gold else Color.White, FontWeight.ExtraBold, maxLines = 1) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        GBtn(BK.DARK, Modifier.weight(1f).height(26.dp), enabled = g.raiseTo > mn, onClick = { g.raiseTo = (g.raiseTo - st.bb.coerceAtLeast(1)).coerceIn(mn, mx); g.bump() }) { T("−", 15.sp, Color.White, FontWeight.Black, lineHeight = 15.sp) }
                        GBtn(BK.DARK, Modifier.weight(1f).height(26.dp), enabled = g.raiseTo < mx, onClick = { g.raiseTo = (g.raiseTo + st.bb.coerceAtLeast(1)).coerceIn(mn, mx); g.bump() }) { T("+", 15.sp, Color.White, FontWeight.Black, lineHeight = 15.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColBtn(text: String, kind: BK, h: Dp = 50.dp, enabled: Boolean = true, onClick: () -> Unit) {
    watch()
    GBtn(kind, Modifier.width(124.dp).height(h), enabled = enabled, onClick = onClick) {
        T(text.uppercase(), 13.5.sp, btnInk(kind), num = true, align = TextAlign.Center, maxLines = 2, lineHeight = 15.sp, fit = true)
    }
}

@Composable
private fun TurnWho(k: Int) {
    watch()
    val me = k == 0
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(99.dp)).background(if (me) C.gold.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.35f))
            .border(1.dp, if (me) C.gold.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(99.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        val name = if (k < 0) "…" else if (me) tr("Ty").uppercase() else Game.pl(Game.st.seats[k]).name
        if (!me) T(L("NA TAHU", "TO ACT", "AM ZUG"), 10.sp, Color.White.copy(alpha = 0.55f), FontWeight.ExtraBold, letter = 1.sp, maxLines = 1)
        T(if (me) L("HRAJEŠ TY", "YOUR TURN", "DU BIST DRAN") else name, 15.sp, C.gold, num = true, maxLines = 1)
        if (k >= 0 && Game.online()) {
            val left = secondsLeft(Game.st.turnAt)
            if (left <= 30) T("${left}s", 11.sp, if (left <= 10) C.lose else C.muted, FontWeight.Bold)
        }
    }
}

@Composable
private fun MyCards() {
    watch()
    val g = Game
    val st = g.st
    val cw = 80.dp
    val both = !st.hero.contains(null)
    val nx = g.firstEmpty()
    val folded = st.seats[0].folded
    val info: String = if (both) {
        val sc = Cards.ev(st.hero.map { it!! } + g.boardKnown())
        val c = Cards.cat(sc)
        var e = ""
        val eq = g.eq
        if (eq != null && !folded && g.advisorOn()) e = " · ${((if (eq.allFolded) 1.0 else eq.eq) * 100).roundToInt()} %"
        I18n.cat(Cards.CAT[c] + " " + Cards.rl((sc shr 16) and 15)) + e
    } else if (st.hero.any { it != null }) tr("vyber druhou kartu") else tr("vyber své karty")
    // optional: keep my cards face down, press and hold to look
    val hidden = g.gm() && g.look.hide && both && !Ui.peek
    Box(Modifier.fillMaxWidth().height(cw * 1.4f + 22.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxWidth().alpha(if (folded && g.playing()) 0.45f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // the two hole cards side by side, both fully visible
            // drag the cards to the middle of the table to fold
            val me = st.seats[0]
            val canFold = g.gm() && g.playing() && st.done == null && both && !me.folded && !me.out && !me.allin
            var home by remember { mutableStateOf(Offset.Zero) }
            var dragBy by remember { mutableStateOf(Offset.Zero) }
            Row(
                Modifier
                    .onGloballyPositioned { home = it.boundsInRoot().center }
                    .graphicsLayer { translationX = dragBy.x; translationY = dragBy.y; rotationZ = (dragBy.x / 40f).coerceIn(-12f, 12f) }
                    .then(if (canFold) Modifier.pointerInput(st.handNo) {
                        detectDragGestures(
                            onDragStart = { Ui.foldDrag = Offset.Zero },
                            onDragEnd = {
                                val hot = Ui.foldHot
                                dragBy = Offset.Zero; Ui.foldDrag = null; Ui.foldHot = false
                                if (hot) {
                                    if (st.turn == 0) g.heroAction("fold")
                                    else { g.preCF = st.handNo; g.preCall = -1; g.bump(); g.toast(L("Až budeš na tahu: check, jinak fold", "On your turn: check, otherwise fold", "Wenn du dran bist: Check, sonst Fold")) }
                                }
                            },
                            onDragCancel = { dragBy = Offset.Zero; Ui.foldDrag = null; Ui.foldHot = false },
                            onDrag = { ch, d ->
                                ch.consume()
                                dragBy += d
                                Ui.foldDrag = dragBy
                                Ui.foldHot = Ui.foldR > 0f && (home + dragBy - Ui.foldZone).getDistance() < Ui.foldR * 1.25f
                            },
                        )
                    } else Modifier)
                    .then(if (g.gm() && g.look.hide) Modifier.pointerInput(Unit) { detectTapGestures(onPress = { Ui.peek = true; tryAwaitRelease(); Ui.peek = false }) } else Modifier),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in 0..1) {
                    val c = st.hero[i]
                    when {
                        c == null -> CardSlot(cw, "+", next = nx == i, modifier = Modifier.tap { openCardSlot(i) })
                        g.gm() -> DealIn("${st.handNo}-h$i-$c") { if (hidden) CardBack(cw) else CardView(c, cw, glow = true) }
                        else -> {
                            Box(Modifier.pointerInput(i) {
                                detectTapGestures(
                                    onPress = { Ui.peek = true; tryAwaitRelease(); Ui.peek = false },
                                    onTap = { openCardSlot(i) },
                                )
                            }) { if (Ui.peek) CardView(c, cw, glow = true) else CardBack(cw) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f)) {
                    if (both && ((!g.gm() && !Ui.peek) || hidden)) T(tr("podrž = ukázat"), 11.sp, C.muted, FontWeight.Bold, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    else T(info.replace(" · ", "  "), 12.sp, C.gold, FontWeight.Bold, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                // eye: hide / show my cards
                if (g.gm()) Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (g.look.hide) C.gold.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                        .tap { g.look.hide = !g.look.hide; g.saveLook(); g.bump(); g.toast(if (g.look.hide) L("Karty lícem dolů · podrž je pro ukázání", "Cards face down · press and hold to look", "Karten verdeckt · gedrückt halten zum Ansehen") else L("Karty lícem nahoru", "Cards face up", "Karten offen")) },
                    contentAlignment = Alignment.Center,
                ) { T(if (g.look.hide) "🙈" else "👁", 12.sp) }
            }
        }
    }
}

@Composable
private fun SetupBench() {
    watch()
    val g = Game
    val st = g.st
    ColBtn(tr("Nastavení"), BK.DARK, h = 40.dp) { Ui.openSettings() }
    ColBtn(tr("Připravit"), BK.CALL, h = 44.dp) {
        if (st.n < 2) g.toast(tr("Posaď ke stolu aspoň jednoho soupeře"))
        else { st.phase = "ready"; st.sample = false; g.update() }
    }
    val all = g.P.values.filter { it.id != "hero" && it.stored }
        .sortedWith(compareBy<com.onoseto.chipsnfish.game.Player> { p -> st.seats.any { it.pid == p.id } }.thenBy { it.name.lowercase() })
    Column(
        Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(10.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState()).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(tr("Hráči"))
        if (all.isEmpty()) T(tr("Seznam hráčů je prázdný. Přidej je v záložce Hráči."), 10.sp, C.muted, align = TextAlign.Center)
        BenchItem(null) {
            val np = g.newPlayer(L("Hráč ", "Player ", "Spieler ") + st.n)
            g.storePlayer(np); seatFromBench(np.id)
        }
        all.forEach { q ->
            val sat = st.seats.any { it.pid == q.id }
            BenchItem(q, sat) { if (!sat) seatFromBench(q.id) }
        }
    }
}

private fun seatFromBench(pid: String) {
    val g = Game
    val st = g.st
    if (st.n >= 10) { g.toast(tr("Maximálně 10 hráčů")); return }
    g.ensurePos()
    st.seats.add(g.seatObj(pid, st.startStack))
    st.pos!!.add(0.0)
    st.n++
    g.respace()
    g.saveLayout()
    g.update()
    g.toast(tr("Hráč přisedl"))
}

@Composable
private fun BenchItem(p: com.onoseto.chipsnfish.game.Player?, sat: Boolean = false, onClick: () -> Unit) {
    watch()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (sat) Color.Transparent else C.panel2).alpha(if (sat) 0.5f else 1f).tap(onClick).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (p == null) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(C.gold.copy(alpha = 0.2f)).border(2.dp, C.gold, CircleShape), contentAlignment = Alignment.Center) { T("+", 14.sp, C.gold, num = true) }
            T(tr("Nový"), 11.sp, C.gold, FontWeight.Bold)
        } else {
            Avatar(p, 24.dp, border = 2.dp)
            T(p.name, 11.sp, C.ink, FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            if (sat) T("✓", 11.sp, C.win)
        }
    }
}

// ---------------- raise popup ----------------

@Composable
private fun RaisePop() {
    watch()
    val g = Game
    val st = g.st
    val k = st.turn
    val s = st.seats[k]
    val (mn, mx) = g.minMax(s)
    val toCall = max(0, st.curBet - s.bet)
    if (g.raiseTo < mn || g.raiseTo > mx) g.raiseTo = mn
    var v by remember(k, st.betStreet, st.curBet) { mutableFloatStateOf(g.raiseTo.toFloat()) }
    var txt by remember(k, st.betStreet, st.curBet) { mutableStateOf(g.raiseTo.toString()) }
    fun setTo(x: Int) { g.raiseTo = x.coerceIn(mn, mx); v = g.raiseTo.toFloat(); txt = g.raiseTo.toString() }
    val pt = g.potTotal()
    fun pre(f: Double) = (((st.curBet + f * (pt + toCall)) / g.step()).roundToInt() * g.step()).coerceIn(mn, mx)
    val raiseLbl = if (st.curBet == 0) "Bet" else "Raise"
    Column(
        Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color(0xFF141F27))
            .border(1.dp, C.gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Slider(
                value = v, onValueChange = { x -> val st2 = g.step(); val r = ((x / st2).roundToInt() * st2).coerceIn(mn, mx); g.raiseTo = r; v = r.toFloat(); txt = r.toString() },
                valueRange = mn.toFloat()..max(mn + 1, mx).toFloat(), modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = C.gold, activeTrackColor = C.gold, inactiveTrackColor = C.line),
            )
            BasicTextField(
                txt, { t -> txt = t.filter { it.isDigit() }.take(9); txt.toIntOrNull()?.let { g.raiseTo = it.coerceIn(mn, mx); v = g.raiseTo.toFloat() } },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = C.gold, fontSize = 22.sp, fontFamily = F.num, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(C.gold),
                modifier = Modifier.width(96.dp).height(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0B1217)).border(2.dp, C.gold, RoundedCornerShape(10.dp)).padding(top = 6.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Min" to mn, tr("½ potu") to pre(0.5), tr("¾ potu") to pre(0.75), "Pot" to pre(1.0), "All-in" to mx).forEach { (l, x) ->
                Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(9.dp)).tap { setTo(x) }, contentAlignment = Alignment.Center) {
                    T(l, 11.5.sp, C.ink, FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GBtn(BK.DARK, Modifier.weight(1f).height(46.dp), onClick = { g.raiseOpen = false; g.bump() }) { T(tr("Zrušit"), 14.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true) }
            GBtn(BK.RAISE, Modifier.weight(2.2f).height(46.dp), onClick = { g.heroAction("raise") }) {
                val to = g.raiseTo
                T(if (to >= mx) "All-in " + g.fm(mx) else raiseLbl + L(" na ", " to ", " auf ") + g.fm(to), 16.sp, C.goldInk, num = true, maxLines = 1, fit = true)
            }
        }
    }
}


// ---------------- online table ----------------

fun share(ctx: android.content.Context, text: String) {
    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    ctx.startActivity(android.content.Intent.createChooser(i, L("Pozvat přátele", "Invite friends", "Freunde einladen")))
}

@Composable
private fun OnlineLobbyCenter() {
    watch()
    val O = com.onoseto.chipsnfish.online.Online
    val code = O.code ?: ""
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (O.name.isNotBlank()) T(O.name, 16.sp, Color.White.copy(alpha = 0.85f), FontWeight.ExtraBold)
        T(L("KÓD STOLU", "TABLE CODE", "TISCHCODE"), 11.sp, Color.White.copy(alpha = 0.6f), FontWeight.ExtraBold, letter = 2.sp)
        T(code, 40.sp, C.gold, num = true, letter = 6.sp)
        T(L("Čekáme na hráče · u stolu ${Game.st.seats.size}", "Waiting for players · ${Game.st.seats.size} at the table", "Warten auf Spieler · ${Game.st.seats.size} am Tisch"), 13.sp, Color.White.copy(alpha = 0.75f), FontWeight.SemiBold)
        if (O.cup || O.cupVote != null) { Spacer(Modifier.height(4.dp)); CupBanner() }
        else T(L("Pošli přátelům pozvánku tlačítkem vpravo.", "Send your friends an invite with the button on the right.", "Schick deinen Freunden eine Einladung mit dem Knopf rechts."), 12.sp, Color.White.copy(alpha = 0.55f), align = TextAlign.Center)
        if (!O.connected) T(L("Připojuji…", "Connecting…", "Verbinde…"), 12.sp, C.lose, FontWeight.Bold)
    }
}

/** Right column for an online table outside of my own betting turn. Returns true when it drew the column. */
@Composable
private fun ColumnScope.OnlineBox(): Boolean {
    watch()
    val g = Game
    val st = g.st
    val O = com.onoseto.chipsnfish.online.Online
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val seated = st.seats.isNotEmpty() && st.seats[0].pid == O.me
    val leave = {
        Ui.confirm(L("Opustit stůl?", "Leave the table?", "Tisch verlassen?"),
            L("Pokud právě hraješ, tvoje karty se zahodí. Ke stolu se můžeš vrátit stejným kódem.", "If you're in a hand, your cards are folded. You can come back with the same code.", "Wenn du in einer Hand bist, wird gefoldet. Mit demselben Code kannst du zurück."),
            L("Opustit", "Leave", "Verlassen")) { O.leave { Ui.showMenu() } }
    }
    if (st.phase == "setup") {
        ColBtn(L("Pozvat přátele", "Invite friends", "Freunde einladen"), BK.CALL, h = 56.dp) { share(ctx, O.inviteText()) }
        if (O.isHost) ColBtn(tr("▶ START"), BK.RAISE, h = 56.dp, enabled = st.seats.size >= 2 && !O.busy) { O.start() }
        if (!O.cup && O.cupVote == null) ColBtn("🏆 " + L("Turnaj", "Cup", "Turnier"), BK.GOLD, h = 44.dp) {
            if (O.players.count { !it.left } < 3) Game.toast(L("Turnaj o pohár potřebuje aspoň 3 hráče u stolu.", "A cup tournament needs at least 3 players.", "Ein Pokalturnier braucht mindestens 3 Spieler."))
            else Ui.open("cup")
        }
        else Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(10.dp)).padding(6.dp)) {
            T(L("Hru spustí zakladatel stolu.", "The host starts the game.", "Der Gastgeber startet das Spiel."), 11.sp, C.muted, align = TextAlign.Center)
        }
        ColBtn(L("Opustit stůl", "Leave table", "Tisch verlassen"), BK.DARK, h = 40.dp) { leave() }
        return true
    }
    if (!seated) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel).border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(6.dp)) {
            T(L("Přisedneš od další handy.", "You'll be dealt in next hand.", "Du spielst ab der nächsten Hand mit."), 12.sp, C.gold, FontWeight.Bold, align = TextAlign.Center)
        }
        ColBtn(L("Pozvat přátele", "Invite friends", "Freunde einladen"), BK.CALL) { share(ctx, O.inviteText()) }
        ColBtn(L("Opustit stůl", "Leave table", "Tisch verlassen"), BK.DARK, h = 40.dp) { leave() }
        return true
    }
    if (st.over != null) {
        val w = st.seats.maxByOrNull { it.stack }
        val wn = w?.let { if (it.pid == O.me) tr("Ty") else g.pl(it).name } ?: ""
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.win.copy(alpha = 0.2f)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("🏆", 20.sp)
            T(wn, 15.sp, C.gold, num = true, maxLines = 1)
            T(L("vyhrává hru", "wins the game", "gewinnt das Spiel"), 11.sp, C.ink, FontWeight.Bold)
            if (O.cup) {
                val tier = com.onoseto.chipsnfish.online.Fame.tierFor(st.n)
                T(com.onoseto.chipsnfish.online.Fame.cupIcon(tier) + " " + O.cupName, 10.sp, C.gold, FontWeight.Bold, align = TextAlign.Center, maxLines = 2)
            }
        }
        ColBtn("🏆 " + L("Síň slávy", "Hall of fame", "Ruhmeshalle"), BK.DARK, h = 40.dp) { Ui.open("fame") }
        if (O.isHost) ColBtn(L("Hrát znovu", "Play again", "Nochmal"), BK.CALL) { O.restart() }
        ColBtn(L("Opustit stůl", "Leave table", "Tisch verlassen"), BK.DARK) { leave() }
        return true
    }
    if (st.seats.getOrNull(0)?.out == true) {
        val left = st.seats.count { !it.out }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("👀 " + L("Sleduješ hru", "Watching", "Du schaust zu"), 12.sp, C.ink, FontWeight.ExtraBold, align = TextAlign.Center, maxLines = 1, fit = true)
            T(L("vypadl jsi · ve hře ještě $left", "you're out · $left still in", "du bist raus · noch $left im Spiel"), 10.5.sp, C.muted, align = TextAlign.Center)
        }
        if (st.done != null && O.isHost) ColBtn(tr("Další hand") + " ▶", BK.RAISE, h = 52.dp, enabled = !O.busy) { O.next() }
        ColBtn(L("Opustit stůl", "Leave table", "Tisch verlassen"), BK.DARK, h = 40.dp) { leave() }
        return true
    }
    if (st.done != null) {
        MyCards(); Spacer(Modifier.weight(1f))
        val inHand = !st.seats[0].out && !st.hero.contains(null)
        if (inHand && !O.didShow()) {
            ColBtn(L("Ukázat karty", "Show cards", "Karten zeigen"), BK.CALL, h = 46.dp) { O.show() }
            ColBtn(L("Zahodit", "Muck", "Wegwerfen"), BK.DARK, h = 40.dp) { O.muck() }
        }
        if (O.isHost) ColBtn(tr("Další hand") + " ▶", BK.RAISE, h = 52.dp, enabled = !O.busy) { O.next() }
        else Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel).padding(6.dp)) {
            T(L("Další hand za chvíli…", "Next hand in a moment…", "Nächste Hand gleich…"), 11.sp, C.muted, align = TextAlign.Center)
        }
        return true
    }
    return false
}

// ---------------- velvet table drawing ----------------

fun velvetNoise(): androidx.compose.ui.graphics.ImageBitmap {
    val n = 160
    val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    val r = java.util.Random(11)
    val px = IntArray(n * n)
    for (i in px.indices) {
        val light = r.nextBoolean()
        val a = r.nextInt(22)
        px[i] = (a shl 24) or (if (light) 0xFFFFFF else 0x000000)
    }
    // soft fibres: short diagonal strokes like velvet pile
    repeat(900) {
        val x0 = r.nextInt(n); val y0 = r.nextInt(n); val len = 2 + r.nextInt(4)
        val a = 10 + r.nextInt(16)
        for (k in 0 until len) {
            val x = (x0 + k) % n; val y = (y0 + k) % n
            px[y * n + x] = (a shl 24) or 0xFFFFFF
        }
    }
    bmp.setPixels(px, 0, n, 0, 0, n, n)
    return bmp.asImageBitmap()
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVelvetTable(geo: Geo, felt: List<Long>, noise: androidx.compose.ui.graphics.ImageBitmap) {
    val dp = density
    val rp = 14 * Game.zoom.toFloat() * 0.8f
    val l = (geo.x0 - rp) * dp; val t = (geo.y0 - rp) * dp
    val rw = (geo.x1 - geo.x0 + rp * 2) * dp; val rh = (geo.y1 - geo.y0 + rp * 2) * dp
    val rr = (geo.r + rp) * dp
    // soft shadow on the floor (stacked translucent layers)
    for (i in 1..4) drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(l, t + i * 2.5f * dp), Size(rw, rh), CornerRadius(rr))
    // padded leather rail
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF6B3F26), C.leather, Color(0xFF2A160D), C.leather2), t, t + rh), Offset(l, t), Size(rw, rh), CornerRadius(rr))
    // sheen on the rail: same shape as the rail (a half-height rect with this radius got its corners shrunk and stuck out as grey corners)
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.Transparent), t, t + rh * 0.45f), Offset(l + 2 * dp, t + 1.5f * dp), Size(rw - 4 * dp, rh - 3 * dp), CornerRadius(rr - 2 * dp))
    drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(l, t), Size(rw, rh), CornerRadius(rr), style = Stroke(1.2f * dp))
    // double stitching
    val st1 = rp * 0.28f * dp
    drawRoundRect(C.stitch, Offset(l + st1, t + st1), Size(rw - st1 * 2, rh - st1 * 2), CornerRadius(rr - st1),
        style = Stroke(1.3f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * dp, 4 * dp))))
    // felt geometry
    val fl = (geo.x0 + 2) * dp; val ft = (geo.y0 + 2) * dp
    val fw = (geo.x1 - geo.x0 - 4) * dp; val fh = (geo.y1 - geo.y0 - 4) * dp
    val fr = CornerRadius(max(4f, geo.r - 2) * dp)
    // brass trim between rail and felt
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFFF7DC8B), C.gold2, Color(0xFFFFE9A8), C.gold2, Color(0xFFF7DC8B)), Offset(fl, ft), Offset(fl + fw, ft + fh)),
        Offset(fl - 2.5f * dp, ft - 2.5f * dp), Size(fw + 5 * dp, fh + 5 * dp), CornerRadius(fr.x + 2.5f * dp), style = Stroke(2.2f * dp))
    // velvet base colour with lamp light from above
    drawRoundRect(Brush.radialGradient(listOf(Color(felt[0]), Color(felt[1]), Color(felt[2])), Offset(fl + fw / 2, ft + fh * 0.42f), max(fw, fh) * 0.62f), Offset(fl, ft), Size(fw, fh), fr)
    // velvet pile texture
    drawRoundRect(androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(noise, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)),
        Offset(fl, ft), Size(fw, fh), fr, alpha = 0.9f)
    // soft sheen of the lamp
    drawRoundRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset(fl + fw / 2, ft + fh * 0.36f), max(fw, fh) * 0.38f), Offset(fl, ft), Size(fw, fh), fr)
    // vignette towards the rail
    drawRoundRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.38f)), Offset(fl + fw / 2, ft + fh / 2), max(fw, fh) * 0.62f), Offset(fl, ft), Size(fw, fh), fr)
    // inner shadow under the rail
    for (i in 1..3) drawRoundRect(Color.Black.copy(alpha = 0.16f), Offset(fl + i * dp, ft + i * dp), Size(fw - i * 2 * dp, fh - i * 2 * dp), CornerRadius(fr.x - i * dp), style = Stroke(2.2f * dp))
    // betting line
    val bi = min(fw, fh) * 0.13f
    drawRoundRect(C.gold.copy(alpha = 0.16f), Offset(fl + bi, ft + bi), Size(fw - bi * 2, fh - bi * 2), CornerRadius(max(2f, fr.x - bi)), style = Stroke(1.3f * dp))
}


fun secondsLeft(turnAt: Long): Int = (((turnAt + 60000) - com.onoseto.chipsnfish.online.Online.serverNow()) / 1000).toInt().coerceAtLeast(0)

/** Draining ring around the player to act (60 s per move online). */
@Composable
fun TurnTimer(turnAt: Long, modifier: Modifier) {
    watch()
    var now by remember { mutableStateOf(com.onoseto.chipsnfish.online.Online.serverNow()) }
    LaunchedEffect(turnAt) { while (true) { now = com.onoseto.chipsnfish.online.Online.serverNow(); delay(200) } }
    val f = (1f - (now - turnAt) / 60000f).coerceIn(0f, 1f)
    val col = if (f > 0.5f) C.gold else if (f > 0.2f) Color(0xFFFF9A3C) else C.lose
    Canvas(modifier) {
        val w = 3.dp.toPx()
        drawArc(Color.Black.copy(alpha = 0.35f), 0f, 360f, false, style = Stroke(w), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w))
        drawArc(col, -90f, 360f * f, false, style = Stroke(w, cap = StrokeCap.Round), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w))
    }
}


@Composable
private fun CoachPanel() {
    watch()
    val m = com.onoseto.chipsnfish.game.Coach.message() ?: return
    val shape = RoundedCornerShape(12.dp)
    if (Ui.coachMin) {
        Box(Modifier.clip(shape).background(C.panel.copy(alpha = 0.92f)).border(1.dp, C.gold.copy(alpha = 0.6f), shape).tap { Ui.coachMin = false }.padding(horizontal = 10.dp, vertical = 8.dp)) {
            T("🎓 " + m.title, 12.sp, C.gold, FontWeight.ExtraBold, maxLines = 1)
        }
        return
    }
    Column(
        Modifier.width(300.dp).heightIn(max = 210.dp).shadow(10.dp, shape).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF01B2A34), Color(0xF0101920))))
            .border(1.dp, C.gold.copy(alpha = 0.55f), shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("🎓 " + m.title, 13.sp, C.gold, FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
            T("–", 18.sp, C.muted, FontWeight.Bold, modifier = Modifier.tap { Ui.coachMin = true }.padding(horizontal = 6.dp))
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            m.lines.forEach { l -> androidx.compose.material3.Text(rich(l), color = C.ink, fontSize = 12.5.sp, lineHeight = 16.sp) }
        }
        if (m.act.isNotEmpty() && Game.st.turn == 0) {
            GBtn(BK.GOLD, Modifier.fillMaxWidth().height(34.dp), onClick = { Game.doCoach(m.act, m.amt) }) {
                T(L("Zahrát: ", "Play: ", "Spielen: ") + m.label, 13.sp, C.goldInk, FontWeight.ExtraBold, maxLines = 1, fit = true)
            }
        }
    }
}
