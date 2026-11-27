package com.onoseto.chipsnfish.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.onoseto.chipsnfish.R

/**
 * Real card artwork (no drawn cards):
 *  - "picture": traditional English pattern, Vector Playing Cards by Byron Knoll (public domain)
 *  - "french":  French portrait pattern, SVG-cards by David Bellot (LGPL 2.1, licence in assets)
 * Index = card int (rank * 4 + suit, suits ♠ ♥ ♦ ♣).
 */
object CardImg {
    val DECKS = setOf("picture", "french")
    private val PICTURE = intArrayOf(
        R.drawable.card_2s, R.drawable.card_2h, R.drawable.card_2d, R.drawable.card_2c,
        R.drawable.card_3s, R.drawable.card_3h, R.drawable.card_3d, R.drawable.card_3c,
        R.drawable.card_4s, R.drawable.card_4h, R.drawable.card_4d, R.drawable.card_4c,
        R.drawable.card_5s, R.drawable.card_5h, R.drawable.card_5d, R.drawable.card_5c,
        R.drawable.card_6s, R.drawable.card_6h, R.drawable.card_6d, R.drawable.card_6c,
        R.drawable.card_7s, R.drawable.card_7h, R.drawable.card_7d, R.drawable.card_7c,
        R.drawable.card_8s, R.drawable.card_8h, R.drawable.card_8d, R.drawable.card_8c,
        R.drawable.card_9s, R.drawable.card_9h, R.drawable.card_9d, R.drawable.card_9c,
        R.drawable.card_ts, R.drawable.card_th, R.drawable.card_td, R.drawable.card_tc,
        R.drawable.card_js, R.drawable.card_jh, R.drawable.card_jd, R.drawable.card_jc,
        R.drawable.card_qs, R.drawable.card_qh, R.drawable.card_qd, R.drawable.card_qc,
        R.drawable.card_ks, R.drawable.card_kh, R.drawable.card_kd, R.drawable.card_kc,
        R.drawable.card_as, R.drawable.card_ah, R.drawable.card_ad, R.drawable.card_ac,
    )
    private val FRENCH = intArrayOf(
        R.drawable.card2_2s, R.drawable.card2_2h, R.drawable.card2_2d, R.drawable.card2_2c,
        R.drawable.card2_3s, R.drawable.card2_3h, R.drawable.card2_3d, R.drawable.card2_3c,
        R.drawable.card2_4s, R.drawable.card2_4h, R.drawable.card2_4d, R.drawable.card2_4c,
        R.drawable.card2_5s, R.drawable.card2_5h, R.drawable.card2_5d, R.drawable.card2_5c,
        R.drawable.card2_6s, R.drawable.card2_6h, R.drawable.card2_6d, R.drawable.card2_6c,
        R.drawable.card2_7s, R.drawable.card2_7h, R.drawable.card2_7d, R.drawable.card2_7c,
        R.drawable.card2_8s, R.drawable.card2_8h, R.drawable.card2_8d, R.drawable.card2_8c,
        R.drawable.card2_9s, R.drawable.card2_9h, R.drawable.card2_9d, R.drawable.card2_9c,
        R.drawable.card2_ts, R.drawable.card2_th, R.drawable.card2_td, R.drawable.card2_tc,
        R.drawable.card2_js, R.drawable.card2_jh, R.drawable.card2_jd, R.drawable.card2_jc,
        R.drawable.card2_qs, R.drawable.card2_qh, R.drawable.card2_qd, R.drawable.card2_qc,
        R.drawable.card2_ks, R.drawable.card2_kh, R.drawable.card2_kd, R.drawable.card2_kc,
        R.drawable.card2_as, R.drawable.card2_ah, R.drawable.card2_ad, R.drawable.card2_ac,
    )
    val BACKS = mapOf(
        "red" to R.drawable.back_red, "blue" to R.drawable.back_blue, "green" to R.drawable.back_green,
        "black" to R.drawable.back_black, "maroon" to R.drawable.back_maroon, "navy" to R.drawable.back_navy,
    )
    private val cache = HashMap<Int, ImageBitmap>()

    @Composable
    fun res(id: Int): ImageBitmap {
        val r = LocalContext.current.resources
        return cache.getOrPut(id) { BitmapFactory.decodeResource(r, id).apply { setHasMipMap(true) }.asImageBitmap() }
    }

    @Composable
    fun bitmap(c: Int, deck: String = "picture"): ImageBitmap = res(if (deck == "french") FRENCH[c] else PICTURE[c])

    @Composable
    fun back(key: String): ImageBitmap = res(BACKS[key] ?: R.drawable.back_red)
}
