package com.daboprint3dtech.blink.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.daboprint3dtech.blink.BlinkApp
import com.daboprint3dtech.blink.MainActivity
import com.daboprint3dtech.blink.R
import com.daboprint3dtech.blink.data.auth.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Tile delle impostazioni rapide: arma/disarma al volo tutti i sistemi Blink.
 * Lo stato riflette se tutti i sistemi sono armati.
 */
class ArmTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val container get() = (applicationContext as BlinkApp).container

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            container.auth.init()
            if (container.auth.state.value is AuthState.LoggedIn) {
                runCatching { container.repository.refresh(maxAge = java.time.Duration.ofSeconds(30)) }
            }
            render()
        }
    }

    override fun onClick() {
        super.onClick()
        if (container.auth.state.value !is AuthState.LoggedIn) {
            openApp()
            return
        }
        val target = !(container.repository.allSystemsArmed() ?: false)
        setBusy()
        scope.launch {
            runCatching { container.repository.setAllSystemsArmed(target) }
            render()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render() {
        val tile = qsTile ?: return
        val loggedIn = container.auth.state.value is AuthState.LoggedIn
        val armed = container.repository.allSystemsArmed()
        tile.state = when {
            !loggedIn -> Tile.STATE_UNAVAILABLE
            armed == true -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_shield)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !loggedIn -> "Accedi"
                armed == true -> "Armato"
                armed == false -> "Disarmato"
                else -> "—"
            }
        }
        tile.updateTile()
    }

    private fun setBusy() {
        val tile = qsTile ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "…"
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
