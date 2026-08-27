package com.bolnaa.android.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.bolnaa.android.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class BolnaaTileService : TileService() {

    private lateinit var preferencesManager: PreferencesManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val isCurrentlyActive = preferencesManager.isServiceActive.first()
            val newActive = !isCurrentlyActive
            preferencesManager.setServiceActive(newActive)
            updateTileState(newActive)

            if (newActive) {
                FlowOverlayService.start(this@BolnaaTileService)
            }
        }
    }

    private fun updateTileState(active: Boolean? = null) {
        val tile = qsTile ?: return
        serviceScope.launch {
            val isActive = active ?: preferencesManager.isServiceActive.first()
            tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "Bolnaa"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isActive) "Active" else "Paused"
            }
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
