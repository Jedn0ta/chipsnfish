@file:OptIn(ExperimentalFoundationApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.launch
import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.composed
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
        val bd = rememberBackdrop()
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f), LocalBackdrop provides bd) {
            Box(
                Modifier.size(DW.dp, DH.dp).background(Brush.radialGradient(listOf(C.room2, C.room), Offset(DW * scale * 0.4f, DH * scale * 0.45f), DW * scale * 0.7f))
                    .onGloballyPositioned { bd.origin = it.positionInRoot(); bd.size = it.size },
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
    Box(Modifier.fillMaxSize()) {
    if (Ui.view != "table") LobbyBackground()
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
                onLongClick = { if (playing && Ui.view == "table") Ui.open("hands") else Ui.open("about") },
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
        } else if (!(playing && Ui.view == "table")) {
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

// ---------------- lobby ----------------

@Composable
private fun MenuScreen(onExit: () -> Unit) {
    watch()
    val g = Game
    val O = com.onoseto.chipsnfish.online.Online
    val gs = g.gameSaved()
    val gOn = gs != null && gs.over == null
    // a finished online game is closed when you come back to the lobby
    LaunchedEffect(O.code, O.status) { if (O.code != null && O.status == "over") O.leave() }
    Box(Modifier.fillMaxSize().zIndex(30f).background(Color(0xFF050B1A)).tap { }) {
        LobbyBackground()
        // top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FishLogo(Modifier.size(32.dp))
            Column {
                T("HOLD'EM", 11.sp, LobbyBlue, num = true, letter = 4.sp, lineHeight = 12.sp)
                T("CHIPS&FISH", 22.sp, Color.White, num = true, letter = 1.5.sp, lineHeight = 24.sp)
            }
            Spacer(Modifier.weight(1f))
            OnlinePill()
            LobbyWallet()
            LobbyIconBtn("gear") { Ui.openSettings() }
            LobbyIconBtn("power") { g.saveLocal(); onExit() }
        }
        // left: me
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 16.dp, top = 40.dp).width(236.dp)
                .glass(RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 28.dp, bottomStart = 8.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileBlock()
            val near = com.onoseto.chipsnfish.online.Nearby.tables.filter { it.code != O.code }
            if (near.isNotEmpty()) {
                val t = near.last()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp)).background(Color(0x3326D07C)).border(1.dp, Color(0x8826D07C), RoundedCornerShape(99.dp))
                        .tap { O.join(t.code) { goTable() } }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF3CE08A)))
                    T(L("${t.who} hraje na Wi-Fi · přisednout", "${t.who} on your Wi-Fi · join", "${t.who} im WLAN · beitreten"), 11.5.sp, Color.White, FontWeight.Bold, maxLines = 1, fit = true)
                }
            }
            FriendsOnlineRow()
        }
        // centre: the big play button
        Column(Modifier.align(Alignment.Center).padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (O.code != null) PlayOrb(L("POKRAČOVAT", "CONTINUE", "WEITER"), L("stůl ${O.code}", "table ${O.code}", "Tisch ${O.code}")) { goTable() }
            else PlayOrb(L("HRÁT", "PLAY", "SPIELEN"), L("s přáteli", "with friends", "mit Freunden")) { Ui.open("online", OnlineArg()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (O.code != null) GlassPill(L("Jiný stůl", "Other table", "Anderer Tisch")) { Ui.open("online", OnlineArg()) }
                GlassPill(L("Mám kód stolu", "I have a code", "Ich habe einen Code")) { Ui.open("online", OnlineArg(tab = "join")) }
            }
        }
        // right: the rest of the lobby, fanned around the play button
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 40.dp).width(214.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End,
        ) {
            LobbyRow(L("Trénink", "Practice", "Training"),
                if (gOn) L("Pokračovat · hand #${gs!!.handNo}", "Continue · hand #${gs!!.handNo}", "Weiter · Hand #${gs!!.handNo}") else L("Proti počítači", "Vs the computer", "Gegen den Computer"),
                hot = gOn, extra = if (gOn) L("Nový", "New", "Neu") else null, onExtra = { O.stopPolling(); Ui.openGameSetup() }) {
                O.stopPolling()
                if (gOn) { g.switchMode("game"); Ui.hideMenu(); Ui.goView("table") } else Ui.openGameSetup()
            }
            LobbyRow(L("Přátelé", "Friends", "Freunde"),
                O.friends?.count { it.online }?.takeIf { it > 0 }?.let { L("$it online", "$it online", "$it online") } ?: L("Kódy a pozvánky", "Codes & invites", "Codes & Einladungen")) { Ui.open("groups") }
            LobbyRow(L("Statistiky", "Statistics", "Statistiken"), L("Všichni hráči", "All players", "Alle Spieler")) {
                Ui.hideMenu(); Ui.solo = true; Ui.goView("players")
            }
            LobbyRow(L("Síň slávy", "Hall of fame", "Ruhmeshalle"), L("Žebříček a poháry", "Ranking & cups", "Rangliste & Pokale")) { Ui.open("fame") }
            if (com.onoseto.chipsnfish.Updater.state == "available")
                GlassPill(L("Nová verze · aktualizovat", "New version · update", "Neue Version · aktualisieren"), hot = true) {
                    Ui.open("settings", SettingsArg(tab = "update", level = 0, startStack = 0, autoBlinds = true, dealer = 0))
                }
        }
    }
}

