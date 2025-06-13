package dev.legendsayantan.extendroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.pm.PackageManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.lib.MediaCore.Companion.requestMediaProjection
import dev.legendsayantan.extendroid.services.ExtendService

class MainActivity : AppCompatActivity() {
    val prefs by lazy { Prefs(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HiddenApiBypass.addHiddenApiExemptions("");
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        initialiseSetupMenu()
        handleCards()
        initialiseConfigure()
        startForegroundService()
    }

    private fun initialiseSetupMenu() {
        val setupImage = findViewById<ImageView>(R.id.setupShizukuImage)
        val allowImage = findViewById<ImageView>(R.id.allowShizukuImage)

        if (isShizukuSetup()) {
            setupImage.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.round_done_24,
                    null
                )
            )
            if (isShizukuAllowed()) {
                allowImage.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.round_done_24,
                        null
                    )
                )
            } else {
                allowImage.setOnClickListener {
                    Shizuku.addRequestPermissionResultListener { _: Int, i1: Int ->
                        if (i1 == PackageManager.PERMISSION_GRANTED) {
                            initialiseSetupMenu()
                        }
                    }
                    Shizuku.requestPermission(0)
                }
            }
        } else {
            setupImage.setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(getString(R.string.package_shizuku))
                    ?: Intent(Intent.ACTION_VIEW).apply {
                        setData(getString(R.string.url_shizuku).toUri())
                    }
                startActivity(intent)
            }
        }
    }

    private fun handleCards() {
        val setupCard = findViewById<MaterialCardView>(R.id.cardSetup)
        val configCard = findViewById<MaterialCardView>(R.id.cardConfigure)
        val setupHeader = findViewById<MaterialCardView>(R.id.headerSetup)
        val configHeader = findViewById<MaterialCardView>(R.id.headerConfigure)
        val configBlocker = findViewById<LinearLayout>(R.id.configBlocker)
        val ready = isShizukuSetup() && isShizukuAllowed()
        setupCard.isEnabled = !ready
        configCard.isEnabled = ready
        configBlocker.isVisible = !ready
        setupCard.alpha = if (ready) 0.6f else 1f
        setupHeader.alpha = if (ready) 0.6f else 1f
        configCard.alpha = if (ready) 1f else 0.6f
        configHeader.alpha = if (ready) 1f else 0.6f
    }


    private fun initialiseConfigure() {
        val floatingBall = findViewById<MaterialSwitch>(R.id.floatingBall)
        val collapseSeconds = findViewById<TextInputEditText>(R.id.collapseSeconds)
        val densityAuto = findViewById<MaterialSwitch>(R.id.densityAuto)
        val densityScale = findViewById<Slider>(R.id.densityScale)
        val dimAmount = findViewById<Slider>(R.id.dimAmount)

        floatingBall.isChecked = prefs.floatingBall
        collapseSeconds.hint = prefs.collapseSeconds.toString()
        collapseSeconds.setText(prefs.collapseSeconds.toString())
        densityAuto.isChecked = prefs.densityAuto
        densityScale.value = prefs.densityScale
        dimAmount.value = prefs.backgroundDim

        floatingBall.setOnCheckedChangeListener { btn, checked ->
            prefs.floatingBall = checked
        }
        collapseSeconds.doOnTextChanged { txt, a, b, c ->
            prefs.collapseSeconds =
                (collapseSeconds.text.toString().toLongOrNull() ?: 30L).coerceAtLeast(1)
        }
        densityAuto.setOnCheckedChangeListener { btn, checked ->
            prefs.densityAuto = checked
        }
        densityScale.addOnChangeListener { slider, value, fromUser ->
            prefs.densityScale = value
        }
        dimAmount.addOnChangeListener { slider, value, fromUser ->
            prefs.backgroundDim = value
        }
    }

    private fun startForegroundService() {
        if (!isShizukuSetup() || !isShizukuAllowed()) return
        if (ExtendService.svc != null) return
        val svcIntent = Intent(applicationContext, ExtendService::class.java)
        startForegroundService(svcIntent)
        this@MainActivity.requestMediaProjection()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        MediaCore.onMediaProjectionResult(requestCode,resultCode,data)
    }


    companion object {
        public fun isShizukuSetup(): Boolean {
            return Shizuku.pingBinder()
        }

        public fun isShizukuAllowed(): Boolean {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

}