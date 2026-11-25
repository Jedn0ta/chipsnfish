@file:OptIn(ExperimentalLayoutApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onoseto.chipsnfish.I18n
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Cards
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.game.HandRec
import com.onoseto.chipsnfish.game.Reads
import com.onoseto.chipsnfish.tr
import kotlin.math.roundToInt

@Composable
fun PlayersView() {
    watch()
    @Suppress("UNUSED_VARIABLE") val rev = Game.rev
    @Suppress("UNUSED_VARIABLE") val r2 = Ui.sheetRev
    val g = Game
    val st = g.st
    val inGame = st.phase != "setup"
    fun seatIdx(id: String) = st.seats.indexOfFirst { it.pid == id }
    val meId = com.onoseto.chipsnfish.online.Online.me
    val list = g.P.values.filter { it.id != "hero" && it.stored && it.id != meId }.sortedWith { a, b ->
        if (inGame) {
            val ia = seatIdx(a.id); val ib = seatIdx(b.id)
            if ((ia >= 0) != (ib >= 0)) return@sortedWith if (ia >= 0) -1 else 1
            if (ia >= 0) return@sortedWith ia - ib
        }
        a.name.lowercase().compareTo(b.name.lowercase())
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(tr("Hráči").uppercase(), 30.sp, num = true)
                T(L("${list.size} v seznamu", "${list.size} on the list", "${list.size} in der Liste"), 12.5.sp, C.muted)
                Spacer(Modifier.weight(1f))
                Btn(tr("+ Přidat hráče"), height = 40.dp) {
                    val np = g.newPlayer(tr("Nový hráč")); g.storePlayer(np); g.bump(); Ui.open("player", PlayerArg(np.id))
                }
            }
        }
        if (list.isEmpty()) item {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(tr("Seznam je prázdný"), 16.sp, weight = FontWeight.Bold)
                T(tr("Přidej hráče, se kterými hraješ. Pak je v přípravě hry posadíš ke stolu z lavičky."), 13.sp, C.muted, align = TextAlign.Center)
            }
        }
        var shownOthers = false
        list.forEachIndexed { i, p ->
            val sat = seatIdx(p.id) >= 0
            if (inGame && i == 0 && sat) item { Label(tr("U stolu · v pořadí míst"), Modifier.widthIn(max = 760.dp).fillMaxWidth()) }
            if (inGame && !sat && !shownOthers && i > 0) { shownOthers = true; item { Label(tr("Ostatní hráči"), Modifier.widthIn(max = 760.dp).fillMaxWidth()) } }
            item(key = p.id) {
                val t = Reads.effType(p)
                Row(
                    Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(12.dp))
                        .tap { Ui.open("player", PlayerArg(p.id)) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(p, 46.dp)
                    Column(Modifier.weight(1f)) {
                        T(p.name + if (sat) " " + tr("· u stolu") else "", 15.sp, weight = FontWeight.Bold, maxLines = 1)
                        if (p.hands > 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("vpip", "pfr", "af", "hands").forEach { k ->
                                val v = g.statFmt(k, g.statVal(p, k))
                                T(if (k == "hands") "$v ${tr("hand")}" else "${g.statLabel(k)} $v", 12.sp, C.muted, modifier = Modifier.tap { Ui.tip = g.statTip(p, k) })
                            }
                        } else T(tr("Zatím žádná uložená hand"), 12.sp, C.muted)
                        T(L("Sklon k bluffu ${(Reads.baseBluff(p) * 100).roundToInt()} % · ${p.caught}× chycen · ${p.showed}× silná", "Bluff tendency ${(Reads.baseBluff(p) * 100).roundToInt()} % · ${p.caught}× caught · ${p.showed}× strong", "Bluff-Neigung ${(Reads.baseBluff(p) * 100).roundToInt()} % · ${p.caught}× erwischt · ${p.showed}× stark"), 12.sp, C.muted)
                    }
                    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(C.type(t)).padding(horizontal = 8.dp, vertical = 3.dp)) { T(Reads.PROF.getValue(t).s, 10.5.sp, Color(0xFF111111), FontWeight.ExtraBold) }
                }
            }
        }
        item {
            Note(L("Podrž kteroukoli statistiku a uvidíš, co znamená. Statistiky se plní z uložených hand. Po 8 handách aplikace sama odhadne typ hráče. VPIP = jak často jde dobrovolně do hry, PFR = jak často zvyšuje preflop, AF = poměr útoků k callům po flopu.",
                "Tap any stat to see what it means. Stats fill up from saved hands. After 8 hands the app estimates the player type. VPIP = how often they voluntarily play, PFR = how often they raise preflop, AF = ratio of attacks to calls after the flop.",
                "Tippe eine Statistik an, um sie erklärt zu bekommen. Statistiken füllen sich aus gespeicherten Händen. Nach 8 Händen schätzt die App den Spielertyp. VPIP = wie oft freiwillig gespielt, PFR = wie oft preflop erhöht, AF = Verhältnis Angriffe zu Calls nach dem Flop."),
                Modifier.widthIn(max = 760.dp).fillMaxWidth())
        }
    }
}

