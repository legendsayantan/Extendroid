package dev.legendsayantan.extendroid.lib

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.IOException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * @author legendsayantan
 */
class DisplayHelper {
    @SuppressLint("BlockedPrivateApi", "PrivateApi")
    companion object{

        val SurfaceControlClass = Class.forName("android.view.SurfaceControl")
        var DisplayControlClass: Class<*>? = null

        init {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val classLoaderFactoryClass =
                    Class.forName("com.android.internal.os.ClassLoaderFactory")
                val createClassLoaderMethod = classLoaderFactoryClass.getDeclaredMethod(
                    "createClassLoader",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    ClassLoader::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    String::class.java
                )
                val classLoader = createClassLoaderMethod.invoke(
                    null,
                    "/system/framework/services.jar",
                    null,
                    null,
                    ClassLoader.getSystemClassLoader(),
                    0,
                    true,
                    null
                ) as ClassLoader
                DisplayControlClass =
                    classLoader.loadClass("com.android.server.display.DisplayControl")

                val loadLibraryMethod = Runtime::class.java.getDeclaredMethod(
                    "loadLibrary0",
                    Class::class.java,
                    String::class.java
                )
                loadLibraryMethod.isAccessible = true
                loadLibraryMethod.invoke(
                    Runtime.getRuntime(),
                    DisplayControlClass,
                    "android_servers"
                )
            }
        }

        @SuppressLint("PrivateApi", "BlockedPrivateApi")
        fun injectKeyEventToDisplay(
            displayId: Int,
            action: Int,
            keyCode: Int,
            metaState: Int
        ) {
            // 1) Create KeyEvent
            val now = SystemClock.uptimeMillis()
            val event = KeyEvent(
                now, now,
                action, keyCode, /*repeat*/0,
                metaState, /*deviceId*/0,
                /*scancode*/0, /*flags*/0, InputDevice.SOURCE_TOUCHSCREEN
            )

            // 2) Reflectively set its displayId (hidden API)
            KeyEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(event, displayId)

            // 3) Get IInputManager from ServiceManager
            val svcMgr = Class.forName("android.os.ServiceManager")
            val getService: Method = svcMgr.getMethod("getService", String::class.java)

            @Suppress("UNCHECKED_CAST")
            val imBinder = getService.invoke(null, "input") as IBinder

            val imStub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asIface = imStub.getMethod("asInterface", IBinder::class.java)
            val im = asIface.invoke(null, imBinder)

            // 4) Inject the event (mode 0 = ASYNC)
            val injectMethod = im.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC == 0
            injectMethod.invoke(im, event, 0)
        }

        fun injectMotionEvent(
            event: MotionEvent,
            displayId: Int
        ) {
            // 1) Build the MotionEvent
            event.apply {
                // set the target display
                // API 30+ has setDisplayId; pre‑30 we reflect
                val m =
                    MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
                m.invoke(this, displayId)
            }


            // 2) Get the IInputManager binder
            val smClass = Class.forName("android.os.ServiceManager")
            val getService: Method = smClass.getMethod("getService", String::class.java)

            @Suppress("UNCHECKED_CAST")
            val inputBinder = getService.invoke(null, "input") as IBinder

            // 3) Obtain IInputManager interface
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface: Method = stubClass.getMethod("asInterface", IBinder::class.java)
            val inputManager = asInterface.invoke(null, inputBinder)

            // 4) Call hidden injectInputEvent(InputEvent, int mode)
            val injectMethod = inputManager.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            // mode: 0 = INJECT_INPUT_EVENT_MODE_ASYNC
            injectMethod.invoke(inputManager, event, 0)

            // 5) Recycle the event
            event.recycle()
        }

        @SuppressLint("PrivateApi")
        fun listenForInputEvents() {
            val path = "/dev/input/event2" // adjust as needed
            Thread{
                try {
                    FileInputStream(path).use { fis ->
                        BufferedInputStream(fis).use { bis ->
                            val buf = ByteArray(24)
                            while (true) {
                                val read = bis.read(buf)
                                if (read != buf.size) {
                                    System.err.println("Read incomplete event, got " + read + " bytes")
                                    break
                                }

                                val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                                val sec = bb.getLong()
                                val usec = bb.getLong()
                                val type = bb.getShort()
                                val code = bb.getShort()
                                val value = bb.getInt()

                                System.out.printf(
                                    "Event - time: %d.%06d, type: 0x%04x, code: 0x%04x, value: %d%n",
                                    sec, usec, type.toInt() and 0xFFFF, code.toInt() and 0xFFFF, value
                                )
                            }
                        }
                    }
                } catch (e: IOException) {
                    System.err.println("Error reading input_event: " + e.message)
                }
            }.start()
        }









