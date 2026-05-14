package dev.legendsayantan.extendroid

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VirtualDisplayNoContentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(resources.getColor(R.color.theme5, theme))
        }

        val welcomeTextView = TextView(this).apply {
            text = "You're connected!"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.theme1, theme))
        }

        val subTextView = TextView(this).apply {
            text = "Select an app from the sidebar to start using it."
            textSize = 16f // Smaller text size for the subtext
            gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.theme1, theme))
            // Adding a little bit of top margin to separate it from the title
            setPadding(0, 16, 0, 0)
        }

        rootLayout.addView(welcomeTextView)
        rootLayout.addView(subTextView)

        setContentView(rootLayout)
    }
}