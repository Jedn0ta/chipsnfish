@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.onoseto.chipsnfish.ui

import com.onoseto.chipsnfish.online.OFriend
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.onoseto.chipsnfish.I18n
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Advisor
import com.onoseto.chipsnfish.game.Cards
import com.onoseto.chipsnfish.game.EndState
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.game.GameCfg
import com.onoseto.chipsnfish.game.Player
import com.onoseto.chipsnfish.game.Reads
import com.onoseto.chipsnfish.tr
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------- host ----------------

@Composable
fun SheetHost() {
    watch()
    val sh = Ui.sheet ?: return
    @Suppress("UNUSED_VARIABLE") val r1 = Ui.sheetRev
    @Suppress("UNUSED_VARIABLE") val r2 = Game.rev
    val wide = sh.kind == "advent" || sh.kind in setOf("card", "settings", "gamesetup", "online", "groups", "analysis", "seat", "player", "fame")
    val compact = sh.kind == "confirm" || sh.kind == "friend"
    Box(
        Modifier.fillMaxSize().zIndex(40f).background(Color(0xB3020814)).tap { Ui.close() }.padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.widthIn(max = if (wide) 780.dp else if (compact) 480.dp else 620.dp).fillMaxWidth()
                .then(if (compact) Modifier.heightIn(max = 344.dp) else Modifier.height(344.dp))
                .glass(RoundedCornerShape(24.dp), Color(0x8C0A1530), lift = 24.dp)
                .tap { },
        ) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (sh.kind == "card") Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (sh.kind) {
                "card" -> CardSheet(sh.arg as CardArg)
                "analysis" -> AnalysisSheet()
                "seat" -> SeatSheet(sh.arg as Int)
                "player" -> PlayerSheet(sh.arg as PlayerArg)
                "end" -> EndSheet(sh.arg as EndState)
                "hands" -> HandsSheet()
                "blinds" -> BlindsSheet(sh.arg as BlindsArg)
                "settings" -> SettingsSheet(sh.arg as SettingsArg)
                "gamesetup" -> GameSetupSheet(sh.arg as GameCfg)
                "about" -> AboutSheet()
                "confirm" -> ConfirmSheet(sh.arg as ConfirmArg)
                "online" -> OnlineSheet(sh.arg as? OnlineArg ?: OnlineArg())
                "groups" -> GroupsSheet()
                "friend" -> FriendSheet(sh.arg as OFriend)
                "fame" -> FameSheet()
                "advent" -> AdventSheet()
                "cup" -> CupSheet()
                "group" -> GroupSheet(sh.arg as GroupArg)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { CloseX() }
        }
    }
}

@Composable
fun SheetHead(title: String, extra: @Composable () -> Unit = {}) {
    watch()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        T(title.uppercase(), 24.sp, C.ink, num = true, letter = 0.8.sp, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
        Box(Modifier.weight(1f)) { extra() }
        Spacer(Modifier.width(36.dp))
    }
}

