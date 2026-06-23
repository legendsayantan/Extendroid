package dev.legendsayantan.extendroid.lib

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Build

class FakeContext private constructor(base: Context) : ContextWrapper(base) {

    companion object {
        fun get(): FakeContext {
            val systemContext = getSystemContext()
            return FakeContext(systemContext!!)
        }

        @SuppressLint("PrivateApi")
        private fun getSystemContext(): Context? {
            return try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                var activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                if (activityThread == null) {
                    android.util.Log.e("ExtendroidDebug", "currentActivityThread is null, calling systemMain")
                    activityThread = activityThreadClass.getMethod("systemMain").invoke(null)
                } else {
                    android.util.Log.e("ExtendroidDebug", "currentActivityThread is NOT null")
                }
                
                val getSystemContextMethod = activityThreadClass.getMethod("getSystemContext")
                val ctx = getSystemContextMethod.invoke(activityThread) as Context
                android.util.Log.e("ExtendroidDebug", "getSystemContext returned successfully: $ctx")
                ctx
            } catch (e: Exception) {
                android.util.Log.e("ExtendroidDebug", "getSystemContext exception: ${e.stackTraceToString()}")
                null
            }
        }
    }

    override fun getPackageName(): String {
        return "com.android.shell"
    }

    override fun getOpPackageName(): String {
        return "com.android.shell"
    }

    @TargetApi(Build.VERSION_CODES.S)
    override fun getAttributionSource(): AttributionSource {
        val builder = AttributionSource.Builder(android.os.Process.SHELL_UID)
        builder.setPackageName("com.android.shell")
        return builder.build()
    }

    override fun getApplicationContext(): Context {
        return this
    }

    override fun createPackageContext(packageName: String, flags: Int): Context {
        return this
    }
}