/** Phone tilt (-1..1 on both screen axes), smoothed – drives the chip's glare. */
@Composable
private fun rememberTilt(): androidx.compose.runtime.State<androidx.compose.ui.geometry.Offset> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tilt = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val sm = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        @Suppress("DEPRECATION")
        val rot = (ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
        var fx = 0f; var fy = 0f
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val gx = e.values[0]; val gy = e.values[1]
                val (sx, sy) = when (rot) {
                    android.view.Surface.ROTATION_90 -> gy to gx
                    android.view.Surface.ROTATION_180 -> gx to -gy
                    android.view.Surface.ROTATION_270 -> -gy to -gx
                    else -> -gx to gy
                }
                fx += ((sx / 6f).coerceIn(-1f, 1f) - fx) * 0.12f
                fy += ((sy / 6f).coerceIn(-1f, 1f) - fy) * 0.12f
                tilt.value = androidx.compose.ui.geometry.Offset(fx, fy)
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
    return tilt
}

/** The main button: a thick glowing blue glass poker chip with the fish. The glare follows the phone's tilt;
 *  now and then it flips; a tap sends it spinning. */
@Composable
private fun PlayOrb(title: String, sub: String, onClick: () -> Unit) {
    watch()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val tilt by rememberTilt()
    val inf = rememberInfiniteTransition(label = "chip")
    val glowT = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "glow")
    val flip = remember { androidx.compose.animation.core.Animatable(0f) }
    val shine = remember { androidx.compose.animation.core.Animatable(-1f) }
    val burst = remember { androidx.compose.animation.core.Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5200L + (Math.random() * 3000).toLong())
            if (busy) continue
            shine.snapTo(-1f)
            launch { shine.animateTo(1.4f, tween(1100, easing = LinearEasing)) }
            if (Math.random() < 0.55) { flip.snapTo(0f); flip.animateTo(360f, tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
        }
    }
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    val b = burst.value
    val dens = LocalDensity.current.density
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(158.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.34f
                val g = 0.35f + 0.25f * kotlin.math.sin(glowT.value * 6.283f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF4FA8FF).copy(alpha = g), Color.Transparent), radius = size.minDimension * 0.5f), size.minDimension * 0.5f)
                if (b > 0f) for (k in 0..2) {
                    val ph = ((b - k * 0.15f) / 0.7f).coerceIn(0f, 1f)
                    if (ph > 0f) drawOval(LobbyBlue.copy(alpha = 0.8f * (1f - ph)),
                        topLeft = center - androidx.compose.ui.geometry.Offset(r * (1f + 0.9f * ph), r * 0.8f * (1f + 0.9f * ph)),
                        size = androidx.compose.ui.geometry.Size(2 * r * (1f + 0.9f * ph), 1.6f * r * (1f + 0.9f * ph)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke((4f - 3f * ph) * density))
                }
            }
            val scale = press * (1f + 0.22f * kotlin.math.sin(b * 3.1416f))
            androidx.compose.foundation.Canvas(
                Modifier.size(122.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, rotationY = flip.value + tilt.x * 10f, rotationX = -tilt.y * 8f,
                        rotationZ = b * 540f, cameraDistance = 14f * dens)
                    .clickable(src, null) {
                        if (busy) return@clickable
                        busy = true
                        scope.launch {
                            burst.snapTo(0f)
                            burst.animateTo(1f, tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            onClick()
                            burst.snapTo(0f); busy = false
                        }
                    },
            ) {
                val R = size.width / 2 * 0.96f
                val k = (0.80f + 0.06f * tilt.y).coerceIn(0.7f, 0.9f)      // how much we look at it from above
                val T = R * 0.20f                                            // thickness
                val cx = size.width / 2
                val cy = size.height / 2 - T / 2
                val top = androidx.compose.ui.geometry.Rect(cx - R, cy - R * k, cx + R, cy + R * k)
                // shadow on the table
                drawOval(Brush.radialGradient(listOf(Color(0x99000000), Color.Transparent), androidx.compose.ui.geometry.Offset(cx, cy + T + R * k * 0.35f), R * 1.1f),
                    topLeft = androidx.compose.ui.geometry.Offset(cx - R * 1.1f, cy + T - R * k * 0.4f), size = androidx.compose.ui.geometry.Size(R * 2.2f, R * k * 1.6f))
                // the side (edge) of the chip
                val side = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - R, cy); lineTo(cx - R, cy + T)
                    arcTo(androidx.compose.ui.geometry.Rect(cx - R, cy + T - R * k, cx + R, cy + T + R * k), 180f, -180f, false)
                    lineTo(cx + R, cy)
                    arcTo(top, 0f, 180f, false); close()
                }
                drawPath(side, Brush.horizontalGradient(listOf(Color(0xFF0B2470), Color(0xFF2F7BFF), Color(0xFF6CC4FF), Color(0xFF1C47C9), Color(0xFF081A55)), cx - R, cx + R))
                // white edge inserts on the visible side
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45.0 + 22.5 + tilt.x * 20)).toFloat()
                    val sn = kotlin.math.sin(a)
                    if (sn <= 0.15f) continue
                    val x = cx + R * kotlin.math.cos(a)
                    val y = cy + R * k * sn
                    val w = R * 0.16f * sn
                    drawRect(Color.White.copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(x - w / 2, y + T * 0.12f), androidx.compose.ui.geometry.Size(w, T * 0.76f))
                }
                // top face (squashed circle)
                withTransform({ scale(1f, k, androidx.compose.ui.geometry.Offset(cx, cy)) }) {
                    val c = androidx.compose.ui.geometry.Offset(cx, cy)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF9AD8FF), Color(0xFF2F7BFF), Color(0xFF0F2C8C)),
                        c + androidx.compose.ui.geometry.Offset(-R * 0.35f - tilt.x * R * 0.3f, -R * 0.4f - tilt.y * R * 0.3f), R * 1.6f), R, c)
                    for (i in 0 until 8) rotate(i * 45f, c) {
                        drawRoundRect(Color.White.copy(alpha = 0.92f), androidx.compose.ui.geometry.Offset(cx - R * 0.1f, cy - R * 0.99f),
                            androidx.compose.ui.geometry.Size(R * 0.2f, R * 0.2f), androidx.compose.ui.geometry.CornerRadius(R * 0.04f))
                    }
                    drawCircle(Brush.radialGradient(listOf(Color(0xCC1A3E8C), Color(0xEE0A1A48)), c, R * 0.72f), R * 0.72f, c)
                    drawCircle(Color.White.copy(alpha = 0.75f), R * 0.66f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(R * 0.03f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(R * 0.09f, R * 0.07f))))
                    val s = R * 1.25f / 40f
                    translate(cx - 20f * s - R * 0.03f, cy - 20f * s) {
                        scale(s, s, androidx.compose.ui.geometry.Offset.Zero) {
                            drawPath(fishPath, Brush.verticalGradient(listOf(Color(0xFFDDF3FF), Color(0xFF7FC4FF)), 14f, 26f))
                            drawCircle(Color(0xFF0A1A48), 1.3f, androidx.compose.ui.geometry.Offset(14.6f, 19.2f))
                        }
                    }
                    // glare that follows the tilt of the phone
                    val gx = cx - tilt.x * R * 0.55f
                    val gy = cy - R * 0.35f - tilt.y * R * 0.45f
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f), Color.Transparent), androidx.compose.ui.geometry.Offset(gx, gy), R * 0.75f),
                        R, c)
                    // occasional light sweep
                    val sx = shine.value * R * 2f
                    clipPath(androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, R)) }) {
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                            start = androidx.compose.ui.geometry.Offset(cx + sx - R * 0.5f, cy - R), end = androidx.compose.ui.geometry.Offset(cx + sx + R * 0.1f, cy + R)))
                    }
                    drawCircle(Color.White.copy(alpha = 0.6f), R - 1.5f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                }
            }
        }
        T(title, 22.sp, Color.White, num = true, letter = 3.sp, maxLines = 1, lineHeight = 24.sp)
        T(sub, 11.5.sp, LobbyBlue, FontWeight.ExtraBold, letter = 1.5.sp, maxLines = 1)
    }
}

