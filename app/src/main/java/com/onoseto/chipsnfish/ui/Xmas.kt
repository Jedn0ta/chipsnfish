@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.online.Online
import com.onoseto.chipsnfish.online.Social
import com.onoseto.chipsnfish.online.Supa
import com.onoseto.chipsnfish.online.SupaCfg
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneId

/** One advent window: the same item for everybody. pos = where it sits on the avatar. */
data class AdItem(val day: Int, val id: String, val icon: String, val cs: String, val en: String, val de: String, val pos: String) {
    fun name() = L(cs, en, de)
}

object Xmas {
    /** Must match cf_advent_item() in schema.sql. */
    val ADVENT = listOf(
        AdItem(1, "santa", "", "Santova čepice", "Santa hat", "Weihnachtsmütze", "top"),
        AdItem(2, "mustache", "🥸", "Falešný knír", "Fake moustache", "Falscher Schnurrbart", "face"),
        AdItem(3, "fish", "🐟", "Živá ryba", "Live fish", "Lebender Fisch", "side"),
        AdItem(4, "sock", "🧦", "Smradlavá ponožka", "Smelly sock", "Stinkesocke", "side"),
        AdItem(5, "unicorn", "🦄", "Jednorožec", "Unicorn", "Einhorn", "top"),
        AdItem(6, "chili", "🌶️", "Pálivá paprička", "Hot chili", "Scharfe Chili", "side"),
        AdItem(7, "snorkel", "🤿", "Šnorchl", "Snorkel", "Schnorchel", "face"),
        AdItem(8, "chicken", "🐔", "Slepice na hlavě", "Chicken on the head", "Huhn auf dem Kopf", "top"),
        AdItem(9, "banana", "🍌", "Banán", "Banana", "Banane", "side"),
        AdItem(10, "snowman", "⛄", "Sněhulák", "Snowman", "Schneemann", "side"),
        AdItem(11, "cheese", "🧀", "Smradlavý sýr", "Stinky cheese", "Stinkekäse", "side"),
        AdItem(12, "duck", "🦆", "Kachna na hlavě", "Duck on the head", "Ente auf dem Kopf", "top"),
        AdItem(13, "octopus", "🐙", "Chobotnice", "Octopus", "Krake", "top"),
        AdItem(14, "tree", "🎄", "Stromeček", "Christmas tree", "Tannenbaum", "side"),
        AdItem(15, "clown", "🤡", "Klaun", "Clown", "Clown", "face"),
        AdItem(16, "dino", "🦖", "Dinosaurus", "Dinosaur", "Dinosaurier", "side"),
        AdItem(17, "mushroom", "🍄", "Muchomůrka", "Toadstool", "Fliegenpilz", "top"),
        AdItem(18, "tp", "🧻", "Toaleťák", "Toilet roll", "Klopapier", "side"),
        AdItem(19, "milk", "🥛", "Mléko pro Santu", "Milk for Santa", "Milch für Santa", "side"),
        AdItem(20, "deer", "🦌", "Sob", "Reindeer", "Rentier", "side"),
        AdItem(21, "cactus", "🌵", "Kaktus na hlavě", "Cactus on the head", "Kaktus auf dem Kopf", "top"),
        AdItem(22, "pizza", "🍕", "Kousek pizzy", "Slice of pizza", "Stück Pizza", "side"),
        AdItem(23, "penguin", "🐧", "Tučňák", "Penguin", "Pinguin", "side"),
        AdItem(24, "santagold", "", "Zlatá Santova čepice", "Golden Santa hat", "Goldene Weihnachtsmütze", "top"),
    )
    fun item(id: String) = ADVENT.firstOrNull { it.id == id }

    /** Windows are laid out in a mixed order, like a real calendar. */
    val ORDER = listOf(7, 19, 3, 22, 12, 1, 16, 9, 24, 5, 14, 20, 2, 11, 17, 8, 23, 4, 13, 21, 6, 18, 10, 15)

    fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Prague"))
    /** Testing before Advent: the calendar shows already and every window can be opened (until 1 December). */
    fun testing() = today().let { it.monthValue == 11 && it.year == 2026 }
    /** December = Christmas look (Santa hat on the fish chip). */
    fun season() = today().monthValue == 12
    /** Highest window that may be opened today (0 outside 1–24 December). */
    fun openUpTo(): Int = if (testing()) 24 else today().let { if (it.monthValue == 12) minOf(it.dayOfMonth, 24) else 0 }
    fun calendarOn() = openUpTo() > 0

