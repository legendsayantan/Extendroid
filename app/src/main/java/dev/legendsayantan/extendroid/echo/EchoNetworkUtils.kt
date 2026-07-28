package dev.legendsayantan.extendroid.echo

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dev.legendsayantan.extendroid.EchoActivity
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils
import dev.legendsayantan.extendroid.Utils.Companion.toJsonSanitized
import dev.legendsayantan.extendroid.lib.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException

/**
 * @author legendsayantan
 */
class EchoNetworkUtils {
    companion object {
        const val ONE_MINUTE = 60 * 1000L // 1 minute in milliseconds
        const val THIRTY_MINUTES = 30 * ONE_MINUTE // 30 minutes in milliseconds
        const val USE_PRODUCTION = true // Set to true for production
        lateinit var mappings: Map<String, String>
        lateinit var processedMappings: Map<String, RemoteSessionHandler.Companion.PacketType>
        fun getBackendUrl(ctx: Context): String {
            return if (USE_PRODUCTION) {
                ctx.getString(R.string.url_backend_prod)
            } else {
                ctx.getString(R.string.url_backend_dev)
            }
        }

        val user
            get() = FirebaseAuth.getInstance().currentUser

        fun trySyncBoostersWithServer(ctx: Context) {
            if (user == null) return
            val prefs = Prefs(ctx)
            if (System.currentTimeMillis() < prefs.nextSyncTime) return
            val uID = user!!.uid.toJsonSanitized()
            val logging = Logging(ctx)
            user!!.getIdToken(false).addOnSuccessListener { it ->
                GlobalScope.launch(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val req = Request.Builder()
                        .url(getBackendUrl(ctx) + "/user?uid=$uID&token=${it.token}")
                        .get()
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-UID", uID)
                        .addHeader("Authorization", "Bearer ${it.token}")
                        .build()
                    client.newCall(req)
                        .enqueue(object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: IOException) {
                                logging.e(e, "trySyncBoostersWithServer")
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
                                        prefs.balance = try {
                                            org.json.JSONObject(txt).optDouble("balance", 0.0).toFloat()
                                        } catch (e: Exception) {
                                            logging.e(e, "trySyncBoostersWithServer")
                                            0.0f
                                        }
                                        prefs.nextSyncTime =
                                            System.currentTimeMillis() + EchoActivity.hourMinuteForBoosters(
                                                prefs.balance
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
                                    logging.e(errorBody, "trySyncBoostersWithServer")
                                }
                            }
                        })
                }
            }.addOnFailureListener {
                prefs.balance = 0.0f
                prefs.nextSyncTime = System.currentTimeMillis() + ONE_MINUTE
            }
        }

        /**
         * GET /signal with uid, token, client="device", sessionId, and return JSON response via callback
         */
        fun getSignalWithCallback(
            ctx: Context,
            uid: String,
            token: String,
            sessionId: String,
            callback: (String?, Exception?) -> Unit
        ) {
            val client = OkHttpClient()
            val url = getBackendUrl(ctx) + "/signal?uid=$uid&token=$token&client=device&sessionId=$sessionId"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-UID", uid)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("X-Echo-Version", "2")
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    callback(null, e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        callback(body, null)
                    } else {
                        val errorBody = response.body?.string()
                        callback(errorBody, IOException("Unexpected code $response"))
                    }
                }
            })
        }

        /**
         * POST /signal with uid, token, devicesdp (v2: no deviceice, includes sessionId)
         */
        fun postSignal(
            ctx: Context,
            uid: String,
            token: String,
            error: String? = null,
            devicesdp: String = "",
            sessionId: String = ""
        ) {
            val client = OkHttpClient()

            val json = if (error.isNullOrBlank()) {
                """
        {
            "uid": "$uid",
            "token": "$token",
            "sessionId": ${org.json.JSONObject.quote(sessionId)},
            "devicesdp": ${org.json.JSONObject.quote(devicesdp)}
        }
    """.trimIndent()
            } else { """{"uid": "$uid","token": "$token","sessionId":${org.json.JSONObject.quote(sessionId)},"error": ${org.json.JSONObject.quote(error)}}""" }

            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(getBackendUrl(ctx) + "/signal")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-UID", uid)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("X-Echo-Version", "2")
                .build()
            val logging = Logging(ctx)
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    logging.e(e, "postSignal")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use { resp ->
                        logging.i("${resp.code}  ${resp.body?.string()}", "postSignal")
                    }
                }
            })
        }

        /**
         * POST /signal/ice — sends a batch of trickle ICE candidates (device→web direction).
         *
         * Fire-and-forget: a lost batch degrades ICE gathering speed but never breaks the session.
         */
        fun sendIceCandidateBatch(
            ctx: Context,
            uid: String,
            token: String,
            sessionId: String,
            candidatesJson: String  // JSON array of { sdpMid, sdpMLineIndex, candidate }
        ) {
            val bodyJson = """
                {"uid":"$uid","token":"$token","sessionId":${org.json.JSONObject.quote(sessionId)},
                 "candidates":$candidatesJson,"direction":"device"}
            """.trimIndent()
            val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(getBackendUrl(ctx) + "/signal/ice")
                .post(body)
                .addHeader("X-Echo-Version", "2")
                .addHeader("Authorization", "Bearer $token")
                .build()

            val logging = Logging(ctx)
            OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    logging.e(e, "sendIceCandidateBatch")
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close() // fire-and-forget: just release the connection
                }
            })
        }

        fun updateMappings(ctx: Context, valueCallback: (Map<String, String>) -> Unit) {
            val prefs = Prefs(ctx)
            if (System.currentTimeMillis() - prefs.lastMappingsLoaded < 24 * 60 * 60 * 1000 && USE_PRODUCTION && !Utils.isDebuggable(ctx)) {
                valueCallback(prefs.echoMappings)
                return
            }
            val url = ctx.getString(R.string.mappingJsonUrl);
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Extendroid")
                .build()
            val logging = Logging(ctx)
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let {
                            if (it.isNotBlank()) prefs.setEchoMappings(it)
                        }
                    }
                    valueCallback(prefs.echoMappings)
                }
            } catch (e: IOException) {
                logging.e(e, "updateMappings")
                valueCallback(prefs.echoMappings)
            }
        }

        fun prepareMappings(ctx: Context) {
            updateMappings(ctx) {
                mappings = it
                // Built per-entry: an unrecognized/typo'd PacketType name (stale APK vs. newer
                // mappings.json, or a corrupted CDN response) should drop just that entry, not
                // throw away the whole mapping and break session setup.
                val safeMappings = mutableMapOf<String, RemoteSessionHandler.Companion.PacketType>()
                it.forEach { entry ->
                    try {
                        safeMappings[entry.key.lowercase()] = RemoteSessionHandler.Companion.PacketType.valueOf(entry.value)
                    } catch (e: IllegalArgumentException) {
                        Logging(ctx).e(e, "prepareMappings")
                    }
                }
                processedMappings = safeMappings
            }
        }

        fun getDisclaimerText(ctx: Context, then: (String) -> Unit) {
            // Replace with your actual disclaimer URL
            val disclaimerUrl = ctx.getString(R.string.disclaimerUrl)
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(disclaimerUrl)
                .get()
                .build()
            val logging = Logging(ctx)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.string()?.let {
                                launch(Dispatchers.Main) { // Switch to Main thread for UI updates
                                    then(it)
                                }
                            } ?: {
                                logging.e("Empty response body", "getDisclaimerText")
                            }
                        } else {
                            logging.e("Error: ${response.code}", "getDisclaimerText")
                        }
                    }
                } catch (e: IOException) {
                    logging.e(e, "getDisclaimerText")
                }
            }
        }

    }
}