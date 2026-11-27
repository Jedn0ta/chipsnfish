@file:OptIn(ExperimentalLayoutApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
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
        if (a.hands != b.hands) return@sortedWith b.hands - a.hands
        a.name.lowercase().compareTo(b.name.lowercase())
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T(L("Statistiky", "Statistics", "Statistiken"), 26.sp, Color.White, FontWeight.ExtraBold)
            T(L("${list.size} hráčů · přibývají sami", "${list.size} players · added automatically", "${list.size} Spieler · kommen automatisch dazu"), 12.sp, GlassMuted, modifier = Modifier.padding(bottom = 5.dp))
            Spacer(Modifier.weight(1f))
            T(L("VPIP hraje · PFR zvyšuje · AF útočí", "VPIP plays · PFR raises · AF attacks", "VPIP spielt · PFR erhöht · AF greift an"), 11.sp, GlassMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(L("Zatím tu nikdo není", "Nobody here yet", "Noch niemand hier"), 16.sp, Color.White, FontWeight.Bold)
                T(L("Každý, s kým si zahraješ, i všichni tví přátelé se tu objeví sami i se statistikami.", "Everyone you play with and all your friends show up here by themselves, with their stats.", "Alle, mit denen du spielst, und alle deine Freunde erscheinen hier automatisch mit Statistiken."), 13.sp, GlassMuted, align = TextAlign.Center)
            }
            return@Column
        }
        androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
        ) {
            items(list.size, key = { list[it].id }) { i -> PlayerCard(list[i], seatIdx(list[i].id) >= 0) }
        }
    }
}

/** One player as a glass card: avatar, type, the four key numbers. */
@Composable
private fun PlayerCard(p: com.onoseto.chipsnfish.game.Player, sat: Boolean) {
    watch()
    val g = Game
    val t = Reads.effType(p)
    val friend = com.onoseto.chipsnfish.online.Online.isFriend(p.id)
    Column(
        Modifier.width(184.dp).fillMaxHeight()
            .glass(RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 6.dp), if (sat) Color(0x3D2F7BFF) else Color(0x2A2A5CA8), lift = 8.dp)
            .tap { Ui.open("player", PlayerArg(p.id)) }.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(p, 38.dp, border = 2.dp)
            Column(Modifier.weight(1f)) {
                T(p.name, 14.5.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(C.type(t)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        T(Reads.PROF.getValue(t).s, 9.5.sp, Color(0xFF111111), FontWeight.ExtraBold, maxLines = 1)
                    }
                    if (friend) T("★", 11.sp, LobbyBlue, FontWeight.Bold)
                    if (sat) T(L("u stolu", "seated", "am Tisch"), 9.5.sp, LobbyBlue, FontWeight.Bold)
                }
            }
        }
        if (p.hands > 0) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("vpip", "pfr", "af", "hands").forEach { k ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.07f)).tap { Ui.tip = g.statTip(p, k) }.padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T(g.statFmt(k, g.statVal(p, k)), 12.5.sp, Color.White, num = true, maxLines = 1, fit = true)
                    T(if (k == "hands") tr("hand") else g.statLabel(k), 8.sp, GlassMuted, FontWeight.Bold, maxLines = 1)
                }
            }
        } else T(L("Zatím bez odehraných hand", "No hands played yet", "Noch keine Hände"), 11.sp, GlassMuted)
        T(L("bluff ${(Reads.baseBluff(p) * 100).roundToInt()} % · chycen ${p.caught}× · silná ${p.showed}×", "bluff ${(Reads.baseBluff(p) * 100).roundToInt()} % · caught ${p.caught}× · strong ${p.showed}×", "Bluff ${(Reads.baseBluff(p) * 100).roundToInt()} % · erwischt ${p.caught}× · stark ${p.showed}×"),
            10.sp, GlassMuted, maxLines = 1, fit = true)
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            T(tr("Záznam"), 26.sp, Color.White, FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Stat3(n.toString(), tr("hand"), Color.White, Modifier.width(110.dp))
            Stat3(w.toString(), tr("vyhráno"), Color.White, Modifier.width(110.dp))
            Stat3((if (net > 0) "+" else "") + g.fm(net), tr("žetony celkem"), if (net >= 0) C.win else C.lose, Modifier.width(130.dp))
        }
        if (n == 0) {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(tr("Zatím žádná hand"), 16.sp, Color.White, FontWeight.Bold)
                T(L("Odehrané handy se sem ukládají samy.", "Played hands are saved here automatically.", "Gespielte Hände landen hier automatisch."), 13.sp, GlassMuted, align = TextAlign.Center)
            }
            return@Column
        }
        androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
            items(h.take(150), key = { it.id }) { r -> RecCard(r) }
        }
    }
}

@Composable
private fun Stat3(v: String, l: String, col: Color, modifier: Modifier) {
    watch()
    Column(modifier.glass(RoundedCornerShape(16.dp), lift = 6.dp).padding(horizontal = 12.dp, vertical = 5.dp)) {
        T(v, 18.sp, col, num = true, maxLines = 1, fit = true); T(l.uppercase(), 9.sp, GlassMuted, FontWeight.Bold, maxLines = 1)
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
        Modifier.width(300.dp).fillMaxHeight().glass(RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 6.dp), lift = 8.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("#${r.no} · $when_ · " + L("${r.n} hráčů", "${r.n} players", "${r.n} Spieler") + " · ${r.blinds} · ${Cards.STN[r.street]}", 12.5.sp, C.muted, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(rc).padding(horizontal = 8.dp, vertical = 3.dp)) { T(rl.uppercase(), 11.sp, Color(0xFF111111), FontWeight.ExtraBold) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                r.hero.forEach { c -> if (c == null) CardSlot(24.dp, "") else CardView(c, 24.dp) }
                Spacer(Modifier.width(5.dp))
                r.board.forEach { c -> if (c == null) CardSlot(24.dp, "") else CardView(c, 24.dp) }
            }
            T((if (r.net > 0) "+" else "") + g.fm(r.net), 18.sp, if (r.net >= 0) C.win else C.lose, num = true)
        }
        T("Pot ${g.fm(r.pot)}" + (wn?.let { L(" · vyhrál $it", " · won by $it", " · gewonnen von $it") } ?: "") + (r.eq?.let { L(" · tvá šance na konci ${(it * 100).roundToInt()} %", " · your equity at the end ${(it * 100).roundToInt()} %", " · deine Equity am Ende ${(it * 100).roundToInt()} %") } ?: ""), 12.5.sp, C.muted)
        T((if (open) "▾ " else "▸ ") + tr("Akce hráčů"), 13.sp, C.muted, FontWeight.Bold, modifier = Modifier.tap { open = !open })
        if (open) r.seats.forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(s.name + (s.mood?.joinToString("", " ") ?: ""), 12.sp, modifier = Modifier.width(80.dp), maxLines = 1)
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