@Composable
fun CloseX() {
    watch()
    Box(Modifier.size(38.dp).clip(CircleShape).background(C.panel2).tap { Ui.close() }, contentAlignment = Alignment.Center) { T("✕", 16.sp) }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, number: Boolean = false, placeholder: String = "", minH: Dp = 44.dp, single: Boolean = true, big: Boolean = false, readOnly: Boolean = false) {
    watch()
    Box(modifier.heightIn(min = minH).clip(RoundedCornerShape(10.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty() && placeholder.isNotEmpty()) T(placeholder, 15.sp, C.muted)
        BasicTextField(
            value, onChange, singleLine = single, readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            textStyle = TextStyle(color = C.ink, fontSize = if (big) 19.sp else 15.sp, fontFamily = if (number || big) F.num else F.body, fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Normal),
            cursorBrush = SolidColor(C.gold), modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun Seg(options: List<Pair<String, String>>, cur: String, colorFor: (String) -> Color? = { null }, onPick: (String) -> Unit) {
    watch()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, C.line, RoundedCornerShape(10.dp))) {
        options.forEachIndexed { i, (v, l) ->
            val on = v == cur
            val bg = if (on) colorFor(v) ?: C.ink else Color.Transparent
            Box(Modifier.weight(1f).height(42.dp).background(bg).tap { onPick(v) }, contentAlignment = Alignment.Center) {
                T(l, 13.sp, if (on) C.room else C.muted, FontWeight.Bold, align = TextAlign.Center)
            }
            if (i < options.size - 1) Box(Modifier.width(1.dp).height(42.dp).background(C.line))
        }
    }
}

@Composable
fun Picks(items: List<Pair<String, String>>, cur: String, small: Boolean = false, onPick: (String) -> Unit) {
    watch()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(if (small) 5.dp else 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { (v, l) -> if (small) SmallPill(l, v == cur) { onPick(v) } else Pill(l, v == cur) { onPick(v) } }
    }
}

// ---------------- card picker ----------------

@Composable
private fun CardSheet(a: CardArg) {
    watch()
    val g = Game
    val cur = a.slot
    val grp = g.slotGroup(cur)
    val title = when {
        cur >= 10 -> g.pl(g.st.seats[(cur - 10) shr 1]).name
        cur < 2 -> tr("Tvoje karty")
        cur < 5 -> "Flop"; cur == 5 -> "Turn"; else -> "River"
    }
    val used = HashSet<Int>()
    for (j in g.allSlots()) { val v = g.slotVal(j); if (v != null && j != cur) used.add(v) }
    val curV = g.slotVal(cur)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        T(title.uppercase(), 22.sp, num = true, maxLines = 1)
        grp.forEach { j ->
            val v = g.slotVal(j)
            Box(Modifier.clip(RoundedCornerShape(8.dp)).border(2.dp, if (j == cur) C.gold else Color.Transparent, RoundedCornerShape(8.dp)).padding(2.dp).tap { a.slot = j; Ui.redraw() }) {
                if (v == null) CardSlot(34.dp, "?") else CardView(v, 34.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        if (curV != null) Btn(tr("Odebrat"), BK.GHOST, height = 40.dp) { g.setSlot(cur, null); g.update(); Ui.redraw() }
        Btn(tr("Hotovo"), height = 40.dp) { Ui.close() }
        Spacer(Modifier.width(36.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (su in 0..3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                T(Cards.SYM[su], 22.sp, if (Cards.RED[su]) Color(0xFFFF6B6B) else C.ink, modifier = Modifier.width(22.dp))
                for (r in 12 downTo 0) {
                    val c = r * 4 + su
                    val dis = c in used
                    val sel = c == curV
                    Box(
                        Modifier.weight(1f).height(56.dp).alpha(if (dis) 0.18f else 1f).clip(RoundedCornerShape(8.dp))
                            .background(if (sel) C.gold else Color.White)
                            .then(if (!dis) Modifier.tap {
                                g.setSlot(cur, c); g.st.sample = false; g.update()
                                grp.firstOrNull { g.slotVal(it) == null }?.let { a.slot = it }
                                Ui.redraw()
                            } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val col = if (Cards.RED[su]) C.cardRed else C.cardBlk
                            T(Cards.rl(r), 20.sp, col, num = true, lineHeight = 20.sp)
                            T(Cards.SYM[su], 14.sp, col, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- advisor ----------------

@Composable
private fun AnalysisSheet() {
    watch()
    val g = Game
    val st = g.st
    SheetHead(tr("Rádce"))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (g.playing()) CoachBox()
    // equity
    BoxPanel {
        if (st.hero.contains(null)) {
            Label(tr("Šance na výhru")); Note(tr("Klepni dole vlevo na své karty a vyber je."))
        } else {
            val bd = g.boardKnown()
            val sc = Cards.ev(st.hero.map { it!! } + bd)
            val name = I18n.cat(Cards.handName(sc).second)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(tr("Šance na výhru"), Modifier.weight(1f))
                androidx.compose.material3.Text(rich(L("Máš: <b>$name</b>", "You have: <b>$name</b>", "Du hast: <b>$name</b>") +
                    if (bd.isEmpty()) L(" · top ${max(1, (Cards.pctOf(st.hero[0]!!, st.hero[1]!!) * 100).roundToInt())} % rukou", " · top ${max(1, (Cards.pctOf(st.hero[0]!!, st.hero[1]!!) * 100).roundToInt())} % of hands", " · Top ${max(1, (Cards.pctOf(st.hero[0]!!, st.hero[1]!!) * 100).roundToInt())} % der Hände") else ""),
                    color = C.ink, fontSize = 13.sp)
            }
            val e = g.eq
            when {
                st.seats[0].folded -> Note(tr("Složil jsi."))
                e != null && e.allFolded -> androidx.compose.material3.Text(rich("<b>" + tr("Všichni soupeři složili.") + "</b> " + tr("Pot je tvůj.")), color = C.ink)
                e != null -> {
                    val lose = 1 - e.win - e.tie
                    val nOpp = g.liveOpps().size
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        T(g.pc(e.eq), 42.sp, num = true, modifier = Modifier.weight(1f))
                        T(L("proti $nOpp ${if (nOpp == 1) "soupeři" else "soupeřům"}", "against $nOpp ${if (nOpp == 1) "opponent" else "opponents"}", "gegen $nOpp ${if (nOpp == 1) "Gegner" else "Gegner"}"), 12.5.sp, C.muted, align = TextAlign.End)
                    }
                    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(99.dp)).background(C.panel)) {
                        if (e.win > 0) Box(Modifier.weight(e.win.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(C.win))
                        if (e.tie > 0) Box(Modifier.weight(e.tie.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(C.gold))
                        if (lose > 0) Box(Modifier.weight(lose.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(C.lose))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legend(C.win, tr("Výhra") + " " + g.pc(e.win)); Legend(C.gold, tr("Dělba") + " " + g.pc(e.tie)); Legend(C.lose, tr("Prohra") + " " + g.pc(lose))
                    }
                    val toCall = max(0, st.curBet - st.seats[0].bet)
                    if (toCall > 0) {
                        val need = toCall.toDouble() / (g.potTotal() + toCall)
                        val good = e.eq >= need
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(C.panel)) {
                            Box(Modifier.width(4.dp).height(30.dp).background(if (good) C.win else C.lose))
                            androidx.compose.material3.Text(rich("<b>" + tr(if (good) "Call se vyplatí" else "Call se nevyplatí") + "</b> · " + L("k dorovnání ${g.fm(toCall)}, potřebuješ ${g.pc(need)}", "to call ${g.fm(toCall)}, you need ${g.pc(need)}", "zum Callen ${g.fm(toCall)}, du brauchst ${g.pc(need)}")),
                                color = C.ink, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                    }
                }
                else -> Note(tr("Počítám…"))
            }
        }
    }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    // bluff reads
    BoxPanel {
        Label(tr("Odhad bluffu soupeřů"))
        val lv = g.liveOpps()
        if (lv.isEmpty()) Note(tr("Nikdo ze soupeřů už není ve hře."))
        lv.forEach { s ->
            val p = g.pl(s)
            val m = Reads.model(st, g.P, s)
            val b = Reads.seatBluff(st, g.P, s)
            val lc = C.lvl(Reads.lvl(b.v).second)
            val k = st.seats.indexOf(s)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel).tap { Ui.open("seat", k) }.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(p, 34.dp, border = 2.dp)
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(rich("<b>${p.name}</b> · ${Reads.prof(p).l} · top ${(m.w * 100).roundToInt()} %"), color = C.ink, fontSize = 13.sp, maxLines = 1)
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f))) {
                        Box(Modifier.fillMaxWidth(b.v.toFloat()).fillMaxHeight().background(lc))
                    }
                    Note(if (b.act) L("útok na ${I18n.stn4(m.br!!.st)}", "aggression on the ${I18n.stn4(m.br!!.st)}", "Angriff am ${I18n.stn4(m.br!!.st)}") else tr("obecný sklon, zatím neútočil"))
                }
                T("${(b.v * 100).roundToInt()}%", 21.sp, lc, num = true)
            }
        }
        Note(tr("Plné procento = odhad, že jeho poslední sázka je bluff. Čárkovaný štítek u stolu = jen jeho obecný sklon k bluffování."))
    }
    // next card
    BoxPanel {
        val r = g.nextCache ?: Reads.nextStats(st).also { g.nextCache = it }
        if (r.msg != null) { Label(tr("Další karta")); Note(r.msg) }
        else {
            if (st.tab >= r.views.size) st.tab = 0
            val v = r.views[st.tab]
            val t = v.t
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(tr(if (v.key == "flop") "Co přinese flop" else "Zlepšení ruky"), Modifier.weight(1f))
                if (r.views.size > 1) r.views.forEachIndexed { i, x ->
                    Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(99.dp)).background(if (i == st.tab) C.ink else Color.Transparent).border(1.dp, if (i == st.tab) C.ink else C.line, RoundedCornerShape(99.dp))
                        .tap { st.tab = i; g.saveLocal(); Ui.redraw() }.padding(horizontal = 11.dp, vertical = 5.dp)) {
                        T(x.name, 12.5.sp, if (i == st.tab) C.room else C.muted, FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(g.pc(t.imp.toDouble() / max(1, t.n)), 34.sp, num = true)
                T(when (v.key) {
                    "next" -> L("další karta zlepší tvou ruku", "next card improves your hand", "nächste Karte verbessert deine Hand") + if (v.outs) L(" · ${t.imp} outů z ${t.n}", " · ${t.imp} outs of ${t.n}", " · ${t.imp} Outs von ${t.n}") else ""
                    "flop" -> L("na flopu zlepší tvou ruku", "the flop improves your hand", "der Flop verbessert deine Hand")
                    else -> L("do riveru zlepší tvou ruku", "by the river improves your hand", "bis zum River verbessert deine Hand")
                }, 13.sp, C.muted)
            }
            val rows = (8 downTo 1).filter { t.by[it] > 0 }
            if (rows.isEmpty()) Note(tr("Žádná karta tvou ruku sama nezlepší."))
            val tot = t.imp.toDouble() / max(1, t.n)
            rows.forEach { c ->
                val p = t.by[c].toDouble() / t.n
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        T(I18n.cat(Cards.CAT[c]), 14.sp, modifier = Modifier.weight(1f))
                        if (v.outs) T(L("${t.by[c]} outů", "${t.by[c]} outs", "${t.by[c]} Outs"), 13.sp, C.muted, modifier = Modifier.padding(end = 10.dp))
                        T(g.pc(p), 14.sp, weight = FontWeight.Bold, modifier = Modifier.widthIn(min = 58.dp), align = TextAlign.End)
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(9.dp)).background(C.panel)) {
                        Box(Modifier.fillMaxWidth(min(1.0, p / max(0.0001, tot)).toFloat()).fillMaxHeight().background(C.gold))
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
private fun Legend(c: Color, t: String) {
    watch()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(5.dp)); T(t, 13.sp)
    }
}

@Composable
private fun CoachBox() {
    watch()
    val a = Advisor.advise() ?: return
    val col = when (a.tone) { "g" -> C.win; "r" -> C.lose; else -> C.gold }
    BoxPanel(Modifier.border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
        Label(tr("Rádce doporučuje"))
        T(a.label, 28.sp, col, num = true)
        a.lines.forEach { l ->
            Row { T("•", 13.sp, C.muted, modifier = Modifier.padding(end = 6.dp)); androidx.compose.material3.Text(rich(l), color = C.ink, fontSize = 13.sp, lineHeight = 17.sp) }
        }
        if (a.myTurn && a.act.isNotEmpty()) {
            Btn(L("Zahrát: ", "Play: ", "Spielen: ") + a.label, modifier = Modifier.fillMaxWidth()) {
                Ui.close(); Game.doCoach(a.act, a.amt)
            }
        }
    }
}

// ---------------- seat & player ----------------

@Composable
private fun HeadWho(p: Player, name: String, onName: (String) -> Unit, extra: @Composable () -> Unit = {}) {
    watch()
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadImg(ctx, uri)?.let { p.img = it; Game.persistP(p); Game.update(); Ui.redraw() } ?: Game.toast(tr("Tuhle fotku se nepodařilo načíst"))
    }
    val canEdit = !p.bot && !(Game.online() && p.id != com.onoseto.chipsnfish.online.Online.me)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.then(if (canEdit) Modifier.tap { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } else Modifier)) {
            Avatar(p, 48.dp)
            if (canEdit) Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(C.panel2).border(1.dp, C.line, CircleShape), contentAlignment = Alignment.Center) { T("📷", 9.sp) }
        }
        Column(Modifier.weight(1f)) {
            if (!canEdit) T(p.name, 18.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            else BasicTextField(name, onName, singleLine = true, textStyle = TextStyle(color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), cursorBrush = SolidColor(C.gold), modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            T(if (p.id == "hero") tr("To jsi ty") else Reads.typeLine(p), 12.sp, C.muted, maxLines = 1)
        }
        extra()
        Spacer(Modifier.width(34.dp))
    }
}

@Composable
private fun ActionChips(actions: List<com.onoseto.chipsnfish.game.Act>) {
    watch()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { a ->
            val ag = Reads.agg(a.a); val fo = a.a == "fold"
            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(C.panel2).border(1.dp, if (ag) C.gold else if (fo) C.lose else C.line, RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                T("${Cards.STN[a.st]}: ${Reads.actl(a.a)}${a.amt?.let { " " + Game.fm(it) } ?: ""}", 11.sp, if (ag) C.gold else if (fo) C.lose else C.ink, FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SeatSheet(k: Int) {
    watch()
    val g = Game
    val st = g.st
    val s = st.seats.getOrNull(k) ?: run { Ui.close(); return }
    val p = g.pl(s)
    var name by remember(p.id) { mutableStateOf(p.name) }
    HeadWho(p, name, { name = it }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (g.online() && k != 0 && com.onoseto.chipsnfish.online.SupaCfg.configured) {
                val O = com.onoseto.chipsnfish.online.Online
                if (O.isFriend(p.id)) T("★ " + L("V přátelích", "Friend", "Freund"), 12.sp, C.gold, FontWeight.Bold)
                else Btn(L("★ Přidat do přátel", "★ Add friend", "★ Zu Freunden"), BK.GOLD, height = 34.dp) { O.addFriend(p.id, p.name) }
            }
            if (g.gm() && k != 0 && g.playing() && !s.out && !s.folded && st.done == null && st.one?.get(k) == null && (g.online() || st.hole[k] != null))
                Btn("🂠 " + L("Ukaž kartu", "Show a card", "Zeig eine Karte"), BK.DARK, height = 34.dp) { Ui.close(); g.askCard(k) }
            if (com.onoseto.chipsnfish.online.Social.available() && com.onoseto.chipsnfish.online.SupaCfg.configured)
                Btn("🍹 " + L("Drink", "Drink", "Drink"), BK.DARK, height = 34.dp) { Ui.close(); com.onoseto.chipsnfish.online.Social.openPanel("drink", s.pid) }
            if (com.onoseto.chipsnfish.online.Social.available() && com.onoseto.chipsnfish.online.SupaCfg.configured)
                Btn("🎩 " + L("Klobouk", "Hat", "Hut"), BK.DARK, height = 34.dp) { Ui.close(); com.onoseto.chipsnfish.online.Social.openPanel("hat", s.pid) }
        }
    }
    if (s.actions.isNotEmpty()) ActionChips(s.actions)
    val notes = remember(p.id) { mutableStateOf(p.notes) }
    val type = remember(p.id) { mutableStateOf(p.override) }
    PlayerBlock(p, notes, type, save = { savePlayer(p, name, notes.value, type.value) }, top = { if (k != 0) ReadBox(s) })
}

@Composable
private fun ReadBox(s: com.onoseto.chipsnfish.game.Seat) {
    watch()
    val g = Game
    val m = Reads.model(g.st, g.P, s)
    val b = Reads.seatBluff(g.st, g.P, s)
    val (lt, li) = Reads.lvl(b.v)
    val lc = C.lvl(li)
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel2).tap { open = !open }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T(L("Bluff", "Bluff", "Bluff"), 11.sp, C.muted, FontWeight.ExtraBold)
            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(lc).padding(horizontal = 6.dp, vertical = 1.dp)) { T(lt.uppercase(), 9.5.sp, Color(0xFF111111), FontWeight.ExtraBold) }
            Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f))) { Box(Modifier.fillMaxWidth(b.v.toFloat()).fillMaxHeight().background(lc)) }
            T("${(b.v * 100).roundToInt()} %", 20.sp, num = true)
        }
        T((if (b.act) L("Odhad pro jeho sázku na ${I18n.stn4(m.br!!.st)}.", "For his bet on the ${I18n.stn4(m.br!!.st)}.", "Für seinen Einsatz am ${I18n.stn4(m.br!!.st)}.") else L("Obecný sklon, zatím neútočil.", "General tendency, no aggression yet.", "Allgemeine Neigung, noch kein Angriff.")) +
            L(" Hraje cca top ${(m.w * 100).roundToInt()} % rukou.", " Plays about top ${(m.w * 100).roundToInt()} % of hands.", " Spielt etwa Top ${(m.w * 100).roundToInt()} % der Hände.") + if (m.br != null) (if (open) " ▾" else " ▸") else "", 11.5.sp, C.muted, maxLines = if (open) 2 else 1)
        if (open) m.br?.r?.forEach { r ->
            Row(Modifier.fillMaxWidth()) {
                T(r.t, 11.5.sp, modifier = Modifier.weight(1f), maxLines = 1)
                T(if (r.isBase) "${(r.d * 100).roundToInt()} %" else (if (r.d > 0) "+" else "−") + "${kotlin.math.abs((r.d * 100).roundToInt())}", 11.5.sp, weight = FontWeight.Bold)
            }
        }
    }
}

private fun savePlayer(p: Player, name: String, notes: String, type: String) {
    val g = Game
    if (name.trim().isNotEmpty()) p.name = name.trim()
    p.notes = notes
    p.override = type
    if (p.id != "hero" && !p.bot) p.stored = true
    g.persistP(p)
    Ui.close()
    g.update()
    g.toast(L("Hráč ${p.name} uložen", "Player ${p.name} saved", "Spieler ${p.name} gespeichert"))
}

@Composable
private fun PlayerSheet(a: PlayerArg) {
    watch()
    val g = Game
    val p = g.P[a.id] ?: run { Ui.close(); return }
    var name by remember(p.id) { mutableStateOf(p.name) }
    HeadWho(p, name, { name = it })
    val notes = remember(p.id) { mutableStateOf(p.notes) }
    val type = remember(p.id) { mutableStateOf(p.override) }
    PlayerBlock(p, notes, type, save = { savePlayer(p, name, notes.value, type.value) }) {
        val O = com.onoseto.chipsnfish.online.Online
        val online = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-").containsMatchIn(p.id) && p.id != O.me && com.onoseto.chipsnfish.online.SupaCfg.configured
        if (online) {
            if (O.isFriend(p.id)) T("★ " + L("V přátelích", "Friend", "Freund"), 12.sp, C.gold, FontWeight.Bold)
            else Btn(L("★ Přidat do přátel", "★ Add friend", "★ Zu Freunden"), BK.GOLD, height = 34.dp) { O.addFriend(p.id, p.name) }
        }
        if (g.st.seats.any { it.pid == p.id }) T(tr("Hráč teď sedí u stolu."), 11.sp, C.muted)
        else T(tr(if (a.confirm) "Opravdu smazat i se statistikami?" else "Smazat hráče"), 12.sp, C.lose, FontWeight.Bold, modifier = Modifier.tap {
            if (!a.confirm) { a.confirm = true; Ui.redraw() }
            else { g.P.remove(p.id); g.saveLocal(); Ui.close(); g.bump(); g.toast(tr("Hráč smazán")) }
        }.padding(vertical = 2.dp))
    }
}

@Composable
fun StatCell(p: Player, k: String, modifier: Modifier = Modifier) {
    watch()
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(C.panel2)
            .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = { Ui.tip = Game.statTip(p, k) }, onClick = { Ui.tip = Game.statTip(p, k) })
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        T(Game.statFmt(k, Game.statVal(p, k)), 18.sp, num = true, lineHeight = 19.sp)
        T(Game.statLabel(k).uppercase(), 9.sp, C.muted, FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun PlayerBlock(
    p: Player, notes: androidx.compose.runtime.MutableState<String>, type: androidx.compose.runtime.MutableState<String>,
    save: () -> Unit, top: @Composable () -> Unit = {}, bottom: @Composable () -> Unit = {},
) {
    watch()
    if (p.id == "hero" || p.id == com.onoseto.chipsnfish.online.Online.me) {
        MoodTable(p.id)
        Btn(tr("Uložit"), modifier = Modifier.fillMaxWidth(), height = 40.dp) { save() }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            top()
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("hands", "vpip", "pfr").forEach { StatCell(p, it, Modifier.weight(1f)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("af", "caught", "showed").forEach { StatCell(p, it, Modifier.weight(1f)) } }
            MoodTable(p.id)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(tr("Typ hráče"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                (listOf("auto" to "Auto") + Reads.PROF.values.map { it.key to it.l }).forEach { (k, l) -> SmallPill(l, type.value == k) { type.value = k } }
            }
            val t = Reads.PROF[if (type.value != "auto") type.value else Reads.autoType(p).t] ?: Reads.PROF.getValue("unk")
            androidx.compose.material3.Text(rich(t.d + " " + L("Sklon k bluffu", "Bluff tendency", "Bluff-Neigung") + ": <b>${(Reads.baseBluff(p) * 100).roundToInt()} %</b>."), color = C.muted, fontSize = 11.sp, lineHeight = 14.sp)
            Field(notes.value, { notes.value = it }, Modifier.fillMaxWidth(), placeholder = tr("Poznámky"), minH = 48.dp, single = false)
            Btn(tr("Uložit hráče"), modifier = Modifier.fillMaxWidth(), height = 38.dp) { save() }
            bottom()
        }
    }
}

@Composable
fun SmallPill(text: String, on: Boolean, onClick: () -> Unit) {
    watch()
    val shape = RoundedCornerShape(99.dp)
    Box(Modifier.clip(shape).background(if (on) Color(0xFF2F7BFF) else Color.White.copy(alpha = 0.07f)).border(1.dp, if (on) Color(0xFF8AD2FF) else Color.White.copy(alpha = 0.18f), shape).tap(onClick).padding(horizontal = 9.dp, vertical = 4.dp)) {
        T(text, 11.5.sp, Color.White, FontWeight.Bold, maxLines = 1, fit = true)
    }
}

@Composable
private fun MoodTable(pid: String) {
    watch()
    val rows = Game.moodStats(pid)
    if (rows.isEmpty()) return
    Label(tr("Pod vlivem"))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel2).padding(8.dp)) {
        Row { listOf("", tr("hand"), "VPIP", "PFR", "AF", tr("výhry")).forEach { T(it, 11.sp, C.muted, FontWeight.Bold, modifier = Modifier.weight(1f)) } }
        rows.forEach { o ->
            Row {
                T(if (o.m == "·") tr("bez") else o.m, 14.sp, modifier = Modifier.weight(1f))
                T("${o.n}", 13.sp, modifier = Modifier.weight(1f))
                T("${(o.vp * 100.0 / o.n).roundToInt()} %", 13.sp, modifier = Modifier.weight(1f))
                T("${(o.pfr * 100.0 / o.n).roundToInt()} %", 13.sp, modifier = Modifier.weight(1f))
                T(String.format(I18n.locale, "%.1f", o.agg.toDouble() / max(1, o.calls)), 13.sp, modifier = Modifier.weight(1f))
                T("${(o.win * 100.0 / o.n).roundToInt()} %", 13.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

fun loadImg(ctx: Context, uri: Uri): String? = runCatching {
    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val side = min(src.width, src.height)
    val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val out = Bitmap.createScaledBitmap(crop, 160, 160, true)
    val bo = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 82, bo)
    "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

@Composable
private fun PhotoButtons(p: Player) {
    watch()
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val url = loadImg(ctx, uri)
            if (url == null) Game.toast(tr("Tuhle fotku se nepodařilo načíst"))
            else { p.img = url; Game.persistP(p); Game.update(); Ui.redraw(); Game.toast(tr(if (p.id == "hero") "Obrázek nahrán" else "Fotka nahrána")) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(tr(if (p.id == "hero") (if (p.img.isNotEmpty()) "Změnit obrázek" else "Nahrát svůj obrázek") else (if (p.img.isNotEmpty()) "📷 Změnit fotku" else "📷 Nahrát fotku")), BK.GHOST, height = 38.dp) {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (p.img.isNotEmpty()) Btn(tr(if (p.id == "hero") "Odebrat" else "Odebrat fotku"), BK.GHOST, height = 38.dp) { p.img = ""; Game.persistP(p); Game.update(); Ui.redraw() }
    }
}

// ---------------- end of hand (live mode) ----------------

@Composable
private fun EndSheet(e: EndState) {
    watch()
    val g = Game
    val st = g.st
    val lv = g.liveOpps()
    val sd = g.showdownEval(e)
    SheetHead(tr("Konec handy"))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipPile(g.potTotal(), 4, 20.dp)
        Column { T("POT", 11.sp, C.muted, FontWeight.ExtraBold); T(g.fm(g.potTotal()), 26.sp, C.gold, num = true) }
    }
    if (lv.isNotEmpty()) {
        Label(tr("Showdown · karty soupeřů"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lv.forEach { s ->
                val k2 = st.seats.indexOf(s)
                Column(Modifier.clip(RoundedCornerShape(10.dp)).background(C.panel2).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(g.pl(s).name, 12.sp, weight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (j in 0..1) {
                            val sl = 10 + 2 * k2 + j
                            val v = g.slotVal(sl)
                            Box(Modifier.tap {
                                val first = g.slotGroup(sl).firstOrNull { g.slotVal(it) == null }
                                Ui.open("card", CardArg(if (v == null && first != null) first else sl), Sheet("end", e))
                            }) { if (v == null) CardSlot(30.dp, "?") else CardView(v, 30.dp) }
                        }
                    }
                    sd.cat[s.pid]?.let { T(I18n.cat(Cards.CAT[it]), 11.sp, C.gold, FontWeight.Bold) }
                }
            }
        }
        if (sd.done) Note("<b>" + sd.text + "</b>") else Note(tr("Zadej karty, které soupeři ukázali. Když budou známé všechny, aplikace sama určí vítěze."))
    }
    Label(tr("Jak to dopadlo?"))
    Seg(listOf("win" to tr("Vyhrál jsem"), "split" to tr("Dělba"), "lose" to tr("Prohrál jsem")), e.result) { e.result = it; e.manRes = true; Ui.redraw() }
    if (e.result != "win") {
        Label(tr(if (e.result == "split") "S kým jsi dělil?" else "Kdo vyhrál?"))
        Picks((lv.ifEmpty { g.opps() }).map { it.pid to g.pl(it).name }, e.winner ?: "") { e.winner = it; e.manRes = true; Ui.redraw() }
    }
    if (lv.isNotEmpty()) {
        Label(tr("Co ukázali soupeři?"))
        Note(tr("Zapíše se do profilu hráče a zpřesní příští odhady bluffu."))
        lv.forEach { s ->
            T(g.pl(s).name, 12.sp, weight = FontWeight.Bold)
            Seg(listOf("none" to tr("Neukázal"), "bluff" to tr("Bluffoval"), "strong" to tr("Silná ruka")), e.rev[s.pid] ?: "none",
                colorFor = { if (it == "bluff") C.lose else if (it == "strong") C.win else null }) { e.rev[s.pid] = it; e.man[s.pid] = true; Ui.redraw() }
        }
    }
    Btn(tr("Uložit a rozdat další handu"), modifier = Modifier.fillMaxWidth()) { Ui.close(); g.saveHand(e) }
}

// ---------------- hand rankings ----------------

@Composable
private fun HandsSheet() {
    watch()
    SheetHead(tr("Výherní kombinace"))
    val hx = listOf(
        Triple("Královská postupka", listOf(48, 44, 40, 36, 32), "A, K, Q, J, 10 ve stejné barvě"),
        Triple("Postupka v barvě", listOf(29, 25, 21, 17, 13), "Pět karet po sobě ve stejné barvě"),
        Triple("Čtveřice", listOf(44, 45, 46, 47, 6), "Čtyři karty stejné hodnoty"),
        Triple("Full house", listOf(40, 41, 42, 31, 28), "Trojice a pár"),
        Triple("Barva", listOf(50, 38, 26, 18, 2), "Pět karet stejné barvy"),
        Triple("Postupka", listOf(35, 30, 24, 21, 19), "Pět karet po sobě v různých barvách"),
        Triple("Trojice", listOf(20, 21, 22, 47, 8), "Tři karty stejné hodnoty"),
        Triple("Dva páry", listOf(36, 38, 13, 15, 48), "Dva různé páry"),
        Triple("Pár", listOf(33, 35, 46, 16, 7), "Dvě karty stejné hodnoty"),
        Triple("Vysoká karta", listOf(51, 42, 28, 17, 3), "Nic z výše uvedeného, rozhoduje nejvyšší karta"),
    )
    hx.forEachIndexed { i, (n, cs, d) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            T("${i + 1}", 18.sp, C.gold, num = true, modifier = Modifier.width(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { cs.forEach { CardView(it, 25.dp) } }
            Column { T(I18n.cat(n), 14.sp, weight = FontWeight.ExtraBold); T(tr(d), 12.sp, C.muted) }
        }
    }
    Note(tr("Při shodě rozhoduje vyšší hodnota, pak kicker (nejvyšší zbývající karta). Barvy mezi sebou nemají pořadí. Z 2 karet v ruce a 5 na stole se počítá nejlepších 5."))
}

// ---------------- blinds ----------------

@Composable
private fun BlindsSheet(a: BlindsArg) {
    watch()
    val g = Game
    val st = g.st
    val cur = st.curLevel
    val rec = min(Cards.LEVELS.size - 1, cur + 1)
    SheetHead(tr("Zvýšit blindy"))
    androidx.compose.material3.Text(rich(L("Teď se hraje <b>${Cards.lvlStr(cur)}</b>. Doporučené další zvýšení je <b>${Cards.lvlStr(rec)}</b>.", "Now playing <b>${Cards.lvlStr(cur)}</b>. Recommended next level is <b>${Cards.lvlStr(rec)}</b>.", "Aktuell <b>${Cards.lvlStr(cur)}</b>. Empfohlene nächste Stufe: <b>${Cards.lvlStr(rec)}</b>.")), color = C.ink, fontSize = 13.sp)
    LevelGrid(sel = a.sel, cur = cur, rec = rec, minLevel = cur) { a.sel = it; Ui.redraw() }
    var mins by remember { mutableStateOf(st.blindMin.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T(tr("Odpočet do zvýšení"), 13.sp)
        Field(mins, { v -> mins = v.filter { it.isDigit() }.take(3); st.blindMin = mins.toIntOrNull() ?: 0; if (st.blindStart == 0L) st.blindStart = System.currentTimeMillis(); g.saveLocal(); g.bump() }, Modifier.width(80.dp), number = true, big = true)
        T(tr("minut (0 = vypnuto)"), 13.sp)
        Btn(tr("Znovu od začátku"), BK.GHOST, height = 40.dp) { st.blindStart = System.currentTimeMillis(); st.blindPaused = null; g.saveLocal(); g.bump(); g.toast(tr("Odpočet spuštěn znovu")) }
    }
    if (a.sel == cur) Btn(L("Ponechat ", "Keep ", "Beibehalten ") + Cards.lvlStr(cur), BK.GHOST, Modifier.fillMaxWidth()) { Ui.close() }
    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(L("Zvýšit hned na ", "Raise now to ", "Sofort erhöhen auf ") + Cards.lvlStr(a.sel), modifier = Modifier.weight(1f)) { Ui.close(); g.applyBlindsNow(a.sel) }
        Btn(tr("Od další handy"), BK.GHOST, Modifier.weight(1f)) { Ui.close(); g.applyBlindsNext(a.sel) }
    }
}

@Composable
private fun LevelGrid(sel: Int, cur: Int = -1, rec: Int = -1, minLevel: Int = 0, count: Int = Cards.LEVELS.size, onPick: (Int) -> Unit) {
    watch()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until count).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { i ->
                    val dis = i < minLevel
                    val isCur = i == cur; val isSel = i == sel && i != cur; val isRec = i == rec && i != sel
                    val bg = if (isCur) C.ink else if (isSel) C.gold else C.panel2
                    val fg = if (isCur) C.room else if (isSel) C.goldInk else if (isRec) C.gold else C.ink
                    Column(
                        Modifier.weight(1f).height(50.dp).alpha(if (dis) 0.35f else 1f).clip(RoundedCornerShape(10.dp)).background(bg)
                            .border(1.dp, if (isRec) C.gold else if (isCur) C.ink else C.line, RoundedCornerShape(10.dp))
                            .then(if (!dis) Modifier.tap { onPick(i) } else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        T(Cards.lvlStr(i), 18.sp, fg, num = true)
                        if (isCur) T(tr("teď").uppercase(), 9.sp, fg, FontWeight.ExtraBold) else if (i == rec && cur >= 0) T(tr("doporučeno").uppercase(), 9.sp, fg, FontWeight.ExtraBold)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------- settings ----------------

@Composable
private fun SettingsSheet(d: SettingsArg) {
    watch()
    val g = Game
    val st = g.st
    val ctx = LocalContext.current
    if (d.tab == "chips" || d.tab == "table") d.tab = "look"
    if (d.tab == "lang") d.tab = "game"
    if (d.tab == "game" && !g.gm() && !g.online() && !g.playing()) d.tab = "look"
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    // left: setting groups as small glass cards (same style as the lobby buttons)
    Column(Modifier.width(168.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        T(L("Nastavení", "Settings", "Einstellungen"), 22.sp, Color.White, FontWeight.ExtraBold)
        listOf(
            Triple("game", L("Hra", "Game", "Spiel"), L("rádce, nová hra", "coach, new game", "Coach, neues Spiel")),
            Triple("look", L("Stůl a karty", "Table & cards", "Tisch & Karten"), L("barva, rub, líc", "colour, back, face", "Farbe, Rücken, Bild")),
            Triple("data", L("Můj profil", "My profile", "Mein Profil"), L("jméno, foto, soukromí", "name, photo, privacy", "Name, Foto, Privatsphäre")),
            Triple("update", L("Aktualizace", "Updates", "Updates"), L("nová verze", "new version", "neue Version")),
        ).forEach { (k, l, sub) -> SettingsTabCard(l, sub, d.tab == k) { d.tab = k; Ui.redraw() } }
    }
    Column(Modifier.weight(1f).padding(top = 4.dp, end = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    when (d.tab) {
        "game" -> {
            if (g.online()) OnlineSettingsGame()
            else if (g.bot() && g.playing() && g.st.over == null) {
                SummaryRow(listOf(
                    tr("Soupeřů") to "${st.seats.drop(1).count { !it.out }} / ${st.n - 1}",
                    tr("Blindy teď") to Cards.lvlStr(st.curLevel),
                    tr("Start žetony") to g.fm(st.startStack),
                    tr("Zvyšování blindů") to if (st.blindHands > 0) L("po ${st.blindHands} hand", "every ${st.blindHands}", "alle ${st.blindHands}") else tr("nikdy"),
                ))
                CoachSwitch(g.gameCfg.coach) { g.gameCfg.coach = it; g.saveCfg(); Ui.coachMin = false; g.bump() }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn(tr("☰ Hlavní menu"), BK.GHOST, Modifier.weight(1f), height = 40.dp) { Ui.close(); Ui.showMenu() }
                    Btn(tr("♠ Výherní kombinace"), BK.GHOST, Modifier.weight(1f), height = 40.dp) { Ui.open("hands", null, Ui.sheet) }
                    Btn(tr("Nová hra"), BK.GHOST, Modifier.weight(1f), height = 40.dp) { Ui.openGameSetup() }
                    Btn(tr("Ukončit hru"), BK.DANGER, Modifier.weight(1f), height = 40.dp) {
                        Ui.confirm(tr("Ukončit hru?"), L("Hra proti počítači skončí a vrátíš se do menu.", "The game against the computer ends and you go back to the menu.", "Das Spiel gegen den Computer endet, zurück ins Menü."), tr("Ukončit hru")) { g.endGameVsComputer(); g.saveLocal(); Ui.close(); Ui.showMenu() }
                    }
                }
            } else DefaultTableSettings()
        }
        "look" -> LookSettings()
        "data" -> MySettings()

        else -> UpdateTab()
    }
    }
    }
}

/** My profile: who I am at the table, what friends see, notifications. */
@Composable
private fun MySettings() {
    watch()
    val g = Game
    val ctx = LocalContext.current
    val me = g.P.getValue("hero")
    val O = com.onoseto.chipsnfish.online.Online
    val online = com.onoseto.chipsnfish.online.SupaCfg.configured
    var name by remember { mutableStateOf(me.name) }
    LaunchedEffect(Unit) { if (online) { O.loadFriendCode(); com.onoseto.chipsnfish.online.Social.loadBank() } }
    val leaf = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // who I am
        Column(
            Modifier.weight(1f).clip(leaf).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.14f), leaf).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(me, 64.dp, ring = LobbyBlue)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(L("Jméno u stolu", "Name at the table", "Name am Tisch"))
                    Field(name, { name = it.take(20); me.name = it.trim().ifEmpty { "Ty" }; g.saveLocal(); g.bump() }, Modifier.fillMaxWidth(), big = true, minH = 40.dp)
                }
            }
            PhotoButtons(me)
            if (online) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("💰", com.onoseto.chipsnfish.online.Social.bank?.let { g.fm(it) } ?: "…", L("peněženka", "wallet", "Geldbörse"), Modifier.weight(1f))
                MiniStat("⭐", com.onoseto.chipsnfish.online.Fame.honor?.let { g.fm(it) } ?: "…", L("body cti", "honor", "Ehre"), Modifier.weight(1f))
                MiniStat("🏆", (com.onoseto.chipsnfish.online.Fame.cups ?: 0).toString(), L("poháry", "cups", "Pokale"), Modifier.weight(1f))
            }
            if (online) O.myFriendCode?.let { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(L("Můj kód pro přátele", "My friend code", "Mein Freundescode"), 12.sp, GlassMuted, FontWeight.Bold, modifier = Modifier.weight(1f))
                    T(c, 18.sp, LobbyBlue, num = true, letter = 2.sp)
                }
            }
        }
        // privacy and notifications
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (online) {
                OptRow(L("Moje karty po foldu", "My cards after a fold", "Meine Karten nach Fold"),
                    L("Kdo složí, smí vidět moje karty", "Players who folded may see my cards", "Wer gefoldet hat, sieht meine Karten")) {
                    SmallPill(L("Ano", "Yes", "Ja"), g.gameCfg.peek) { if (!g.gameCfg.peek) O.setPeek(true); Ui.redraw() }
                    SmallPill(L("Ne", "No", "Nein"), !g.gameCfg.peek) { if (g.gameCfg.peek) O.setPeek(false); Ui.redraw() }
                }
                var ev by remember { mutableStateOf(com.onoseto.chipsnfish.online.Invites.every(ctx)) }
                OptRow(L("Pozvánky a výzvy", "Invites and challenges", "Einladungen"),
                    L("Jak často se telefon sám podívá, když je aplikace zavřená", "How often the phone checks while the app is closed", "Wie oft geprüft wird, wenn die App zu ist")) {
                    listOf(0 to L("Vyp.", "Off", "Aus"), 15 to "15 min", 60 to "1 h").forEach { (m, l) ->
                        SmallPill(l, ev == m) { ev = m; com.onoseto.chipsnfish.online.Invites.setEvery(ctx, m) }
                    }
                }
            }
            OptRow(L("Moje karty u stolu", "My cards at the table", "Meine Karten am Tisch"),
                L("Skryté = lícem dolů, podržením je ukážeš", "Hidden = face down, hold to look", "Verdeckt = gedrückt halten zum Ansehen")) {
                SmallPill(L("Vidět", "Shown", "Offen"), !g.look.hide) { g.look.hide = false; g.saveLook(); g.bump() }
                SmallPill(L("Skryté", "Hidden", "Verdeckt"), g.look.hide) { g.look.hide = true; g.saveLook(); g.bump() }
            }
            if (online) androidx.compose.material3.Text(
                L("Smazat můj online profil", "Delete my online profile", "Mein Online-Profil löschen"), color = C.lose.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp).tap {
                    Ui.confirm(L("Smazat online profil?", "Delete online profile?", "Online-Profil löschen?"),
                        L("Ze serveru se smaže tvoje jméno, fotka, peněženka, body cti, poháry a přátelé a odejdeš od stolu. Nejde to vrátit.",
                          "Your name, photo, wallet, honor points, cups and friends are deleted from the server and you leave the table. This can't be undone.",
                          "Name, Foto, Geldbörse, Ehrenpunkte, Pokale und Freunde werden vom Server gelöscht. Nicht umkehrbar."),
                        L("Smazat", "Delete", "Löschen")) { O.deleteMe { } }
                },
            )
        }
    }
}

@Composable
private fun MiniStat(icon: String, value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T("$icon $value", 14.sp, Color.White, num = true, maxLines = 1)
        T(label, 10.sp, GlassMuted, FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun OptRow(title: String, sub: String, choices: @Composable () -> Unit) {
    val leaf = RoundedCornerShape(topStart = 16.dp, topEnd = 5.dp, bottomEnd = 16.dp, bottomStart = 5.dp)
    Row(
        Modifier.fillMaxWidth().clip(leaf).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.12f), leaf).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T(title, 13.sp, Color.White, FontWeight.ExtraBold, maxLines = 1)
            T(sub, 10.5.sp, GlassMuted, maxLines = 2, lineHeight = 12.sp)
        }
        choices()
    }
}

/** Defaults used when you open a new table with friends or start practice. */
@Composable
private fun DefaultTableSettings() {
    watch()
    val c = Game.gameCfg
    fun save(f: (com.onoseto.chipsnfish.game.GameCfg) -> com.onoseto.chipsnfish.game.GameCfg) { Game.gameCfg = f(Game.gameCfg); Game.saveCfg(); Ui.redraw() }
    T(L("Výchozí nastavení nového stolu", "Defaults for a new table", "Standard für einen neuen Tisch"), 16.sp, Color.White, FontWeight.ExtraBold)
    Note(L("Použije se, když zakládáš stůl s přáteli nebo trénink. Při zakládání to můžeš ještě změnit.", "Used when you open a table with friends or start practice. You can still change it then.", "Gilt für neue Tische mit Freunden und das Training. Beim Eröffnen noch änderbar."))
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(tr("Startovní žetony"))
            Picks(listOf("100" to "100", "200" to "200", "300" to "300"), (if (c.stack in listOf(100, 200, 300)) c.stack else 100).toString(), small = true) { v -> save { it.copy(stack = v.toInt()) } }
            Label(tr("Startovní blindy"))
            Picks((0 until 7).map { it.toString() to Cards.lvlStr(it) }, c.level.toString(), small = true) { v -> save { it.copy(level = v.toInt()) } }
            Label(tr("Zvyšování blindů"))
            Picks(listOf("0" to tr("Nikdy"), "5" to "5 hand", "10" to "10 hand", "15" to "15 hand", "20" to "20 hand"), c.every.toString(), small = true) { v -> save { it.copy(every = v.toInt()) } }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(L("Trénink · počet soupeřů", "Practice · opponents", "Training · Gegner"))
            Picks((1..9).map { it.toString() to it.toString() }, c.opp.toString(), small = true) { v -> save { it.copy(opp = v.toInt()) } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Switch(c.advisor, { v -> save { it.copy(advisor = v) } }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF2F7BFF)))
                Column {
                    T(L("Rádce u stolu s přáteli", "Advisor at friends' tables", "Berater am Freundestisch"), 14.sp, weight = FontWeight.Bold)
                    T(L("žárovka se šancemi z veřejných informací", "the bulb with odds from public info", "Glühbirne mit Chancen aus öffentlichen Infos"), 11.sp, C.muted, maxLines = 1)
                }
            }
            CoachSwitch(c.coach) { v -> save { it.copy(coach = v) } }
        }
    }
}

