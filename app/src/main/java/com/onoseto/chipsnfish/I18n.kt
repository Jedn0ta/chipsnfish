package com.onoseto.chipsnfish

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Czech is the source language (as in the original app). English and German come from the
 * original translation table (res/raw/i18n.json) or from inline L(cs, en, de) templates.
 */
object I18n {
    var lang: String = "cs"
    private var dict: Map<String, Pair<String?, String?>> = emptyMap()

    fun load(ctx: Context) {
        if (dict.isNotEmpty()) return
        val txt = ctx.resources.openRawResource(R.raw.i18n).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val o = Json.parseToJsonElement(txt) as JsonObject
        dict = o.mapValues { (_, v) ->
            val a = v as JsonArray
            fun s(i: Int): String? = (a.getOrNull(i) as? JsonPrimitive)?.let { if (it.isString) it.content else null }
            s(0) to s(1)
        }
    }

    fun tr(cs: String): String {
        if (lang == "cs") return cs
        val k = cs.trim()
        val d = dict[k] ?: return cs
        val t = if (lang == "en") d.first else d.second
        return if (t == null) cs else cs.replace(k, t)
    }

    fun L(cs: String, en: String, de: String): String = when (lang) { "en" -> en; "de" -> de; else -> cs }

    val locale: java.util.Locale
        get() = when (lang) { "en" -> java.util.Locale.US; "de" -> java.util.Locale.GERMANY; else -> java.util.Locale("cs", "CZ") }

    private val CAT_T = mapOf(
        "Vysoká karta" to ("High card" to "Höchste Karte"),
        "Pár" to ("Pair" to "Paar"),
        "Dva páry" to ("Two pair" to "Zwei Paare"),
        "Trojice" to ("Three of a kind" to "Drilling"),
        "Postupka" to ("Straight" to "Straße"),
        "Barva" to ("Flush" to "Flush"),
        "Full house" to ("Full house" to "Full House"),
        "Čtveřice" to ("Four of a kind" to "Vierling"),
        "Postupka v barvě" to ("Straight flush" to "Straight Flush"),
        "Královská postupka" to ("Royal flush" to "Royal Flush"),
    )

    /** Translate a hand-category phrase like "Postupka do 9" or "Dva páry K + 7". */
    fun cat(s: String): String {
        if (lang == "cs") return s
        val key = CAT_T.keys.sortedByDescending { it.length }.firstOrNull { s.startsWith(it) } ?: return s
        val t = CAT_T.getValue(key).let { if (lang == "en") it.first else it.second }
        val rest = s.substring(key.length).replace(" do ", if (lang == "en") " to " else " bis ")
        return t + rest
    }

    fun stn4(i: Int): String = when (lang) {
        "en" -> arrayOf("preflop", "flop", "turn", "river")[i]
        "de" -> arrayOf("Preflop", "Flop", "Turn", "River")[i]
        else -> com.onoseto.chipsnfish.game.Cards.STN4[i]
    }
}

fun tr(cs: String) = I18n.tr(cs)
fun L(cs: String, en: String, de: String) = I18n.L(cs, en, de)
