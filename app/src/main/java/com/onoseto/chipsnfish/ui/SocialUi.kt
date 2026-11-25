package com.onoseto.chipsnfish.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.online.Social
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Round button in the bottom-left corner of the table that opens emoji & drinks. */
@Composable
fun SocialBtn(modifier: Modifier) {
    watch()
    Box(
        modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .background(if (Social.open) C.gold.copy(alpha = 0.3f) else C.panel.copy(alpha = 0.92f))
            .border(1.dp, if (Social.open) C.gold else C.line, RoundedCornerShape(10.dp))
            .tap { if (Social.open) Social.open = false else Social.openPanel("emoji") },
        contentAlignment = Alignment.Center,
    ) { T("😀", 20.sp) }
}

/** Drinks standing in front of a player (shown under the stack label). */
@Composable
fun SeatDrinks(pid: String, sc: Float) {
    watch()
    val ds = Social.drinksFor(pid)
    if (ds.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy((1 * sc).dp), verticalAlignment = Alignment.CenterVertically) {
        ds.take(3).forEach { d -> T(Social.drink(d.kind)?.icon ?: "", (13 * sc).sp, lineHeight = (16 * sc).sp) }
        if (ds.size > 3) T("+${ds.size - 3}", (10 * sc).sp, C.gold, num = true, lineHeight = (16 * sc).sp)
    }
}

private fun seatPoint(geo: Geo, pid: String, h: Float): Offset {
    val st = Game.st
    val k = st.seats.indexOfFirst { it.pid == pid }
    val pos = st.pos
    return if (k >= 0 && pos != null) geo.pt(pos[k]) else Offset(geo.cx, h)
}

/** Speech bubbles with emoji above the seats and drinks flying across the table. */
@Composable
fun SocialFx(geo: Geo, w: Float, h: Float) {
    watch()
    val st = Game.st
    val sc = geo.sc / 0.8f
    val pos = st.pos ?: return
    st.seats.forEachIndexed { k, s ->
        val b = Social.bubbles[s.pid] ?: return@forEachIndexed
        key(s.pid, b.id) {
            val p = geo.pt(pos[k])
            val pop = remember { Animatable(0.3f) }
            LaunchedEffect(Unit) {
                pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
                delay(4300)
                pop.animateTo(0f, tween(220))
                if (Social.bubbles[s.pid]?.id == b.id) Social.bubbles.remove(s.pid)
            }
            val bw = 170f
            val above = p.y - 25 * sc - 50 > -4
            val y = if (above) p.y - 25 * sc - 50 else p.y + 40 * sc
            val x = max(2f, min(w - bw - 2, p.x - bw / 2))
            Box(Modifier.offset(x.dp, y.dp).width(bw.dp).zIndex(18f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.graphicsLayer(scaleX = pop.value, scaleY = pop.value, alpha = pop.value.coerceIn(0f, 1f),
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (above) 1f else 0f))
                        .shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) { T(b.text, 24.sp, Color.Black, lineHeight = 32.sp, maxLines = 1) }
            }
        }
    }
    // drinks sliding over the felt
    Social.flies.toList().forEach { f ->
        key(f.id) {
            val t = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                t.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
                delay(250)
                Social.flies.removeAll { it.id == f.id }
            }
            val a = seatPoint(geo, f.from, h)
            val b = seatPoint(geo, f.to, h)
            val v = t.value
            val arc = sin(v * PI).toFloat()
            val x = a.x + (b.x - a.x) * v
            val y = a.y + (b.y - a.y) * v - arc * 50
            val s = 1f + arc * 0.9f
            Box(Modifier.offset((x - 20).dp, (y - 20).dp).size(40.dp).zIndex(19f).graphicsLayer(scaleX = s, scaleY = s, rotationZ = arc * 18f), contentAlignment = Alignment.Center) {
                T(f.icon, 26.sp)
            }
        }
    }
}

/** The emoji / drink panel. */
@Composable
fun SocialPop() {
    watch()
    if (!Social.open) return
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(392.dp).shadow(12.dp, shape).clip(shape).background(C.panel).border(1.dp, C.line, shape).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallPill("😀 Emoji", Social.tab == "emoji") { Social.tab = "emoji" }
            SmallPill("🍹 " + L("Drink pro hráče", "Buy a drink", "Drink spendieren"), Social.tab == "drink") { Social.openPanel("drink") }
            Spacer(Modifier.weight(1f))
            if (Social.tab == "drink") T("💰 " + (Social.bank?.let { Game.fm(it) } ?: "…"), 14.sp, C.gold, num = true)
            Box(Modifier.size(28.dp).clip(CircleShape).background(C.panel2).tap { Social.open = false }, contentAlignment = Alignment.Center) {
                T("✕", 14.sp, C.muted, FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().height(164.dp)) {
            if (Social.tab == "emoji") EmojiTab() else DrinkTab()
        }
    }
}