/** A small glass card in the settings menu (same look as the lobby buttons, smaller). */
@Composable
private fun SettingsTabCard(title: String, sub: String, on: Boolean, onClick: () -> Unit) {
    watch()
    val sh = RoundedCornerShape(topStart = 18.dp, topEnd = 5.dp, bottomEnd = 18.dp, bottomStart = 5.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .glass(sh, if (on) Color(0x552F7BFF) else Color(0x2A2A5CA8), lift = 6.dp)
            .then(if (on) Modifier.border(1.5.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF9AD6FF), Color(0xFF2F7BFF))), sh) else Modifier)
            .tap(onClick).padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(if (on) LobbyBlue else Color.White.copy(alpha = 0.22f)))
        Column(Modifier.weight(1f)) {
            T(title, 14.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true, lineHeight = 16.sp)
            T(sub, 10.sp, if (on) LobbyBlue else Color.White.copy(alpha = 0.55f), FontWeight.SemiBold, maxLines = 1, fit = true, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun UpdateTab() {
    watch()
    val ctx = LocalContext.current
    val U = com.onoseto.chipsnfish.Updater
    val (code, name) = remember { U.current(ctx) }
    if (com.onoseto.chipsnfish.BuildConfig.SELF_UPDATE) {
        // old APK from GitHub: updates now come only from Google Play
        Summary(listOf(L("Nainstalovaná verze", "Installed version", "Installierte Version") to "$name (#$code)",
            L("Aktualizace", "Updates", "Updates") to "Google Play"))
        Note("<b>" + L("Aktualizace teď chodí přes Obchod Play.", "Updates now come from the Play Store.", "Updates kommen jetzt aus dem Play Store.") + "</b> " +
            L("Tuhle verzi z GitHubu jednou odinstaluj a nainstaluj aplikaci z Obchodu Play, pak se bude aktualizovat sama. Statistiky si předtím zálohuj v Já a data.",
              "Uninstall this GitHub version once and install the app from the Play Store; then it updates itself. Back up your stats in Me & data first.",
              "Deinstalliere diese GitHub-Version einmal und installiere die App aus dem Play Store; dann aktualisiert sie sich selbst. Sichere vorher deine Statistiken unter Ich & Daten."))
        Btn(L("Otevřít v Obchodě Play", "Open in the Play Store", "Im Play Store öffnen"), BK.CALL, Modifier.fillMaxWidth()) { U.openStore(ctx, U.PLAY_TEST) }
        return
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (U.state == "idle") U.check(ctx) }
    Summary(listOf(L("Nainstalovaná verze", "Installed version", "Installierte Version") to "$name (#$code)",
        L("Nejnovější verze", "Latest version", "Neueste Version") to if (U.latest > 0) "#${U.latest}" else if (U.state == "latest") "#$code" else "–"))
    when (U.state) {
        "checking" -> Note(L("Hledám novou verzi v Obchodě Play…", "Looking for a new version on Google Play…", "Suche im Play Store nach neuer Version…"))
        "latest" -> Note("<b>" + L("Máš nejnovější verzi.", "You have the latest version.", "Du hast die neueste Version.") + "</b>")
        "available" -> {
            Note("<b>" + L("V Obchodě Play je nová verze #${U.latest}.", "Version #${U.latest} is on Google Play.", "Version #${U.latest} ist im Play Store.") + "</b> " +
                L("Data (hráči, statistiky, rozehrané hry) zůstanou.", "Your data (players, stats, games) stays.", "Deine Daten (Spieler, Statistiken, Spiele) bleiben."))
            Btn(L("⬇ Aktualizovat", "⬇ Update", "⬇ Aktualisieren"), BK.CALL, Modifier.fillMaxWidth()) {
                (ctx as? android.app.Activity)?.let { U.update(it) } ?: U.openStore(ctx)
            }
        }
        "error" -> Note(L("Obchod Play se teď nepodařilo zeptat. Zkontroluj internet, nebo otevři aplikaci v Obchodě.", "Couldn't ask Google Play right now. Check your internet or open the app in the Play Store.", "Google Play ist gerade nicht erreichbar. Internet prüfen oder die App im Play Store öffnen."))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(L("Zkontrolovat aktualizace", "Check for updates", "Nach Updates suchen"), BK.GHOST, Modifier.weight(1f)) { U.check(ctx) }
        Btn(L("Otevřít v Obchodě Play", "Open in the Play Store", "Im Play Store öffnen"), BK.GHOST, Modifier.weight(1f)) { U.openStore(ctx) }
    }
    Note(L("Aplikace se aktualizuje i sama přes Obchod Play, pokud to máš v Obchodě povolené.",
        "The Play Store also updates the app automatically if auto-update is on.",
        "Der Play Store aktualisiert die App auch automatisch, wenn Auto-Update an ist."))
}

@Composable
fun CoachSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    watch()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.Switch(on, onChange, colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = C.goldInk, checkedTrackColor = C.gold))
        Column {
            T("🎓 " + L("Rádce pro začátečníky", "Beginner coach", "Anfänger-Berater"), 14.sp, weight = FontWeight.Bold)
            T(L("Vysvětlí hru, pojmy i co hrát.", "Explains the game, terms and moves.", "Erklärt Spiel, Begriffe und Züge."), 11.5.sp, C.muted, maxLines = 1)
        }
    }
}