    /** Opened windows (also mirrored from the server when signed in). */
    var opened by mutableStateOf<Set<Int>>(emptySet())

    fun load() {
        opened = (Game.getS("hr-advent-${today().year}") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()
        if (!SupaCfg.configured) return
        Game.scope.launch {
            runCatching {
                val r = Supa.rpc("cf_advent_days") as? JsonArray ?: return@runCatching
                opened = opened + r.mapNotNull { it.jsonPrimitive.intOrNull }
                save()
            }
        }
    }

    private fun save() = Game.setS("hr-advent-${today().year}", opened.sorted().joinToString(","))

    fun open(day: Int, done: (AdItem) -> Unit) {
        val it = ADVENT[day - 1]
        if (day > openUpTo()) return
        fun ok() { opened = opened + day; save(); Social.myHats = (Social.myHats + it.id).distinct(); done(it) }
        if (!SupaCfg.configured) { ok(); return }
        Game.scope.launch {
            try { Supa.rpc("cf_advent_open", buildJsonObject { put("p_day", day) }); ok() }
            catch (e: Exception) { ok(); Game.toast(Online.err(e)) }
        }
    }

    /** Put the item on (online: server profile; offline: just me on this phone). */
    fun wear(id: String) {
        if (SupaCfg.configured) Social.wearHat(id)
        else { Game.P["hero"]?.hat = id; Game.saveLocal(); Game.bump() }
    }
}

/** Santa hat drawn in code (red, or gold for the 24th). */
@Composable
fun SantaHat(modifier: Modifier, gold: Boolean = false) {
    Canvas(modifier) {
        val w = size.width
        fun o(x: Float, y: Float) = Offset(x * w, y * w)
        val cone = Path().apply {
            moveTo(0.12f * w, 0.76f * w)
            cubicTo(0.18f * w, 0.30f * w, 0.52f * w, 0.02f * w, 0.88f * w, 0.20f * w)
            cubicTo(0.70f * w, 0.28f * w, 0.74f * w, 0.52f * w, 0.88f * w, 0.76f * w)
            close()
        }
        val main = if (gold) listOf(Color(0xFFFFE082), Color(0xFFE0A800), Color(0xFF9C6F00)) else listOf(Color(0xFFFF5A5A), Color(0xFFD32F2F), Color(0xFF8E1414))
        drawPath(cone, Brush.linearGradient(main, o(0.2f, 0.2f), o(0.8f, 0.8f)))
        drawPath(cone, Color.Black.copy(alpha = 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.015f))
        // fur band
        drawRoundRect(Color(0x33000000), o(0.04f, 0.70f), Size(0.92f * w, 0.22f * w), CornerRadius(0.11f * w))
        drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFE8E8E8)), 0.66f * w, 0.88f * w), o(0.04f, 0.66f), Size(0.92f * w, 0.22f * w), CornerRadius(0.11f * w))
        // pom-pom
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFDADADA)), o(0.86f, 0.17f), 0.13f * w), 0.11f * w, o(0.88f, 0.2f))
        if (gold) for (p in listOf(0.3f to 0.45f, 0.5f to 0.3f, 0.62f to 0.55f)) drawCircle(Color.White.copy(alpha = 0.8f), 0.025f * w, o(p.first, p.second))
    }
}

