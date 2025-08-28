package dev.legendsayantan.extendroid

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dev.legendsayantan.extendroid.Utils.Companion.showInfoDialog
import kotlin.math.floor
import dev.legendsayantan.extendroid.echo.RemoteUnlocker;
import dev.legendsayantan.extendroid.services.ExtendService

class EchoActivity : AppCompatActivity() {
    val prefs by lazy { Prefs(this) }
    val remoteUnlocker by lazy { RemoteUnlocker(this) }
    val user
        get() = FirebaseAuth.getInstance().currentUser
    val emailField by lazy { findViewById<EditText>(R.id.email) }
    val passwordField by lazy { findViewById<EditText>(R.id.password) }
    val signupBtn by lazy { findViewById<MaterialButton>(R.id.signupBtn) }
    val loginBtn by lazy { findViewById<MaterialButton>(R.id.loginBtn) }
    val forgotPasswordBtn by lazy { findViewById<MaterialButton>(R.id.forgotBtn) }
    val logoutBtn by lazy { findViewById<MaterialButton>(R.id.logoutBtn) }
    val signInlayout by lazy { findViewById<LinearLayout>(R.id.signInAccount) }
    val accountTextView by lazy { findViewById<TextView>(R.id.accountTextView) }

    val usageCard by lazy { findViewById<MaterialCardView>(R.id.usageCard) }
    val boosterAmountText by lazy { findViewById<TextView>(R.id.boosterAmount) }
    val quotaTimeText by lazy { findViewById<TextView>(R.id.quotaTime) }

    val accessCard by lazy { findViewById<MaterialCardView>(R.id.accessCard) }
    val blacklistBtn by lazy { findViewById<MaterialButton>(R.id.blacklistBtn) }

    val unlockCard by lazy { findViewById<MaterialCardView>(R.id.unlockCard) }
    val trainingBtn by lazy { findViewById<MaterialButton>(R.id.trainingBtn) }
    val removeTrainingBtn by lazy { findViewById<MaterialCardView>(R.id.removeTrainingBtn) }
    val testUnlockBtn by lazy { findViewById<MaterialButton>(R.id.testUnlockBtn) }

    val authStateListener = FirebaseAuth.AuthStateListener {
        Handler(mainLooper).post {
            updateAccount()
            updateBalance()
            updateRemoteAccess()
            updateRemoteUnlock()
        }
    }
    val echoChanged: (Context) -> Unit = { ctx ->
        Handler(mainLooper).postDelayed({
            updateBalance()
        }, 1000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_echo)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        preventShowing = true
        updateAccount()
        updateBalance()
        updateRemoteAccess()
        updateRemoteUnlock()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        prefs.registerEchoChangeListener(echoChanged)
    }

