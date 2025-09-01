package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.os.Build
import android.os.Handler
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import dev.legendsayantan.extendroid.EchoActivity
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.Utils
import dev.legendsayantan.extendroid.Utils.Companion.toJsonSanitized
import dev.legendsayantan.extendroid.echo.EchoNetworkUtils.Companion.THIRTY_MINUTES
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.services.ExtendService
import dev.legendsayantan.extendroid.services.ExtendService.Companion.svc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * @author legendsayantan
 */
class CloudMessageService : FirebaseMessagingService(){
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val ctx = applicationContext
        GlobalScope.launch(Dispatchers.IO){
            registerTokenToBackend(ctx,{
                Prefs(ctx).fcmSent = true
                Handler(ctx.mainLooper).post { Toast.makeText(ctx,it, Toast.LENGTH_LONG).show() }
            },{
                Prefs(ctx).fcmSent = false
                Handler(ctx.mainLooper).post { Toast.makeText(ctx,it, Toast.LENGTH_LONG).show() }
            })
        }
    }

    override fun onMessageReceived(remoteMessage: com.google.firebase.messaging.RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val a = remoteMessage.data
        if (a.toString().isNotBlank() && svc!=null && MediaCore.mInstance?.projection!=null) {
            //we can start!
            val uid = FirebaseAuth.getInstance().currentUser?.uid?: return
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.addOnCompleteListener {
                if (!it.isSuccessful) {
                    Handler(applicationContext.mainLooper).post {
                        Toast.makeText(applicationContext, "Failed to get ID Token: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                    return@addOnCompleteListener
                }
                val idToken = it.result!!.token!!
                ExtendService.setupEchoCommand(a,uid,idToken)
            }
        } else {
            // Handle the case where there is no data in the message
            Handler(applicationContext.mainLooper).post {
                Toast.makeText(applicationContext, "Unable to start Echo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        // Handle deleted messages here
    }

    companion object{

        fun registerTokenToBackend(ctx:Context,onSuccess: (String) -> Unit = {}, onFailure: (String) -> Unit = {}) {
            val user = FirebaseAuth.getInstance().currentUser
            if(user==null){
                return
            }
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        onFailure("Fetching FCM registration token failed: ${task.exception?.message}")
                        return@addOnCompleteListener
                    }

                    val uid = user.uid.toJsonSanitized()
                    val idToken = user.getIdToken(false).result?.token
                    val fcmToken = task.result
                    val prefs = Prefs(ctx)
                    var desiredDeviceName = prefs.deviceName.ifBlank { Build.MANUFACTURER.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }+" "+ Build.MODEL }
                    val client = OkHttpClient()
                    val checkIfDesiredNameIsRegistered = Request.Builder()
                        .url(EchoNetworkUtils.getBackendUrl(ctx) + "/device?uid=$uid&token=$idToken&devicepattern=$desiredDeviceName%")
                        .get()
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-UID", uid)
                        .addHeader("Authorization", "Bearer $idToken")
                        .build()
                    client.newCall(checkIfDesiredNameIsRegistered).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            onFailure("Error: ${e.message ?: "Unknown error"}")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use { response ->
                                if (response.isSuccessful) {
                                    val resbody = response.body?.string()
                                    val uniqueName = getUsableDeviceName(desiredDeviceName,JSONObject(resbody).get("results").toString())
                                    if(uniqueName!=desiredDeviceName){
                                        // Our desired name is already registered, but we need to check if it's the same device
                                        if(prefs.deviceName != desiredDeviceName){
                                            //A different device has the name we want, so we need to use the uniqueName
                                            desiredDeviceName = uniqueName
                                        }
                                    }
                                }
                                // Now we can register the device with the backend
                                val deviceRegisterJson = "{\"uid\": \"$uid\",\"token\": \"$idToken\", \"devicename\":\"${desiredDeviceName.trim()}\", \"fcmtoken\": \"$fcmToken\"}"

                                val req = Request.Builder()
                                    .url(EchoNetworkUtils.getBackendUrl(ctx)+ "/device?uid=$uid&token=$idToken")
                                    .post(
                                        deviceRegisterJson.toRequestBody("application/json; charset=utf-8".toMediaType())
                                    )
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("X-UID", uid)
                                    .addHeader("Authorization", "Bearer $idToken")
                                    .build()
                                client.newCall(req).enqueue(object : okhttp3.Callback {
                                    override fun onFailure(call: Call, e: IOException) {
                                        onFailure("Error: ${e.message ?: "Unknown error"}")
                                    }

                                    override fun onResponse(call: Call, response: Response) {
                                        response.use { res ->
                                            val body = res.body?.string() ?: ""
                                            if (res.isSuccessful) {
                                                prefs.deviceName = desiredDeviceName
                                                prefs.balance = body.substringAfter("\"balance\":").substringBefore("}")
                                                    .toFloatOrNull() ?: 0.0f
                                                prefs.nextSyncTime =
                                                    System.currentTimeMillis() + EchoActivity.hourMinuteForBoosters(
                                                        prefs.balance
                                                    ).second.let {
                                                        if (it < 30) Utils.minuteToMilliseconds(
                                                            it.coerceAtLeast(1)
                                                        ) else THIRTY_MINUTES
                                                    }
                                                onSuccess("Success: $body")
                                            } else {
                                                onFailure("Error ${res.code}: $body")
                                            }
                                        }
                                    }
                                })
                            }
                        }
                    });
                }

        }

        /**
         * Return a device name that does not already exist in `devices`.
         *
         * - `name` is the desired base name, e.g. "Realme RMX2156"
         * - `devices` is a JSON array string where each element is an object that may contain "devicename"
         * - If `name` is free, returns `name`.
         * - Otherwise returns the first available variant "name (N)" with N >= 2 (do NOT use (1)).
         * - If parsing fails, falls back to a simple existence check and appends " (2)" when needed.
         */
        fun getUsableDeviceName(name: String, devices: String): String {
            val base = name.trim()
            try {
                val existing = mutableSetOf<String>()
                val arr = JSONArray(devices)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val dn = obj.optString("devicename", "").trim()
                    if (dn.isNotEmpty()) existing.add(dn)
                }

                if (!existing.contains(base)) return base

                var n = 2
                while (true) {
                    val candidate = "$base ($n)"
                    if (!existing.contains(candidate)) return candidate
                    n++
                    // (practically infinite loop shouldn't happen; you'll eventually find a free slot)
                }
            } catch (e: Exception) {
                // Parsing error: best-effort fallback
                return if (!devices.contains(base)) base else "$base (2)"
            }
        }
    }

}