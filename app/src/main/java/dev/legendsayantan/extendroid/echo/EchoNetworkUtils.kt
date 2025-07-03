package dev.legendsayantan.extendroid.echo

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils
import dev.legendsayantan.extendroid.Utils.Companion.toJsonSanitized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException

/**
 * @author legendsayantan
 */
class EchoNetworkUtils {
    companion object {
        const val ONE_MINUTE = 60 * 1000L // 1 minute in milliseconds
        const val THIRTY_MINUTES = 30 * ONE_MINUTE // 30 minutes in milliseconds
        val user
            get() = FirebaseAuth.getInstance().currentUser

        fun trySyncBalanceWithServer(ctx: Context) {
            if (user == null) return
            val prefs = Prefs(ctx)
            if (System.currentTimeMillis() < prefs.nextSyncTime) return
            val email = user!!.email?.toJsonSanitized()
            user!!.getIdToken(false).addOnSuccessListener { it ->
                GlobalScope.launch(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val req = Request.Builder()
                        .url(ctx.getString(R.string.url_backend) + "/balance?email=$email&idToken=${it.token}")
                        .get()
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer ${it.token}")
                        .build()
                    client.newCall(req)
                        .enqueue(object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: IOException) {
                                e.printStackTrace()
                            }

                            override fun onResponse(
                                call: okhttp3.Call,
                                response: okhttp3.Response
                            ) {
                                if (response.isSuccessful) {
                                    val body = response.body
                                    if (body != null) {
                                        //get individual fields from the response body
                                        val txt = body.string()
                                        prefs.balance =
                                            txt.substringAfter("\"balance\":")
                                                .substringBefore("}").substringBefore(",")
                                                .toFloatOrNull() ?: 0.0f
                                        prefs.nextSyncTime =
                                            System.currentTimeMillis() + EchoControlDialog.hourMinuteForCredits(
                                                prefs.balance,
                                                prefs.lowQuality
                                            ).second.let {
                                                if (it < 30) Utils.minuteToMilliseconds(
                                                    it.coerceAtLeast(1)
                                                ) else THIRTY_MINUTES
                                            }
                                    }
                                } else {
                                    prefs.balance = 0.0f
                                    prefs.nextSyncTime = System.currentTimeMillis() + ONE_MINUTE
                                    // Handle the error response
                                    val errorBody =
                                        response.body?.string() ?: "Error syncing with server"
                                    System.err.print(errorBody)
                                }
                            }
                        })
                }
            }.addOnFailureListener {
                prefs.balance = 0.0f
                prefs.nextSyncTime = System.currentTimeMillis() + ONE_MINUTE
            }
        }
    }
}