/** Rounded glass pill (secondary action). */
@Composable
private fun GlassPill(text: String, hot: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).glass(RoundedCornerShape(99.dp), if (hot) Color(0x552F7BFF) else Color(0x2A2A5CA8), lift = 6.dp)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 13.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true) }
}

/** Glass chart of my results: chips won or lost over the last hands. */
@Composable
private fun ResultsChart() {
    watch()
    val hs = Game.H.take(40).reversed()
    val pts = run { var acc = 0; listOf(0) + hs.map { acc += it.net; acc } }
    val total = pts.last()
    val games = Game.G.size
    val won = Game.G.count { it.place == 1 }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(L("POSLEDNÍ HANDY", "LAST HANDS", "LETZTE HÄNDE"), 8.5.sp, Color.White.copy(alpha = 0.55f), FontWeight.ExtraBold, letter = 1.sp, modifier = Modifier.weight(1f))
            if (hs.isNotEmpty()) T((if (total > 0) "+" else "") + Game.fm(total), 13.sp, if (total >= 0) Color(0xFF6CF0A8) else Color(0xFFFF8A80), num = true)
        }
        if (hs.size < 2) {
            Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                T(L("Zahraj si a uvidíš tu svůj graf", "Play a few hands to see your chart", "Spiel ein paar Hände für dein Diagramm"), 11.sp, Color.White.copy(alpha = 0.5f), align = TextAlign.Center)
            }
        } else androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(46.dp)) {
            val mn = minOf(0, pts.min()).toFloat(); val mx = maxOf(0, pts.max()).toFloat()
            val span = (mx - mn).coerceAtLeast(1f)
            fun px(i: Int) = i * size.width / (pts.size - 1)
            fun py(v: Int) = size.height - (v - mn) / span * size.height
            val line = androidx.compose.ui.graphics.Path()
            pts.forEachIndexed { i, v -> if (i == 0) line.moveTo(px(i), py(v)) else line.lineTo(px(i), py(v)) }
            val fill = androidx.compose.ui.graphics.Path().apply { addPath(line); lineTo(size.width, py(0)); lineTo(0f, py(0)); close() }
            val up = total >= 0
            val col = if (up) Color(0xFF6CC4FF) else Color(0xFFFF8A80)
            drawPath(fill, Brush.verticalGradient(listOf(col.copy(alpha = 0.45f), col.copy(alpha = 0.02f))))
            drawLine(Color.White.copy(alpha = 0.25f), androidx.compose.ui.geometry.Offset(0f, py(0)), androidx.compose.ui.geometry.Offset(size.width, py(0)), 1f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            drawPath(line, col, style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            val last = androidx.compose.ui.geometry.Offset(px(pts.size - 1), py(total))
            drawCircle(col.copy(alpha = 0.35f), 6f * density, last); drawCircle(Color.White, 2.5f * density, last)
        }
        if (games > 0) T(L("Hry s přáteli: $games · vyhráno $won", "Games with friends: $games · won $won", "Spiele mit Freunden: $games · gewonnen $won"), 10.sp, Color.White.copy(alpha = 0.6f), FontWeight.Bold, maxLines = 1, fit = true)
    }
}

