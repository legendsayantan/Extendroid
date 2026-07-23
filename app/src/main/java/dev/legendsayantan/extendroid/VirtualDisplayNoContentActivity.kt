package dev.legendsayantan.extendroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TextClock
import androidx.appcompat.app.AppCompatActivity
import android.app.ActivityOptions
import com.google.android.material.card.MaterialCardView

class VirtualDisplayNoContentActivity : AppCompatActivity() {

    companion object {
        val instances = mutableMapOf<Int, VirtualDisplayNoContentActivity>()
    }
    
    private var currentDisplayId: Int = -1

    private lateinit var batteryIcon: ImageView
    private lateinit var batteryValueText: TextView

    private lateinit var networkIcon: ImageView
    private lateinit var networkValueText: TextView

    private lateinit var ringerIcon: ImageView
    private lateinit var ringerValueText: TextView

    private var telephonyCallback: Any? = null
    private var cellSignalLevel = 4

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100) / scale else 0
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val chargeString = if (isCharging) "Charging" else "On Battery"
            batteryValueText.text = "$pct% ($chargeString)"
        }
    }

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateRingerStatus()
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.RSSI_CHANGED_ACTION) {
                updateNetworkStatus()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { updateNetworkStatus() }
        }
        override fun onLost(network: Network) {
            runOnUiThread { updateNetworkStatus() }
        }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            runOnUiThread { updateNetworkStatus() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        currentDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: -1
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.displayId
        }
        if (currentDisplayId != -1) {
            instances[currentDisplayId] = this
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val intent = Intent(this, FocusYankActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                val options = android.app.ActivityOptions.makeBasic()
                options.launchDisplayId = 0
                startActivity(intent, options.toBundle())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 500)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(resources.getColor(R.color.theme5, theme))
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
        }

        // Time Clock
        val timeClock = TextClock(this).apply {
            textSize = 56f
            setTextColor(resources.getColor(R.color.theme0, theme))
            format12Hour = "hh:mm a"
            format24Hour = "HH:mm"
            gravity = Gravity.CENTER
        }

        // Date Clock
        val dateClock = TextClock(this).apply {
            textSize = 18f
            setTextColor(resources.getColor(R.color.theme1, theme))
            format12Hour = "EEEE, MMMM d, yyyy"
            format24Hour = "EEEE, MMMM d, yyyy"
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 32.dp())
        }

        val cardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val (batteryCard, bIcon, bVal) = createStatusCard(R.drawable.ic_battery, "BATTERY")
        batteryIcon = bIcon
        batteryValueText = bVal

        val (networkCard, nIcon, nVal) = createStatusCard(R.drawable.ic_wifi, "NETWORK")
        networkIcon = nIcon
        networkValueText = nVal

        val (ringerCard, rIcon, rVal) = createStatusCard(R.drawable.ic_ringer_normal, "RINGER")
        ringerIcon = rIcon
        ringerValueText = rVal

        cardsContainer.addView(batteryCard)
        cardsContainer.addView(networkCard)
        cardsContainer.addView(ringerCard)

        val welcomeTextView = TextView(this).apply {
            text = "You're connected!"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.theme0, theme))
            setPadding(0, 40.dp(), 0, 0)
        }

        val subTextView = TextView(this).apply {
            text = "Select an app from the sidebar to start using it."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.theme1, theme))
            setPadding(0, 8.dp(), 0, 0)
        }

        val stopButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Stop Extendroid"
            textSize = 14f
            setTextColor(resources.getColor(R.color.white, theme))
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E57373"))
            cornerRadius = 8.dp()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 24.dp(), 0, 0)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            layoutParams = lp

            setOnClickListener {
                try {
                    val intent = Intent(this@VirtualDisplayNoContentActivity, dev.legendsayantan.extendroid.services.ExtendService::class.java)
                    stopService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                finish()
            }
        }

        rootLayout.addView(timeClock)
        rootLayout.addView(dateClock)
        rootLayout.addView(cardsContainer)
        rootLayout.addView(welcomeTextView)
        rootLayout.addView(subTextView)
        rootLayout.addView(stopButton)

        setContentView(rootLayout)

        // Register Receivers
        val batteryIntent = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let { batteryReceiver.onReceive(this, it) }
        
        registerReceiver(ringerReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        registerReceiver(wifiReceiver, IntentFilter(WifiManager.RSSI_CHANGED_ACTION))

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        registerTelephonyCallback()
        updateRingerStatus()
        updateNetworkStatus()
    }

    private fun createStatusCard(iconResId: Int, label: String): Triple<MaterialCardView, ImageView, TextView> {
        val card = MaterialCardView(this).apply {
            radius = 12.dp().toFloat()
            setCardBackgroundColor(ColorStateList.valueOf(resources.getColor(R.color.theme4, theme)))
            strokeWidth = 0
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8.dp(), 8.dp(), 8.dp(), 8.dp())
            }
            layoutParams = lp
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        }

        val icon = ImageView(this).apply {
            setImageResource(iconResId)
            layoutParams = LinearLayout.LayoutParams(32.dp(), 32.dp())
            setColorFilter(resources.getColor(R.color.white, theme))
        }

        val labelText = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(resources.getColor(R.color.theme1, theme))
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 2.dp())
        }

        val valueText = TextView(this).apply {
            text = "Loading..."
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, theme))
            gravity = Gravity.CENTER
        }

        container.addView(icon)
        container.addView(labelText)
        container.addView(valueText)
        card.addView(container)

        return Triple(card, icon, valueText)
    }

    private fun updateRingerStatus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> {
                ringerIcon.setImageResource(R.drawable.ic_ringer_silent)
                ringerValueText.text = "Silent"
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                ringerIcon.setImageResource(R.drawable.ic_ringer_vibrate)
                ringerValueText.text = "Vibrate"
            }
            AudioManager.RINGER_MODE_NORMAL -> {
                ringerIcon.setImageResource(R.drawable.ic_ringer_normal)
                ringerValueText.text = "Normal"
            }
        }
    }

    private fun updateNetworkStatus() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (activeNetwork == null || caps == null) {
                networkIcon.setImageResource(R.drawable.ic_cellular)
                networkValueText.text = "Offline"
                return
            }

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                networkIcon.setImageResource(R.drawable.ic_wifi)
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wifiManager.connectionInfo
                val rssi = info.rssi
                val level = WifiManager.calculateSignalLevel(rssi, 5)
                val levelString = when (level) {
                    0 -> "Poor"
                    1 -> "Fair"
                    2 -> "Good"
                    3 -> "Very Good"
                    else -> "Excellent"
                }
                networkValueText.text = "Wi-Fi: $levelString"
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                networkIcon.setImageResource(R.drawable.ic_cellular)
                val levelString = when (cellSignalLevel) {
                    0 -> "Poor"
                    1 -> "Fair"
                    2 -> "Good"
                    3 -> "Very Good"
                    else -> "Excellent"
                }
                networkValueText.text = "Cellular: $levelString"
            } else {
                networkIcon.setImageResource(R.drawable.ic_cellular)
                networkValueText.text = "Connected"
            }
        } catch (e: Exception) {
            networkValueText.text = "Connected"
        }
    }

    private fun registerTelephonyCallback() {
        try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        cellSignalLevel = signalStrength.level
                        updateNetworkStatus()
                    }
                }
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val callback = object : PhoneStateListener() {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        cellSignalLevel = signalStrength.level
                        updateNetworkStatus()
                    }
                }
                telephonyManager.listen(callback, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                telephonyCallback = callback
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentDisplayId != -1 && instances[currentDisplayId] == this) {
            instances.remove(currentDisplayId)
        }
        try {
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(ringerReceiver)
            unregisterReceiver(wifiReceiver)
        } catch (e: Exception) {}

        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {}

        try {
            telephonyCallback?.let { callback ->
                val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callback is TelephonyCallback) {
                    telephonyManager.unregisterTelephonyCallback(callback)
                } else if (callback is PhoneStateListener) {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(callback, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {}
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

}