    fun updateAccount() {
        signInlayout?.isVisible = user == null
        logoutBtn?.isVisible = signInlayout?.isVisible == false
        accountTextView?.isSelected = true
        accountTextView?.text = if (signInlayout?.isVisible == false) {
            getString(R.string.signed_in_as, user!!.email)
        } else {
            getString(R.string.sign_in_or_sign_up_to_use_extendroid_echo)
        }

        signupBtn!!.setOnClickListener {
            val email = emailField!!.text.toString().trim()
            val password = passwordField!!.text.toString().trim()
            if (email.isNotEmpty() && password.length >= 6) {
                val auth = FirebaseAuth.getInstance()
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        FirebaseMessaging.getInstance().deleteToken().addOnSuccessListener {
                            FirebaseMessaging.getInstance().token
                        }
                    } else {
                        // Show error message
                        val exception = task.exception?.message ?: "Sign up failed"
                        Toast.makeText(this, exception, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        loginBtn!!.setOnClickListener {
            val email = emailField!!.text.toString().trim()
            val password = passwordField!!.text.toString().trim()
            if (email.isNotEmpty() && password.length >= 6) {
                //login using firebase
                val auth = FirebaseAuth.getInstance()
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        FirebaseMessaging.getInstance().deleteToken().addOnSuccessListener {
                            FirebaseMessaging.getInstance().token
                        }
                    } else {
                        // Show error message
                        val exception = task.exception?.message ?: "Login failed"
                        Toast.makeText(this, exception, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                // Show error message
                if (email.isEmpty()) emailField!!.error = "Email cannot be empty"
                if (password.isEmpty()) passwordField!!.error = "Password cannot be empty"
                else if (password.length < 6) passwordField!!.error =
                    "Password must be at least 6 characters"
            }
        }
        forgotPasswordBtn!!.setOnClickListener {
            val email = emailField!!.text.toString().trim()
            if (email.isNotEmpty()) {
                // Send password reset email
                val auth = FirebaseAuth.getInstance()
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Password reset email sent", Toast.LENGTH_LONG)
                                .show()
                        } else {
                            val exception = task.exception?.message ?: "Failed to send reset email"
                            Toast.makeText(this, exception, Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                emailField!!.error = "Email cannot be empty"
            }
        }
        logoutBtn!!.setOnClickListener {
            val auth = FirebaseAuth.getInstance()
            auth.signOut()
        }
    }

    fun updateBalance() {
        usageCard?.isVisible = user != null
        if (usageCard!!.isVisible) {
            updateBoosterQuantity()
        }
    }

    fun updateBoosterQuantity() {
        boosterAmountText?.text = String.format("%.2f", prefs.balance)
        quotaTimeText?.text = estimateHourMinuteForBoosters(prefs.balance)
    }

    fun updateRemoteAccess() {
        accessCard?.isVisible = user != null
    }

    fun updateRemoteUnlock() {
        unlockCard?.isVisible = user != null
        trainingBtn?.isVisible = remoteUnlocker.unlockData.isEmpty()
        removeTrainingBtn?.isVisible = remoteUnlocker.unlockData.isNotEmpty()
        testUnlockBtn?.isVisible = remoteUnlocker.unlockData.isNotEmpty()
        trainingBtn.setOnClickListener {
            Utils.showInfoDialog(
                this@EchoActivity,
                getString(R.string.unlock_training),
                getString(R.string.training_steps)
            ) {
                ExtendService.svc?.let {
                    remoteUnlocker.startTraining(it) { success ->
                        Handler(mainLooper).postDelayed({
                            if(success){
                                updateRemoteUnlock()
                            }else{
                                showInfoDialog(this@EchoActivity,"An error occured!","Failed to train, try to restart your device.",{})
                            }
                        },1000)
                    }
                }
            }
        }
        removeTrainingBtn.setOnClickListener {
            Utils.showInfoDialog(
                this@EchoActivity,
                getString(R.string.confirmation),
                getString(R.string.training_data_removal_message)
            ) {
                remoteUnlocker.unlockData = emptyArray()
                updateRemoteUnlock()
            }
        }
        testUnlockBtn.setOnClickListener {
            Utils.showInfoDialog(
                this@EchoActivity,
                getString(R.string.test_unlock),
                getString(R.string.test_unlock_steps)
            ) {
                ExtendService.svc?.let {
                    remoteUnlocker.testUnlock(it)
                }
            }
        }
    }

    override fun onDestroy() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        prefs.unregisterEchoChangeListener(echoChanged)
        preventShowing = false
        super.onDestroy()
    }

    companion object {
        var preventShowing = false
        var HOURS_PER_CREDIT = 0.2f

        fun hourMinuteForBoosters(balance: Float): Pair<Int, Int> {
            val hours =
                balance * HOURS_PER_CREDIT
            val hourNumber = floor(hours)
            var minuteNumber = floor((hours - hourNumber) * 60).coerceAtLeast(0f)
            return hourNumber.toInt() to minuteNumber.toInt()
        }

        fun estimateHourMinuteForBoosters(balance: Float): String {
            return hourMinuteForBoosters(balance).let { it ->
                "${it.first} h" + if (it.second > 0) " ${it.second} m" else ""
            }
        }
    }
}