/** My avatar, name and what I have. */
@Composable
private fun ProfileBlock() {
    watch()
    val me = Game.P.getValue("hero")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(me, 52.dp, ring = LobbyBlue, border = 2.dp)
        Column(Modifier.weight(1f)) {
            T(L("HRÁČ", "PLAYER", "SPIELER"), 9.5.sp, LobbyBlue, FontWeight.ExtraBold, letter = 2.sp)
            NameField()
        }
    }
    val F = com.onoseto.chipsnfish.online.Fame
    val S = com.onoseto.chipsnfish.online.Social
    ResultsChart()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatChip(L("žetony", "chips", "Chips"), S.bank?.let { Game.fm(it) } ?: "–", Modifier.weight(1f))
        StatChip(L("čest", "honor", "Ehre"), Game.fm(F.honor ?: 0), Modifier.weight(1f))
        StatChip(L("poháry", "cups", "Pokale"), "${F.cups ?: 0}", Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.07f)).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(value, 15.sp, Color.White, num = true, maxLines = 1, fit = true)
        T(label.uppercase(), 8.5.sp, Color.White.copy(alpha = 0.55f), FontWeight.ExtraBold, letter = 1.sp)
    }
}

/** Editable player name (saved and sent to the server). */
@Composable
private fun NameField() {
    watch()
    val me = Game.P.getValue("hero")
    var name by remember { mutableStateOf(if (me.name == "Ty") "" else me.name) }
    LaunchedEffect(name) {
        delay(900)
        if (com.onoseto.chipsnfish.online.SupaCfg.configured && name.isNotBlank()) runCatching { com.onoseto.chipsnfish.online.Online.syncProfile() }
    }
    Box(Modifier.fillMaxWidth()) {
        if (name.isEmpty()) T(L("Zadej jméno ✎", "Your name ✎", "Dein Name ✎"), 17.sp, LobbyBlue, FontWeight.ExtraBold)
        androidx.compose.foundation.text.BasicTextField(
            name, { v -> name = v.take(24); me.name = v.trim().ifEmpty { "Ty" }; Game.saveLocal(); Game.bump() },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(LobbyBlue), modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One slim glass row on the right side of the lobby. */
@Composable
private fun LobbyRow(title: String, sub: String, hot: Boolean = false, indent: androidx.compose.ui.unit.Dp = 0.dp, extra: String? = null, onExtra: () -> Unit = {}, onClick: () -> Unit) {
    watch()
    val sh = RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.96f else 1f, label = "row")
    Row(
        Modifier.fillMaxWidth().height(54.dp).graphicsLayer(scaleX = sc, scaleY = sc)
            .glass(sh, if (hot) Color(0x3D2F7BFF) else Color(0x2A2A5CA8), lift = 10.dp)
            .then(if (hot) Modifier.border(1.5.dp, Brush.linearGradient(listOf(Color(0xFF9AD6FF), Color(0xFF2F7BFF))), sh) else Modifier)
            .clickable(src, null, onClick = onClick).padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(if (hot) LobbyBlue else Color.White.copy(alpha = 0.25f)))
        Column(Modifier.weight(1f)) {
            T(title, 16.5.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true, lineHeight = 19.sp)
            T(sub, 11.5.sp, if (hot) LobbyBlue else Color.White.copy(alpha = 0.6f), FontWeight.SemiBold, maxLines = 1, fit = true, lineHeight = 14.sp)
        }
        if (extra != null) Box(
            Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(99.dp))
                .tap(onExtra).padding(horizontal = 10.dp, vertical = 5.dp),
        ) { T(extra, 11.sp, Color.White, FontWeight.Bold) }
        T("›", 22.sp, Color.White.copy(alpha = 0.55f), FontWeight.Light)
    }
}