@Composable
fun RecordsView() {
    watch()
    @Suppress("UNUSED_VARIABLE") val rev = Game.rev
    val g = Game
    val h = g.H
    val n = h.size
    val w = h.count { it.result == "win" }
    val net = h.sumOf { it.net }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { T(tr("Záznam").uppercase(), 30.sp, num = true, modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) }
        item {
            Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat3(n.toString(), tr("hand"), C.ink, Modifier.weight(1f))
                Stat3(w.toString(), tr("vyhráno"), C.ink, Modifier.weight(1f))
                Stat3((if (net > 0) "+" else "") + g.fm(net), tr("žetony celkem"), if (net >= 0) C.win else C.lose, Modifier.weight(1f))
            }
        }
        if (n == 0) item {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(tr("Zatím žádná hand"), 16.sp, weight = FontWeight.Bold)
                T(L("Odehraj handu u stolu v režimu Rádce a dej „Uložit handu“.", "Play a hand at the table in Advisor mode and choose “Save hand”.", "Spiele eine Hand im Berater-Modus und wähle „Hand speichern“."), 13.sp, C.muted, align = TextAlign.Center)
            }
        }
        items(h.take(150), key = { it.id }) { r -> RecCard(r) }
    }
}

@Composable
private fun Stat3(v: String, l: String, col: Color, modifier: Modifier) {
    watch()
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        T(v, 28.sp, col, num = true); T(l.uppercase(), 11.sp, C.muted, FontWeight.Bold)
    }
}

@Composable
private fun RecCard(r: HandRec) {
    watch()
    val g = Game
    var open by remember { mutableStateOf(false) }
    val when_ = java.text.SimpleDateFormat("d.M. HH:mm", I18n.locale).format(java.util.Date(r.t))
    val wn = r.winner?.let { w -> r.seats.find { it.pid == w }?.name }
    val (rl, rc) = when (r.result) { "win" -> tr("Výhra") to C.win; "lose" -> tr("Prohra") to C.lose; else -> tr("Dělba") to C.gold }
    Column(
        Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("#${r.no} · $when_ · " + L("${r.n} hráčů", "${r.n} players", "${r.n} Spieler") + " · ${r.blinds} · ${Cards.STN[r.street]}", 12.5.sp, C.muted, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(rc).padding(horizontal = 8.dp, vertical = 3.dp)) { T(rl.uppercase(), 11.sp, Color(0xFF111111), FontWeight.ExtraBold) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                r.hero.forEach { c -> if (c == null) CardSlot(30.dp, "") else CardView(c, 30.dp) }
                Spacer(Modifier.width(8.dp))
                r.board.forEach { c -> if (c == null) CardSlot(30.dp, "") else CardView(c, 30.dp) }
            }
            T((if (r.net > 0) "+" else "") + g.fm(r.net), 18.sp, if (r.net >= 0) C.win else C.lose, num = true)
        }
        T("Pot ${g.fm(r.pot)}" + (wn?.let { L(" · vyhrál $it", " · won by $it", " · gewonnen von $it") } ?: "") + (r.eq?.let { L(" · tvá šance na konci ${(it * 100).roundToInt()} %", " · your equity at the end ${(it * 100).roundToInt()} %", " · deine Equity am Ende ${(it * 100).roundToInt()} %") } ?: ""), 12.5.sp, C.muted)
        T((if (open) "▾ " else "▸ ") + tr("Akce hráčů"), 13.sp, C.muted, FontWeight.Bold, modifier = Modifier.tap { open = !open })
        if (open) r.seats.forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(s.name + (s.mood?.joinToString("", " ") ?: ""), 13.sp, modifier = Modifier.width(130.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.cards?.takeIf { it.firstOrNull() != null }?.let { cs -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { cs.forEach { c -> if (c != null) CardView(c, 20.dp) } } }
                    if (s.actions.isEmpty()) T("–", 12.sp, C.muted)
                    s.actions.forEach { a ->
                        val ag = Reads.agg(a.a); val fo = a.a == "fold"
                        Box(Modifier.clip(RoundedCornerShape(99.dp)).background(C.panel2).border(1.dp, if (ag) C.gold else if (fo) C.lose else C.line, RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            T("${Cards.STN[a.st][0]}: ${Reads.actl(a.a)}${a.amt?.let { " " + g.fm(it) } ?: ""}", 11.sp, if (ag) C.gold else if (fo) C.lose else C.ink, FontWeight.Bold)
                        }
                    }
                    if (s.reveal == "bluff") Box(Modifier.clip(RoundedCornerShape(99.dp)).background(C.lose).padding(horizontal = 7.dp, vertical = 2.dp)) { T("Bluff", 11.sp, Color(0xFF111111), FontWeight.Bold) }
                    if (s.reveal == "strong") Box(Modifier.clip(RoundedCornerShape(99.dp)).background(C.win).padding(horizontal = 7.dp, vertical = 2.dp)) { T(tr("Silná"), 11.sp, Color(0xFF111111), FontWeight.Bold) }
                }
            }
        }
        T(tr("Smazat záznam"), 12.5.sp, C.muted, modifier = Modifier.tap {
            Ui.confirm(tr("Smazat záznam?"), tr("Hand zmizí ze záznamu. Statistiky hráčů zůstanou."), tr("Smazat")) {
                g.H.removeAll { it.id == r.id }; g.saveLocal(); g.bump()
            }
        })
    }
}
