package dev.legendsayantan.extendroid

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    val REQUEST_CODE = 1000
    private var mediaProjection : MediaProjection? = null
    val projectionManager: MediaProjectionManager by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        request()
    }
    fun startDisplay(mySurface:Surface){
        val displayManager: DisplayManager =
            getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayMetrics: DisplayMetrics = getResources().displayMetrics

        val virtualDisplay = displayManager.createVirtualDisplay(
            "MyVirtualDisplay",  // Name of the virtual display
            1920, 1280,  // Width and height
            displayMetrics.densityDpi,  // Screen density
            mySurface,  // Surface for the virtual display
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC // Flags
        )


        // Identify the presentation display based on your criteria, e.g., display ID
        var presentationDisplay: Display? = null
        presentationDisplay = virtualDisplay.display
    }

    fun request(){

        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager


// Start an activity for result to request permission
        val intent = projectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_CODE)
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