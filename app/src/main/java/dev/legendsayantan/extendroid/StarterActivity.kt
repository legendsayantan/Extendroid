package dev.legendsayantan.extendroid

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import dev.legendsayantan.extendroid.lib.ShizukuActions
import java.util.Timer
import kotlin.concurrent.timerTask

class StarterActivity : AppCompatActivity() {
    private var pkg :String? = null
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starter)
        pkg = intent.getStringExtra("pkg")
        findViewById<MaterialButton>(R.id.relaunchButton).setOnTouchListener { v,event->
            if(event.action==MotionEvent.ACTION_DOWN){
                startApp()
            }
            return@setOnTouchListener true
        }
        findViewById<MaterialButton>(R.id.restartButton).setOnTouchListener { v, event ->
            if(event.action==MotionEvent.ACTION_DOWN){
                ShizukuActions.dispatchForceStop(pkg.toString())
                Handler(mainLooper).postDelayed({
                    startApp()
                },1500)
            }
            return@setOnTouchListener true
        }
        startApp()
    }

    private fun startApp(){
        val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        intent?.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}