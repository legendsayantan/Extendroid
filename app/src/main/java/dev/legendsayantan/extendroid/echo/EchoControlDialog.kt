package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.R
import kotlin.math.floor

/**
 * @author legendsayantan
 */
class EchoControlDialog(
    val ctx: Context
) : AlertDialog(ctx) {
    val prefs by lazy { Prefs(ctx) }
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

    val authStateListener = object : FirebaseAuth.AuthStateListener {
        override fun onAuthStateChanged(p0: FirebaseAuth) {
            Handler(ctx.mainLooper).post {
                updateAccount()
                updateBalance()
            }
        }
    }
    val echoChanged: (Context) -> Unit = { ctx ->
        Handler(ctx.mainLooper).postDelayed({
            updateBoosterQuantity()
        }, 1000)
    }

    var dView: View

    init {
        val lInflater = LayoutInflater.from(ctx)
        dView = lInflater.inflate(R.layout.dialog_echo_mode, null)

        setView(dView)
        setCanceledOnTouchOutside(false)
        applyWindowParameters()
    }

    fun applyWindowParameters() {
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            //setWindowAnimations(R.style.Base_Theme_Extendroid)
            addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                        or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            attributes = attributes.apply {
                dimAmount = dimValue
                gravity = android.view.Gravity.CENTER
            }
        }
    }

    override fun show() {
        super.show()
        preventShowing = true
        applyWindowParameters()
        updateAccount()
        updateBalance()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        prefs.registerEchoChangeListener(echoChanged)
    }


    fun updateAccount() {
        signInlayout?.isVisible = user == null
        logoutBtn?.isVisible = signInlayout?.isVisible == false
        accountTextView?.isSelected = true
        accountTextView?.text = if (signInlayout?.isVisible == false) {
            ctx.getString(R.string.signed_in_as, user!!.email)
        } else {
            ctx.getString(R.string.sign_in_or_sign_up_to_use_extendroid_echo)
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
                        Toast.makeText(ctx, exception, Toast.LENGTH_LONG).show()
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
                        Toast.makeText(ctx, exception, Toast.LENGTH_LONG).show()
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
                            Toast.makeText(ctx, "Password reset email sent", Toast.LENGTH_LONG)
                                .show()
                        } else {
                            val exception = task.exception?.message ?: "Failed to send reset email"
                            Toast.makeText(ctx, exception, Toast.LENGTH_LONG).show()
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

    override fun dismiss() {
        super.dismiss()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        prefs.unregisterEchoChangeListener(echoChanged)
        preventShowing = false
    }

    override fun cancel() {
        super.cancel()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        prefs.unregisterEchoChangeListener(echoChanged)
        preventShowing = false
    }

    companion object {
        var preventShowing = false
        var dimValue = 0.75f
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