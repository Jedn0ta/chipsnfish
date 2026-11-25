@file:OptIn(ExperimentalFoundationApi::class)

package com.onoseto.chipsnfish.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.verticalScroll
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Cards
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.tr
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

const val DW = 800f
const val DH = 360f

@Composable
fun AppRoot(onExit: () -> Unit, setKeepOn: (Boolean) -> Unit) {
    watch()
    @Suppress("UNUSED_VARIABLE") val rev = Game.rev
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val scale = min(constraints.maxWidth / DW, constraints.maxHeight / DH)
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            Box(
                Modifier.size(DW.dp, DH.dp).background(Brush.radialGradient(listOf(C.room2, C.room), Offset(DW * scale * 0.4f, DH * scale * 0.45f), DW * scale * 0.7f)),
            ) {
                if (!Ui.splash) MainLayout()
                if (Ui.menu && !Ui.splash) MenuScreen(onExit)
                if (!Ui.splash) Box(Modifier.fillMaxWidth().padding(top = 40.dp).zIndex(35f), contentAlignment = Alignment.TopCenter) { InboxBanner() }
                SheetHost()
                TipOverlay()
                ToastView()
                if (Ui.splash) Splash()
            }
        }
    }
    LaunchedEffect(Ui.deep, Ui.splash) {
        val d = Ui.deep
        if (d != null && !Ui.splash) {
            Ui.deep = null
            if (!com.onoseto.chipsnfish.online.SupaCfg.configured) Game.toast(L("Online server zatím není připojený.", "The online server isn't connected yet.", "Der Online-Server ist noch nicht verbunden."))
            else if (d.first == "t") com.onoseto.chipsnfish.online.Online.join(d.second) { goTable() }
            else com.onoseto.chipsnfish.online.Online.joinGroup(d.second) { n ->
                Game.toast(L("Jsi ve skupině „$n“", "You joined \"$n\"", "Du bist in der Gruppe „$n“")); Ui.open("groups")
            }
        }
    }
    LaunchedEffect(Ui.exitRequest) { if (Ui.exitRequest > 0) { Game.saveLocal(); onExit() } }
    val keep = !Game.gm() && Game.playing() && Ui.view == "table" && !Ui.menu && !Ui.splash
    LaunchedEffect(keep) { setKeepOn(keep) }
    BackHandler {
        when {
            Ui.tip != null -> Ui.tip = null
            Ui.sheet != null -> Ui.close()
            Ui.moodSeat >= 0 -> Ui.moodSeat = -1
            Game.raiseOpen -> { Game.raiseOpen = false; Game.bump() }
            Ui.splash -> onExit()
            Ui.menu -> { Game.saveLocal(); onExit() }
            Ui.solo -> { Ui.solo = false; Ui.goView("table"); Ui.showMenu() }
            Ui.view != "table" -> Ui.goView("table")
            else -> Ui.showMenu()
        }
    }
}

@Composable
private fun MainLayout() {
    watch()
    Column(Modifier.fillMaxSize()) {
        TopBar()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (Ui.view) {
                "players" -> PlayersView()
                "records" -> RecordsView()
                else -> TableView()
            }
        }
    }
}

// ---------------- top bar ----------------

private val gearPath by lazy { PathParser().parsePathString("M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z").toPath() }
private val peoplePath by lazy { PathParser().parsePathString("M2.5 20c.8-3.6 3.4-5.5 6.5-5.5s5.7 1.9 6.5 5.5M16.5 14.6c2.6.2 4.4 2 5 4.9").toPath() }

