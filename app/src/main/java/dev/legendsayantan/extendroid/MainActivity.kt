package dev.legendsayantan.extendroid

import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.view.SurfaceControl
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import dev.legendsayantan.extendroid.ShizukuActions.Companion.setMainDisplayPowerMode
import dev.legendsayantan.extendroid.ShizukuActions.Companion.grantMediaProjectionAdb
import dev.legendsayantan.extendroid.adapters.SessionsAdapter
import rikka.shizuku.Shizuku


class MainActivity : AppCompatActivity() {
    val REQUEST_CODE = 1000
    val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<MaterialCardView>(R.id.startStopBtn).setOnClickListener {
            if (isServiceRunning()) {
                stopApp()
            } else {
                startApp()
            }
        }
        registerNewSessionBtnListener(intent)
        registerDisableScreenBtnListener()
        registerFloatingMenuSwitch()
        refreshStatus = {
            try {
                Handler(mainLooper).post { updateStatuses() }
            } catch (_: Exception) {
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        registerNewSessionBtnListener(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isServiceRunning()) {
            ShizukuActions.dispatchForceStop(packageName)
        }
    }


    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    private fun updateStatuses() {
        findViewById<TextView>(R.id.statusText).text = "${getShizukuStatus()}\n${getServerStatus()}"
        findViewById<TextView>(R.id.actionText).text = if (isServiceRunning()) "Stop" else "Start"
        findViewById<ImageView>(R.id.actionImage).setImageResource(if (isServiceRunning()) R.drawable.rounded_stop_24 else R.drawable.baseline_play_arrow_24)
        val recyclerView = findViewById<RecyclerView>(R.id.sessions)
        val dataList = ExtendService.queryWindows()
        recyclerView?.adapter = SessionsAdapter(applicationContext, dataList) { id ->
            ExtendService.onDetachWindow(id, true)
        }
        recyclerView.invalidate()
    }

    private fun startApp() {
        requestShizukuIfRequired {
            // Start the app here
            grantMediaProjectionAdb()
            Handler(mainLooper).postDelayed({
                startMediaProjection()
            }, 2000)
        }
    }

    private fun startMediaProjection() {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Start an activity for result to request permission
        val intent = projectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_CODE)
    }

    private fun stopApp() {
        stopService(Intent(this, ExtendService::class.java))
    }


    private fun getShizukuStatus(): String {
        return "Shizuku Status: " + if (Shizuku.pingBinder())
            (if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "Ready" else "Need Permission")
        else "Inactive"
    }

    private fun getServerStatus(): String {
        return "Http Server Status: not available"
    }

    private fun isServiceRunning(): Boolean {
        return ExtendService.running
    }

    private fun requestShizukuIfRequired(callback: () -> Unit) {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.addRequestPermissionResultListener { _: Int, i1: Int ->
                if (i1 == PackageManager.PERMISSION_GRANTED) {
                    callback()
                }
            }
            Shizuku.requestPermission(0)
        } else {
            callback()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                ExtendService.result = resultCode
                ExtendService.data = data
                startForegroundService(Intent(this, ExtendService::class.java))
                updateStatuses()
            } else {
                // Handle the case where the user denied the permission
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerNewSessionBtnListener(intent: Intent) {
        val task = {
            val d = NewSessionDialog(this) { pkg, size, helper ->
                ExtendService.onAttachWindow(pkg, size, helper) { id ->
                    updateStatuses()
                }
            }
            if (intent.getBooleanExtra("add", false)) d.setOnDismissListener { finish() }
            d.show()
            if (!isServiceRunning()) {
                startApp()
            }
        }
        if (intent.getBooleanExtra("add", false)) task()
        findViewById<MaterialCardView>(R.id.addBtn).setOnClickListener {
            task()
        }
    }

    fun registerDisableScreenBtnListener() {
        findViewById<MaterialCardView>(R.id.disableScreen).setOnClickListener {
            setMainDisplayPowerMode(0)
        }
    }

    fun registerFloatingMenuSwitch() {
        val switch = findViewById<MaterialSwitch>(R.id.controlSwitch)
        switch.isChecked = prefs.getBoolean("floatingmenu", false)
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("floatingmenu", isChecked).apply()
        }
        shouldShowMenu = { switch.isChecked }
    }

    companion object {
        var refreshStatus = {}
        var shouldShowMenu: () -> Boolean = { false }
    }

}