@Composable
private fun EmojiTab() {
    watch()
    var msg by remember { mutableStateOf(listOf<Int>()) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(C.panel2).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (msg.isEmpty()) T(L("Vyber až 5 emoji…", "Pick up to 5 emoji…", "Bis zu 5 Emoji wählen…"), 12.5.sp, C.muted, modifier = Modifier.weight(1f), maxLines = 1)
            else T(msg.joinToString("") { Social.EMOJI[it] }, 22.sp, modifier = Modifier.weight(1f), maxLines = 1, lineHeight = 28.sp)
            T("${msg.size}/${Social.MAX}", 11.sp, if (msg.size >= Social.MAX) C.gold else C.muted, num = true)
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).tap { msg = msg.dropLast(1) }, contentAlignment = Alignment.Center) { T("⌫", 17.sp, C.muted) }
            Btn(L("Poslat", "Send", "Senden"), BK.GOLD, Modifier.width(88.dp), enabled = msg.isNotEmpty(), height = 32.dp) {
                Social.send(msg); msg = emptyList(); Social.open = false
            }
        }
        Social.EMOJI.chunked(10).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { c, e ->
                    val i = r * 10 + c
                    Box(
                        Modifier.size(35.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.04f))
                            .tap { if (msg.size < Social.MAX) msg = msg + i else Game.toast(L("Nejvíc 5 emoji na zprávu", "Max 5 emoji per message", "Höchstens 5 Emoji pro Nachricht")) },
                        contentAlignment = Alignment.Center,
                    ) { T(e, 21.sp, lineHeight = 26.sp) }
                }
            }
        }
    }
}

@Composable
private fun DrinkTab() {
    watch()
    val seats = Game.st.seats.filter { !it.out }
    val target = Social.target?.takeIf { t -> seats.any { it.pid == t } } ?: seats.getOrNull(1)?.pid ?: seats.firstOrNull()?.pid
    var pick by remember { mutableStateOf("beer") }
    val d = Social.drink(pick)!!
    val who = seats.firstOrNull { it.pid == target }?.let { Game.pl(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // who gets it
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(L("Komu:", "To:", "Für:"), 11.5.sp, C.muted, FontWeight.Bold)
            seats.take(10).forEach { s ->
                val p = Game.pl(s)
                val on = s.pid == target
                Box(Modifier.size(32.dp).tap { Social.target = s.pid }, contentAlignment = Alignment.Center) {
                    Avatar(p, if (on) 32.dp else 27.dp, ring = if (on) C.gold else Color.White.copy(alpha = 0.25f), border = 2.dp, dim = !on)
                }
            }
        }
        // which drink
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Social.DRINKS.forEach { x ->
                val on = x.id == pick
                Column(
                    Modifier.width(60.dp).clip(RoundedCornerShape(10.dp)).background(if (on) C.gold.copy(alpha = 0.22f) else C.panel2)
                        .border(1.dp, if (on) C.gold else Color.Transparent, RoundedCornerShape(10.dp)).tap { pick = x.id }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T(x.icon, 21.sp, lineHeight = 25.sp)
                    T(x.name(), 9.5.sp, C.ink, FontWeight.Bold, maxLines = 1, lineHeight = 11.sp)
                    T(Game.fm(x.price), 11.sp, C.gold, num = true, lineHeight = 13.sp)
                }
            }
        }
        val bank = Social.bank
        val enough = bank == null || bank >= d.price
        Btn(
            if (who == null) L("Vyber hráče", "Pick a player", "Spieler wählen")
            else "${d.icon} ${d.name()} " + L("pro", "for", "für") + " ${if (target == Social.myPid()) L("sebe", "me", "mich") else who.name} · ${Game.fm(d.price)}",
            BK.GOLD, Modifier.fillMaxWidth(), enabled = who != null && enough, height = 34.dp,
        ) { Social.buy(target!!, pick) { Social.open = false } }
        T(if (enough) L("Platí se z peněženky, ne ze žetonů ve hře. Každý den +200 🎁.", "Paid from your wallet, not from chips in play. +200 every day 🎁.", "Aus der Geldbörse, nicht aus den Spielchips. Täglich +200 🎁.")
          else L("Na tenhle drink v peněžence nestačí žetony. Zítra přibude +200 🎁.", "Not enough in your wallet for this one. +200 tomorrow 🎁.", "Dafür reicht die Geldbörse nicht. Morgen +200 🎁."),
            10.5.sp, C.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 1)
    }
}
