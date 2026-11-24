package com.onoseto.chipsnfish.game

/**
 * Cards are ints 0..51: rank = c shr 2 (0 = "2" … 12 = "A"), suit = c and 3 (0 ♠, 1 ♥, 2 ♦, 3 ♣).
 * Port of the evaluator and hand tables from the original Chips&Fish web app.
 */
object Cards {
    const val RANKS = "23456789TJQKA"
    val SYM = arrayOf("♠", "♥", "♦", "♣")
    val SNAME = arrayOf("piky", "srdce", "kára", "kříže")
    val RED = booleanArrayOf(false, true, true, false)
    val CAT = arrayOf("Vysoká karta", "Pár", "Dva páry", "Trojice", "Postupka", "Barva", "Full house", "Čtveřice", "Postupka v barvě")
    val STN = arrayOf("Preflop", "Flop", "Turn", "River")
    val STN4 = arrayOf("preflopu", "flopu", "turnu", "riveru")
    val LEVELS = arrayOf(
        intArrayOf(1, 2), intArrayOf(2, 4), intArrayOf(3, 6), intArrayOf(4, 8), intArrayOf(5, 10),
        intArrayOf(10, 20), intArrayOf(15, 30), intArrayOf(20, 40), intArrayOf(25, 50), intArrayOf(50, 100),
    )

    fun lvl(i: Int) = LEVELS[i.coerceIn(0, LEVELS.size - 1)]
    fun lvlStr(i: Int) = lvl(i).let { "${it[0]}/${it[1]}" }

    fun rl(r: Int): String = if (r == 8) "10" else RANKS[r].toString()
    fun rank(c: Int) = c shr 2
    fun suit(c: Int) = c and 3
    fun cat(score: Int) = score shr 20

    /** Evaluator with its own buffers so it can run on a background thread. */
    class Eval {
        private val rc = IntArray(13)
        private val sc = IntArray(4)
        private val sm = IntArray(4)

        private fun straightHigh(m: Int): Int {
            val e = (m shl 1) or ((m shr 12) and 1)
            for (h in 13 downTo 4) if (((e shr (h - 4)) and 31) == 31) return h - 1
            return -1
        }

        private fun kick(mask: Int, n: Int, sft0: Int): Int {
            var v = 0
            var c = 0
            var sft = sft0
            var r = 12
            while (r >= 0 && c < n) {
                if ((mask shr r) and 1 == 1) {
                    v = v or (r shl sft); sft -= 4; c++
                }
                r--
            }
            return v
        }

        fun ev(cs: IntArray, len: Int = cs.size): Int {
            rc.fill(0); sc.fill(0); sm.fill(0)
            var mask = 0
            for (i in 0 until len) {
                val c = cs[i]
                val r = c shr 2
                val s = c and 3
                rc[r]++; sc[s]++
                sm[s] = sm[s] or (1 shl r)
                mask = mask or (1 shl r)
            }
            for (s in 0 until 4) if (sc[s] >= 5) {
                val sh = straightHigh(sm[s])
                if (sh >= 0) return (8 shl 20) or (sh shl 16)
                return (5 shl 20) or kick(sm[s], 5, 16)
            }
            var q = -1; var t1 = -1; var t2 = -1; var p1 = -1; var p2 = -1
            for (r in 12 downTo 0) {
                when (rc[r]) {
                    4 -> q = r
                    3 -> if (t1 < 0) t1 = r else if (t2 < 0) t2 = r
                    2 -> if (p1 < 0) p1 = r else if (p2 < 0) p2 = r
                }
            }
            if (q >= 0) return (7 shl 20) or (q shl 16) or kick(mask and (1 shl q).inv(), 1, 12)
            if (t1 >= 0 && (t2 >= 0 || p1 >= 0)) return (6 shl 20) or (t1 shl 16) or (maxOf(t2, p1) shl 12)
            val sh = straightHigh(mask)
            if (sh >= 0) return (4 shl 20) or (sh shl 16)
            if (t1 >= 0) return (3 shl 20) or (t1 shl 16) or kick(mask and (1 shl t1).inv(), 2, 12)
            if (p2 >= 0) return (2 shl 20) or (p1 shl 16) or (p2 shl 12) or kick(mask and (1 shl p1).inv() and (1 shl p2).inv(), 1, 8)
            if (p1 >= 0) return (1 shl 20) or (p1 shl 16) or kick(mask and (1 shl p1).inv(), 3, 12)
            return kick(mask, 5, 16)
        }
    }

    private val mainEval = Eval()

    /** Main-thread convenience. */
    fun ev(cards: List<Int>): Int = synchronized(mainEval) { mainEval.ev(cards.toIntArray()) }

