package dev.legendsayantan.extendroid.services

import android.service.quicksettings.TileService
import dev.legendsayantan.extendroid.lib.ShizukuActions.Companion.setMainDisplayPowerMode

class ScreenTileService : TileService() {
    override fun onClick() {
        setMainDisplayPowerMode(0)
    }
}