/** Advent calendar: 24 windows, today's (and missed earlier ones) can be opened; everyone gets the same item. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdventSheet() {
    watch()
    LaunchedEffect(Unit) { Xmas.load(); Social.loadHats() }
    val upTo = Xmas.openUpTo()
    var preview by remember { mutableStateOf(false) }
    var got by remember { mutableStateOf<AdItem?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SantaHat(Modifier.size(30.dp))
        T(L("Adventní kalendář", "Advent calendar", "Adventskalender"), 22.sp, Color.White, FontWeight.ExtraBold,
            modifier = Modifier.combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = { preview = !preview }, onClick = {}))
        T(if (Xmas.testing()) L("TEST: do 1. prosince jdou otevřít všechna okénka", "TEST: all windows open until 1 December", "TEST: bis 1. Dezember alle Fenster offen")
          else if (upTo == 0) L("otevře se 1. prosince", "opens on 1 December", "öffnet am 1. Dezember") else L("každý den jedno okénko, pro všechny to samé", "one window a day, the same for everyone", "jeden Tag ein Fenster, für alle gleich"),
            12.sp, GlassMuted, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
    }
    if (Xmas.testing() && Xmas.opened.size < 24) Btn(L("Otevřít všechna okénka (test)", "Open all windows (test)", "Alle Fenster öffnen (Test)"), BK.GHOST, height = 34.dp) {
        (1..24).filter { it !in Xmas.opened }.forEach { d -> Xmas.open(d) { got = it } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(Modifier.width(460.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 8) {
            Xmas.ORDER.forEach { d -> AdWindow(d, d <= upTo, d in Xmas.opened, preview) { Xmas.open(d) { got = it } } }
        }
        // what came out of the last window
        Column(Modifier.weight(1f).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val it = got
            if (it == null) {
                T(L("Klepni na dnešní okénko", "Tap today's window", "Tippe auf das heutige Fenster"), 13.sp, GlassMuted, FontWeight.Bold, align = TextAlign.Center)
            } else {
                val pop = remember(it.id) { Animatable(0.3f) }
                LaunchedEffect(it.id) { pop.animateTo(1f, tween(450)) }
                Box(Modifier.size(80.dp).graphicsLayer(scaleX = pop.value, scaleY = pop.value), contentAlignment = Alignment.Center) { AdIcon(it, 70.dp) }
                T(it.name(), 15.sp, Color.White, FontWeight.ExtraBold, align = TextAlign.Center)
                val wearing = Game.P["hero"]?.hat == it.id
                Btn(if (wearing) L("Máš na sobě ✓", "Wearing ✓", "Getragen ✓") else L("Nasadit", "Put on", "Anziehen"), if (wearing) BK.GHOST else BK.GOLD, Modifier.fillMaxWidth(), height = 38.dp) { Xmas.wear(it.id) }
            }
        }
    }
}

@Composable
private fun AdWindow(day: Int, open: Boolean, done: Boolean, preview: Boolean, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val item = Xmas.ADVENT[day - 1]
    val scope = rememberCoroutineScope()
    val flip = remember { Animatable(0f) }
    Box(
        Modifier.size(52.dp).clip(shape)
            .background(
                if (done || preview) Brush.radialGradient(listOf(Color(0xFF2C4A7A), Color(0xFF14233D)))
                else if (open) Brush.verticalGradient(listOf(Color(0xFFC62828), Color(0xFF7F1010)))
                else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))),
            )
            .border(if (open && !done) 2.dp else 1.dp, if (open && !done) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.18f), shape)
            .graphicsLayer(rotationY = flip.value)
            .tap {
                if (open && !done) scope.launch { flip.animateTo(90f, tween(220)); onOpen(); flip.snapTo(-90f); flip.animateTo(0f, tween(220)) }
                else if (!open) Game.toast(L("Okénko $day se otevře $day. prosince", "Window $day opens on $day December", "Fenster $day öffnet am $day. Dezember"))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done || preview) AdIcon(item, 30.dp)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T("$day", 19.sp, if (open) Color.White else Color.White.copy(alpha = 0.45f), num = true, lineHeight = 20.sp)
            if (!open) T("🔒", 9.sp, lineHeight = 10.sp)
        }
    }
}

@Composable
fun AdIcon(it: AdItem, size: Dp) {
    if (it.icon.isEmpty()) SantaHat(Modifier.size(size), gold = it.id == "santagold")
    else T(it.icon, (size.value * 0.72f).sp, lineHeight = (size.value * 0.85f).sp)
}

/** The fish chip logo; in December it wears a Santa hat. */
@Composable
fun FishHat(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        FishLogo(Modifier.matchParentSize())
        if (Xmas.season()) SantaHat(Modifier.align(Alignment.TopEnd).offset(x = size * 0.2f, y = -size * 0.34f).size(size * 0.66f).graphicsLayer(rotationZ = 16f))
    }
}
