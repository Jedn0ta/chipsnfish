package com.onoseto.chipsnfish

import com.onoseto.chipsnfish.game.Cards
import com.onoseto.chipsnfish.game.GState
import com.onoseto.chipsnfish.game.Player
import com.onoseto.chipsnfish.game.Reads
import com.onoseto.chipsnfish.game.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun c(s: String): Int {
        val r = Cards.RANKS.indexOf(s[0])
        val su = "shdc".indexOf(s[1])
        return r * 4 + su
    }
    private fun ev(vararg cs: String) = Cards.ev(cs.map { c(it) })

    @Test fun categories() {
        assertEquals(8, Cards.cat(ev("As", "Ks", "Qs", "Js", "Ts", "2d", "3c")))
        assertEquals(7, Cards.cat(ev("9s", "9h", "9d", "9c", "2s", "3d", "4c")))
        assertEquals(6, Cards.cat(ev("9s", "9h", "9d", "2c", "2s", "3d", "4c")))
        assertEquals(5, Cards.cat(ev("As", "8s", "6s", "4s", "2s", "3d", "4c")))
        assertEquals(4, Cards.cat(ev("As", "2h", "3d", "4c", "5s", "9d", "Kc")))
        assertEquals(3, Cards.cat(ev("7s", "7h", "7d", "Kc", "2s", "9d", "4c")))
        assertEquals(2, Cards.cat(ev("7s", "7h", "Kd", "Kc", "2s", "9d", "4c")))
        assertEquals(1, Cards.cat(ev("7s", "7h", "Qd", "Kc", "2s", "9d", "4c")))
        assertEquals(0, Cards.cat(ev("7s", "8h", "Qd", "Kc", "2s", "9d", "4c")))
    }

    @Test fun kickers() {
        assertTrue(ev("As", "Ah", "Kd", "7c", "5s", "3d", "2c") > ev("As", "Ah", "Qd", "7c", "5s", "3d", "2c"))
        assertEquals(ev("As", "Kh", "Qd", "Jc", "Ts", "3d", "2c"), ev("Ad", "Kc", "Qh", "Js", "Tc", "3h", "2s"))
    }

    @Test fun pctTable() {
        assertTrue(Cards.pctOf(c("As"), c("Ah")) < 0.01f)
        assertTrue(Cards.pctOf(c("7s"), c("2h")) > 0.9f)
    }

    @Test fun equityAces() {
        val st = GState(n = 2, phase = "play")
        st.seats = mutableListOf(Seat("hero", 100), Seat("v", 100))
        st.hero = mutableListOf(c("As"), c("Ah"))
        val players = mapOf("hero" to Player("hero", "Ty"), "v" to Player("v", "V"))
        val r = Reads.simulate(st, players, false, 2000, 20000)
        assertTrue("AA vs random ${r.eq}", r.eq in 0.82..0.88)
    }
}
