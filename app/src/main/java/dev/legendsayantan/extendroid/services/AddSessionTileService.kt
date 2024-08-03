package dev.legendsayantan.extendroid.services

import android.content.Intent
import android.service.quicksettings.TileService
import dev.legendsayantan.extendroid.MainActivity

class AddSessionTileService : TileService() {

    override fun onClick() {
        super.onClick()
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("add", true)
        )
    }
}