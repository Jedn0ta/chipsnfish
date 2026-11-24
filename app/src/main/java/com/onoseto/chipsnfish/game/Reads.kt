package com.onoseto.chipsnfish.game

import com.onoseto.chipsnfish.I18n
import com.onoseto.chipsnfish.L
import kotlin.math.abs
import kotlin.random.Random

/** Player types and the opponent model (range width, pressure, bluff read) from the original app. */
class Prof(val key: String, val s: String, val range: Double, val bluff: Double) {
    val l: String
        get() = when (key) {
            "unk" -> L("Neznámý", "Unknown", "Unbekannt")
            "rock" -> L("Skála", "Rock", "Fels")
            "tag" -> "TAG"
            "lag" -> "LAG"
            "station" -> L("Calling station", "Calling station", "Calling Station")
            else -> L("Maniak", "Maniac", "Maniac")
        }
    val d: String
        get() = when (key) {
            "unk" -> L("Zatím o něm nic nevíš.", "You know nothing about him yet.", "Du weißt noch nichts über ihn.")
            "rock" -> L("Hraje málo rukou a pasivně. Když vsadí, má to.", "Plays few hands, passively. When he bets, he has it.", "Spielt wenige Hände, passiv. Wenn er setzt, hat er es.")
            "tag" -> L("Vybíravý a agresivní. Solidní hráč.", "Selective and aggressive. Solid player.", "Selektiv und aggressiv. Solider Spieler.")
            "lag" -> L("Hraje hodně rukou a tlačí. Bluffuje často.", "Plays many hands and pushes. Bluffs often.", "Spielt viele Hände und drückt. Blufft oft.")
            "station" -> L("Dorovnává skoro všechno, sám málo útočí.", "Calls almost everything, rarely attacks.", "Callt fast alles, greift selten an.")
            else -> L("Sází a zvyšuje skoro s čímkoli.", "Bets and raises with almost anything.", "Setzt und erhöht mit fast allem.")
        }
}

object Reads {
    val PROF = linkedMapOf(
        "unk" to Prof("unk", "?", 0.45, 0.25),
        "rock" to Prof("rock", "ROCK", 0.14, 0.08),
        "tag" to Prof("tag", "TAG", 0.2, 0.22),
        "lag" to Prof("lag", "LAG", 0.42, 0.38),
        "station" to Prof("station", "CALL", 0.6, 0.1),
        "maniac" to Prof("maniac", "MANI", 0.75, 0.55),
    )
    val SZ = mapOf("s" to 0.42, "m" to 0.55, "l" to 0.7, "o" to 0.8)

    fun agg(a: String) = a == "bet" || a == "raise" || a == "allin"

    fun actl(a: String): String = when (a) {
        "fold" -> "Fold"; "check" -> "Check"; "call" -> "Call"; "bet" -> "Bet"; "raise" -> "Raise"; "allin" -> "All-in"; "sb" -> "SB"; "bb" -> "BB"; else -> a
    }

    data class AutoType(val t: String, val enough: Boolean)

    fun autoType(p: Player): AutoType {
        if (p.hands < 8) return AutoType("unk", false)
        val v = p.vp.toDouble() / p.hands
        val r = p.pfr.toDouble() / p.hands
        val ratio = if (p.vp > 0) r / v else 0.0
        val af = p.agg.toDouble() / maxOf(1, p.calls)
        val t = when {
            v >= 0.5 && af >= 2.5 -> "maniac"
            v >= 0.35 -> if (ratio >= 0.45 || af >= 2) "lag" else "station"
            v <= 0.22 -> if (ratio >= 0.5 || af >= 1.5) "tag" else "rock"
            else -> if (ratio >= 0.55 || af >= 1.8) (if (v > 0.28) "lag" else "tag") else "station"
        }
        return AutoType(t, true)
    }

    fun effType(p: Player): String = if (p.override != "auto" && PROF.containsKey(p.override)) p.override else autoType(p).t
    fun prof(p: Player) = PROF.getValue(effType(p))

