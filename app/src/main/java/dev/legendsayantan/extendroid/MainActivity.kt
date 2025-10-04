package dev.legendsayantan.extendroid

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.initialize
import com.google.firebase.messaging.FirebaseMessaging
import dev.legendsayantan.extendroid.Utils.Companion.isShizukuAllowed
import dev.legendsayantan.extendroid.Utils.Companion.isShizukuSetup
import dev.legendsayantan.extendroid.Utils.Companion.miuiRequirements
import dev.legendsayantan.extendroid.echo.EchoNetworkUtils
import dev.legendsayantan.extendroid.lib.Logging
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.lib.MediaCore.Companion.requestMediaProjection
import dev.legendsayantan.extendroid.services.ExtendService
import dev.legendsayantan.extendroid.services.ExtendService.Companion.svcIntent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    val prefs by lazy { Prefs(applicationContext) }
    val logging by lazy { Logging(applicationContext) }
    var onPauseTask = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HiddenApiBypass.addHiddenApiExemptions("");
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
        FirebaseMessaging.getInstance().isAutoInitEnabled = false

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        prefs.registerConfigChangeListener {
            handleSections()
        }
        refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay;
        }.refreshRate.toInt()
    }

    override fun onPause() {
        super.onPause()
        onPauseTask()
    }

    override fun onResume() {
        super.onResume()
        initialiseSetupMenu()
        handleSections()
        initialiseConfigure()
        initialiseBottomBar()
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
                val intent =
                    packageManager.getLaunchIntentForPackage(getString(R.string.package_shizuku))
                        ?: Intent(Intent.ACTION_VIEW).apply {
                            setData(getString(R.string.url_shizuku).toUri())
                        }
                startActivity(intent)
            }
        }
    }

    private fun handleSections() {

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

    fun initialiseBottomBar() {
        val echoBtn = findViewById<MaterialCardView>(R.id.echoBtn)
        val echoTxt = findViewById<TextView>(R.id.echoTxt)
        val logBtn = findViewById<MaterialCardView>(R.id.logBtn)
        val stopBtn = findViewById<ImageView>(R.id.stopBtn)

        val openEcho = {
            val intent = Intent(this@MainActivity, EchoActivity::class.java)
            startActivity(intent)
        }
        echoBtn.setOnClickListener {
            if (prefs.disclaimerTextShown) {
                openEcho()
            } else {
                Toast.makeText(applicationContext, "Please wait...", Toast.LENGTH_SHORT).show()
                Utils.showInfoDialog(this@MainActivity, "Disclaimer",
                    "${getString(R.string.agreed_to_tos)} ${getString(R.string.url_homepage_web)}/tos"
                ) {
                    prefs.disclaimerTextShown = true
                    runOnUiThread {
                        openEcho()
                    }
                }
            }

        }
        echoBtn.isEnabled = Utils.isSafeForUI(this)
        echoBtn.alpha = if (Utils.isSafeForUI(this)) 1f else 0.6f
        if (ExtendService.svc == null) {
            stopBtn.visibility = View.GONE
        } else {
            stopBtn.visibility = View.VISIBLE
            stopBtn.setOnClickListener {
                try {
                    stopService(svcIntent)
                } catch (_: Exception) { }
                onPauseTask = {
                    exitProcess(0)
                }
                Toast.makeText(
                    applicationContext,
                    "Stopping services, press back to restart the app.",
                    Toast.LENGTH_LONG
                ).show()
                initialiseSetupMenu()
                handleSections()
                initialiseConfigure()
                initialiseBottomBar()
            }
        }
        echoTxt.isSelected = true
        echoTxt.text =
            if (FirebaseAuth.getInstance().currentUser != null && prefs.fcmSent && ExtendService.svc != null) {
                EchoNetworkUtils.trySyncBoostersWithServer(applicationContext)
                "Echo Boosters : ${String.format("%.2f", prefs.balance)}"
            } else {
                getString(R.string.extendroid_echo)
            }

        logBtn.setOnClickListener {
            startActivity(Intent(applicationContext, LogsActivity::class.java))
        }
    }

    private fun startForegroundService() {
        if (!isShizukuSetup() || !isShizukuAllowed()) return
        if (ExtendService.svc != null) return
        if (!prefs.allowedMiuiPerms) {
            miuiRequirements()
            prefs.allowedMiuiPerms = true
        }
        svcIntent = Intent(applicationContext, ExtendService::class.java)
        startForegroundService(svcIntent)
        val projectionScheduler = Timer()
        projectionScheduler.schedule(timerTask {
            if(ExtendService.svc!=null){
                requestMediaProjection()
                this.cancel()
                projectionScheduler.cancel()
            }
        },500,500)
        if (intent.action == ACTION_AUTOSTART || intent.hasExtra(EXTRA_AUTOSTART)) {
            val closeTimer = Timer()
            closeTimer.schedule(timerTask {
                if (ExtendService.svc != null && MediaCore.mInstance?.projection != null) {
                    this.cancel()
                    closeTimer.cancel()
                    finishAffinity()
                }
            }, 500, 500)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            MediaCore.onMediaProjectionResult(requestCode, resultCode, data)
        }catch (e: Exception){

        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }


    companion object {
        var refreshRate : Int = 60
        const val ACTION_AUTOSTART = "dev.legendsayantan.extendroid.action.autostart"
        const val EXTRA_AUTOSTART = "dev.legendsayantan.extendroid.extra.autostart"
    }

}