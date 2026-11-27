package com.onoseto.chipsnfish.game

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onoseto.chipsnfish.I18n
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The whole game state and logic, ported from the original Chips&Fish web app.
 * Storage keys and JSON shapes match the web app, so a backup made there restores here.
 */
object Game {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences
    val scope: CoroutineScope = MainScope()

    // ---- observable for Compose ----
    var rev by mutableIntStateOf(0)
    var toastMsg by mutableStateOf<String?>(null)
    var toastId by mutableIntStateOf(0)
    var eq by mutableStateOf<Reads.SimRes?>(null)
    var nextCache: Reads.NextStats? = null

    // ---- data ----
    var mode = "coach"
    val P: MutableMap<String, Player> = LinkedHashMap()
    var H: MutableList<HandRec> = mutableListOf()
    /** finished online games with friends */
    var G: MutableList<GameRec> = mutableListOf()
    lateinit var st: GState
    private val undoStack = ArrayDeque<String>()
    var look = Look()
    var zoom = 1.0
    var layouts: MutableMap<String, List<Double>> = mutableMapOf()
    var gameCfg = GameCfg()
    var fish: MutableList<String>? = null

    val CHIPDEF = listOf(
        ChipDef("black", "Černý žeton", 0xFF1C1C1C, 1),
        ChipDef("blue", "Modrý žeton", 0xFF1565C0, 2),
        ChipDef("red", "Červený žeton", 0xFFC62828, 5),
        ChipDef("green", "Zelený žeton", 0xFF2E7D32, 25),
        ChipDef("bill", "Papírová bankovka", 0xFFD9C995, 100),
    )
    var chips: List<ChipDef> = CHIPDEF.map { it.copy() }

    @kotlinx.serialization.Serializable
    data class ChipSave(val k: String, val v: Int, val off: Boolean = false)

    // ---- ui flags the engine needs ----
    var raiseOpen = false
    var raiseTo = 0
    var screenActive = true // false when menu / sheet / splash blocks the table (pauses bots)

    /** Game-like table (vs computer or online): real dealt cards, no manual entry. */
    fun gm() = mode == "game" || mode == "online"
    /** Pre-selected "check / fold" for this hand number (-1 = off): fires automatically when it's the hero's turn. */
    var preCF = -1
    /** Pre-selected "call any" for this hand number (-1 = off). */
    var preCall = -1
    fun bot() = mode == "game"
    fun online() = mode == "online"
    fun advisorOn() = if (online()) com.onoseto.chipsnfish.online.Online.advisor else true
    fun playing() = st.phase == "play"

    /** Ask seat k to show one hole card; a friend online decides, a bot decides by itself. */
    fun askCard(k: Int) {
        if (k <= 0 || !gm()) return
        val s = st.seats.getOrNull(k) ?: return
        if (online()) { com.onoseto.chipsnfish.online.Online.askCard(s.pid); return }
        val cs = st.hole[k] ?: return
        val hand = st.handNo
        val name = pl(s).name
        scope.launch {
            kotlinx.coroutines.delay(900L + (0..1200).random())
            if (st.handNo != hand) return@launch
            if (Math.random() < 0.35) toast(name + L(" kartu ukázat nechce", " won't show a card", " will keine Karte zeigen"))
            else {
                st.one = (st.one ?: mutableMapOf()).also { it[k] = cs[(0..1).random()] }
                toast(name + L(" ukázal kartu", " showed a card", " zeigt eine Karte")); bump()
            }
        }
    }

    /** Cards of seat k that I may see because I folded (or busted) and that player allows it; bots always do. */
    fun peekCards(k: Int): List<Int>? {
        if (k == 0 || !gm() || !playing() || st.done != null) return null
        val me = st.seats.getOrNull(0) ?: return null
        if (!me.folded && !me.out) return null
        val s = st.seats.getOrNull(k) ?: return null
        if (s.folded || s.out) return null
        return if (online()) st.peek?.get(k) else st.hole[k]
    }

