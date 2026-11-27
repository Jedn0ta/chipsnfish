package com.onoseto.chipsnfish.ui

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
    val BACKS = linkedMapOf("red" to "Rudý", "blue" to "Modrý", "black" to "Černý", "fish" to "Rybí")
    val FACES = linkedMapOf("classic" to "Klasický", "jumbo" to "Velké znaky", "four" to "Čtyřbarevný", "picture" to "Obrázkový")
}

object F {
    /** Condensed bold numbers (stands in for Saira Condensed). */
    val num: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    val body: FontFamily = FontFamily.SansSerif
}
