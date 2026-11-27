package com.onoseto.chipsnfish.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.onoseto.chipsnfish.R

/**
 * Traditional English-pattern card faces (public-domain artwork by Byron Knoll, "Vector Playing Cards").
 * Index = card int (rank * 4 + suit, suits ♠ ♥ ♦ ♣).
 */
object CardImg {
    val IDS = intArrayOf(
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
    private val cache = HashMap<Int, ImageBitmap>()

    @Composable
    fun bitmap(c: Int): ImageBitmap {
        val res = LocalContext.current.resources
        return cache.getOrPut(c) {
            BitmapFactory.decodeResource(res, IDS[c]).apply { setHasMipMap(true) }.asImageBitmap()
        }
    }
}