/** Secondary button in glass style. */
@Composable
private fun GlassBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(11.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 14.5.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true) }
}

@Composable
private fun LobbyIconBtn(icon: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).glass(androidx.compose.foundation.shape.CircleShape, lift = 6.dp).tap(onClick), contentAlignment = Alignment.Center) { Icon24(icon, Color.White) }
}

/** Who of my friends is online right now (top of the lobby). */
@Composable
private fun OnlinePill() {
    watch()
    val fs = com.onoseto.chipsnfish.online.Online.friends ?: return
    val on = fs.filter { it.online }
    Row(
        Modifier.height(38.dp).glass(RoundedCornerShape(99.dp), lift = 6.dp).tap { Ui.open("groups") }.padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on.isEmpty()) Color(0xFF6B7A90) else Color(0xFF3CE08A)))
        if (on.isEmpty()) T(L("Nikdo z přátel není online", "No friends online", "Keine Freunde online"), 12.5.sp, Color.White.copy(alpha = 0.75f), FontWeight.Bold, maxLines = 1)
        else {
            Box {
                on.take(3).forEachIndexed { i, f -> Box(Modifier.padding(start = (i * 16).dp)) { Avatar(com.onoseto.chipsnfish.game.Player(f.uid, f.name, img = f.img), 24.dp, ring = Color(0xFF3CE08A), border = 1.5.dp) } }
            }
            T(if (on.size == 1) L("${on[0].name} je online", "${on[0].name} is online", "${on[0].name} ist online") else L("${on.size} přátelé online", "${on.size} friends online", "${on.size} Freunde online"),
                12.5.sp, Color.White, FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Wallet chips and honor points as one glass pill. */
@Composable
private fun LobbyWallet() {
    watch()
    val F = com.onoseto.chipsnfish.online.Fame
    val S = com.onoseto.chipsnfish.online.Social
    if (S.bank == null && F.honor == null) return
    Row(
        Modifier.height(38.dp).glass(RoundedCornerShape(99.dp), lift = 6.dp).tap { Ui.open("fame") }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChipDisc(C.gold, 16.dp)
        T(S.bank?.let { Game.fm(it) } ?: "–", 14.sp, Color.White, num = true)
        Box(Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.2f)))
        T("★", 14.sp, LobbyBlue, FontWeight.Bold)
        T(Game.fm(F.honor ?: 0), 14.sp, Color.White, num = true)
    }
}