    fun typeLine(p: Player): String {
        val a = autoType(p)
        if (p.override != "auto" && PROF.containsKey(p.override)) return PROF.getValue(p.override).l + L(" · nastaveno ručně", " · set manually", " · manuell gesetzt")
        return if (a.enough) "${PROF.getValue(a.t).l} · " + L("auto podle ${p.hands} hand", "auto from ${p.hands} hands", "auto aus ${p.hands} Händen")
        else L("Neznámý · auto, zatím ${p.hands} z 8 hand", "Unknown · auto, ${p.hands} of 8 hands so far", "Unbekannt · auto, bisher ${p.hands} von 8 Händen")
    }

    fun rangeW(seat: Seat, p: Player): Double {
        var w = prof(p).range
        if (p.hands >= 8) {
            val k = minOf(1.0, p.hands / 40.0)
            w = w * (1 - k) + (p.vp.toDouble() / p.hands) * k
        }
        for (a in seat.actions.filter { it.st == 0 }) {
            when (a.a) {
                "raise", "bet" -> w *= 0.55
                "allin" -> w *= 0.3
                "call" -> w *= 0.92
            }
        }
        return w.coerceIn(0.03, 1.0)
    }

    fun pressure(seat: Seat): Double {
        var p = 0.0
        val s = HashSet<Int>()
        for (a in seat.actions) {
            if (a.st == 0) continue
            val v = when (a.a) {
                "call" -> 0.3
                "bet" -> SZ[a.sz] ?: 0.55
                "raise" -> minOf(0.95, (SZ[a.sz] ?: 0.55) + 0.15)
                "allin" -> 0.92
                else -> 0.0
            }
            if (agg(a.a)) s.add(a.st)
            p = maxOf(p, v)
        }
        return minOf(0.95, p + 0.08 * maxOf(0, s.size - 1))
    }

    fun baseBluff(p: Player): Double = (prof(p).bluff * 6 + p.caught) / (6.0 + p.caught + p.showed)

    /** A reason line: text, delta (or base value when isBase). */
    data class Reason(val t: String, val d: Double, val isBase: Boolean = false)
    data class BluffRead(val v: Double, val st: Int, val r: List<Reason>)

    fun boardKnown(st: GState): List<Int> = st.board.filterNotNull()
    fun live(st: GState) = st.seats.filter { !it.folded }

