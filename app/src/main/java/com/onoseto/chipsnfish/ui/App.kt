@file:OptIn(ExperimentalFoundationApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
            else if (d.first == "f") com.onoseto.chipsnfish.online.Online.addFriendByCode(d.second) { Ui.open("groups") }
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
    Box(Modifier.fillMaxSize().zIndex(30f).background(C.room).tap { }) {
        // the lobby: a warm card room in the background
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.onoseto.chipsnfish.R.drawable.lobby_bg), null,
            Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        LobbyBokeh()
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.62f), 0.55f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.45f))))
        // top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FishLogo(Modifier.size(34.dp))
            Row {
                T("HOLD'EM ", 22.sp, C.ink, num = true, letter = 1.sp)
                GoldTitle("CHIPS&FISH", 22f)
            }
            Spacer(Modifier.weight(1f))
            WalletBadge()
            LobbyIconBtn("gear") { Ui.openSettings() }
            LobbyIconBtn("power") { g.saveLocal(); onExit() }
        }
        // left: play with friends
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 16.dp, top = 30.dp).width(400.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glass(Modifier.fillMaxWidth()) {
                T(L("HRÁT S PŘÁTELI", "PLAY WITH FRIENDS", "MIT FREUNDEN SPIELEN"), 26.sp, C.ink, num = true, letter = 1.sp)
                T(L("Online stůl pro 2–10 hráčů. Karty rozdává server.", "Online table for 2–10 players. The server deals.", "Online-Tisch für 2–10 Spieler. Der Server gibt."), 12.5.sp, C.muted, maxLines = 1, fit = true)
                Spacer(Modifier.height(4.dp))
                if (O.code != null) {
                    Btn("▶ " + L("Pokračovat · stůl ${O.code}", "Continue · table ${O.code}", "Weiter · Tisch ${O.code}"), BK.GOLD, Modifier.fillMaxWidth(), height = 48.dp) { goTable() }
                    Btn(L("Jiný stůl", "Other table", "Anderer Tisch"), BK.GHOST, Modifier.fillMaxWidth(), height = 36.dp) { Ui.open("online", OnlineArg()) }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("▶ " + L("Nový stůl", "New table", "Neuer Tisch"), BK.GOLD, Modifier.weight(1f), height = 48.dp) { Ui.open("online", OnlineArg()) }
                    Btn(L("Připojit kódem", "Join with code", "Mit Code beitreten"), BK.DARK, Modifier.weight(1f), height = 48.dp) { Ui.open("online", OnlineArg(tab = "join")) }
                }
                val near = com.onoseto.chipsnfish.online.Nearby.tables.filter { it.code != O.code }
                if (near.isNotEmpty()) {
                    val t = near.last()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.win.copy(alpha = 0.16f)).border(1.dp, C.win.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .tap { O.join(t.code) { goTable() } }.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        T("📶", 14.sp)
                        T(L("Na Wi-Fi hraje ${t.who} · ${t.code}", "${t.who} on your Wi-Fi · ${t.code}", "${t.who} im WLAN · ${t.code}"), 13.sp, C.ink, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, fit = true)
                        T(L("Přisednout ›", "Join ›", "Beitreten ›"), 12.5.sp, C.win, FontWeight.ExtraBold)
                    }
                }
                FriendsOnlineRow()
            }
            NameBar()
        }
        // right: the other rooms of the lobby
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 30.dp).width(340.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LobbyTile("🤖", L("Trénink", "Practice", "Training"),
                    if (gOn) L("Pokračovat · hand #${gs!!.handNo}", "Continue · hand #${gs!!.handNo}", "Weiter · Hand #${gs!!.handNo}") else L("Proti počítači", "Vs the computer", "Gegen Computer"),
                    Modifier.weight(1f), hot = gOn) {
                    O.stopPolling()
                    if (gOn) { g.switchMode("game"); Ui.hideMenu(); Ui.goView("table") } else Ui.openGameSetup()
                }
                LobbyTile("👥", L("Přátelé", "Friends", "Freunde"),
                    O.friends?.count { it.online }?.takeIf { it > 0 }?.let { L("$it online", "$it online", "$it online") } ?: L("Kódy a pozvánky", "Codes & invites", "Codes & Einladungen"),
                    Modifier.weight(1f)) { Ui.open("groups") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LobbyTile("📊", L("Statistiky", "Statistics", "Statistiken"), L("Všichni hráči", "All players", "Alle Spieler"), Modifier.weight(1f)) {
                    Ui.hideMenu(); Ui.solo = true; Ui.goView("players")
                }
                LobbyTile("🏆", L("Síň slávy", "Hall of fame", "Ruhmeshalle"), L("Body cti a poháry", "Honor & cups", "Ehre & Pokale"), Modifier.weight(1f)) { Ui.open("fame") }
            }
            if (gOn) T(L("+ Nový trénink", "+ New practice", "+ Neues Training"), 12.sp, C.muted, FontWeight.Bold,
                modifier = Modifier.align(Alignment.End).tap { O.stopPolling(); Ui.openGameSetup() })
            if (com.onoseto.chipsnfish.Updater.state == "available") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.gold.copy(alpha = 0.16f)).border(1.dp, C.gold.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .tap { Ui.open("settings", SettingsArg(tab = "update", level = 0, startStack = 0, autoBlinds = true, dealer = 0)) }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    T("⬆", 14.sp, C.gold, FontWeight.ExtraBold)
                    T(L("Nová verze aplikace", "New app version", "Neue App-Version"), 13.sp, C.ink, FontWeight.Bold, modifier = Modifier.weight(1f))
                    T(L("Aktualizovat ›", "Update ›", "Aktualisieren ›"), 12.5.sp, C.gold, FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** Frosted dark panel used in the lobby. */
@Composable
private fun Glass(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(18.dp)
    Column(
        modifier.shadow(16.dp, sh).clip(sh)
            .background(Brush.verticalGradient(listOf(Color(0xE61C2A35), Color(0xF00E161C))))
            .border(1.dp, Brush.verticalGradient(listOf(C.gold.copy(alpha = 0.7f), Color.White.copy(alpha = 0.06f))), sh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content,
    )
}

@Composable
private fun LobbyTile(icon: String, title: String, sub: String, modifier: Modifier, hot: Boolean = false, onClick: () -> Unit) {
    watch()
    val sh = RoundedCornerShape(16.dp)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.95f else 1f, label = "tile")
    Column(
        modifier.height(104.dp).graphicsLayer(scaleX = sc, scaleY = sc).shadow(10.dp, sh, spotColor = if (hot) C.gold else Color.Black).clip(sh)
            .background(Brush.verticalGradient(listOf(Color(0xE6223442), Color(0xF0121B22))))
            .border(if (hot) 2.dp else 1.dp, if (hot) Brush.linearGradient(listOf(Color(0xFFFFE9A8), C.gold2)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))), sh)
            .clickable(src, null, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        T(icon, 24.sp, lineHeight = 28.sp)
        Spacer(Modifier.weight(1f))
        T(title.uppercase(), 17.sp, C.ink, num = true, maxLines = 1, fit = true)
        T(sub + " ›", 11.5.sp, if (hot) C.gold else C.muted, FontWeight.Bold, maxLines = 1, fit = true)
    }
}

@Composable
private fun LobbyIconBtn(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon24(icon, C.ink) }
}

