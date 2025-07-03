package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.os.Handler
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils.Companion.toJsonSanitized
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
        val a = remoteMessage.data.toString()
        Handler(applicationContext.mainLooper).post {
            Toast.makeText(applicationContext,a, Toast.LENGTH_LONG).show()
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

                    val email = user.email?.toJsonSanitized()
                    val idToken = user.getIdToken(false).result?.token
                    val fcmToken = task.result

                    val jsonBody = "{\"email\": \"$email\",\"idToken\": \"$idToken\",\"fcmToken\": \"$fcmToken\"}"
                    val client = OkHttpClient()
                    val req = Request.Builder()
                        .url(ctx.getString(R.string.url_backend) + "/token")
                        .post(
                            jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
                        )
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $idToken")
                        .build()
                    client.newCall(req).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            onFailure("Error: ${e.message ?: "Unknown error"}")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                if (it.isSuccessful) {
                                    onSuccess("Success: ${it.body?.string()}")
                                } else {
                                    onFailure("Error ${it.code}: ${it.body?.string()}")
                                }
                            }
                        }
                    })
                }

        }
    }

}