        fun getBuiltInDisplay(): IBinder? {
            val method = SurfaceControlClass.getMethod("getInternalDisplayToken")
            return method.invoke(null) as IBinder?
        }

        fun setDisplayPowerMode(mode:Int){
            val method: Method = SurfaceControlClass.getMethod(
                "setDisplayPowerMode",
                IBinder::class.java,
                Int::class.javaPrimitiveType
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && DisplayControlClass != null) {
                val getPhysicalDisplayIdsMethod: Method =
                    DisplayControlClass!!.getMethod("getPhysicalDisplayIds")
                val getPhysicalDisplayTokenMethod: Method = DisplayControlClass!!.getMethod(
                    "getPhysicalDisplayToken",
                    Long::class.javaPrimitiveType
                )

                val displayIds = getPhysicalDisplayIdsMethod.invoke(null) as LongArray?
                if (displayIds != null) {
                    for (displayId in displayIds) {
                        val token =
                            getPhysicalDisplayTokenMethod.invoke(null, displayId) as IBinder?
                        method.invoke(null,token, mode)
                    }
                }
            }else{
                method.invoke(null, getBuiltInDisplay(), mode)
            }
        }

        // helper to check if screen is on
        fun isInteractive(ctx: Context): Boolean {
            return try {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isInteractive ?: false
            } catch (e: Exception) {
                false
            }
        }

