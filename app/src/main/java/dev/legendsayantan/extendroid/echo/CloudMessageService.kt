package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
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
import java.util.Locale

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
                        .url(EchoNetworkUtils.getBackendUrl(ctx) + "/device?uid=$uid&token=$idToken&devicename=$desiredDeviceName")
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
                                    // A device with our desired name is already registered, but we need to check if it's the same device
                                    if(prefs.deviceName != desiredDeviceName){
                                        //A different device has the name we want, so we need to change the name of current device
                                        desiredDeviceName = "$desiredDeviceName (${Settings.Global.DEVICE_NAME})"
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
                                            if (res.isSuccessful) {
                                                prefs.deviceName = desiredDeviceName
                                                prefs.balance = res.body?.string()?.substringAfter("\"balance\":")?.substringBefore("}")?.toFloatOrNull() ?: 0.0f
                                                prefs.nextSyncTime =
                                                    System.currentTimeMillis() + EchoControlDialog.hourMinuteForBoosters(
                                                        prefs.balance
                                                    ).second.let {
                                                        if (it < 30) Utils.minuteToMilliseconds(
                                                            it.coerceAtLeast(1)
                                                        ) else THIRTY_MINUTES
                                                    }
                                                onSuccess("Success: ${res.body?.string()}")
                                            } else {
                                                onFailure("Error ${res.code}: ${res.body?.string()}")
                                            }
                                        }
                                    }
                                })
                            }
                        }
                    });
                }

        }
    }

}