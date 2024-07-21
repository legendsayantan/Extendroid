package dev.legendsayantan.extendroid

import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import dev.legendsayantan.extendroid.ShizukuActions.Companion.grantAudioRecordPerm
import dev.legendsayantan.extendroid.ShizukuActions.Companion.grantMediaProjectionPerm
import rikka.shizuku.Shizuku


class MainActivity : AppCompatActivity() {
    val REQUEST_CODE = 1000
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<MaterialCardView>(R.id.startStopBtn).setOnClickListener {
            if (getAppStatus()) {
                stopApp()
            } else {
                startApp()
            }
        }
    }


    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    private fun updateStatuses() {
        findViewById<TextView>(R.id.statusText).text = "${getShizukuStatus()}\n${getServerStatus()}"
        findViewById<TextView>(R.id.actionText).text = if (getAppStatus()) "Stop" else "Start"
        findViewById<ImageView>(R.id.actionImage).setImageResource(if (getAppStatus()) R.drawable.baseline_stop_24 else R.drawable.baseline_play_arrow_24)
    }

    private fun startApp() {
        requestShizukuIfRequired {
            // Start the app here
//            grantAudioRecordPerm()
//            grantMediaProjectionPerm()
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
        TODO("Not yet implemented")
    }


    private fun getShizukuStatus(): String {
        return "Shizuku Status: " + if (Shizuku.pingBinder())
            (if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "Ready" else "Need Permission")
        else "Inactive"
    }

    private fun getServerStatus(): String {
        return "Http Server Status: not available"
    }

    private fun getAppStatus(): Boolean {
        return false
    }

    private fun requestShizukuIfRequired(callback: () -> Unit) {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.addRequestPermissionResultListener({ i: Int, i1: Int ->
                if (i1 == PackageManager.PERMISSION_GRANTED) {
                    callback()
                }
            }, Handler(mainLooper))
            Shizuku.requestPermission(0)
        } else {
            callback()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                MyService.result = resultCode
                MyService.data = data
                startForegroundService(Intent(this, MyService::class.java))
            } else {
                // Handle the case where the user denied the permission
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


}