@Composable
fun Icon24(kind: String, col: Color, size: Dp = 20.dp) {
    watch()
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, Offset.Zero) {
            val st = Stroke(2f, cap = StrokeCap.Round)
            when (kind) {
                "table" -> {
                    drawRoundRect(col, Offset(3f, 7f), androidx.compose.ui.geometry.Size(18f, 10f), androidx.compose.ui.geometry.CornerRadius(5f), style = st)
                    drawCircle(col, 1.3f, Offset(12f, 3.5f)); drawCircle(col, 1.3f, Offset(12f, 20.5f)); drawCircle(col, 1.2f, Offset(1.6f, 12f)); drawCircle(col, 1.2f, Offset(22.4f, 12f))
                }
                "players" -> {
                    drawCircle(col, 3.5f, Offset(9f, 8f), style = st); drawCircle(col, 2.6f, Offset(17f, 9f), style = st)
                    drawPath(peoplePath, col, style = st)
                }
                "records" -> {
                    drawRoundRect(col, Offset(4f, 3f), androidx.compose.ui.geometry.Size(16f, 18f), androidx.compose.ui.geometry.CornerRadius(2f), style = st)
                    drawLine(col, Offset(8f, 8f), Offset(16f, 8f), 2f, StrokeCap.Round); drawLine(col, Offset(8f, 12f), Offset(16f, 12f), 2f, StrokeCap.Round); drawLine(col, Offset(8f, 16f), Offset(13f, 16f), 2f, StrokeCap.Round)
                }
                "gear" -> { drawCircle(col, 3f, Offset(12f, 12f), style = st); drawPath(gearPath, col, style = st) }
                "power" -> {
                    drawLine(col, Offset(12f, 3f), Offset(12f, 11f), 2.2f, StrokeCap.Round)
                    drawArc(col, -50f, 280f, false, Offset(4f, 5f), androidx.compose.ui.geometry.Size(16f, 16f), style = Stroke(2.2f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    watch()
    val g = Game
    val st = g.st
    val playing = g.playing()
    var fishTaps by remember { mutableStateOf(listOf<Long>()) }
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FishLogo(
            Modifier.size(28.dp).combinedClickable(remember { MutableInteractionSource() }, null,
                onLongClick = { Ui.open("about") },
                onClick = {
                    Sound.chip()
                    val now = System.currentTimeMillis()
                    fishTaps = fishTaps.filter { now - it < 2500 } + now
                    if (fishTaps.size >= 4) {
                        fishTaps = emptyList()
                        Ui.rainId++
                        repeat(4) { Sound.chip() }
                        Ui.fishSay(L("Jackpot! Ryba vysypala kasu.", "Jackpot! The fish emptied the till.", "Jackpot! Der Fisch hat die Kasse geleert."))
                    } else {
                        Ui.swimId++
                        val fl = g.fishLines()
                        Ui.fishSay(if (fl.isNotEmpty()) fl.random() else "Blub.")
                    }
                })
        )
        if (g.online()) {
            val ok = com.onoseto.chipsnfish.online.Online.connected
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) C.win else C.lose).tap {
                g.toast(if (ok) L("Spojení se serverem je v pořádku", "Connected to the server", "Mit dem Server verbunden") else L("Spojení se serverem vypadlo, zkouším znovu…", "Lost connection to the server, retrying…", "Verbindung zum Server verloren, versuche erneut…"))
            })
        }
        if (Ui.solo) {
            Box(Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, C.line, RoundedCornerShape(9.dp)).tap { Ui.solo = false; Ui.goView("table"); Ui.showMenu() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                T(tr("‹ Menu"), 13.sp, C.gold, FontWeight.ExtraBold)
            }
        } else {
            listOf("table", "players", "records").forEach { v ->
                val on = Ui.view == v
                Box(
                    Modifier.size(44.dp, 32.dp).clip(RoundedCornerShape(9.dp)).background(if (on) C.gold.copy(alpha = 0.08f) else Color.Transparent)
                        .border(1.dp, if (on) C.gold.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(9.dp)).tap { Ui.goView(v) },
                    contentAlignment = Alignment.Center,
                ) { Icon24(v, if (on) C.gold else C.muted, 21.dp) }
            }
        }
        if (st.sample && Ui.view == "table" && !Ui.solo) {
            Box(Modifier.height(30.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, C.gold, RoundedCornerShape(9.dp)).tap { g.restartFromSample() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                T(tr("Vlastní hra ›"), 12.sp, C.gold, FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        if (Ui.view == "table" && playing && !Ui.solo) {
            Row(Modifier.clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.42f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp)).padding(2.dp)) {
                listOf("PREFLOP", "FLOP", "TURN", "RIVER").forEachIndexed { i, n ->
                    val on = i == st.betStreet
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) C.gold else Color.Transparent).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        T(n, 13.sp, if (on) C.goldInk else if (i < st.betStreet) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f), num = true, letter = 0.8.sp)
                    }
                }
            }
            T("#${st.handNo}", 15.sp, C.muted, num = true)
            BlindGroup()
        }
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(10.dp)).tap { Ui.openSettings() }, contentAlignment = Alignment.Center) {
            Icon24("gear", C.ink, 19.dp)
        }
    }
}