    /** Starting-hand percentile table (top x % of 1326 combos). */
    private val PCT = FloatArray(338).also { pct ->
        fun pts(r: Int): Double = when (r) { 12 -> 10.0; 11 -> 8.0; 10 -> 7.0; 9 -> 6.0; else -> (r + 2) / 2.0 }
        data class E(val k: Int, val c: Int, val s: Double, val t: Int)
        val l = ArrayList<E>()
        for (hi in 0 until 13) for (lo in 0..hi) {
            if (hi == lo) {
                l.add(E(hi * 13 + lo, 6, maxOf(5.0, pts(hi) * 2), hi * 2)); continue
            }
            for (su in intArrayOf(1, 0)) {
                var s = pts(hi) + if (su == 1) 2 else 0
                val g = hi - lo - 1
                s -= when (g) { 0 -> 0; 1 -> 1; 2 -> 2; 3 -> 4; else -> 5 }
                if (g <= 1 && hi < 10) s += 1
                l.add(E((if (su == 1) 169 else 0) + hi * 13 + lo, if (su == 1) 4 else 12, s, hi + lo))
            }
        }
        l.sortWith(compareByDescending<E> { it.s }.thenByDescending { it.t })
        var cum = 0
        for (x in l) { cum += x.c; pct[x.k] = cum / 1326f }
    }

    fun pctOf(a: Int, b: Int): Float {
        val r1 = a shr 2; val r2 = b shr 2
        val hi = maxOf(r1, r2); val lo = minOf(r1, r2)
        val su = r1 != r2 && (a and 3) == (b and 3)
        return PCT[(if (su) 169 else 0) + hi * 13 + lo]
    }

    private fun bitCount5(w0: Int): Int = Integer.bitCount(w0 and 31)

    fun handStr(e: Eval, a: Int, b: Int, board: IntArray): Float {
        if (board.size < 3) return 1f
        val all = IntArray(board.size + 2); all[0] = a; all[1] = b; board.copyInto(all, 2)
        val sc = e.ev(all)
        val c = cat(sc)
        val bc = cat(e.ev(board))
        if (c > bc) {
            if (c >= 4) return 1f
            if (c == 3) return 0.9f
            if (c == 2) return 0.78f
            val pr = (sc shr 16) and 15
            val top = board.maxOf { it shr 2 }
            if (a shr 2 == b shr 2 && a shr 2 > top) return 0.7f
            if (pr >= top) return 0.6f
            return 0.42f
        }
        var fd = false; var sd = false
        if (board.size < 5) {
            for (s in 0 until 4) {
                val n = all.count { (it and 3) == s }
                if (n >= 4 && ((a and 3) == s || (b and 3) == s)) fd = true
            }
            var m = 0
            for (x in all) m = m or (1 shl (x shr 2))
            val e2 = (m shl 1) or ((m shr 12) and 1)
            for (st in 0..9) if (bitCount5(e2 shr st) >= 4) { sd = true; break }
        }
        if (fd && sd) return 0.5f
        if (fd) return 0.38f
        if (sd) return 0.3f
        return if (a shr 2 == 12 || b shr 2 == 12) 0.16f else 0.08f
    }

    data class Tex(val ms: Int, val sw: Int, val paired: Boolean, val top: Int)

    fun tex(cs: List<Int>): Tex {
        var ms = 0
        for (s in 0 until 4) ms = maxOf(ms, cs.count { (it and 3) == s })
        var m = 0
        for (x in cs) m = m or (1 shl (x shr 2))
        val e = (m shl 1) or ((m shr 12) and 1)
        var sw = 0
        for (st in 0..9) sw = maxOf(sw, bitCount5(e shr st))
        val ranks = cs.map { it shr 2 }
        return Tex(ms, sw, ranks.toSet().size < ranks.size, ranks.maxOrNull() ?: 0)
    }

    /** "AKs", "T9o", "77" */
    fun hcls(a: Int, b: Int): String {
        val r1 = a shr 2; val r2 = b shr 2
        val hi = maxOf(r1, r2); val lo = minOf(r1, r2)
        return rl(hi) + rl(lo) + if (hi == lo) "" else if ((a and 3) == (b and 3)) "s" else "o"
    }

    /** Hand name like "Dva páry K + 7", "Postupka do 9", "Královská postupka". Czech base, translated by caller. */
    fun handName(sc: Int): Pair<Int, String> {
        val c = cat(sc); val r = (sc shr 16) and 15
        val d = when {
            c == 8 && r == 12 -> "Královská postupka"
            c == 4 || c == 8 -> CAT[c] + " do " + rl(r)
            c == 2 || c == 6 -> CAT[c] + " " + rl(r) + " + " + rl((sc shr 12) and 15)
            else -> CAT[c] + " " + rl(r)
        }
        return c to d
    }
}