    // ================= persistence =================
    fun getS(k: String): String? = prefs.getString(k, null)
    fun setS(k: String, v: String?) {
        prefs.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply()
    }

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.getSharedPreferences("chipsfish", Context.MODE_PRIVATE)
        I18n.lang = getS("hr-lang")?.trim('"') ?: "cs"
        loadAll()
    }

    private fun loadAll() {
        P.clear()
        runCatching { json.decodeFromString<Map<String, Player>>(getS("hr3-players") ?: "{}") }.getOrNull()?.let { P.putAll(it) }
        H = runCatching { json.decodeFromString<MutableList<HandRec>>(getS("hr3-hands") ?: "[]") }.getOrNull() ?: mutableListOf()
        G = runCatching { json.decodeFromString<MutableList<GameRec>>(getS("hr7-games") ?: "[]") }.getOrNull() ?: mutableListOf()
        look = runCatching { json.decodeFromString<Look>(getS("hr-look") ?: "{}") }.getOrNull() ?: Look()
        zoom = getS("hr4-zoom")?.toDoubleOrNull() ?: 1.0
        layouts = runCatching { json.decodeFromString<MutableMap<String, List<Double>>>(getS("hr4-layouts") ?: "{}") }.getOrNull() ?: mutableMapOf()
        gameCfg = runCatching { json.decodeFromString<GameCfg>(getS("hr6-cfg") ?: "{}") }.getOrNull() ?: GameCfg()
        fish = runCatching { json.decodeFromString<MutableList<String>>(getS("hr-fish") ?: "null") }.getOrNull()
        // chip values are fixed now (no longer editable in settings)
        chips = CHIPDEF.map { it.copy() }
        if (P["hero"] == null) P["hero"] = Player("hero", "Ty")
        mode = if (getS("hr-mode")?.trim('"') == "game") "game" else "coach"
        var s = loadState(if (mode == "game") "hr6-game" else "hr4-state")
        if (mode == "game" && !stValid(s)) { mode = "coach"; s = loadState("hr4-state") }
        if (mode == "coach") {
            // the old live-advisor mode isn't offered any more: never come back to a half-played table there
            st = freshState(); st.seats = mutableListOf(seatObj("hero", st.startStack))
        } else if (s == null || s.seats.isEmpty() || s.seats.drop(1).any { P[it.pid] == null }) sampleState() else st = s
        syncSeats()
    }

    private fun loadState(k: String): GState? = runCatching { json.decodeFromString<GState>(getS(k) ?: return null) }.getOrNull()

    fun stValid(x: GState?): Boolean = x != null && x.seats.isNotEmpty() && x.seats.drop(1).all { P[it.pid] != null }

    fun saveLocal() {
        if (!online()) {
            setS(if (bot()) "hr6-game" else "hr4-state", json.encodeToString(st))
            setS("hr-mode", "\"$mode\"")
        }
        setS("hr3-players", json.encodeToString(P.toMap()))
        setS("hr3-hands", json.encodeToString(H.take(400)))
        setS("hr7-games", json.encodeToString(G.take(300)))
    }

    fun saveLook() = setS("hr-look", json.encodeToString(look))
    fun saveZoom() = setS("hr4-zoom", zoom.toString())
    fun saveChips() = setS("hr5-chips", json.encodeToString(chips.map { ChipSave(it.k, it.v, it.off) }))
    fun saveFish() = setS("hr-fish", fish?.let { json.encodeToString(it) })
    fun saveCfg() = setS("hr6-cfg", json.encodeToString(gameCfg))
    fun setLang(l: String) { I18n.lang = l; setS("hr-lang", l); bump() }

    fun backupData(): String {
        saveLocal()
        val keys = prefs.all.keys.filter { it.startsWith("hr") }
        val data = buildJsonObject { for (k in keys) prefs.getString(k, null)?.let { put(k, it) } }
        return json.encodeToString(buildJsonObject {
            put("app", "chipsfish"); put("v", 1); put("t", System.currentTimeMillis()); put("data", data)
        })
    }

    /** Returns the backup time if valid, or null. */
    fun parseBackup(txt: String): Pair<Long, JsonObject>? = runCatching {
        val o = Json.parseToJsonElement(txt).jsonObject
        if (o["app"]?.jsonPrimitive?.content != "chipsfish") return null
        (o["t"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) to o["data"]!!.jsonObject
    }.getOrNull()

    fun restoreData(data: JsonObject) {
        val ed = prefs.edit()
        prefs.all.keys.filter { it.startsWith("hr") }.forEach { ed.remove(it) }
        for ((k, v) in data) (v as? JsonPrimitive)?.let { ed.putString(k, it.content) }
        ed.commit()
        I18n.lang = getS("hr-lang")?.trim('"') ?: "cs"
        loadAll()
        undoStack.clear(); eq = null
        bump()
    }

    fun bump() { rev++ }

    fun toast(t: String) { toastMsg = t; toastId++ }

    // ================= players =================
    private val rnd = SecureRandom()
    fun uid(p: String) = p + java.lang.Long.toString(System.currentTimeMillis(), 36) + java.lang.Long.toString(rnd.nextLong() and 0xFFFFFF, 36)

    fun newPlayer(name: String): Player {
        val p = Player(uid("p"), name, t = System.currentTimeMillis())
        P[p.id] = p
        return p
    }

    fun storePlayer(p: Player) { p.stored = true; saveLocal() }
    fun persistP(p: Player) { p.t = System.currentTimeMillis(); if (p.stored) storePlayer(p) else saveLocal() }
    fun regCount() = P.values.count { it.id != "hero" && it.stored }
    fun pl(s: Seat): Player = P[s.pid] ?: Player(s.pid, "?").also { P[s.pid] = it }

    // ================= engine =================
    fun nxt(i: Int): Int {
        for (k in 1..st.n) { val j = (i + k) % st.n; if (!st.seats[j].out) return j }
        return (i + 1) % st.n
    }

    fun live() = st.seats.filter { !it.folded }
    fun opps() = st.seats.drop(1)
    fun liveOpps() = opps().filter { !it.folded }
    fun potTotal() = st.seats.sumOf { it.total }
    fun potMiddle() = st.seats.sumOf { it.total - it.bet }
    fun boardKnown() = st.board.filterNotNull()
    fun streetIdx(): Int { val n = boardKnown().size; return if (n >= 5) 3 else if (n == 4) 2 else if (n >= 3) 1 else 0 }

    fun positions(): Pair<Int, Int> {
        val d = st.dealer
        if (st.seats.count { !it.out } == 2) return d to nxt(d)
        val sb = nxt(d)
        return sb to nxt(sb)
    }

    fun put(s: Seat, amt0: Int): Int {
        val amt = amt0.coerceIn(0, s.stack)
        s.stack -= amt; s.bet += amt; s.total += amt
        if (s.stack == 0) s.allin = true
        return amt
    }

    fun needs(s: Seat) = !s.folded && !s.allin && (!s.acted || s.bet < st.curBet)

    fun nextToAct(from: Int): Int {
        var i = from
        repeat(st.n) { i = nxt(i); if (needs(st.seats[i])) return i }
        return -1
    }

    fun seatObj(pid: String, stack: Int) = Seat(pid, stack)

    fun cleanup() {
        val ids = P.keys.toList()
        for (id in ids) {
            val p = P[id] ?: continue
            if (id != "hero" && !p.stored && !p.bot && st.seats.none { it.pid == id }) P.remove(id)
        }
    }

    fun syncSeats() {
        while (st.seats.size < st.n) {
            val p = newPlayer(L("Hráč ", "Player ", "Spieler ") + st.seats.size)
            st.seats.add(seatObj(p.id, st.startStack))
        }
        while (st.seats.size > st.n) st.seats.removeAt(st.seats.size - 1)
        if (st.dealer >= st.n) st.dealer = 0
        cleanup()
    }

    fun clearHand() {
        st.done = null; st.reveal = null; st.hole = mutableMapOf(); st.one = null
        st.seats.forEach { it.folded = it.out; it.allin = false; it.bet = 0; it.total = 0; it.acted = false; it.actions = mutableListOf() }
        st.hero = mutableListOf(null, null)
        st.board = mutableListOf(null, null, null, null, null)
        st.shown = mutableMapOf()
        st.betStreet = 0; st.curBet = 0; st.turn = -1
        undoStack.clear()
        raiseOpen = false
    }

    fun startHand() {
        clearHand()
        if (!bot()) st.seats.forEach { if (it.stack <= 0) it.stack = st.startStack }
        if (bot() && st.blindHands > 0 && st.handNo > 0 && st.handNo % st.blindHands == 0 && st.level < Cards.LEVELS.size - 1) {
            st.level++
            val l = Cards.lvlStr(st.level)
            scope.launch { delay(300); toast(L("Blindy rostou na $l", "Blinds go up to $l", "Blinds steigen auf $l")) }
        }
        st.curLevel = st.level
        st.sb = Cards.lvl(st.curLevel)[0]; st.bb = Cards.lvl(st.curLevel)[1]
        st.lastRaise = st.bb
        st.handNo += 1
        val (sb, bb) = positions()
        if (st.autoBlinds) {
            val a = put(st.seats[sb], st.sb); st.seats[sb].actions.add(Act(0, "sb", a))
            val b = put(st.seats[bb], st.bb); st.seats[bb].actions.add(Act(0, "bb", b))
            st.curBet = max(st.seats[sb].bet, st.seats[bb].bet)
        }
        st.turn = nextToAct(bb)
        if (bot()) gameDeal()
    }

    fun startStreet() {
        st.seats.forEach { it.bet = 0; it.acted = false }
        st.curBet = 0; st.lastRaise = st.bb
        st.turn = if (live().size > 1 && live().count { !it.allin } > 1) nextToAct(st.dealer) else -1
        raiseOpen = false
    }

    fun snapshot() {
        undoStack.addLast(json.encodeToString(st))
        if (undoStack.size > 60) undoStack.removeFirst()
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        st = json.decodeFromString(s)
        raiseOpen = false
        update(); toast(tr("Vráceno"))
    }

    fun act(i: Int, type: String, to0: Int = 0) {
        val s = st.seats[i]
        val stt = st.betStreet
        val potBefore = potTotal()
        val callAmt = max(0, st.curBet - s.bet)
        val curBefore = st.curBet
        val rec = Act(stt, type)
        var to = to0
        if (type == "fold") s.folded = true
        else if (type == "call") {
            rec.amt = put(s, callAmt)
            if (callAmt == 0) rec.a = "check"
        } else if (type != "check") {
            if (type == "allin") to = s.bet + s.stack
            put(s, to - s.bet)
            if (s.bet > curBefore) {
                val inc = s.bet - curBefore
                val ratio = inc.toDouble() / max(1, potBefore + callAmt)
                rec.sz = if (ratio <= 0.4) "s" else if (ratio <= 0.75) "m" else if (ratio <= 1.1) "l" else "o"
                if (inc >= st.lastRaise) st.lastRaise = inc
                st.curBet = s.bet
                st.seats.forEachIndexed { j, o -> if (j != i) o.acted = false }
                if (type != "allin") rec.a = if (curBefore == 0) "bet" else "raise"
            } else if (type == "allin") rec.a = "call"
            rec.amt = s.bet
            if (s.allin && type != "allin" && rec.a != "call") rec.a = "allin"
        }
        s.acted = true
        s.actions.add(rec)
        st.turn = if (live().size <= 1) -1 else nextToAct(i)
        if (st.turn >= 0 && live().count { !it.allin } == 1 && st.seats[st.turn].bet >= st.curBet) st.turn = -1
    }

    fun minMax(s: Seat): Pair<Int, Int> {
        val mx = s.bet + s.stack
        var mn = if (st.curBet == 0) st.bb else st.curBet + st.lastRaise
        mn = min(mn, mx)
        return mn to mx
    }

    fun step() = max(1, st.sb)

    // ================= state init =================
    fun freshState() = GState()

    fun sampleState() {
        val names = listOf("Hráč 1", "Hráč 2", "Hráč 3", "Hráč 4", "Hráč 5")
        val types = listOf("rock", "lag", "unk", "tag", "station")
        val ps = names.mapIndexed { i, n -> newPlayer(n).also { it.override = types[i] } }
        st = freshState().apply {
            n = 6; phase = "play"; dealer = 3; startLevel = 4; level = 4; curLevel = 4; startStack = 1000; sample = true
        }
        st.seats = (listOf(seatObj("hero", 1000)) + ps.map { seatObj(it.id, 1000) }).toMutableList()
        startHand()
        st.hero = mutableListOf(49, 45)
        act(0, "raise", 30); act(1, "fold"); act(2, "call"); act(3, "fold"); act(4, "fold"); act(5, "call")
        st.board = mutableListOf(44, 21, 2, null, null)
        st.betStreet = 1
        startStreet()
        act(5, "check"); act(0, "bet", 40); act(2, "raise", 120)
        undoStack.clear()
    }

    // ================= layout positions along the rail =================
    fun defaultPos(n: Int) = MutableList(n) { it.toDouble() / n }
    fun ensurePos() {
        val p = st.pos
        if (p == null || p.size != st.n) {
            val l = layouts[st.n.toString()]
            st.pos = if (l != null && l.size == st.n) l.toMutableList() else defaultPos(st.n)
        }
    }
    fun saveLayout() {
        layouts[st.n.toString()] = st.pos!!.toList()
        setS("hr4-layouts", json.encodeToString(layouts.toMap()))
    }
    fun respace() {
        val n = st.n; val p = st.pos!!; val p0 = p[0]
        for (k in 1 until n) p[k] = (((p0 + k.toDouble() / n) % 1) + 1) % 1
    }

    // ================= card slots (live advisor mode) =================
    fun slotVal(i: Int): Int? {
        if (i >= 10) { val k = (i - 10) shr 1; val j = (i - 10) and 1; return st.shown[k]?.getOrNull(j) }
        return if (i < 2) st.hero[i] else st.board[i - 2]
    }
    fun setSlot(i: Int, v: Int?) {
        if (i >= 10) {
            val k = (i - 10) shr 1; val j = (i - 10) and 1
            val l = st.shown.getOrPut(k) { mutableListOf(null, null) }
            l[j] = v; return
        }
        if (i < 2) st.hero[i] = v else st.board[i - 2] = v
    }
    fun firstEmpty(): Int { for (i in 0 until 7) if (slotVal(i) == null) return i; return -1 }
    fun slotStreet(i: Int) = if (i < 5) 1 else if (i == 5) 2 else 3
    fun slotGroup(i: Int): List<Int> = when {
        i >= 10 -> { val b = i - ((i - 10) and 1); listOf(b, b + 1) }
        i < 2 -> listOf(0, 1)
        i < 5 -> listOf(2, 3, 4)
        else -> listOf(i)
    }
    fun allSlots(): List<Int> { val a = (0..6).toMutableList(); for (k in st.shown.keys) { a.add(10 + 2 * k); a.add(11 + 2 * k) }; return a }
    fun slotOpen(i: Int): Boolean {
        if (i < 2) return true
        if (!playing() || slotVal(i) != null) return true
        return st.betStreet == slotStreet(i) - 1 && st.turn < 0 && live().size > 1
    }
    fun nextBoardSlot(): Int { for (j in 2 until 7) if (slotVal(j) == null) return if (slotOpen(j)) j else -1; return -1 }
    fun lockMsg(i: Int): String {
        val s = slotStreet(i)
        val need = arrayOf("", "flop", "turn", "river")[s]
        if (live().size <= 1) return tr("Hand skončila, karty se už nerozdávají")
        if (st.betStreet < s - 1) {
            val after = I18n.stn4(s - 1)
            return L("${need.replaceFirstChar { it.uppercase() }} přijde až po $after", "${need.replaceFirstChar { it.uppercase() }} comes after the $after", "${need.replaceFirstChar { it.uppercase() }} kommt nach dem $after")
        }
        val now = I18n.stn4(st.betStreet)
        return L("Nejdřív dokonči sázky na $now, pak rozdej $need", "Finish betting on the $now first, then deal the $need", "Erst die Einsätze am $now beenden, dann $need geben")
    }

    // ================= update / equity =================
    private var simJob: Job? = null
    private var eqKey = ""

    fun update() {
        if (playing()) {
            val si = streetIdx()
            if (si > st.betStreet) { st.betStreet = si; startStreet() }
        }
        saveLocal()
        nextCache = null
        recalc()
        bump()
        gameTick()
    }

    fun recalc(force: Boolean = false) {
        if (!playing() || st.hero.contains(null)) { simJob?.cancel(); eq = null; eqKey = ""; return }
        if (gm() && (st.turn != 0 || st.done != null) && !force) return
        val key = "${st.handNo}|${st.hero}|${st.board}|" + st.seats.joinToString(",") { (if (it.folded) "f" else "") + it.actions.size }
        if (key == eqKey && eq != null) return
        simJob?.cancel()
        val copy = json.decodeFromString<GState>(json.encodeToString(st))
        val players = P.mapValues { it.value.copy() }
        simJob = scope.launch {
            val r = withContext(Dispatchers.Default) { Reads.simulate(copy, players, true, 220, 30000) }
            eqKey = key
            eq = r
            bump()
        }
    }

    /** Synchronous equity when the advisor opens and nothing is computed yet. */
    fun ensureEq() {
        if (playing() && !st.hero.contains(null) && eq == null) eq = Reads.simulate(st, P, true, 150, 25000)
    }

    // ================= game vs computer =================
    val BOTN = mapOf(
        "cs" to listOf("Karel", "Božena", "Pepa", "Mirek", "Jarka", "Zdeněk", "Lída", "Venca", "Olga", "Franta", "Hanka", "Ludvík", "Marta", "Tonda", "Věra", "Kamil"),
        "en" to listOf("Jack", "Emma", "Mike", "Sophie", "Dave", "Olivia", "Tom", "Grace", "Frank", "Lucy", "Sam", "Mia", "Ben", "Ruby", "Leo", "Kate"),
        "de" to listOf("Hans", "Greta", "Klaus", "Heidi", "Jürgen", "Anke", "Uwe", "Petra", "Fritz", "Sabine", "Rolf", "Ute", "Dieter", "Inge", "Kurt", "Lena"),
    )
    class BotP(val open: Double, val agg: Double, val bluff: Double, val loose: Double)
    val BOTP = mapOf(
        "rock" to BotP(0.13, 0.35, 0.04, -0.05),
        "tag" to BotP(0.2, 0.62, 0.12, -0.02),
        "lag" to BotP(0.36, 0.72, 0.24, 0.03),
        "station" to BotP(0.48, 0.15, 0.04, 0.12),
        "maniac" to BotP(0.66, 0.88, 0.4, 0.07),
    )
    val STYLES = mapOf(
        "mix" to listOf("rock", "tag", "lag", "station", "maniac", "tag", "station"),
        "fish" to listOf("station", "station", "maniac", "lag", "rock"),
        "pro" to listOf("tag", "tag", "lag", "rock"),
    )

    fun rnd32() = rnd.nextDouble()

    fun shuffled(): MutableList<Int> {
        val d = (0 until 52).toMutableList()
        for (i in 51 downTo 1) { val j = floor(rnd32() * (i + 1)).toInt(); val t = d[i]; d[i] = d[j]; d[j] = t }
        return d
    }

    fun gameDeal() {
        val d = shuffled()
        st.hero = mutableListOf(d.removeAt(d.size - 1), d.removeAt(d.size - 1))
        st.hole = mutableMapOf()
        st.seats.forEachIndexed { k, s -> if (k > 0 && !s.out) st.hole[k] = listOf(d.removeAt(d.size - 1), d.removeAt(d.size - 1)) }
        st.deck = d
    }

    fun gameSaved(): GState? {
        val g = if (bot()) st else loadState("hr6-game")
        return if (stValid(g) && g!!.mode == "game") g else null
    }

    fun newGame(cfg: GameCfg) {
        gameJob?.cancel()
        saveLocal()
        mode = "game"
        P.keys.filter { P[it]?.bot == true }.forEach { P.remove(it) }
        val pool = (BOTN[I18n.lang] ?: BOTN.getValue("cs")).shuffled(java.util.Random(rnd.nextLong()))
        val sty = STYLES[cfg.style] ?: STYLES.getValue("mix")
        val bots = (0 until cfg.opp).map { i ->
            newPlayer(pool[i % pool.size]).also { it.bot = true; it.bt = sty[floor(rnd32() * sty.size).toInt()] }
        }
        st = freshState().apply {
            mode = "game"; n = cfg.opp + 1; phase = "play"; startStack = cfg.stack; startLevel = cfg.level
            level = cfg.level; curLevel = cfg.level; autoBlinds = true; blindMin = 0; blindHands = cfg.every
            speed = cfg.speed; dealer = floor(rnd32() * (cfg.opp + 1)).toInt(); handNo = 0
        }
        st.seats = (listOf(seatObj("hero", cfg.stack)) + bots.map { seatObj(it.id, cfg.stack) }).toMutableList()
        st.pos = null
        st.watchPlace = 0
        com.onoseto.chipsnfish.online.Social.resetTable()
        startHand()
        eq = null; undoStack.clear(); raiseOpen = false
        update()
    }

    /** Returns false when switching to game mode and there is no saved game. */
    fun switchMode(m: String): Boolean {
        if (m == mode) return true
        gameJob?.cancel()
        saveLocal()
        mode = m
        val x = loadState(if (m == "game") "hr6-game" else "hr4-state")
        if (stValid(x)) st = x!!
        else if (m == "game") { mode = "coach"; return false }
        else { st = freshState(); st.seats = mutableListOf(seatObj("hero", st.startStack)) }
        if (m == "coach") syncSeats()
        undoStack.clear(); eq = null; raiseOpen = false
        update()
        return true
    }

    fun botEq(hole: List<Int>, bd: List<Int>, nOpp: Int, it: Int): Double {
        val known = HashSet<Int>().apply { addAll(hole); addAll(bd) }
        val deck = (0 until 52).filter { it !in known }.toIntArray()
        val d = deck.size
        val need = 5 - bd.size
        val e = Cards.Eval()
        val my = IntArray(7); my[0] = hole[0]; my[1] = hole[1]
        val ov = IntArray(7)
        for (i in bd.indices) { my[2 + i] = bd[i]; ov[2 + i] = bd[i] }
        val r = java.util.Random()
        var w = 0.0
        repeat(it) {
            var m = d
            fun draw(): Int { val j = r.nextInt(m); m--; val c = deck[j]; deck[j] = deck[m]; deck[m] = c; return c }
            for (j in 0 until need) { val c = draw(); my[2 + bd.size + j] = c; ov[2 + bd.size + j] = c }
            val mine = e.ev(my)
            var best = -1; var cnt = 0
            repeat(nOpp) {
                ov[0] = draw(); ov[1] = draw()
                val v = e.ev(ov)
                if (v > best) { best = v; cnt = 1 } else if (v == best) cnt++
            }
            if (mine > best) w += 1.0 else if (mine == best) w += 1.0 / (cnt + 1)
        }
        return w / it
    }

    data class Dec(val t: String, val to: Int = 0)

    fun botDecide(k: Int): Dec {
        val s = st.seats[k]
        val p = pl(s)
        val pr = BOTP[p.bt] ?: BOTP.getValue("tag")
        val hole = st.hole[k] ?: return Dec("fold")
        val bd = boardKnown()
        val toCall = max(0, st.curBet - s.bet)
        val pot = potTotal()
        val (mn, mx) = minMax(s)
        val canRaise = mx > st.curBet && s.stack > toCall
        val active = st.seats.count { !it.out }
        val nOpp = max(1, live().size - 1)
        val bb = st.bb
        val r = Math.random()
        fun to(f: Double): Int { val v = ((st.curBet + f * (pot + toCall)) / step()).roundToInt() * step(); return v.coerceIn(mn, mx) }
        fun raise(v: Int) = if (v >= mx) Dec("allin") else Dec("raise", v)
        fun call() = if (toCall >= s.stack) Dec("allin") else Dec(if (toCall > 0) "call" else "check")
        fun fold() = if (toCall > 0) Dec("fold") else Dec("check")
        if (bd.isEmpty()) {
            val q = Cards.pctOf(hole[0], hole[1]).toDouble()
            val order = ArrayList<Int>()
            run {
                var i = st.dealer
                for (c in 0 until st.n) { i = nxt(i); order.add(i); if (i == st.dealer) break }
            }
            val idx = order.indexOf(k)
            val late = if (order.size > 1) max(0, idx - 2).toDouble() / max(1, order.size - 3) else 1.0
            val rg = min(0.95, pr.open * (0.75 + 0.55 * late) * (if (active <= 3) 1.55 else if (active <= 5) 1.2 else 1.0))
            val stackBB = (s.stack + s.bet).toDouble() / bb
            if (stackBB <= 12) {
                val push = min(0.92, rg * (1.25 + (12 - stackBB) * 0.1))
                if (st.curBet <= bb) return if (q <= push) Dec("allin") else fold()
                return if (q <= push * min(1.0, 0.55 + (stackBB * bb / st.curBet) * 0.3)) Dec("allin") else fold()
            }
            val size = st.curBet.toDouble() / bb
            if (st.curBet <= bb) {
                val limp = st.seats.count { x -> !x.folded && x.bet == bb && x.actions.any { it.st == 0 && it.a == "call" } }
                if (canRaise && (q <= rg * 0.55 || (q <= rg && r < pr.agg)))
                    return raise(max(mn, (((2.5 + limp + (if (r < 0.3) 0.5 else 0.0)) * bb) / step()).roundToInt() * step()))
                if (toCall == 0) return Dec("check")
                if (toCall <= bb / 2.0 && q <= rg * 1.6) return call()
                if (q <= rg * (1 + pr.loose * 3) && r > pr.agg * 0.6) return call()
                return fold()
            }
            val tight = min(1.0, 2.6 / max(1.0, size - 0.5))
            val callR = max(0.02, rg * tight * (0.7 + pr.loose * 2.5))
            val valR = (0.025 + pr.agg * 0.045) * (if (size > 8) 0.6 else 1.0)
            if (q <= valR && canRaise) return raise(to(0.85 + r * 0.3))
            if (q <= callR) {
                if (canRaise && r < pr.bluff * 0.22 && size < 6) return raise(to(1.0))
                return call()
            }
            if (toCall <= bb && q <= rg * 1.4) return call()
            return fold()
        }
        val e = botEq(hole, bd, nOpp, if (active > 6) 240 else 340)
        val rel = e * (nOpp + 1)
        val wet = if (bd.size < 5) 1.0 else 0.7
        if (toCall == 0) {
            if (canRaise && rel >= 1.35 && r < 0.4 + pr.agg * 0.55) return raise(to(0.4 + r * 0.4 + (if (p.bt == "maniac") 0.3 else 0.0)))
            if (canRaise && rel < 0.9 && r < pr.bluff * wet * (if (nOpp == 1) 1.0 else 0.45)) return raise(to(0.45 + r * 0.25))
            return Dec("check")
        }
        val odds = toCall.toDouble() / (pot + toCall)
        val big = min(2.0, toCall.toDouble() / max(1, pot - toCall))
        val adj = e - 0.06 * big * (if (p.bt == "station") 0.4 else 1.0) + pr.loose
        if (canRaise && rel >= 1.8 && e > 0.62 && r < pr.agg * 0.75) return raise(to(0.7 + r * 0.5))
        if (adj >= odds) return call()
        if (canRaise && nOpp == 1 && r < pr.bluff * 0.1 * wet) return raise(to(0.8))
        return fold()
    }

    private var gameJob: Job? = null
    var onChipSound: (() -> Unit)? = null

    fun gameTick() {
        gameJob?.cancel(); gameJob = null
        if (!bot() || !playing() || st.done != null || !screenActive) return
        val sp = st.speed
        val bd = boardKnown().size
        val fin: () -> Unit = { finishHand() }
        val deal: () -> Unit = { dealNext() }
        val bot: () -> Unit = { botMove() }
        // nobody can bet any more (all-in): turn the cards face up and deal the rest slowly so everyone sees it
        val runout = st.turn < 0 && live().size > 1 && live().count { !it.allin } <= 1 && bd < 5
        if (runout && st.reveal.isNullOrEmpty()) {
            st.reveal = mutableMapOf()
            st.seats.forEachIndexed { k, s -> if (k > 0 && !s.folded && !s.out) st.hole[k]?.let { st.reveal!![k] = it } }
            toast(L("All-in! Karty se otáčí a dorozdává se do konce.", "All-in! Cards are turned over and the board runs out.", "All-in! Karten werden aufgedeckt, das Board läuft durch."))
        }
        val (ms, fn) = when {
            live().size <= 1 -> (500 * sp) to fin
            st.turn < 0 -> (if (bd < 5) (if (runout) 1500 else 800) * sp else 900 * sp) to (if (bd < 5) deal else fin)
            st.turn > 0 -> ((500 + Math.random() * 700) * sp) to bot
            else -> return
        }
        gameJob = scope.launch { delay(ms.toLong()); fn() }
    }

    fun botMove() {
        val k = st.turn
        if (!bot() || !playing() || k <= 0 || st.done != null) return
        val d = botDecide(k)
        if (d.t == "raise") act(k, "raise", d.to) else act(k, d.t)
        update()
    }

    fun dealNext() {
        if (!bot() || !playing() || st.turn >= 0 || live().size <= 1 || st.done != null) return
        val n = boardKnown().size
        if (n >= 5) return
        val c = if (n == 0) 3 else 1
        for (i in 0 until c) st.board[n + i] = st.deck.removeAt(st.deck.size - 1)
        update()
    }

    fun finishHand() {
        if (!bot() || !playing() || st.done != null) return
        val lv = live()
        val win = IntArray(st.n)
        val sc = LinkedHashMap<Int, Int>()
        var sd = false
        if (lv.size == 1) {
            win[st.seats.indexOf(lv[0])] = potTotal()
        } else {
            sd = true
            val bd = boardKnown()
            lv.forEach { s -> val k = st.seats.indexOf(s); sc[k] = Cards.ev((if (k == 0) st.hero.map { it!! } else st.hole.getValue(k)) + bd) }
            val ord = ArrayList<Int>()
            run { var i = st.dealer; repeat(st.n) { i = (i + 1) % st.n; ord.add(i) } }
            val lvls = lv.map { it.total }.toSortedSet().toList()
            var prev = 0
            for (lvl in lvls) {
                var pot = 0
                st.seats.forEach { pot += max(0, min(it.total, lvl) - prev) }
                val el = ord.filter { sc[it] != null && st.seats[it].total >= lvl }
                val best = el.maxOf { sc.getValue(it) }
                val ws = el.filter { sc[it] == best }
                val sh = pot / ws.size
                ws.forEach { win[it] += sh }
                win[ws[0]] += pot - sh * ws.size
                prev = lvl
            }
            val rest = st.seats.sumOf { max(0, it.total - prev) }
            if (rest > 0) {
                val best = sc.values.max()
                win[sc.entries.first { it.value == best }.key] += rest
            }
            st.reveal = mutableMapOf()
            for (k in sc.keys) if (k > 0) st.reveal!![k] = st.hole.getValue(k)
        }
        win.forEachIndexed { k, v -> st.seats[k].stack += v; st.seats[k].bet = 0 }
        val ws = win.indices.filter { win[it] > 0 }
        st.done = Done(ws, win.toList(), sd, sc.mapValues { Cards.cat(it.value) }, win[0] - st.seats[0].total)
        learnBots(sc)
        if (bot()) com.onoseto.chipsnfish.online.Social.botsReact(ws, st.seats.sumOf { it.total } > 30 * st.bb)
        val alive = st.seats.filter { it.stack > 0 }
        if (st.watchPlace > 0) { if (alive.size <= 1) st.over = Over(false, st.watchPlace) }
        else if (st.seats[0].stack <= 0) st.over = Over(false, alive.size + 1)
        else if (alive.size == 1) st.over = Over(true, 1)
        if (bot()) com.onoseto.chipsnfish.online.Fame.practiceHand(win[0] - st.seats[0].total, st.bb, st.over?.win == true, st.n - 1)
        val stats = st.stats ?: GStats().also { st.stats = it }
        stats.hands++
        if (win[0] > 0) stats.won++
        st.turn = -1
        update()
        if (win[0] > 0) onChipSound?.invoke()
    }

    private fun learnBots(sc: Map<Int, Int>) {
        st.seats.forEachIndexed { k, s ->
            if (k == 0 || s.out) return@forEachIndexed
            val p = P[s.pid] ?: return@forEachIndexed
            val pre = s.actions.filter { it.st == 0 }
            val post = s.actions.filter { it.st > 0 }
            p.hands++
            if (pre.any { it.a == "call" || Reads.agg(it.a) }) p.vp++
            if (pre.any { Reads.agg(it.a) }) p.pfr++
            p.agg += post.count { Reads.agg(it.a) }
            p.calls += post.count { it.a == "call" }
            sc[k]?.let { v ->
                val c = Cards.cat(v)
                if (post.any { Reads.agg(it.a) } && c <= 1 && k !in st.done!!.w) p.caught++
                else if (c >= 2) p.showed++
            }
        }
    }

    /** Returns true when the game is over and setup should open instead. */
    fun gameNext(): Boolean {
        if (!bot()) return false
        if (st.over != null) return true
        st.seats.forEach { if (it.stack <= 0) it.out = true }
        st.dealer = nxt(st.dealer)
        st.tab = 0
        startHand()
        eq = null; raiseOpen = false
        update()
        return false
    }

    private var preOnlineMode = "coach"

    fun enterOnline() {
        gameJob?.cancel()
        if (!online()) { saveLocal(); preOnlineMode = mode }
        mode = "online"
        st = freshState().apply { mode = "online"; phase = "setup"; n = 1 }
        st.seats = mutableListOf(seatObj(com.onoseto.chipsnfish.online.Supa.uid.ifEmpty { "hero" }, 0))
        if (P[st.seats[0].pid] == null) P[st.seats[0].pid] = P.getValue("hero").copy(id = st.seats[0].pid, stored = false)
        eq = null; raiseOpen = false; undoStack.clear()
        bump()
    }

    fun setOnlineView(v: GState) {
        if (!online()) return
        val turnChanged = v.turn != st.turn || v.handNo != st.handNo
        st = v
        nextCache = null
        if (turnChanged && v.turn != 0) raiseOpen = false
        recalc()
        bump()
    }

    fun leaveOnline() {
        if (!online()) return
        mode = preOnlineMode.takeIf { it != "online" } ?: "coach"
        val x = loadState(if (mode == "game") "hr6-game" else "hr4-state")
        if (stValid(x)) st = x!! else { mode = "coach"; st = freshState(); st.seats = mutableListOf(seatObj("hero", st.startStack)) }
        if (mode == "coach") syncSeats()
        eq = null; raiseOpen = false
        bump()
    }

    fun endGameVsComputer() {
        setS("hr6-game", null)
        P.keys.filter { P[it]?.bot == true }.forEach { P.remove(it) }
        mode = "game"
        switchMode("coach")
    }

    // ================= hero actions =================
    fun heroAction(type: String) {
        val k = st.turn
        if (k < 0) return
        if (gm() && k != 0) return
        val s = st.seats[k]
        val name = if (k == 0) tr("Ty") else pl(s).name
        if (type == "raiseopen") {
            raiseOpen = !raiseOpen
            if (raiseOpen) raiseTo = minMax(s).first
            bump(); return
        }
        if (online()) {
            val s0 = st.seats[0]
            val tc = max(0, st.curBet - s0.bet)
            when (type) {
                "fold" -> com.onoseto.chipsnfish.online.Online.move("fold")
                "call" -> com.onoseto.chipsnfish.online.Online.move(if (tc >= s0.stack && tc > 0) "allin" else "call")
                "raise" -> { val (mn, mx) = minMax(s0); val to = raiseTo.coerceIn(mn, mx); com.onoseto.chipsnfish.online.Online.move(if (to >= mx) "allin" else "raise", to) }
            }
            raiseOpen = false; bump(); return
        }
        snapshot()
        when (type) {
            "fold" -> act(k, "fold")
            "call" -> { val tc = max(0, st.curBet - s.bet); if (tc >= s.stack && tc > 0) act(k, "allin") else act(k, "call") }
            "raise" -> {
                val (mn, mx) = minMax(s)
                val to = raiseTo.coerceIn(mn, mx)
                if (to >= mx) act(k, "allin") else act(k, "raise", to)
            }
        }
        val la = s.actions.last()
        toast("$name: ${Reads.actl(la.a)}${la.amt?.let { " " + fm(it) } ?: ""}")
        raiseOpen = false
        st.sample = false
        update()
    }

    fun doCoach(kind: String, amt: Int) {
        if (st.turn != 0) return
        val s = st.seats[0]
        val (mn, mx) = minMax(s)
        val tc = max(0, st.curBet - s.bet)
        if (online()) {
            val O = com.onoseto.chipsnfish.online.Online
            when (kind) {
                "fold" -> O.move(if (tc == 0) "call" else "fold")
                "check", "call" -> O.move(if (tc >= s.stack && tc > 0) "allin" else "call")
                else -> { val to = amt.coerceIn(mn, mx); O.move(if (to >= mx) "allin" else "raise", to) }
            }
            raiseOpen = false; bump(); return
        }
        snapshot()
        when (kind) {
            "fold" -> act(0, if (tc == 0) "call" else "fold")
            "check", "call" -> act(0, if (tc >= s.stack && tc > 0) "allin" else "call")
            else -> { val to = amt.coerceIn(mn, mx); act(0, if (to >= mx) "allin" else "raise", to) }
        }
        val la = s.actions.last()
        toast(tr("Ty") + ": " + Reads.actl(la.a) + (la.amt?.let { " " + fm(it) } ?: ""))
        raiseOpen = false; st.sample = false
        update()
    }

    // ================= live advisor mode flows =================
    /** After busting out vs the computer: stay and watch the rest of the game. */
    fun watchOn() {
        val o = st.over ?: return
        if (o.win || st.seats.count { it.stack > 0 } < 2) return
        st.watchPlace = o.place; st.over = null
        gameNext()
    }

    fun nextHand() {
        st.dealer = nxt(st.dealer); st.sample = false; st.tab = 0
        cleanup(); startHand(); eq = null
        update()
    }

    fun restart(keep: Boolean) {
        val n = st.n; val sl = st.startLevel; val ss = st.startStack; val ab = st.autoBlinds; val dl = st.dealer
        val seats = st.seats
        st = freshState().apply { this.n = n; startLevel = sl; startStack = ss; autoBlinds = ab; dealer = dl; level = sl; curLevel = sl }
        st.sb = Cards.lvl(st.level)[0]; st.bb = Cards.lvl(st.level)[1]
        if (keep) st.seats = seats.map { seatObj(it.pid, st.startStack) }.toMutableList()
        else { st.seats = mutableListOf(seatObj("hero", st.startStack)); st.n = 1; st.dealer = 0 }
        syncSeats()
        st.pos = null
        st.seats.forEach { it.stack = st.startStack }
        cleanup(); eq = null
        update()
    }

    fun restartFromSample() {
        st.seats.drop(1).forEachIndexed { k, s -> val p = pl(s); if (!p.stored) { p.name = L("Hráč ", "Player ", "Spieler ") + (k + 1); p.override = "auto" } }
        st.startLevel = 0
        restart(false)
        toast(tr("Přidej hráče z lavičky na stůl"))
    }

    fun saveHand(e: EndState) {
        val now = System.currentTimeMillis()
        val pot = potTotal()
        val hero = st.seats[0]
        val wSeat = e.winner?.let { w -> st.seats.find { it.pid == w } }
        var heroWin = 0
        when (e.result) {
            "win" -> heroWin = pot
            "split" -> { heroWin = pot / 2; wSeat?.let { it.stack += pot - heroWin } }
            else -> wSeat?.let { it.stack += pot }
        }
        hero.stack += heroWin
        val ee = eq
        val rec = HandRec(
            id = uid("h"), no = st.handNo, t = now, n = st.n, blinds = Cards.lvlStr(st.curLevel), street = st.betStreet,
            hero = st.hero.toList(), board = st.board.toList(), result = e.result,
            winner = if (e.result == "win") null else e.winner,
            eq = if (ee != null && !ee.allFolded) ee.eq else null, pot = pot, net = heroWin - hero.total,
            seats = st.seats.mapIndexed { k, s ->
                RecSeat(s.pid, pl(s).name, s.mood.takeIf { it.isNotEmpty() }?.toList(), s.folded, s.actions.toList(), e.rev[s.pid] ?: "none", st.shown[k]?.toList())
            },
        )
        for (s in opps()) {
            val p = pl(s)
            val pre = s.actions.filter { it.st == 0 }; val post = s.actions.filter { it.st > 0 }
            p.hands++
            if (pre.any { it.a == "call" || Reads.agg(it.a) }) p.vp++
            if (pre.any { Reads.agg(it.a) }) p.pfr++
            p.agg += post.count { Reads.agg(it.a) }
            p.calls += post.count { it.a == "call" }
            if (e.rev[s.pid] == "bluff") p.caught++
            if (e.rev[s.pid] == "strong") p.showed++
            p.t = now
            p.stored = true
        }
        H.add(0, rec)
        saveLocal()
        toast(tr("Hand uložena. Rozdávám další."))
        nextHand()
    }

    class Showdown(val cat: Map<String, Int>, val done: Boolean, val text: String)

    fun showdownEval(e: EndState): Showdown {
        val lv = liveOpps()
        val bd = boardKnown()
        val catM = HashMap<String, Int>()
        for (s in lv) {
            val k = st.seats.indexOf(s)
            val c0 = slotVal(10 + 2 * k); val c1 = slotVal(11 + 2 * k)
            if (bd.size >= 3 && c0 != null && c1 != null) catM[s.pid] = Cards.cat(Cards.ev(listOf(c0, c1) + bd))
        }
        val heroIn = !st.seats[0].folded && !st.hero.contains(null)
        if (bd.size < 5 || lv.isEmpty() || lv.any { catM[it.pid] == null }) return Showdown(catM, false, "")
        val sc = ArrayList<Pair<String, Int>>()
        if (heroIn) sc.add("hero" to Cards.ev(st.hero.map { it!! } + bd))
        for (s in lv) { val k = st.seats.indexOf(s); sc.add(s.pid to Cards.ev(listOf(slotVal(10 + 2 * k)!!, slotVal(11 + 2 * k)!!) + bd)) }
        val best = sc.maxOf { it.second }
        val win = sc.filter { it.second == best }
        fun nm(id: String) = if (id == "hero") tr("Ty") else P[id]?.name ?: "?"
        if (!e.manRes) {
            e.result = if (win.any { it.first == "hero" }) (if (win.size > 1) "split" else "win") else "lose"
            e.winner = win.find { it.first != "hero" }?.first
        }
        val wc = I18n.cat(Cards.CAT[Cards.cat(best)])
        val text = (if (win.size > 1) L("Dělí se: ", "Split: ", "Geteilt: ") else L("Vyhrává: ", "Winner: ", "Gewinner: ")) + win.joinToString(", ") { nm(it.first) } + " · " + wc
        for (s in lv) {
            if (e.man[s.pid] == true) continue
            val c = catM.getValue(s.pid)
            val ag = s.actions.any { it.st > 0 && Reads.agg(it.a) }
            val won = win.any { it.first == s.pid }
            e.rev[s.pid] = if (c >= 2) "strong" else if (ag && !won && c <= 1) "bluff" else "none"
        }
        return Showdown(catM, true, text)
    }

    fun newEnd(): EndState {
        val lv = liveOpps()
        val heroIn = !st.seats[0].folded
        return EndState(if (heroIn) "win" else "lose", if (!heroIn && lv.size == 1) lv[0].pid else null)
    }

    // ================= blinds =================
    fun blindLeft(): Int = st.blindPaused ?: ((st.blindStart + st.blindMin * 60000L - System.currentTimeMillis()) / 1000).toInt()

    fun toggleBlindTimer() {
        if (st.blindPaused != null) {
            st.blindStart = System.currentTimeMillis() - (st.blindMin * 60 - st.blindPaused!!) * 1000L
            st.blindPaused = null
            toast(tr("Odpočet běží"))
        } else {
            st.blindPaused = blindLeft()
            toast(tr("Odpočet pozastaven"))
        }
        saveLocal(); bump()
    }

    fun applyBlindsNow(l: Int) {
        st.level = l; st.curLevel = l
        st.sb = Cards.lvl(l)[0]; st.bb = Cards.lvl(l)[1]
        st.lastRaise = max(st.lastRaise, st.bb)
        st.blindStart = System.currentTimeMillis(); st.blindPaused = null
        update()
        toast(L("Blindy hned ", "Blinds now ", "Blinds sofort ") + Cards.lvlStr(l))
    }

    fun applyBlindsNext(sel: Int) {
        if (sel != st.level) { st.blindStart = System.currentTimeMillis(); st.blindPaused = null }
        st.level = sel
        update()
        toast(if (st.level == st.curLevel) tr("Blindy beze změny") else L("Od další handy blindy ", "From next hand blinds ", "Ab nächster Hand Blinds ") + Cards.lvlStr(st.level))
    }

    // ================= stats =================
    fun statVal(p: Player, k: String): Double? {
        val n = p.hands
        return when (k) {
            "hands" -> n.toDouble()
            "vpip" -> if (n > 0) p.vp.toDouble() / n else null
            "pfr" -> if (n > 0) p.pfr.toDouble() / n else null
            "af" -> if (n > 0) p.agg.toDouble() / max(1, p.calls) else null
            "caught" -> p.caught.toDouble()
            else -> p.showed.toDouble()
        }
    }

    fun statFmt(k: String, v: Double?): String {
        if (v == null) return "–"
        return when (k) {
            "vpip", "pfr" -> "${(v * 100).roundToInt()}%"
            "af" -> String.format(I18n.locale, "%.1f", v)
            else -> v.roundToInt().toString()
        }
    }

    fun statLabel(k: String) = when (k) {
        "hands" -> tr("hand"); "vpip" -> "VPIP"; "pfr" -> "PFR"; "af" -> "AF"
        "caught" -> tr("chycen bluff"); else -> tr("ukázal silnou")
    }

    /** Title, general explanation, interpretation, few-hands warning. */
    fun statTip(p: Player, k: String): List<String> {
        val v = statVal(p, k)
        val n = p.hands
        val few = if (n < 20) L("Zatím jen $n hand – číslo je jen orientační, spolehlivé bývá od 30 hand.", "Only $n hands so far – the number is a rough guide, it gets reliable from 30 hands.", "Erst $n Hände – die Zahl ist nur ein Richtwert, verlässlich ab 30 Händen.") else ""
        val t: String; val g: String; var i = ""
        when (k) {
            "vpip" -> {
                t = "VPIP · dobrovolně do potu"; g = "Jak často hráč preflop dobrovolně vloží žetony (call nebo raise). Povinné blindy se nepočítají."
                if (v != null) i = when {
                    v < 0.15 -> "Velmi tight: hraje jen silné ruce. Když jde do hry, respektuj ho."
                    v < 0.25 -> "Tight: vybíravý, hraje rozumný výběr rukou."
                    v < 0.35 -> "Normální až volnější: běžný rekreační hráč."
                    v < 0.5 -> "Loose: hraje hodně rukou, často i slabé. Value bety proti němu vydělávají."
                    else -> "Velmi loose: hraje skoro všechno. Typická ryba nebo maniak – bluffovat ho nemá smysl, hraj value."
                }
            }
            "pfr" -> {
                t = "PFR · zvýšení preflop"; g = "Jak často hráč preflop zvyšuje (raise, 3-bet)."
                if (v != null) {
                    i = tr(when {
                        v < 0.08 -> "Pasivní: zvyšuje málo. Když zvýší, má obvykle silnou ruku."
                        v < 0.18 -> "Standardní agrese."
                        v < 0.28 -> "Agresivní: zvyšuje často, jeho range je široký."
                        else -> "Velmi agresivní: zvyšuje skoro pořád. Častěji ho chytej silnějšími rukama."
                    })
                    val vp = statVal(p, "vpip")
                    if (vp != null && vp - v > 0.15) i += " " + tr("Velký rozdíl VPIP a PFR: hodně dorovnává a málo zvyšuje (pasivní hráč).")
                }
            }
            "af" -> {
                t = "AF · agrese po flopu"; g = "Poměr útočných akcí (bet, raise) ku callům po flopu."
                if (v != null) i = when {
                    v < 1 -> "Pasivní: častěji dorovnává, než útočí. Jeho bet znamená sílu."
                    v < 2 -> "Vyvážený: útočí i dorovnává."
                    v < 3.5 -> "Agresivní: rád sází a zvyšuje, často i se slabší rukou."
                    else -> "Velmi agresivní: sází skoro pořád. Dorovnávej mu širší a nech ho bluffovat."
                }
            }
            "hands" -> {
                t = "Hand"; g = "Počet uložených hand, ve kterých hráč seděl u stolu."
                i = if (n < 20) "Málo dat, statistiky jsou zatím hrubý odhad." else if (n < 60) "Statistiky už mají slušnou vypovídací hodnotu." else "Hodně dat, statistiky jsou spolehlivé."
            }
            "caught" -> {
                t = "Chycen při bluffu"; g = "Kolikrát hráč útočil po flopu a na showdownu ukázal slabou ruku."
                i = if (v == 0.0) "Zatím nebyl chycen." else if (n > 0 && v!! / n > 0.06) "Bluffuje často. Proti jeho sázkám můžeš dorovnávat širší." else "Občas bluffuje."
            }
            else -> {
                t = "Ukázal silnou ruku"; g = "Kolikrát hráč na showdownu ukázal aspoň dva páry."
                i = if (v == 0.0) "Zatím nic neukázal." else "Když sází, obvykle má. Respektuj jeho velké sázky."
            }
        }
        return listOf(tr(t) + ": " + statFmt(k, v), tr(g), tr(i), few)
    }

    val MOODS = listOf("🍺", "🍷", "🥃", "🌿", "🚬", "☕", "😴", "😡", "😎", "🥳", "😟", "🤒")

    class MoodRow(val m: String, var n: Int = 0, var vp: Int = 0, var pfr: Int = 0, var agg: Int = 0, var calls: Int = 0, var win: Int = 0)

    fun moodStats(pid: String): List<MoodRow> {
        val by = LinkedHashMap<String, MoodRow>()
        for (r in H) {
            val s = r.seats.find { it.pid == pid } ?: continue
            val ms = s.mood?.takeIf { it.isNotEmpty() } ?: listOf("·")
            for (m in ms) {
                val o = by.getOrPut(m) { MoodRow(m) }
                o.n++
                val pre = s.actions.filter { it.st == 0 }; val post = s.actions.filter { it.st > 0 }
                if (pre.any { it.a == "call" || Reads.agg(it.a) }) o.vp++
                if (pre.any { Reads.agg(it.a) }) o.pfr++
                o.agg += post.count { Reads.agg(it.a) }
                o.calls += post.count { it.a == "call" }
                if (if (pid == "hero") r.result == "win" else r.winner == pid) o.win++
            }
        }
        if (by.keys.none { it != "·" }) return emptyList()
        val out = ArrayList<MoodRow>()
        by["·"]?.let { out.add(it) }
        out.addAll(by.values.filter { it.m != "·" })
        return out
    }

    // ================= fish =================
    val FISH_DEF = listOf(
        "Blub. Já jen callnu.",
        "Kdo do půl hodiny nenajde u stolu rybu, je ryba sám.",
        "Ryba plave, žetony taky.",
        "All-in? Blub blub.",
        "Tahle ryba čte bluffy líp než ty.",
        "Nekrm ryby u stolu. Hlavně ne žetony.",
    )
    fun fishLines(): List<String> = fish ?: FISH_DEF.map { tr(it) }

    // ================= chips =================
    fun chipBreak(amt: Int): List<Pair<ChipDef, Int>> {
        val d = (if (chips.any { !it.off }) chips.filter { !it.off } else chips).sortedByDescending { it.v }
        val out = ArrayList<Pair<ChipDef, Int>>()
        var rest = amt
        for (c in d) { val n = rest / c.v; if (n > 0) { out.add(c to n); rest -= n * c.v } }
        if (rest > 0 && out.isEmpty()) out.add(d.last() to 1)
        return out
    }

    // ================= formatting =================
    fun fm(n: Int): String = java.text.NumberFormat.getIntegerInstance(I18n.locale).format(n.toLong())
    fun pc(x: Double, d: Int = 1): String = String.format(I18n.locale, "%.${d}f", x * 100) + " %"
    fun shortN(n0: Int): String {
        val n = n0
        if (n < 10000) return n.toString()
        val v = n / 1000.0
        val s = if (n < 100000) String.format(java.util.Locale.US, "%.1f", v) else String.format(java.util.Locale.US, "%.0f", v)
        return s.replace(".", ",").replace(",0", "") + "k"
    }

    @Suppress("unused") private fun unused() = abs(0)
}