@Composable
private fun BlindGroup() {
    watch()
    val g = Game
    val st = g.st
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    @Suppress("UNUSED_VARIABLE") val tick = now
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(99.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A2412), Color(0xFF1A160B)))).border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(99.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // timer
        if (g.gm()) {
            if (st.blindHands > 0 && st.level < Cards.LEVELS.size - 1) {
                val left = st.blindHands - ((st.handNo - 1) % st.blindHands)
                Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("↑ ", 13.sp, C.muted, FontWeight.Bold)
                    T("$left h", 15.sp, if (left <= 1) C.gold else C.ink, num = true)
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(C.gold.copy(alpha = 0.3f)))
            }
        } else if (st.blindMin > 0) {
            if (st.blindStart == 0L) st.blindStart = System.currentTimeMillis()
            val left = g.blindLeft()
            val a = abs(left)
            val t = "%02d:%02d".format(a / 60, a % 60)
            val blink = rememberInfiniteTransition(label = "b").animateFloat(1f, 0.35f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "bb")
            Row(Modifier.tap { g.toggleBlindTimer() }.padding(horizontal = 9.dp).alpha(if (left < 0) blink.value else 1f), verticalAlignment = Alignment.CenterVertically) {
                T(if (st.blindPaused != null) "❚❚ " else "⏱ ", 12.sp, C.muted)
                T((if (left < 0) "−" else "") + t, 15.sp, if (left < 0) C.lose else if (left <= 60) C.gold else C.ink, num = true)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.gold.copy(alpha = 0.3f)))
        }
        // level
        val cur = Cards.lvl(st.curLevel)
        val pend = st.level != st.curLevel
        Row(Modifier.tap { Ui.open("blinds", BlindsArg(maxOf(st.level, minOf(Cards.LEVELS.size - 1, st.curLevel + 1)))) }.padding(start = 3.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp)) {
                ChipDisc(C.gold2, 24.dp)
                T("↑", 13.sp, C.goldInk, FontWeight.Black, modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.width(5.dp))
            T("${cur[0]}/${cur[1]}", 17.sp, C.gold, num = true)
            if (pend) T(" → " + Cards.lvlStr(st.level), 10.sp, C.gold, FontWeight.ExtraBold)
        }
    }
}

// ---------------- menu & splash ----------------