/** Online friends as little avatars; tap = friends & invites. */
@Composable
private fun FriendsOnlineRow() {
    watch()
    val on = com.onoseto.chipsnfish.online.Online.friends?.filter { it.online }.orEmpty()
    if (on.isEmpty()) return
    Row(Modifier.tap { Ui.open("groups") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        T(L("Online", "Online", "Online"), 11.sp, Color.White.copy(alpha = 0.6f), FontWeight.Bold)
        on.take(7).forEach { f ->
            Avatar(com.onoseto.chipsnfish.game.Player(f.uid, f.name, img = f.img, hat = Game.P[f.uid]?.hat ?: ""), 26.dp, ring = Color(0xFF3CE08A), border = 2.dp)
        }
        if (on.size > 7) T("+${on.size - 7}", 12.sp, Color.White.copy(alpha = 0.6f), num = true)
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
        Modifier.glass(RoundedCornerShape(99.dp), lift = 6.dp)
            .then(if (empty) Modifier.border(1.5.dp, LobbyBlue, RoundedCornerShape(99.dp)) else Modifier).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(me, 30.dp, border = 2.dp)
        T(if (empty) L("Jak ti máme říkat?", "What's your name?", "Wie heißt du?") else L("Hraješ jako", "Playing as", "Du spielst als"), 13.sp, if (empty) LobbyBlue else Color.White.copy(alpha = 0.6f), FontWeight.Bold)
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
