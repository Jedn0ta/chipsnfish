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
    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.load(this)
        Game.init(this)
        Game.onChipSound = { Sound.chip() }
        com.onoseto.chipsnfish.online.Supa.init(this)
        com.onoseto.chipsnfish.online.KeepAlive.schedule(this)
        handleLink(intent)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** https://onoseto.com/cf/?t=CODE · ?g=GROUP · chipsnfish://t/CODE · chipsnfish://g/GROUP */
    private fun handleLink(i: android.content.Intent?) {
        val u = i?.data ?: return
        val t = u.getQueryParameter("t") ?: if (u.scheme == "chipsnfish" && u.host == "t") u.lastPathSegment else null
        val g = u.getQueryParameter("g") ?: if (u.scheme == "chipsnfish" && u.host == "g") u.lastPathSegment else null
        when {
            !t.isNullOrBlank() -> Ui.deep = "t" to t.trim()
            !g.isNullOrBlank() -> Ui.deep = "g" to g.trim()
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
        com.onoseto.chipsnfish.online.Online.stopPolling()
        com.onoseto.chipsnfish.online.Nearby.stop()
        com.onoseto.chipsnfish.online.Online.stopInbox()
    }

    override fun onResume() {
        super.onResume()
        Ui.refreshActive()
        com.onoseto.chipsnfish.online.KeepAlive.opened(this)
        Updater.check(this, quiet = true)
        if (com.onoseto.chipsnfish.online.SupaCfg.configured) {
            com.onoseto.chipsnfish.online.Nearby.start(this)
            com.onoseto.chipsnfish.online.Online.startInbox()
            com.onoseto.chipsnfish.online.Online.loadFriends()
            com.onoseto.chipsnfish.online.Social.loadBank()
        }
        if (Game.online()) com.onoseto.chipsnfish.online.Online.startPolling()
    }
}