@Composable
private fun MenuScreen(onExit: () -> Unit) {
    watch()
    val g = Game
    val O = com.onoseto.chipsnfish.online.Online
    val gs = g.gameSaved()
    val gOn = gs != null && gs.over == null
    Box(Modifier.fillMaxSize().zIndex(30f).background(Brush.radialGradient(listOf(C.room2, C.room))).tap { }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FishLogo(Modifier.size(40.dp))
                Row {
                    T("HOLD'EM ", 28.sp, C.ink, num = true, letter = 1.sp)
                    GoldTitle("CHIPS&FISH", 28f)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NameBar()
                WalletBadge()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeCard(
                    icon = { CardsIcon() }, title = L("Hrát s přáteli", "Play with friends", "Mit Freunden"),
                    desc = L("Online stůl pro 2–10 hráčů. Přátele pozveš odkazem nebo kódem.", "Online table for 2–10 players. Invite friends with a link or code.", "Online-Tisch für 2–10 Spieler. Lade Freunde per Link oder Code ein."),
                    sub = if (O.code != null) L("Pokračovat · stůl ${O.code}", "Continue · table ${O.code}", "Weiter · Tisch ${O.code}") else L("Nový stůl nebo kód", "New table or code", "Neuer Tisch oder Code"),
                    cur = O.code != null, newBtn = O.code != null, newLabel = L("+ Jiný stůl", "+ Other table", "+ Anderer Tisch"), onNew = { Ui.open("online", OnlineArg()) },
                ) { if (O.code != null) goTable() else Ui.open("online", OnlineArg()) }
                ModeCard(
                    icon = { Icon24("players", C.gold, 30.dp) }, title = L("Přátelé", "Friends", "Freunde"),
                    desc = L("Kdo z přátel je v aplikaci a kde hraje. Pozvánky ke stolu a do skupin.", "Which friends are online and where they play. Table and group invites.", "Welche Freunde online sind und wo sie spielen. Tisch- und Gruppeneinladungen."),
                    sub = O.friends?.count { it.online }?.takeIf { it > 0 }?.let { L("$it online", "$it online", "$it online") } ?: L("Otevřít", "Open", "Öffnen"), cur = false, newBtn = false, onNew = {},
                ) { Ui.open("groups") }
                ModeCard(
                    icon = { Icon24("records", C.gold, 30.dp) }, title = tr("Hráči"),
                    desc = L("Statistiky soupeřů, typy hráčů, poznámky a záznam hand.", "Opponent stats, player types, notes and hand history.", "Gegnerstatistiken, Spielertypen, Notizen und Hand-Verlauf."),
                    sub = tr("Otevřít"), cur = false, newBtn = false, onNew = {},
                ) { Ui.hideMenu(); Ui.solo = true; Ui.goView("players") }
            }
            if (com.onoseto.chipsnfish.Updater.state == "available") {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(C.gold.copy(alpha = 0.12f)).border(1.dp, C.gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .tap { Ui.open("settings", SettingsArg(tab = "update", level = 0, startStack = 0, autoBlinds = true, dealer = 0)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    T("⬆", 15.sp, C.gold, FontWeight.ExtraBold)
                    T(L("Je tu nová verze aplikace", "A new app version is available", "Neue App-Version verfügbar"), 14.sp, C.ink, FontWeight.Bold)
                    T(L("Aktualizovat ›", "Update ›", "Aktualisieren ›"), 13.sp, C.gold, FontWeight.ExtraBold)
                }
            }
            val near = com.onoseto.chipsnfish.online.Nearby.tables.filter { it.code != O.code }
            if (near.isNotEmpty()) {
                val t = near.last()
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(C.win.copy(alpha = 0.14f)).border(1.dp, C.win.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .tap { O.join(t.code) { goTable() } }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    T("📶", 15.sp)
                    T(L("Na Wi-Fi hraje ${t.who} · stůl ${t.code}", "${t.who} is playing on your Wi-Fi · table ${t.code}", "${t.who} spielt in deinem WLAN · Tisch ${t.code}"), 14.sp, C.ink, FontWeight.Bold)
                    T(L("Přisednout ›", "Join ›", "Beitreten ›"), 13.sp, C.win, FontWeight.ExtraBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, C.gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).background(C.gold.copy(alpha = 0.08f))
                    .tap { Ui.open("fame") }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                T("🏆", 15.sp)
                T(L("Síň slávy", "Hall of fame", "Ruhmeshalle"), 14.sp, C.ink, FontWeight.Bold)
                T("›", 14.sp, C.gold, FontWeight.ExtraBold)
            }
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, C.line, RoundedCornerShape(12.dp)).tap {
                    O.stopPolling()
                    if (gOn) { g.switchMode("game"); Ui.hideMenu(); Ui.goView("table") } else Ui.openGameSetup()
                }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                T(L("Trénink proti počítači", "Practice vs computer", "Training gegen Computer"), 14.sp, C.ink, FontWeight.Bold)
                T(if (gOn) L("Pokračovat · hand #${gs!!.handNo}", "Continue · hand #${gs!!.handNo}", "Weiter · Hand #${gs!!.handNo}") + " ›" else L("Nová hra ›", "New game ›", "Neues Spiel ›"), 13.sp, C.gold, FontWeight.ExtraBold)
                if (gOn) T(tr("+ Nová hra"), 12.sp, C.muted, FontWeight.Bold, modifier = Modifier.tap { O.stopPolling(); Ui.openGameSetup() })
            }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(12.dp)).tap { Ui.openSettings() }, contentAlignment = Alignment.Center) { Icon24("gear", C.ink) }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(12.dp)).tap { g.saveLocal(); onExit() }, contentAlignment = Alignment.Center) { Icon24("power", C.ink) }
        }
    }
}

@Composable
private fun CardsIcon() {
    watch()
    Box(Modifier.size(34.dp)) {
        CardBack(16.dp, modifier = Modifier.align(Alignment.CenterStart).graphicsLayer(rotationZ = -10f))
        CardView(47, 16.dp, modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer(rotationZ = 8f))
    }
}

@Composable
private fun ModeCard(icon: @Composable () -> Unit, title: String, desc: String, sub: String, cur: Boolean, newBtn: Boolean, newLabel: String = tr("+ Nová hra"), onNew: () -> Unit, onClick: () -> Unit) {
    watch()
    Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.fillMaxWidth().height(150.dp).shadow(if (cur) 14.dp else 8.dp, RoundedCornerShape(16.dp), spotColor = if (cur) C.gold else Color.Black)
                .clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFF223442), Color(0xFF16222B), C.panel)))
                .border(if (cur) 2.dp else 1.dp, if (cur) Brush.linearGradient(listOf(Color(0xFFFFE9A8), C.gold2)) else Brush.verticalGradient(listOf(Color(0xFF3A4F5C), C.line)), RoundedCornerShape(16.dp)).tap(onClick).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(C.gold.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { icon() }
            T(title.uppercase(), 24.sp, C.ink, num = true)
            T(desc, 12.5.sp, C.muted, lineHeight = 16.sp, modifier = Modifier.weight(1f))
            T(sub + " ›", 13.sp, C.gold, FontWeight.ExtraBold)
        }
        if (newBtn) Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, C.line, RoundedCornerShape(10.dp)).tap(onNew), contentAlignment = Alignment.Center) {
            T(newLabel, 13.sp, C.ink, FontWeight.Bold)
        } else Spacer(Modifier.height(34.dp))
    }
}