/** Online friends as little avatars; tap = friends & invites. */
@Composable
private fun FriendsOnlineRow() {
    watch()
    val on = com.onoseto.chipsnfish.online.Online.friends?.filter { it.online }.orEmpty()
    if (on.isEmpty()) return
    Row(Modifier.tap { Ui.open("groups") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        T(L("Online:", "Online:", "Online:"), 11.5.sp, C.muted, FontWeight.Bold)
        on.take(7).forEach { f ->
            Avatar(com.onoseto.chipsnfish.game.Player(f.uid, f.name, img = f.img, hat = Game.P[f.uid]?.hat ?: ""), 26.dp, ring = C.win, border = 2.dp)
        }
        if (on.size > 7) T("+${on.size - 7}", 12.sp, C.muted, num = true)
    }
}

/** Slowly drifting warm light specks over the lobby picture. */
@Composable
private fun LobbyBokeh() {
    val t = rememberInfiniteTransition(label = "bokeh").animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "b")
    val dots = remember { List(18) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat() * 0.6f, 4f + Math.random().toFloat() * 10f, Math.random().toFloat()) } }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        dots.forEach { d ->
            val ph = (t.value + d[3]) % 1f
            val x = (d[0] + ph * 0.08f) % 1f * size.width
            val y = d[1] * size.height + kotlin.math.sin(ph * 6.283f) * 8f
            val a = 0.05f + 0.1f * kotlin.math.sin(ph * 3.1416f)
            drawCircle(Color(0xFFFFD18A).copy(alpha = a), d[2] * density, androidx.compose.ui.geometry.Offset(x, y))
        }
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