    fun bluffRead(st: GState, seat: Seat, p: Player): BluffRead? {
        val post = seat.actions.filter { it.st > 0 }
        val ag = post.filter { agg(it.a) }
        if (ag.isEmpty()) return null
        val ty = effType(p)
        val pr = PROF.getValue(ty)
        val last = ag.last()
        val s = last.st
        val r = ArrayList<Reason>()
        var b = pr.bluff
        r.add(Reason(L("Typ: ", "Type: ", "Typ: ") + pr.l, pr.bluff, true))
        fun add(t: String, d: Double) {
            if (abs(d) < 0.005) return
            b += d; r.add(Reason(t, d))
        }
        if (p.caught > 0 || p.showed > 0) add(L("Historie: ${p.caught}× bluff, ${p.showed}× silná", "History: ${p.caught}× bluff, ${p.showed}× strong", "Verlauf: ${p.caught}× Bluff, ${p.showed}× stark"), baseBluff(p) - pr.bluff)
        val loose = ty == "lag" || ty == "maniac"
        if (last.a == "raise") add(L("Raise bývá silná akce", "A raise is usually a strong action", "Ein Raise ist meist eine starke Aktion"), if (loose) -0.02 else -0.06)
        if (last.a == "allin") add(L("All-in je silná akce", "All-in is a strong action", "All-in ist eine starke Aktion"), -0.04)
        val same = post.filter { it.st == s }
        if (last.a == "raise" && same.isNotEmpty() && same[0].a == "check") add("Check-raise", -0.05)
        if (s == 3 && last.sz == "s") add(L("Malá sázka na riveru bývá tenká value", "A small river bet is usually thin value", "Kleiner River-Bet ist meist dünne Value"), -0.03)
        if (s == 3 && (last.sz == "l" || last.sz == "o")) add(L("Velká sázka na riveru je polarizovaná", "A big river bet is polarized", "Großer River-Bet ist polarisiert"), 0.04)
        if (last.sz == "o" && s < 3) add("Overbet", 0.02)
        val streets = ag.map { it.st }.toSet().size
        if (streets >= 3) add(L("Útočí na všech třech ulicích", "Attacks on all three streets", "Greift auf allen drei Straßen an"), -0.06)
        else if (streets == 2) add(L("Druhá sázka v řadě", "Second bet in a row", "Zweiter Bet in Folge"), -0.02)
        if (s >= 2 && ag.none { it.st == s - 1 } && post.any { it.st == s - 1 && it.a == "check" })
            add(L("Opožděná sázka po checku", "Delayed bet after a check", "Verzögerter Bet nach Check"), 0.04)
        if (ty == "rock" || ty == "station") add(L("Pasivní hráč najednou útočí", "A passive player suddenly attacks", "Passiver Spieler greift plötzlich an"), -0.06)
        val bd = boardKnown(st)
        if (s == 1 && bd.size >= 3) {
            val t = Cards.tex(bd.subList(0, 3))
            if (t.ms < 2 && t.sw < 3 && !t.paired) add(L("Suchý flop, sázka bez ruky je lákavá", "Dry flop, betting without a hand is tempting", "Trockener Flop, Bet ohne Hand ist verlockend"), 0.03)
            else if (t.ms >= 2 || t.sw >= 3) add(L("Mokrý flop, možný semi-bluff", "Wet flop, possible semi-bluff", "Nasser Flop, möglicher Semi-Bluff"), 0.03)
        }
        if (s >= 2 && bd.size > s + 1) {
            val prev = Cards.tex(bd.subList(0, s + 1))
            val now = Cards.tex(bd.subList(0, s + 2))
            val card = bd[s + 1] shr 2
            val scare = (card > prev.top && card >= 10) || (now.ms >= 3 && prev.ms < 3) || (now.sw >= 4 && prev.sw < 4)
            if (scare) add(L("Přišla strašidelná karta", "A scare card came", "Eine Angstkarte kam"), 0.05)
            if (s == 3 && (prev.ms == 2 || prev.sw >= 3) && !scare) add(L("River nedotáhl drawy", "The river missed the draws", "Der River hat die Draws verfehlt"), 0.07)
        }
        val multi = live(st).size - 2
        if (multi > 0) add(L("Ve hře víc hráčů (${multi + 2})", "More players in the hand (${multi + 2})", "Mehr Spieler in der Hand (${multi + 2})"), -minOf(0.09, multi * 0.03))
        if (rangeW(seat, p) < 0.15) add(L("Preflop hrál velmi úzce", "Played very tight preflop", "Preflop sehr eng gespielt"), -0.03)
        return BluffRead(b.coerceIn(0.03, 0.8), s, r)
    }

    data class SeatModel(val w: Double, val p: Double, val b: Double, val br: BluffRead?)

    fun model(st: GState, players: Map<String, Player>, seat: Seat): SeatModel {
        val p = players.getValue(seat.pid)
        val br = bluffRead(st, seat, p)
        return SeatModel(rangeW(seat, p), pressure(seat), br?.v ?: baseBluff(p), br)
    }

    data class SeatBluff(val v: Double, val act: Boolean)

    fun seatBluff(st: GState, players: Map<String, Player>, s: Seat): SeatBluff {
        val p = players[s.pid] ?: return SeatBluff(0.25, false)
        val m = model(st, players, s)
        return if (m.br != null) SeatBluff(m.br.v, true) else SeatBluff(baseBluff(p), false)
    }

