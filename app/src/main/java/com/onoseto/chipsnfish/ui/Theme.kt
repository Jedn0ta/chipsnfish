package com.onoseto.chipsnfish.ui

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object C {
    val room = Color(0xFF080D12)
    val room2 = Color(0xFF15222B)
    val panel = Color(0xFF121C23)
    val panel2 = Color(0xFF1A2832)
    val line = Color(0xFF2B3D49)
    val ink = Color(0xFFF1F4F2)
    val muted = Color(0xFF9AABB3)
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
    val seatLine = Color(0xFF3A4F5C)

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

    val FELTS = linkedMapOf(
        "green" to listOf(0xFF2A9A62, 0xFF1E7F4F, 0xFF0F5233, 0xFF0B3B25),
        "red" to listOf(0xFFB23A3A, 0xFF8E2323, 0xFF541010, 0xFF3D0A0A),
        "blue" to listOf(0xFF2F6FB5, 0xFF1F5590, 0xFF0F2F55, 0xFF0A2240),
        "purple" to listOf(0xFF7A4BB0, 0xFF5C338C, 0xFF341B55, 0xFF25123E),
        "gold" to listOf(0xFFC99A3A, 0xFFA8781F, 0xFF5E420C, 0xFF3F2C07),
        "black" to listOf(0xFF3A3F44, 0xFF26292D, 0xFF121416, 0xFF000000),
    )
    val FELT_NAMES = mapOf("green" to "Zelená", "red" to "Rudá", "blue" to "Modrá", "purple" to "Fialová", "gold" to "Zlatá", "black" to "Černá")
    val BACKS = linkedMapOf("red" to "Rudý", "blue" to "Modrý", "black" to "Černý", "fish" to "Rybí")
    val FACES = linkedMapOf("classic" to "Klasický", "jumbo" to "Velké znaky", "four" to "Čtyřbarevný", "picture" to "Obrázkový")
}

object F {
    /** Condensed bold numbers (stands in for Saira Condensed). */
    val num: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    val body: FontFamily = FontFamily.SansSerif
}