        fun goToSleepRobust(ctx: Context): Boolean {
            val now = SystemClock.uptimeMillis()

            // 1) Try IPowerManager.goToSleep(long, int, int) via ServiceManager (preferred)
            try {
                val svcMgrClass = Class.forName("android.os.ServiceManager")
                val getService: Method = svcMgrClass.getMethod("getService", String::class.java)
                val imBinder = getService.invoke(null, "power") as IBinder

                val ipmStubClass = Class.forName("android.os.IPowerManager\$Stub")
                val asInterface: Method = ipmStubClass.getMethod("asInterface", IBinder::class.java)
                val ipm = asInterface.invoke(null, imBinder)

                if (ipm != null) {
                    // Try several possible signatures seen across versions / OEMs
                    val signatures = listOf(
                        arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                        arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                        arrayOf(Long::class.javaPrimitiveType)
                    )

                    for (sig in signatures) {
                        try {
                            val method = ipm.javaClass.getMethod("goToSleep", *sig)
                            // Prepare args: when 3 params -> (time, reason, flags). Use 0 for reason/flags.
                            val args = when (sig.size) {
                                3 -> arrayOf(now, 0, 0)
                                2 -> arrayOf(now, 0)
                                1 -> arrayOf(now)
                                else -> arrayOf(now)
                            }
                            method.invoke(ipm, *args)
                            // If invoke didn't throw, success.
                            return true
                        } catch (ignored: NoSuchMethodException) {
                            // try next signature
                        } catch (e: Exception) {
                            // Invocation may throw; log & try next fallback
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                // couldn't use IPowerManager route; try next fallback
                e.printStackTrace()
            }

            // 2) Try PowerManager.goToSleep(long) on the system PowerManager (may be hidden; requires permission)
            try {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    try {
                        // Prefer direct method if available
                        val pmClass = pm.javaClass
                        try {
                            val mGoToSleep = pmClass.getMethod("goToSleep", Long::class.javaPrimitiveType)
                            mGoToSleep.invoke(pm, now)
                            return true
                        } catch (nsme: NoSuchMethodException) {
                            // try alternate signature (if any) via reflection
                            // some platforms use different signatures; attempt fallback reflective tries
                            val altSignatures = listOf(
                                arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                                arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            )
                            for (sig in altSignatures) {
                                try {
                                    val method = pmClass.getMethod("goToSleep", *sig)
                                    val args = when (sig.size) {
                                        3 -> arrayOf(now, 0, 0)
                                        2 -> arrayOf(now, 0)
                                        else -> arrayOf(now)
                                    }
                                    method.invoke(pm, *args)
                                    return true
                                } catch (ignored: NoSuchMethodException) {
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3) Fallback: run shell input keyevent 26 (power). This toggles power; if screen was on, it will turn off.
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("input", "keyevent", "26"))
                // Wait briefly for exit; success if process exit code is 0
                val rc = proc.waitFor()
                if (rc == 0) return true
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Nothing worked
            return false
        }

        fun wakeUpRobust(ctx:Context): Boolean {
            val now = SystemClock.uptimeMillis()

            // 1) Try IPowerManager.wakeUp(...) via ServiceManager (preferred)
            try {
                val svcMgrClass = Class.forName("android.os.ServiceManager")
                val getService = svcMgrClass.getMethod("getService", String::class.java)
                val powerBinder = getService.invoke(null, "power") as IBinder

                val ipmStub = Class.forName("android.os.IPowerManager\$Stub")
                val asInterface = ipmStub.getMethod("asInterface", IBinder::class.java)
                val ipm = asInterface.invoke(null, powerBinder)

                if (ipm != null) {
                    val trySigs = listOf(
                        arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java),
                        arrayOf(Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
                        arrayOf(Long::class.javaPrimitiveType)
                    )

                    for (sig in trySigs) {
                        try {
                            val method = ipm.javaClass.getMethod("wakeUp", *sig)
                            val args = when (sig.size) {
                                3 -> arrayOf(now, 0, "wakeup")       // (time, reason, details)
                                2 -> arrayOf(now, true)             // (time, wakeReasonBoolean) — some OEMs vary
                                else -> arrayOf(now)
                            }
                            method.invoke(ipm, *args)
                            // small pause to let system become interactive
                            Thread.sleep(150)
                            if (isInteractive(ctx)) return true
                        } catch (ignored: NoSuchMethodException) {
                            // try next signature
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2) Try PowerManager.wakeUp(...) via reflection on the local PowerManager
            try {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    try {
                        // Common signature: wakeUp(long)
                        val pmClass = pm.javaClass
                        try {
                            val m = pmClass.getMethod("wakeUp", Long::class.javaPrimitiveType)
                            m.invoke(pm, now)
                            Thread.sleep(150)
                            if (isInteractive(ctx)) return true
                        } catch (nsme: NoSuchMethodException) {
                            // try alt signature: wakeUp(long, int, String)
                            val altSigs = listOf(
                                arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java),
                                arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            )
                            for (sig in altSigs) {
                                try {
                                    val m = pmClass.getMethod("wakeUp", *sig)
                                    val args = when (sig.size) {
                                        3 -> arrayOf(now, 0, "wakeup")
                                        2 -> arrayOf(now, 0)
                                        else -> arrayOf(now)
                                    }
                                    m.invoke(pm, *args)
                                    Thread.sleep(150)
                                    if (isInteractive(ctx)) return true
                                } catch (ignoreNsme: NoSuchMethodException) {
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3) Try acquiring a wake lock with ACQUIRE_CAUSES_WAKEUP (may require WAKE_LOCK permission)
            try {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    try {
                        // Use deprecated flags — system-level code typically still accepts them.
                        // They are deprecated for apps, but when running as shell/system they work.
                        val FULL_WAKE = try {
                            // fallback if missing
                            val f = PowerManager::class.java.getField("FULL_WAKE_LOCK")
                            f.getInt(null)
                        } catch (t: Throwable) {
                            // fallback constant value (older SDKs): 0x1
                            PowerManager.FULL_WAKE_LOCK
                        }

                        val ACQUIRE_CAUSES_WAKEUP = try {
                            val f = PowerManager::class.java.getField("ACQUIRE_CAUSES_WAKEUP")
                            f.getInt(null)
                        } catch (t: Throwable) {
                            // fallback to constant from SDK
                            try {
                                val f2 = PowerManager::class.java.getField("ACQUIRE_CAUSES_WAKEUP")
                                f2.getInt(null)
                            } catch (_: Throwable) {
                                0x10000000 // best-effort fallback if reflection fails
                            }
                        }

                        val flags = FULL_WAKE or ACQUIRE_CAUSES_WAKEUP
                        // newWakeLock is public API
                        val wl = pm.newWakeLock(flags, "RobustWake:waketask")
                        // Acquire briefly
                        wl.acquire(1000L) // 1 second
                        Thread.sleep(100) // allow screen to turn on
                        if (isInteractive(ctx)) {
                            try { wl.release() } catch (_: Throwable) {}
                            return true
                        }
                        try { wl.release() } catch (_: Throwable) {}
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4) Fallback: shell input keyevent 224 (KEYCODE_WAKEUP). If that fails, try 26 (POWER).
            try {
                // explicit wakeup key
                var rc = -1
                try {
                    val p1 = Runtime.getRuntime().exec(arrayOf("input", "keyevent", "224"))
                    rc = p1.waitFor()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(150)
                if (isInteractive(ctx)) return true

                // try power key as last resort
                try {
                    val p2 = Runtime.getRuntime().exec(arrayOf("input", "keyevent", "26"))
                    val rc2 = p2.waitFor()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(150)
                if (isInteractive(ctx)) return true
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // nothing worked
            return false
        }
    }
}