@Composable
private fun Splash() {
    watch()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1100); ready = true }
    val spin = rememberInfiniteTransition(label = "s").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r")
    Box(Modifier.fillMaxSize().zIndex(50f).background(Brush.radialGradient(listOf(C.room2, C.room))).tap {
        if (ready) { Sound.chip(); Ui.splash = false; Ui.showMenu() }
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FishLogo(Modifier.size(120.dp).graphicsLayer(rotationZ = spin.value))
            Row {
                T("HOLD'EM ", 40.sp, C.ink, num = true, letter = 1.sp)
                GoldTitle("CHIPS&FISH", 40f)
            }
            T(tr("Klepni pro pokračování"), 14.sp, C.muted, FontWeight.Bold, modifier = Modifier.alpha(if (ready) 1f else 0f))
        }
        T("Onoseto Studio", 11.sp, C.muted.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
    }
}

// ---------------- overlays ----------------

@Composable
private fun TipOverlay() {
    watch()
    val t = Ui.tip ?: return
    Box(Modifier.fillMaxSize().zIndex(60f).background(Color.Black.copy(alpha = 0.35f)).tap { Ui.tip = null }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).shadow(16.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(C.panel).border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T(t[0], 16.sp, C.gold, FontWeight.ExtraBold)
            if (t[1].isNotEmpty()) T(t[1], 13.sp)
            if (t[2].isNotEmpty()) T(t[2], 13.sp, C.ink, FontWeight.Bold)
            if (t[3].isNotEmpty()) T(t[3], 12.sp, C.muted)
        }
    }
}

@Composable
private fun ToastView() {
    watch()
    val msg = Game.toastMsg ?: return
    val id = Game.toastId
    var vis by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) { delay(1800); vis = false }
    if (!vis) return
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).zIndex(70f), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 680.dp).shadow(8.dp, RoundedCornerShape(99.dp)).clip(RoundedCornerShape(99.dp)).background(C.ink).padding(horizontal = 14.dp, vertical = 8.dp)) {
            T(msg, 13.5.sp, C.room, FontWeight.ExtraBold, maxLines = 1, align = TextAlign.Center)
        }
    }
}

@Suppress("unused")
private val circle = CircleShape


@Composable
fun GoldTitle(t: String, size: Float) {
    watch()
    androidx.compose.material3.Text(
        t, fontFamily = F.num, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, letterSpacing = 1.sp,
        style = androidx.compose.ui.text.TextStyle(
            brush = Brush.verticalGradient(listOf(Color(0xFFFFEDB0), C.gold, C.gold2)),
            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), 8f),
        ),
    )
}


/** "You play as: [name]" right on the start screen. */
@Composable
private fun NameBar() {
    watch()
    val me = Game.P.getValue("hero")
    var name by remember { mutableStateOf(if (me.name == "Ty") "" else me.name) }
    LaunchedEffect(name) {
        delay(900)
        if (com.onoseto.chipsnfish.online.SupaCfg.configured && name.isNotBlank()) runCatching { com.onoseto.chipsnfish.online.Online.syncProfile() }
    }
    val empty = name.isBlank()
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(C.panel2.copy(alpha = 0.85f))
            .border(1.dp, if (empty) C.gold else C.line, RoundedCornerShape(99.dp)).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(me, 30.dp, border = 2.dp)
        T(if (empty) L("Jak ti máme říkat?", "What's your name?", "Wie heißt du?") else L("Hraješ jako", "Playing as", "Du spielst als"), 13.sp, if (empty) C.gold else C.muted, FontWeight.Bold)
        Box(Modifier.width(170.dp)) {
            if (name.isEmpty()) T(L("Zadej jméno", "Enter your name", "Name eingeben"), 16.sp, C.muted.copy(alpha = 0.7f))
            androidx.compose.foundation.text.BasicTextField(
                name, { v -> name = v.take(24); me.name = v.trim().ifEmpty { "Ty" }; Game.saveLocal(); Game.bump() },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.gold), modifier = Modifier.fillMaxWidth(),
            )
        }
        T("✎", 14.sp, C.muted)
    }
}