    /** "Nízká/Střední/Vysoká" + colour index 0 green, 1 gold, 2 red */
    fun lvl(v: Double): Pair<String, Int> = when {
        v < 0.15 -> L("Nízká", "Low", "Niedrig") to 0
        v < 0.35 -> L("Střední", "Medium", "Mittel") to 1
        else -> L("Vysoká", "High", "Hoch") to 2
    }

    data class SimRes(val n: Int, val win: Double, val tie: Double, val eq: Double, val allFolded: Boolean = false)

    /** Monte Carlo equity of the hero against the live opponents, weighting their hole cards by the read model. */
    fun simulate(st: GState, players: Map<String, Player>, useModel: Boolean, budgetMs: Long, maxIt: Int): SimRes {
        val hero = st.hero.map { it!! }
        val bd = boardKnown(st)
        val known = HashSet<Int>().apply { addAll(hero); addAll(bd) }
        val deck = (0 until 52).filter { it !in known }.toIntArray()
        val d = deck.size
        val lv = st.seats.drop(1).filter { !it.folded }
        if (lv.isEmpty()) return SimRes(0, 1.0, 0.0, 1.0, true)
        val k = d * (d - 1) / 2
        val ca = IntArray(k); val cb = IntArray(k)
        run {
            var x = 0
            for (i in 0 until d) for (j in i + 1 until d) { ca[x] = i; cb[x] = j; x++ }
        }
        val e = Cards.Eval()
        val bdArr = bd.toIntArray()
        var pct: FloatArray? = null
        var hs: FloatArray? = null
        if (useModel) {
            pct = FloatArray(k); hs = FloatArray(k)
            for (i in 0 until k) {
                val a = deck[ca[i]]; val b = deck[cb[i]]
                pct[i] = Cards.pctOf(a, b)
                hs[i] = if (bd.size >= 3) Cards.handStr(e, a, b, bdArr) else 1f
            }
        }
        val cums = lv.map { s ->
            val cum = DoubleArray(k)
            var t = 0.0
            val m = if (useModel) model(st, players, s) else null
            for (i in 0 until k) {
                var w = 1.0
                if (m != null) {
                    val pr = if (pct!![i] <= m.w) 1.0 else maxOf(0.0, 1 - (pct[i] - m.w) / 0.08)
                    val po = if (bd.size >= 3 && m.p > 0) m.b + (1 - m.b) * (1 - m.p + m.p * hs!![i]) else 1.0
                    w = pr * po + 1e-6
                }
                t += w
                cum[i] = t
            }
            cum
        }
        val used = IntArray(d)
        var it = 0; var win = 0; var tie = 0; var share = 0.0
        val need = 5 - bd.size
        val hv = IntArray(7); hv[0] = hero[0]; hv[1] = hero[1]
        val ov = IntArray(7)
        val brd = IntArray(5)
        for (i in bd.indices) brd[i] = bd[i]
        val holes = IntArray(lv.size * 2)
        val rnd = Random(System.nanoTime())
        val t0 = System.currentTimeMillis()
        while (it < maxIt) {
            val stamp = it + 1
            var hn = 0
            for (cum in cums) {
                val tot = cum[k - 1]
                var got = -1
                for (tr in 0 until 60) {
                    val r = rnd.nextDouble() * tot
                    var lo = 0; var hi = k - 1
                    while (lo < hi) {
                        val md = (lo + hi) ushr 1
                        if (cum[md] < r) lo = md + 1 else hi = md
                    }
                    if (used[ca[lo]] != stamp && used[cb[lo]] != stamp) { got = lo; break }
                }
                if (got < 0) {
                    var a: Int
                    do { a = rnd.nextInt(d) } while (used[a] == stamp)
                    used[a] = stamp
                    var b: Int
                    do { b = rnd.nextInt(d) } while (used[b] == stamp)
                    used[b] = stamp
                    holes[hn++] = deck[a]; holes[hn++] = deck[b]
                } else {
                    used[ca[got]] = stamp; used[cb[got]] = stamp
                    holes[hn++] = deck[ca[got]]; holes[hn++] = deck[cb[got]]
                }
            }
            for (i in 0 until need) {
                var a: Int
                do { a = rnd.nextInt(d) } while (used[a] == stamp)
                used[a] = stamp
                brd[bd.size + i] = deck[a]
            }
            for (i in 0 until 5) { hv[2 + i] = brd[i]; ov[2 + i] = brd[i] }
            val h0 = e.ev(hv)
            var best = -1; var cnt = 0
            var i = 0
            while (i < hn) {
                ov[0] = holes[i]; ov[1] = holes[i + 1]
                val s = e.ev(ov)
                if (s > best) { best = s; cnt = 1 } else if (s == best) cnt++
                i += 2
            }
            if (h0 > best) win++ else if (h0 == best) { tie++; share += 1.0 / (cnt + 1) }
            it++
            if ((it and 255) == 0 && System.currentTimeMillis() - t0 > budgetMs) break
        }
        return SimRes(it, win.toDouble() / it, tie.toDouble() / it, (win + share) / it)
    }

