package com.onoseto.chipsnfish.ui

import kotlin.math.roundToInt
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object C {
    val room = Color(0xFF050B1A)
    val room2 = Color(0xFF0D1B36)
    val panel = Color(0xFF0E1A33)
    val panel2 = Color(0xFF16264A)
    val line = Color(0xFF2A4170)
    val ink = Color(0xFFF1F4F2)
    val muted = Color(0xFFA3B6D6)
    val gold = Color(0xFFF2C14E)
    val gold2 = Color(0xFFC8901D)
    val goldInk = Color(0xFF2B1C02)
    val cardRed = Color(0xFFD3121D)
    val cardBlk = Color(0xFF121212)
    val win = Color(0xFF3FCF7A)
    val lose = Color(0xFFEF5350)
    val call = Color(0xFF22A35A)
    val fold = Color(0xFFC0392B)
    val leather = Color(0xFF3B2216)
    val leather2 = Color(0xFF1C0F09)
    val leatherHi = Color(0xFF56331F)
    val stitch = Color(0x59F0C88C)
    val seatLine = Color(0xFF3A5A8C)

    fun type(t: String): Color = when (t) {
        "rock" -> Color(0xFF8FB4E8)
        "tag" -> Color(0xFF3FCF7A)
        "lag" -> Color(0xFFFF9A3C)
        "station" -> Color(0xFFF2D14E)
        "maniac" -> Color(0xFFFF5A6E)
        else -> Color(0xFF9AABB3)
    }

    /** 0 green, 1 gold, 2 red */
    fun lvl(i: Int) = when (i) { 0 -> win; 1 -> gold; else -> lose }
    fun eqCol(e: Double) = if (e >= 0.5) win else if (e >= 0.3) gold else lose

    /** Velvet colours: light (lamp), mid, dark, edge. */
    val FELTS = linkedMapOf(
        "blue" to listOf(0xFF3F82D4, 0xFF1D5799, 0xFF0B2B57, 0xFF061A36),
        "green" to listOf(0xFF33A86E, 0xFF187A49, 0xFF0A4A2A, 0xFF05301B),
        "red" to listOf(0xFFB8364D, 0xFF861A30, 0xFF4D0A18, 0xFF32060F),
        "purple" to listOf(0xFF8A57C4, 0xFF5E3496, 0xFF33195A, 0xFF22103D),
        "gold" to listOf(0xFFC9A04A, 0xFF9C7425, 0xFF5A400E, 0xFF3C2A08),
        "black" to listOf(0xFF44494F, 0xFF26292D, 0xFF121416, 0xFF050607),
    )
    val FELT_NAMES = mapOf("green" to "Zelená", "red" to "Vínová", "blue" to "Modrá", "purple" to "Fialová", "gold" to "Zlatá", "black" to "Černá")
    /** Back designs; each has colour variants (swipe up / down over the preview in settings). */
    val BACK_DESIGNS = linkedMapOf(
        "lattice" to listOf("b_lattice", "b_lattice_blue"),
        "medal" to listOf("cred", "cblue"),
        "rosette" to listOf("b_rosette", "b_rosette_blue"),
        "dots" to listOf("b_red", "b_blue", "b_green", "b_black", "b_maroon", "b_navy"),
        "diamond" to listOf("red", "blue", "green", "black", "gold"),
        "fish" to listOf("fish"),
    )
    val BACK_NAMES = mapOf("lattice" to "Mřížka", "medal" to "Klasický", "rosette" to "Rozeta", "dots" to "Tečky", "diamond" to "Diamant", "fish" to "Rybí")
    /** Every back key, for checking stored choices. */
    val BACKS: Set<String> = BACK_DESIGNS.values.flatten().toSet()
    fun backDesign(key: String) = BACK_DESIGNS.entries.firstOrNull { key in it.value }?.key ?: "lattice"
    val FACES = linkedMapOf("casino" to "Kasino", "picture" to "Anglické", "french" to "Francouzské", "classic" to "Velké indexy", "jumbo" to "Extra velké", "four" to "Čtyřbarevné")

    /** Suggested table colours: hue, intensity (0 = charcoal). */
    val FELT_PRESETS = listOf(150f to 1f, 213f to 1f, 350f to 1f, 268f to 1f, 41f to 1f, 213f to 0f)

    /** Felt key is a preset name or "h:<hue>:<intensity>:<tone>" (tone 0 dark … 1 light, 0.5 = standard). */
    fun feltHue(key: String): Triple<Float, Float, Float> {
        if (key.startsWith("h:")) {
            val p = key.split(":")
            return Triple((p.getOrNull(1)?.toFloatOrNull() ?: 213f).coerceIn(0f, 360f), (p.getOrNull(2)?.toFloatOrNull() ?: 1f).coerceIn(0f, 1f), (p.getOrNull(3)?.toFloatOrNull() ?: 0.5f).coerceIn(0f, 1f))
        }
        val (h, i) = when (key) { "green" -> 150f to 1f; "red" -> 350f to 1f; "purple" -> 268f to 1f; "gold" -> 41f to 1f; "black" -> 213f to 0f; else -> 213f to 1f }
        return Triple(h, i, 0.5f)
    }

    fun feltKey(h: Float, i: Float, t: Float = 0.5f) = "h:${h.roundToInt()}:${(i * 100).roundToInt() / 100f}:${(t * 100).roundToInt() / 100f}"

    /** Light (lamp), mid, dark, edge — generated from hue, intensity and tone so any colour works. */
    fun felt(key: String): List<Long> {
        val (h, i, t) = feltHue(key)
        val vs = (0.4f + 0.6f * i) * (0.55f + 0.9f * t)
        fun c(s: Float, v: Float) = android.graphics.Color.HSVToColor(floatArrayOf(h, s * i, (v * vs).coerceAtMost(1f))).toLong() and 0xFFFFFFFFL
        return listOf(c(0.70f, 0.83f), c(0.81f, 0.60f), c(0.87f, 0.34f), c(0.89f, 0.21f))
    }

}

object F {
    /** Condensed bold numbers (stands in for Saira Condensed). */
    val num: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    val body: FontFamily = FontFamily.SansSerif
}