private fun askRestore(txt: String) {
    val b = Game.parseBackup(txt)
    if (b == null) { Game.toast(tr("Tohle není platná záloha")); return }
    val (t, data) = b
    val when_ = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, I18n.locale).format(java.util.Date(t))
    Ui.confirm(tr("Obnovit zálohu?"), L("Současná data v aplikaci se nahradí zálohou z $when_.", "The current app data will be replaced by the backup from $when_.", "Die aktuellen Daten werden durch das Backup vom $when_ ersetzt."), tr("Obnovit")) {
        Game.restoreData(data)
        Ui.hideMenu(); Ui.showMenu()
    }
}

@Composable
private fun SummaryRow(items: List<Pair<String, String>>) {
    watch()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (l, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.panel2).padding(horizontal = 10.dp, vertical = 6.dp)) {
                T(l.uppercase(), 10.sp, C.muted, FontWeight.ExtraBold, maxLines = 1); T(v, 18.sp, num = true, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Summary(items: List<Pair<String, String>>) {
    watch()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (l, v) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.panel2).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        T(l.uppercase(), 10.5.sp, C.muted, FontWeight.ExtraBold); T(v, 20.sp, num = true)
                    }
                }
            }
        }
    }
}

// ---------------- game setup ----------------

@Composable
private fun GameSetupSheet(c: GameCfg) {
    watch()
    if (c.stack !in listOf(100, 200, 300)) c.stack = 100
    SheetHead(tr("Nová hra · Texas Hold'em"))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(tr("Soupeři"))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp, 44.dp).alpha(if (c.opp <= 1) 0.35f else 1f).tap { if (c.opp > 1) { c.opp--; Ui.redraw() } }, contentAlignment = Alignment.Center) { T("−", 22.sp, weight = FontWeight.Bold) }
                    T("${c.opp}", 24.sp, num = true, modifier = Modifier.width(40.dp), align = TextAlign.Center)
                    Box(Modifier.size(46.dp, 44.dp).alpha(if (c.opp >= 9) 0.35f else 1f).tap { if (c.opp < 9) { c.opp++; Ui.redraw() } }, contentAlignment = Alignment.Center) { T("+", 22.sp, weight = FontWeight.Bold) }
                }
                T(L("${c.opp + 1} hráčů u stolu", "${c.opp + 1} players at the table", "${c.opp + 1} Spieler am Tisch"), 13.sp, C.muted)
            }
            Label(tr("Styl soupeřů"))
            Picks(listOf("mix" to tr("Mix"), "fish" to tr("Ryby"), "pro" to tr("Profíci")), c.style, small = true) { c.style = it; Ui.redraw() }
            Label(tr("Startovní žetony"))
            Picks(listOf("100" to "100", "200" to "200", "300" to "300"), c.stack.toString(), small = true) { c.stack = it.toInt(); Ui.redraw() }
            CoachSwitch(c.coach) { c.coach = it; Ui.redraw() }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(tr("Rychlost soupeřů"))
            Picks(listOf("1.6" to tr("Pomalu"), "1.0" to tr("Normálně"), "0.45" to tr("Rychle")), c.speed.toString(), small = true) { c.speed = it.toDouble(); Ui.redraw() }
            Label(tr("Startovní blindy"))
            Picks((0 until 7).map { it.toString() to Cards.lvlStr(it) }, c.level.toString(), small = true) { c.level = it.toInt(); Ui.redraw() }
            Label(tr("Zvyšování blindů"))
            Picks(listOf("0" to tr("Nikdy"), "5" to "5 hand", "10" to "10 hand", "15" to "15 hand", "20" to "20 hand").map { it.first to if (it.first == "0") it.second else L(it.second, it.first + " hands", it.first + " Hände") }, c.every.toString(), small = true) { c.every = it.toInt(); Ui.redraw() }
            Spacer(Modifier.height(4.dp))
            val saved = Game.gameSaved()
            if (saved != null && saved.over == null) Note(tr("Rozehraná hra proti počítači skončí."))
            Btn(tr("▶ Rozdat karty"), modifier = Modifier.fillMaxWidth(), height = 46.dp) {
                Game.gameCfg = c.copy(); Game.saveCfg()
                Ui.sheet = null; Ui.menu = false
                Game.newGame(c)
                Ui.goView("table")
                Game.toast(tr("Hra začala · hodně štěstí!"))
            }
        }
    }
}