    class Tally(val by: IntArray, val n: Int, val imp: Int)
    class NextView(val key: String, val t: Tally, val outs: Boolean) {
        val name: String
            get() = when (key) {
                "flop" -> L("Na flopu", "On the flop", "Auf dem Flop")
                "next" -> L("Další karta", "Next card", "Nächste Karte")
                else -> L("Do riveru", "To the river", "Bis zum River")
            }
    }
    class NextStats(val msg: String? = null, val views: List<NextView> = emptyList())

    fun nextStats(st: GState): NextStats {
        if (st.hero.contains(null)) return NextStats(L("Zadej své dvě karty dole u stolu.", "Enter your two cards at the table.", "Gib deine zwei Karten am Tisch ein."))
        val hero = st.hero.map { it!! }
        val bd = boardKnown(st)
        if (bd.size == 5) return NextStats(L("Všech pět karet je na stole.", "All five cards are on the board.", "Alle fünf Karten liegen auf dem Tisch."))
        if (bd.isNotEmpty() && bd.size < 3) return NextStats(L("Dozadávej flop (3 karty).", "Finish entering the flop (3 cards).", "Gib den Flop fertig ein (3 Karten)."))
        val known = HashSet<Int>().apply { addAll(hero); addAll(bd) }
        val deck = (0 until 52).filter { it !in known }
        val e = Cards.Eval()
        val base = (hero + bd).toIntArray()
        val cur = Cards.cat(e.ev(base))
        val buf = IntArray(base.size + 3)
        val bbuf = IntArray(bd.size + 3)
        base.copyInto(buf); bd.toIntArray().copyInto(bbuf)
        fun tally(gen: ((IntArray, Int) -> Unit) -> Unit): Tally {
            val by = IntArray(9)
            var n = 0; var imp = 0
            gen { add, len ->
                n++
                for (j in 0 until len) { buf[base.size + j] = add[j]; bbuf[bd.size + j] = add[j] }
                val c = Cards.cat(e.ev(buf, base.size + len))
                val bc = Cards.cat(e.ev(bbuf, bd.size + len))
                if (c > cur && c > bc) { by[c]++; imp++ }
            }
            return Tally(by, n, imp)
        }
        val views = ArrayList<NextView>()
        val a = IntArray(3)
        if (bd.isEmpty()) {
            views.add(NextView("flop", tally { f ->
                for (i in deck.indices) for (j in i + 1 until deck.size) for (q in j + 1 until deck.size) {
                    a[0] = deck[i]; a[1] = deck[j]; a[2] = deck[q]; f(a, 3)
                }
            }, false))
        } else {
            views.add(NextView("next", tally { f -> for (c in deck) { a[0] = c; f(a, 1) } }, true))
            if (bd.size == 3) views.add(NextView("river", tally { f ->
                for (i in deck.indices) for (j in i + 1 until deck.size) { a[0] = deck[i]; a[1] = deck[j]; f(a, 2) }
            }, false))
        }
        return NextStats(null, views)
    }

    @Suppress("unused")
    private fun lang() = I18n.lang
}
