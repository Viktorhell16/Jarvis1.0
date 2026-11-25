package com.example.overlay

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.data.local.EncryptedPrefsManager

class JarvisTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val prefs = EncryptedPrefsManager(this)
        qsTile?.apply {
            state = if (prefs.state.value.floatingOverlayEnabled && Settings.canDrawOverlays(this@JarvisTileService)) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val prefs = EncryptedPrefsManager(this)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            return
        }
        val nextEnabled = !prefs.state.value.floatingOverlayEnabled
        prefs.setFloatingOverlayEnabled(nextEnabled)
        val serviceIntent = Intent(this, FloatingJarvisService::class.java)
        if (nextEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }
        qsTile?.apply {
            state = if (nextEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
