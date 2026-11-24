package com.onoseto.chipsnfish

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.ui.AppRoot
import com.onoseto.chipsnfish.ui.Sound
import com.onoseto.chipsnfish.ui.Ui

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.load(this)
        Game.init(this)
        Game.onChipSound = { Sound.chip() }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideBars()
        setContent {
            AppRoot(
                onExit = { Game.saveLocal(); finish() },
                setKeepOn = { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                },
            )
        }
    }

    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onPause() {
        super.onPause()
        Game.screenActive = false
        Game.gameTick()
        Game.saveLocal()
    }

    override fun onResume() {
        super.onResume()
        Ui.refreshActive()
    }
}