// ---------------- about / confirm ----------------

@Composable
private fun AboutSheet() {
    watch()
    val g = Game
    SheetHead(tr("O aplikaci"))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        FishLogo(Modifier.size(64.dp))
        Column {
            T("Hold'em Chips&Fish", 18.sp, weight = FontWeight.ExtraBold)
            T(tr("verze") + " 2.0 · Onoseto Studio", 12.sp, C.muted)
            T(L("Pokerový pomocník a hra na Texas Hold'em. Režim Rádce počítá šance, outy a bluffy při živé hře s přáteli, režim Hra je trénink proti počítači. Všechna data zůstávají jen v tomhle zařízení.",
                "Poker helper and Texas Hold'em game. Advisor mode calculates odds, outs and bluffs while you play live with friends; Game mode is practice against the computer. All data stays on this device only.",
                "Poker-Helfer und Texas-Hold'em-Spiel. Der Berater-Modus berechnet Chancen, Outs und Bluffs beim Live-Spiel mit Freunden, der Spielmodus ist Training gegen den Computer. Alle Daten bleiben nur auf diesem Gerät."), 13.sp)
        }
    }
    Label(L("Karty", "Cards", "Karten"))
    Note(L("Anglické karty: Vector Playing Cards, Byron Knoll (volné dílo). Francouzské karty a ruby: SVG-cards, David Bellot (licence LGPL 2.1, github.com/htdebeer/SVG-cards). Kasino karty a ruby: Public Domain and CC0 Playing Cards (CC0).",
        "English cards: Vector Playing Cards by Byron Knoll (public domain). French cards and backs: SVG-cards by David Bellot (LGPL 2.1, github.com/htdebeer/SVG-cards). Casino cards and backs: Public Domain and CC0 Playing Cards (CC0).",
        "Englische Karten: Vector Playing Cards, Byron Knoll (gemeinfrei). Französische Karten und Rückseiten: SVG-cards, David Bellot (LGPL 2.1, github.com/htdebeer/SVG-cards)."))
    Label(tr("Rybí hlášky"))
    val lines = g.fishLines()
    if (lines.isEmpty()) Note(tr("Ryba mlčí. Přidej jí hlášku."))
    lines.forEachIndexed { i, x ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(x, 14.sp, modifier = Modifier.weight(1f))
            T("✕", 14.sp, C.muted, modifier = Modifier.tap { val l = lines.toMutableList(); l.removeAt(i); g.fish = l; g.saveFish(); Ui.redraw() }.padding(6.dp))
        }
    }
    var nw by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(nw, { nw = it.take(90) }, Modifier.weight(1f), placeholder = tr("Nová hláška ryby…"))
        Btn(tr("Přidat"), height = 40.dp) {
            val v = nw.trim(); if (v.isNotEmpty()) { val l = lines.toMutableList(); l.add(v); g.fish = l; g.saveFish(); nw = ""; Ui.redraw(); Ui.fishSay(v) }
        }
    }
    T(tr("Obnovit výchozí hlášky"), 12.5.sp, C.muted, modifier = Modifier.tap { g.fish = null; g.saveFish(); Ui.redraw() })
}

@Composable
private fun ConfirmSheet(a: ConfirmArg) {
    watch()
    SheetHead(a.title)
    T(a.text, 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(tr("Zrušit"), BK.GHOST, Modifier.weight(1f)) { Ui.close() }
        Btn(a.ok, BK.DANGER, Modifier.weight(1f)) { Ui.close(); a.fn() }
    }
}
